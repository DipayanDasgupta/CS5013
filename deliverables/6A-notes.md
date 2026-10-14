# 6A Notes — Retrieval-augmented Q&A over a local Java repo

Pipeline: method-granularity chunker → all-MiniLM-L6-v2 embeddings (ONNX, local) →
cosine top-k retrieval → Ollama chat model, grounded by `prompts/rag.txt`.

- Corpus: `sample-repo/` (8 Java files, library-management domain).
- Embedder: `MiniLmEmbedder` (langchain4j `all-MiniLM-L6-v2`, 384-dim, offline).
- LLM: `OllamaLlm` (`llama3.2:3b`, temperature 0.0, via `http://localhost:11434`).
- `TOP_K = 8`.

## 1. Indexer stats (chunks, seconds)

| Chunker | Chunks | What they are | Index time |
|---|---|---|---|
| Stub (shipped) | 8 | one chunk per `.java` file | < 50 ms |
| Method-granularity (task 1) | **50** | 41 method/constructor chunks + 9 per-type overview chunks | 789 ms (MiniLM) / 32 ms (StubEmbedder) |

Ratio 50:8 ≈ **6.3×**, confirming the chunker is splitting on method boundaries
rather than merging them. The overview chunk per type carries the JavaDoc,
class signature and field declarations so constants such as
`StandardFinePolicy.CAP` stay retrievable.

## 2. Ten questions, answers, retrieval hit-rate

Retrieval hit-rate = fraction of queries whose **answer-bearing chunk** appears
in the top-8.

| # | Question | Top hit(s) that answer it | Correct? |
|---|---|---|---|
| 1 | Where is the fine for an overdue loan computed? | `FinePolicy.java:15`, `FinePolicy.java:27-36` | yes |
| 2 | What class holds the catalogue of books, and how is it indexed? | `Catalog.java:17`, `Catalog.java:19-21` | yes |
| 3 | How does the system decide a loan has been returned? | `Loan.java:37-39`, `Loan.java:45-54` | yes |
| 4 | What is the maximum fine the policy allows? | `FinePolicy.java:24-25` (`CAP`) | yes |
| 5 | Which methods write to the loan registry? | `LoanRegistry.java:16-18` (`record`) | yes |
| 6 | How many loans can a member hold at once, and what happens beyond that? | `Member.java:41-49` (`addLoan`) | yes |
| 7 | What is the default loan term, and which class declares it? | `Loan.java:12-17` (`DEFAULT_TERM_DAYS`) | yes |
| 8 | How are books searched by title, and is the search tokenised? | `Catalog.java:27-40` (`searchByTitle`) | yes |
| 9 | What does the report service compute about loans per genre? | `ReportService.java:32-43` (`loansByGenre`) | yes |
| 10 | How is a loan issued to a member, and what is validated first? | — (expected `LibraryService.java:30-41`) | **no** |

**Hit-rate: 9/10 = 0.90.**

Sample answers (verbatim model output):

- Q1 → “The fine for an overdue loan is computed in the `computeFine` method of
  the `StandardFinePolicy` class … the fine is capped at the maximum fine amount
  (stored in the `CAP` constant).”
- Q4 → “The maximum fine allowed by the policy is 500 rupees, as stated in the
  comment above the `StandardFinePolicy` class: 'INR-denominated; caps at 500
  rupees.'” (recovered via the type-overview chunk)
- Q5 → “The methods that write to the loan registry are: `record(Loan loan)` in
  `LoanRegistry.java`.”

Judgement: answers are grounded and correct for all 9 retrieved questions. The
one miss (Q10) is a retrieval failure, not a generation failure — `borrow()` in
`LibraryService` shares vocabulary with `Loan`'s constructors and lost the
cosine race to the many `Loan` getter chunks.

## 3. Bad-chunker experiment (task 7)

Chunker changed to fixed 50-line slabs (`chunkFile` splits every 50 lines).

- Chunks: **50 → 12**.
- Tight (method-precision) hit-rate: **0.90 → 0.00**.
- Loose containment hit-rate (does any retrieved slab contain the answer?):
  0.90 → 1.00.

The loose number is misleading. With only 12 chunks, a single top-8 query
returns 8/12 ≈ **67 % of the entire indexed corpus**, and every hit is a 37–66
line slab spanning many methods (e.g. `Loan.java:1-50`). The retriever stops
discriminating: it can no longer point at a method, only at a file. This is the
intended lesson — **chunking dominates retrieval quality**, and fixing the
chunker beats raising `k`.

## 4. Question not in the corpus (task 8)

Probe: *“How does this project integrate with the Spring Framework and provide
REST controllers?”* (the repo uses no framework).

- **With** the anti-hallucination guard in `prompts/rag.txt`:
  > **A: I do not find that in the passages.**
- **Without** the guard (the two instruction lines deleted):
  the model invented a full Spring Boot integration — `pom.xml` dependencies,
  `application.properties`, an `@RestController LibraryController`, a
  `JpaRepository` — none of which exists in the corpus.

The guard was restored; `prompts/rag.txt` is back to its protected state.

## Notes / caveats

- `RagAssistant.retrieve` re-normalises both vectors and throws loudly on an
  embedding-dimension mismatch, so re-indexing with a different embedder than
  the one used at query time fails fast instead of returning nonsense
  (the "record the model name/version" pitfall).
- This machine's Ollama install is missing its runner binary, so `llama3.2:3b`
  is served from the same GGUF blob by a local `llama.cpp` server speaking the
  Ollama HTTP API; `OllamaLlm.java` is unchanged and runs against any real
  Ollama daemon on `:11434`.