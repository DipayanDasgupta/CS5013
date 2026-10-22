# 6B Reflection — Agentic ReAct loop over a book catalogue

Model: `llama3.2:3b` (local, temperature 0.0). Loop: `AgentDriver` with a step
budget and a token budget; one trace log per experiment under `traces/`.

## 1. Which tool did the agent prefer, and why?

With **sharp** descriptions the agent went straight to the cheap tool:

| Exp | Question | Tool calls | Steps | Tokens | Stop |
|---|---|---|---|---|---|
| `exp1_sharp_tools` | Le Guin in the 1970s | `search_books("Ursula K. Le Guin\|1970\|1979")` | 2 | 636 | final answer ("1") |

It never touched `list_books`. The reason is in the prompt: `list_books` is
described as *"returns the WHOLE catalogue; expensive, use only if the query
cannot be expressed as a filter"*, and the question is expressible as a filter.
A capable-enough model reads the cost hint and follows it. The observation it
got back was exactly one record (`The Dispossessed`, 1974), so the final "1" is
correct and grounded.

Note the agent still behaved imperfectly in one respect: it wrapped the argument
in quotes (`"...|1970|1979"`). That is why `SearchBooksTool` strips quote
characters — without that, the author filter matched nothing, the agent saw `[]`
and then **hallucinated** the answer "5" after falling back to `list_books`.
Tool-argument hygiene is as important as tool choice.

## 2. How much did tool-description quality affect behaviour?

Enormously. Three single-line edits to the tool descriptions, same question:

| Exp | Change | Observed behaviour | Steps | Tokens |
|---|---|---|---|---|
| `exp1` | (sharp) | `search_books("Ursula K. Le Guin\|1970\|1979")` → correct answer | 2 | 636 |
| `exp2a` | search_books → *"Search for books."* | invented a JSON argument `{"author": "...", "decade": "1970s"}`, got no match, **fell back to the greedy `list_books`** | 3 | 1699 |
| `exp2b` | list_books → *"Get books."* | called `search_books` with the same correct argument **7 times**, never emitted a Final Answer → hit the step budget | 8 | 4104 |
| `exp2c` | argument spec → *"Argument: a query string."* | oscillated `search_books("Ursula K. Le Guin 1970s")` / `list_books()` / malformed `list_books(Observation: [)` | 7 | 11487 |

Every degradation came from deleting or weakening *one* descriptive sentence.
The token cost per run rose 2.6×–18× and two of three vagued runs failed to
terminate on their own. The observation-syntax text also matters: in `exp2c` the
model pasted an `Observation:` line into an `Action Input:` and the parser
correctly bailed out (`no Action/Action Input pair parsed`).

## 3. Budgets

| Exp | Budget | Result |
|---|---|---|
| `exp3_step_budget_3` | stepBudget = 3, hard question | 2 `search_books` calls, then `STOP: step budget (3) exhausted` — clean abort, no crash |
| `exp4_token_budget_100` | tokenBudget = 100 | `STOP: token budget (100) exhausted` after 1 step (275 tokens counted) |

Both budgets fire deterministically because they are checked *inside* the loop
before the next LLM call; the step budget alone is not enough once prompts grow,
which is exactly what the token budget catches.

## 4. What a real production driver would need on top of this

- **Typed tool schemas.** Free-text `Action Input` parsing (here a `|`-delimited
  string plus quote-stripping) is brittle. Real drivers hand the model a JSON
  schema and validate the arguments before invoking the tool.
- **Structured, machine-readable traces.** The Thought text is the model
  narrating itself — it can be honest or a rationalisation, and in `exp1` it
  confidently answered "5" from an empty observation. Traces must record the
  actual tool I/O (done here) and never be trusted as ground truth.
- **Retries / loop detection.** `exp2b` called the identical tool with the
  identical argument seven times. A production driver needs repeated-action
  detection plus backoff, not just a step cap.
- **Real token accounting.** `chars/4` is an estimate; production needs the
  model's own token counts and a cost budget in currency, not just tokens.
- **Observability + timeouts + sandboxing.** Per-call latency/cost metrics,
  hard wall-clock timeouts, and confinement of tool side effects.
