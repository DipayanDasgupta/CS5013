# Prompts used in 6A

## Generation prompt template — `prompts/rag.txt` (shipped, restored)

```
You are a code-search assistant answering questions about a Java repository.
Use ONLY the context below. If the answer is not contained in the context, reply
exactly: "I do not find that in the passages." Do not use outside knowledge.

Question:
{query}

Context (top-K retrieved chunks):
{context}

Answer:
```

`{query}` is substituted with the student question; `{context}` is the top-8
retrieved chunks, serialised as:

```
--- <source>:<startLine>-<endLine> ---
<chunk text, already prefixed with "// File: <path>" and the class signature>
```

Substitution is done via sentinels first (`RagAssistant.buildPrompt`), so a
literal `{context}` or `{query}` occurring inside retrieved source is never
expanded a second time.

## Anti-hallucination experiment (task 8)

Two instruction lines were removed for the negative run:

```
Use ONLY the context below. If the answer is not contained in the context, reply
exactly: "I do not find that in the passages." Do not use outside knowledge.
```

| Condition | Probe question | Model answer |
|---|---|---|
| Guard present | How does this project integrate with the Spring Framework and provide REST controllers? | `I do not find that in the passages.` |
| Guard removed | same | invented Spring Boot setup (`spring-boot-starter-web`, `@RestController`, `JpaRepository`, …) |

Guard was restored afterwards.

## The 11 questions in `Main.QUESTIONS`

These are the *queries* submitted to the retriever (not LLM prompts); the LLM
prompt is always the single template above.

1. Where is the fine for an overdue loan computed?
2. What class holds the catalogue of books, and how is it indexed?
3. How does the system decide a loan has been returned?
4. What is the maximum fine the policy allows?
5. Which methods write to the loan registry?
6. How many loans can a member hold at once, and what happens beyond that?
7. What is the default loan term, and which class declares it?
8. How are books searched by title, and is the search tokenised?
9. What does the report service compute about loans per genre?
10. How is a loan issued to a member, and what is validated first?
11. How does this project integrate with the Spring Framework for dependency
    injection and REST controllers? *(task 8 out-of-corpus probe)*