import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Walks a directory of .java files, splits each file into method-level
 * chunks, embeds each chunk, and stores the result in an in-memory index.
 *
 * Session 6A: complete {@link #chunkFile(Path)} and {@link #embed(String)}.
 */
public class RepoIndexer {

    private final Embedder embedder;
    private final List<Chunk> index = new ArrayList<>();
    private final String embeddingModel;

    public RepoIndexer(Embedder embedder) {
        this.embedder = embedder;
        this.embeddingModel = describe(embedder);
    }

    /** Walks dir, indexing every *.java file below it. */
    public void indexDirectory(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .sorted()
                 .forEach(this::indexFile);
        }
    }

    private void indexFile(Path file) {
        try {
            for (Chunk c : chunkFile(file)) {
                index.add(c.withEmbedding(embed(c.content())));
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to index " + file, e);
        }
    }

    private static final Pattern TYPE_DECL =
            Pattern.compile("\\b(?:class|interface|enum|record|@interface)\\s+\\w+");
    private static final Pattern CONTROL_KEYWORD =
            Pattern.compile("\\b(if|for|while|switch|catch|do|else|try|finally|synchronized)\\b");
    private static final Pattern CALLEE = Pattern.compile("(?:^|[\\s.])(?:new|return|throw)\\s*\\w*\\s*\\(");

    /**
     * Splits a .java file into method-granularity chunks using a
     * comment- and string-aware, brace-balanced scan rather than a regex
     * over method bodies. Emits one chunk per method/constructor, plus one
     * overview chunk per top-level type carrying its JavaDoc, signature and
     * field declarations so constants stay retrievable.
     *
     * Every chunk is prefixed with the file path and the enclosing class
     * signature, and keeps any JavaDoc that immediately precedes the member.
     */
    protected List<Chunk> chunkFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        int n = lines.size();
        if (n == 0) {
            return List.of();
        }

        int[] depth = new int[n];
        boolean[] code = new boolean[n];
        String[] masked = maskLines(lines, depth, code);

        List<Chunk> out = new ArrayList<>();
        for (int decl = 0; decl < n; decl++) {
            if (before(depth, decl) != 0 || !code[decl] || !TYPE_DECL.matcher(masked[decl]).find()) {
                continue;
            }
            int open = openBraceLine(depth, decl, n);
            int close = closeBraceLine(depth, open, n);
            if (open < 0 || close < 0) {
                continue;
            }
            int typeStart = withTrivia(lines, decl, 0);
            String typeHeader = header(lines, typeStart, decl);

            List<int[]> fields = new ArrayList<>();
            for (int[] region : memberRegions(depth, code, lines, open, close)) {
                String header = headerText(lines, region[0], signatureEnd(masked, region));
                if (isMethod(header)) {
                    int start = withTrivia(lines, region[0], typeStart);
                    String body = String.join("\n", lines.subList(start, region[1] + 1));
                    out.add(new Chunk(file.toString(), start + 1, region[1] + 1,
                            "// File: " + file + "\n" + typeHeader + "\n" + body + "\n", null));
                } else if (!TYPE_DECL.matcher(header).find()) {
                    fields.add(new int[]{withTrivia(lines, region[0], typeStart), region[1]});
                }
            }
            if (!fields.isEmpty()) {
                StringBuilder body = new StringBuilder();
                for (int[] f : fields) {
                    body.append(String.join("\n", lines.subList(f[0], f[1] + 1))).append("\n\n");
                }
                out.add(new Chunk(file.toString(), fields.get(0)[0] + 1,
                        fields.get(fields.size() - 1)[1] + 1,
                        "// File: " + file + "\n" + typeHeader + "\n"
                                + body.toString().stripTrailing() + "\n", null));
            }
        }
        if (out.isEmpty()) {
            out.add(new Chunk(file.toString(), 1, n,
                    "// File: " + file + "\n" + String.join("\n", lines) + "\n", null));
        }
        return out;
    }

    /**
     * TODO(session-6A): swap the stub for a real embedder implementation
     * (langchain4j all-MiniLM-L6-v2 is one option). The signature stays
     * the same; only the interface binding at construction time changes.
     */
    protected float[] embed(String text) {
        return embedder.embed(text);
    }

    public List<Chunk> getIndex() {
        return index;
    }

    public int size() {
        return index.size();
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    private static String describe(Embedder e) {
        String name = e.getClass().getSimpleName();
        String text = String.valueOf(e);
        return name.equals(text) ? name : name + " " + text;
    }

    private static String header(List<String> lines, int start, int end) {
        return String.join("\n", lines.subList(start, end + 1));
    }

    private static int before(int[] depth, int line) {
        return line == 0 ? 0 : depth[line - 1];
    }

    private static int openBraceLine(int[] depth, int decl, int n) {
        for (int i = decl + 1; i < n; i++) {
            if (depth[i] >= 1) {
                return i;
            }
        }
        return -1;
    }

    private static int closeBraceLine(int[] depth, int open, int n) {
        for (int i = open; i < n; i++) {
            if (depth[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Contiguous spans at class-body depth: [startLine, endLine] inclusive, 0-based. */
    private static List<int[]> memberRegions(int[] depth, boolean[] code, List<String> lines, int open, int close) {
        List<int[]> out = new ArrayList<>();
        int i = open + 1;
        while (i < close) {
            if (before(depth, i) == 1 && code[i] && !isAnnotationLine(lines.get(i))) {
                int s = i;
                int e = i;
                while (e < close && depth[e] > 1) {
                    e++;
                }
                out.add(new int[]{s, e});
                i = e + 1;
            } else {
                i++;
            }
        }
        return out;
    }

    private static int signatureEnd(String[] masked, int[] region) {
        for (int i = region[0]; i <= region[1]; i++) {
            String m = masked[i];
            if (m.indexOf('{') >= 0 || m.indexOf(';') >= 0 || m.indexOf('}') >= 0) {
                return i;
            }
        }
        return region[1];
    }

    private static String headerText(List<String> lines, int s, int e) {
        return String.join("\n", lines.subList(s, e + 1));
    }

    /**
     * A member is a method when its declaration -- everything before the
     * body, minus any field-initialiser tail -- carries a parameter list.
     * Cutting at the first top-level '=' is what separates
     * {@code int x = new BigDecimal("5")} from {@code int x(int y)}.
     */
    private static boolean isMethod(String header) {
        String declaration = declaration(header);
        if (declaration.isBlank() || CONTROL_KEYWORD.matcher(declaration).find()) {
            return false;
        }
        return declaration.contains("(") && !CALLEE.matcher(declaration).find();
    }

    private static String declaration(String header) {
        String flat = header.replaceAll("(?m)^\\s*@\\w+.*$", "");
        int depth = 0;
        for (int i = 0; i < flat.length(); i++) {
            char ch = flat.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == '=' && depth == 0) {
                return flat.substring(0, i);
            }
        }
        return flat;
    }

    private static int withTrivia(List<String> lines, int start, int floor) {
        int j = start;
        while (j - 1 > floor && (isCommentLine(lines.get(j - 1)) || isAnnotationLine(lines.get(j - 1)))) {
            j--;
        }
        return j;
    }

    private static boolean isCommentLine(String line) {
        String t = line.trim();
        return t.startsWith("*") || t.startsWith("/*") || t.endsWith("*/") || t.startsWith("//");
    }

    /** Annotations belong to the member that follows them, not to their own region. */
    private static boolean isAnnotationLine(String line) {
        return line.trim().startsWith("@");
    }

    /**
     * Returns a copy of each line with comments and literal bodies blanked
     * out, and fills in the brace depth reached at each line end. Braces
     * inside JavaDoc ({@link Foo}), strings and chars therefore never
     * disturb the brace balance.
     */
    private static String[] maskLines(List<String> lines, int[] depth, boolean[] code) {
        String[] masked = new String[lines.size()];
        boolean inBlock = false;
        boolean inText = false;
        boolean inStr = false;
        boolean inChr = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            StringBuilder m = new StringBuilder(line.length());
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                char nx = c + 1 < line.length() ? line.charAt(c + 1) : '\0';
                if (inBlock) {
                    m.append(' ');
                    if (ch == '*' && nx == '/') {
                        m.append(' ');
                        inBlock = false;
                        c++;
                    }
                    continue;
                }
                if (inText) {
                    m.append(' ');
                    if (ch == '\\' && nx != '\0') {
                        m.append(' ');
                        c++;
                    } else if (ch == '"' && nx == '"') {
                        m.append(' ');
                        inText = false;
                        c++;
                    }
                    continue;
                }
                if (inStr) {
                    m.append(' ');
                    if (ch == '\\' && nx != '\0') {
                        m.append(' ');
                        c++;
                    } else if (ch == '"') {
                        inStr = false;
                    }
                    continue;
                }
                if (inChr) {
                    m.append(' ');
                    if (ch == '\\' && nx != '\0') {
                        m.append(' ');
                        c++;
                    } else if (ch == '\'') {
                        inChr = false;
                    }
                    continue;
                }
                if (ch == '/' && nx == '/') {
                    for (int j = c; j < line.length(); j++) {
                        m.append(' ');
                    }
                    break;
                }
                if (ch == '/' && nx == '*') {
                    inBlock = true;
                    m.append("  ");
                    c++;
                    continue;
                }
                if (ch == '"' && nx == '"' && c + 2 < line.length() && line.charAt(c + 2) == '"') {
                    inText = true;
                    m.append("   ");
                    c += 2;
                    continue;
                }
                if (ch == '"') {
                    inStr = true;
                    m.append(' ');
                    continue;
                }
                if (ch == '\'') {
                    inChr = true;
                    m.append(' ');
                    continue;
                }
                m.append(ch);
            }
            int delta = 0;
            for (int c = 0; c < m.length(); c++) {
                if (m.charAt(c) == '{') {
                    delta++;
                } else if (m.charAt(c) == '}') {
                    delta--;
                }
            }
            masked[i] = m.toString();
            depth[i] = before(depth, i) + delta;
            code[i] = !m.toString().isBlank();
        }
        return masked;
    }
}