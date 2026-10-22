import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * search_books: targeted lookup by author and year range.
 *
 * Argument format: {@code author|year_from|year_to}; any empty part means
 * "no constraint" (e.g. {@code "Ursula K. Le Guin|1970|1979"} or
 * {@code "|1990|2000"}).
 *
 * books.json is a flat array of flat records, one per line, so a
 * line-oriented regex scan avoids pulling in a JSON library.
 */
public class SearchBooksTool implements Tool {

    private static final Pattern RECORD = Pattern.compile("\\{[^{}]*\\}");

    private final Path catalogue;

    public SearchBooksTool() {
        this(Path.of("books.json"));
    }

    public SearchBooksTool(Path catalogue) {
        this.catalogue = catalogue;
    }

    @Override
    public String name() {
        return "search_books";
    }

    @Override
    public String call(String arg) {
        try {
            return filter(arg, Files.readString(catalogue));
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    static String filter(String arg, String json) {
        String[] parts = (arg == null ? "" : arg).split("\\|", -1);
        String author = parts.length > 0 ? unquote(parts[0]) : "";
        int from = year(parts, 1, Integer.MIN_VALUE);
        int to = year(parts, 2, Integer.MAX_VALUE);

        List<String> matches = new ArrayList<>();
        Matcher records = RECORD.matcher(json);
        while (records.find()) {
            String record = records.group();
            if (!author.isEmpty() && !text(record, "author").toLowerCase().contains(author.toLowerCase())) {
                continue;
            }
            int published = number(record, "year");
            if (published < from || published > to) {
                continue;
            }
            matches.add(record.replaceAll("\\s+", " ").strip());
        }
        return "[" + String.join(", ", matches) + "]";
    }

    private static int year(String[] parts, int index, int fallback) {
        if (parts.length <= index || parts[index].isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(unquote(parts[index]));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Models routinely wrap the whole argument, and each part, in quotes
     * (e.g. {@code "Ursula K. Le Guin|1970|1979"}). Neither author names nor
     * years in this catalogue contain quotes, so drop them all.
     */
    private static String unquote(String part) {
        String s = part == null ? "" : part.trim();
        while (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s.replace("\"", "").replace("'", "").trim();
    }

    private static String text(String record, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(record);
        return m.find() ? m.group(1) : "";
    }

    private static int number(String record, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(record);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MIN_VALUE;
    }
}