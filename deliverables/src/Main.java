import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * End-to-end driver for Session 6A:
 *   1. index sample-repo/;
 *   2. for each question in {@link #QUESTIONS}, retrieve the top-k
 *      chunks and ask the LLM for an answer.
 *
 * Students add their own questions to {@link #QUESTIONS} (task 5 of
 * handson6.tex) and re-run with {@code make run}.
 *
 * The pipeline below runs against a real embedder (all-MiniLM-L6-v2) and a
 * real local model (Ollama). Set STUB=1 to fall back to the offline
 * {@link StubEmbedder} + {@link StubLlmClient} pair.
 */
public class Main {

    /**
     * Task (5): add nine more questions targeting the sample-repo/
     * library-management domain (books, members, loans, fines).
     *
     * The eleventh question is the task (8) probe: its answer is not in the
     * corpus at all.
     */
    private static final String[] QUESTIONS = {
        "Where is the fine for an overdue loan computed?",
        "What class holds the catalogue of books, and how is it indexed?",
        "How does the system decide a loan has been returned?",
        "What is the maximum fine the policy allows?",
        "Which methods write to the loan registry?",
        "How many loans can a member hold at once, and what happens beyond that?",
        "What is the default loan term, and which class declares it?",
        "How are books searched by title, and is the search tokenised?",
        "What does the report service compute about loans per genre?",
        "How is a loan issued to a member, and what is validated first?",
        "How does this project integrate with the Spring Framework for dependency injection and REST controllers?",
    };

    private static final int TOP_K = 8;

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "sample-repo");
        boolean stubs = args.length > 1 && args[1].equals("--stub");

        // -----------------------------------------------------------
        // 1. Pipeline construction.
        // -----------------------------------------------------------
        Embedder    emb = stubs ? new StubEmbedder() : new MiniLmEmbedder();
        LlmClient   llm = stubs ? new StubLlmClient() : new OllamaLlm();
        RepoIndexer idx = new RepoIndexer(emb);

        // -----------------------------------------------------------
        // 2. Index the corpus; report count + wall-clock time.
        // -----------------------------------------------------------
        Instant t0 = Instant.now();
        idx.indexDirectory(dir);
        Duration elapsed = Duration.between(t0, Instant.now());
        System.out.printf("Indexed %d chunk(s) from %s in %d ms%n",
                idx.size(), dir, elapsed.toMillis());
        System.out.printf("Embedder: %s | LLM: %s%n%n", idx.embeddingModel(), llm);

        // -----------------------------------------------------------
        // 3. Ask each question.  Print retriever hits + LLM answer.
        //    (retrieve() and answer() both re-rank; the redundancy is
        //    deliberate so the trace shows what the retriever picked.)
        // -----------------------------------------------------------
        RagAssistant ra = new RagAssistant(
                idx, emb, llm, Path.of("prompts/rag.txt"));
        for (String q : QUESTIONS) {
            System.out.println("=== Q: " + q);
            List<Chunk> hits = ra.retrieve(q, TOP_K);
            for (Chunk c : hits) {
                System.out.println("  hit: " + c.source()
                        + ":" + c.startLine() + "-" + c.endLine());
            }
            System.out.println("--- A: " + ra.answer(q, TOP_K));
            System.out.println();
        }
    }
}