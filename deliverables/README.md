# Deliverables — CS5013 Module 6, Session 6A (RAG over a local Java repo)

Self-contained copy of the m6-lab-a solution.

## Layout

    src/               JDK-only + langchain4j sources; the four files the PDF asks for are
                       RepoIndexer.java, RagAssistant.java, Main.java, MiniLmEmbedder.java
    test/              RepoIndexerTest.java (seed tests)
    prompts/rag.txt    generation prompt template (restored anti-hallucination guard)
    sample-repo/       the indexed corpus (8 Java files)
    evidence/          raw run logs for the good/bad chunker and the task-8 probe
    Makefile, pom.xml  build + `make langchain4j`
    6A-notes.md        indexer stats, 10 Q&A + hit-rate, bad-chunker, out-of-corpus
    prompts.md         prompt template, guard experiment, question list

## Deliverables requested by the PDF

- `src/RepoIndexer.java` — `chunkFile(Path)` filled in (method-granularity chunker).
- `src/RagAssistant.java` — `retrieve(String,int)` and `buildPrompt(String,List)` filled in.
- `src/Main.java` — extended `QUESTIONS` array (10 domain questions + task-8 probe).
- `prompts/rag.txt` — restored after the task-8 experiment.
- `6A-notes.md` — indexer stats, ten Q&A + retrieval hit-rate, bad-chunker result,
  out-of-corpus result.

## Headline results

| Metric | Good chunker (methods) | Bad chunker (50 lines) |
|---|---|---|
| Chunks | 50 (41 methods + 9 type overviews) | 12 |
| Retrieval hit-rate @8 | **9/10 = 0.90** | 0.00 (method-precision) |
| Index time | 789 ms | 287 ms |

Out-of-corpus probe: `I do not find that in the passages.` with the guard,
invented Spring Boot integration without it.

## Running

The default pipeline uses a real embedder (`all-MiniLM-L6-v2`) and a real LLM
(Ollama `llama3.2:3b`). After `make langchain4j`:

    make deps && make test     # 3/3 green, offline (tests use the stubs)
    make run                   # real embedder + real LLM over sample-repo/

`make run <dir> --stub` swaps in `StubEmbedder` + `StubLlmClient` for a
network-free smoke test.

### Environment note

This machine's Ollama 0.33.2 install ships without its `llama-server` runner
binary, so `llama3.2:3b` was served from the same local GGUF blob by a small
`llama.cpp` shim exposing the Ollama HTTP API on `:11434`. `src/OllamaLlm.java`
is unmodified and targets any standard Ollama daemon.