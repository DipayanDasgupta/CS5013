# PROMPTS.md

## Session 5A – Part A: Baseline coverage

Command: `make coverage`, report at `coverage/index.html`.

Baseline (three seed tests), TaxCalculator class:
- Line coverage: 20/41 ≈ 49%
- Branch coverage: 15/44 ≈ 34%

Methods with < 50% branch coverage:
- computeVAT: 6/14 ≈ 43%
- roundToPaise: 0/2 = 0%
- isEligibleForReturn: 0/10 = 0%
(computeIncomeTax 5/10, applyExemption 4/8 are at exactly 50%.)

## Session 5A – Part B: Generate tests with constraints

Method picked (lowest branch coverage): `isEligibleForReturn` (0/10 branches).

Prompt used (source of the method pasted with it):

> Generate JUnit 5 tests for the following method.
> Rules:
> - Use AAA style with explicit comments.
> - One assertion per test.
> - Test names of the form <method>_<condition>_<expected>.
> - Cover: (a) null input, (b) empty input if applicable,
>   (c) boundary values, (d) one representative happy path.
> - Do NOT use mocks.
> - Do NOT include implementation-copying assertions; each
>   assertion must state a contract, not restate the code.

Generated tests saved (verbatim from the AI reply, then edited per Part C):
`isEligibleForReturn_nullIncome_returnsFalse`,
`isEligibleForReturn_negativeAge_returnsFalse`,
`isEligibleForReturn_seniorAtExactThreshold_returnsFalse`,
`isEligibleForReturn_superSeniorAboveThreshold_returnsTrue`,
`isEligibleForReturn_adultBelowThreshold_returnsFalse`,
`isEligibleForReturn_adultAboveThreshold_returnsTrue`.

Pasted into `test/TaxCalculatorTest.java`; `make test` → 9/9 pass.

## Session 5A – Part C: Read every assertion

Reviewed all 6 new assertions against the contract. One assertion from the
raw AI reply copied the implementation: its expected value recomputed the
threshold inline (`age >= 60 ? 300000 : 250000` arithmetic). Reworded to a
domain-derived expectation: "a 60-year-old at exactly 300000 is NOT eligible
(threshold is strict >)", which asserts the boundary contract. The other
five already stated contracts (null → false, negative age → false, below/above
threshold). No tautological assertions kept.

## Session 5A – Part D: Mutation testing

`make mutation` → PIT report at `build/reports/pitest/index.html`.

Before the targeted test (after Part B tests):
- Mutation coverage: 24/42 killed (57%), test strength 67%.
- Surviving mutants including:
  - `isEligibleForReturn` line ~74: `changed conditional boundary` →
    SURVIVED (`ageYears >= 80` became `ageYears > 80`)
  - `isEligibleForReturn` line ~70: `changed conditional boundary` →
    SURVIVED (`ageYears < 0` became `ageYears <= 0`)
  - `applyExemption`: two `changed conditional boundary` → SURVIVED
  - `computeIncomeTax`: several `changed conditional boundary` → SURVIVED

### Targeted mutant kill

Mutant: line 74, `ageYears >= 80` → `ageYears > 80` (SURVIVED).

Prompt used:

> The mutant at line 74 ('ageYears >= 80' -> 'ageYears > 80') survived
> the test suite. Write ONE new JUnit test that would kill this mutant
> without changing any other test. Explain in one sentence why this test
> kills the mutant.

Added test:

```java
@Test
public void isEligibleForReturn_exactAge80_usesSuperSeniorThreshold() {
    // Arrange
    TaxCalculator c = new TaxCalculator();
    // Act: age exactly 80 must use the 500000 threshold, so 400000 is not eligible
    boolean eligible = c.isEligibleForReturn(new BigDecimal("400000"), 80);
    // Assert
    assertFalse(eligible);
}
```

Why it kills the mutant: with `> 80` the age-80 case falls into the 60–79
branch (threshold 300000), making 400000 "eligible", while the correct
`>= 80` branch (threshold 500000) makes it ineligible — the assertion
distinguishes the two.

After re-running PIT: mutation coverage 25/42 (60%); the line-74 boundary
mutant is now KILLED. Remaining survivor on this method: line 70
(`ageYears < 0` → `<= 0`).

## Session 5A – Part E: Reflect

- Line coverage: 20/41 (49%) → 28/41 (68%).
- Branch coverage: 15/44 (34%) → 25/44 (57%).
- Mutation score: 24/42 (57%) → 25/42 (60%) after the targeted test.
- One sentence: the mutation feedback felt more useful than the coverage
  number — coverage said "isEligibleForReturn is covered" while PIT showed a
  boundary mutant still alive, which pointed directly at the missing
  exact-age-80 case.

## Session 5B – Part A: JavaDoc for one public method

Method chosen: `OrderBook.create(String customerId, BigDecimal amount)`.

Prompt used:

> Generate JavaDoc for the following method. Rules:
> - One-line summary in imperative mood.
> - Describe the contract, not the implementation.
> - @param for each parameter with type and constraints.
> - @return with what is returned.
> - @throws for each declared or unchecked exception the caller should be aware of.
> - If any behaviour is not evident from the code, write "TODO" and skip.

Generated JavaDoc (as pasted into the source, pre-Part-D):

```java
/**
 * Creates a new order with status NEW and a generated id.
 *
 * @param customerId non-blank customer identifier
 * @param amount     order amount, must be greater than zero
 * @return the newly created, persisted Order
 * @throws IllegalArgumentException if customerId is null or blank,
 *         amount is null, or amount is not positive
 */
```

Verification prompt used:

> Given the JavaDoc above and the method body below,
> identify any statement in the JavaDoc that is inconsistent
> with the code. Do not fix; just list.

First-pass inconsistencies found: **1**
1. `@return` said "persisted Order" — acceptable, but `@throws` originally
   omitted the exact `amount must be > 0` message semantics; clarified to
   "amount is not positive". Fixed; second pass returned "no inconsistencies".

## Session 5B – Part B: README

Prompt used:

> Draft a README.md for this repository with sections: description, build,
> quick example, contributing, license. Use MIT license placeholder.
> Rules:
> - Do NOT invent features not present in the code.
> - If a section has no evidence in the code, write "TODO" and skip.
> - The one-line description must be a factual summary of what the code does.

Raw draft saved verbatim in `README.raw.md`.

Invented features found and deleted when editing (verified by grepping the
code — no matches): **4**
1. "Spring Boot REST API" — no Spring imports anywhere in `src/`.
2. "PostgreSQL persistence" — store is an in-memory `ConcurrentHashMap`.
3. "OAuth2 authentication" — no auth code.
4. "Swagger UI" — no web layer at all.
5. Build command `mvn spring-boot:run` replaced with the real `make` targets.

Edited version saved as `README.md` (description written by hand from the
actual class; build section from the Makefile; contributing left as TODO).

## Session 5B – Part C: Markdown API reference

Prompt used:

> Read the following Java library and generate an API reference in Markdown
> covering: every public method of OrderBook, with signature, one-line
> summary, parameters (type + constraints), return value, and exceptions
> thrown; referenced value types (Order, OrderStatus), with each field
> briefly explained; a "Quick example" block of 5-8 lines of real Java
> showing a typical create / find / cancel / totalFor flow.
> If any method's behaviour is unclear from the code alone, add a TODO note.

### Generated API reference

#### `OrderBook`

In-memory order book backed by a `ConcurrentHashMap`.

##### `List<Order> list()`
Returns a snapshot of all orders in the book, in unspecified order.
- Parameters: none
- Returns: unmodifiable list of all `Order`s (empty if none)
- Throws: none declared

##### `Optional<Order> find(String id)`
Looks up an order by id.
- `id` (String): the order id to look up
- Returns: `Optional` containing the order, or empty if absent
- Throws: none declared

##### `Order create(String customerId, BigDecimal amount)`
Creates a new order with status NEW and a generated id.
- `customerId` (String): non-blank customer identifier; leading/trailing
  whitespace is ignored
- `amount` (BigDecimal): must be > 0 and <= 1000000
- Returns: the newly created, persisted `Order`
- Throws: `IllegalArgumentException` if customerId is null or blank;
  if amount is null, not positive, or exceeds 1000000

##### `boolean cancel(String id)`
Marks an existing, non-cancelled order as CANCELLED.
- `id` (String): the order id
- Returns: `true` if the order existed and was newly cancelled,
  `false` if unknown or already CANCELLED
- Throws: none declared

##### `BigDecimal totalFor(String customerId)`
Sums the amounts of all NEW orders belonging to a customer.
- `customerId` (String): the customer whose orders are summed
- Returns: total amount in INR as `BigDecimal`; `BigDecimal.ZERO` if none
- Throws: none declared; TODO: null `customerId` currently throws NPE from
  the stream filter — contract unclear from the code alone

#### `Order` (record)
| Field       | Type          | Meaning                          |
|-------------|---------------|----------------------------------|
| id          | String        | Generated UUID string            |
| customerId  | String        | Owning customer (trimmed, non-blank) |
| amount      | BigDecimal    | Order amount, > 0                |
| status      | OrderStatus   | NEW or CANCELLED                 |
| createdAt   | Instant       | Creation timestamp (UTC)         |

#### `OrderStatus` (enum)
- `NEW` — open order, counts toward `totalFor`
- `CANCELLED` — cancelled, excluded from `totalFor`

#### Quick example

```java
OrderBook book = new OrderBook();
Order o = book.create("alice", new BigDecimal("100.00"));
System.out.println(book.find(o.id()).orElseThrow().customerId());
System.out.println(book.totalFor("alice"));
book.cancel(o.id());
System.out.println(book.totalFor("alice"));
```

### Verification
- Every public method of `OrderBook` appears with correct name/signature:
  `list`, `find`, `create`, `cancel`, `totalFor` — yes.
- `Order` and `OrderStatus` documented with fields — yes.
- Quick example compiles and runs (pasted into `QuickExample.java`, compiled
  with `javac -cp build`): output was `alice`, `100.00`, `0` — passes.
- Declared `IllegalArgumentException` cases (grep of src/OrderBook.java):
  `customerId required`, `amount must be > 0`,
  `amount exceeds maximum 1000000` — all three listed under `create`.
  No other method declares IAE. Missing docs added by hand: none needed.

## Session 5B – Part D: JavaDoc sync after modification

### Method modified
`OrderBook.create(String customerId, BigDecimal amount)` (src/OrderBook.java)

### Modification made
- `customerId` is now trimmed before being stored (`customerId.trim()`); padded
  ids are normalized.
- New upper bound: `amount` must be <= 1000000, otherwise
  `IllegalArgumentException("amount exceeds maximum 1000000")`.

### AI sync prompt used
> The method below was modified as follows: <modification above>.
> Check the JavaDoc against the new body. List any statement in the JavaDoc
> that is now inconsistent, missing, or stale. Do not edit; just list.

### Inconsistencies found by the sync check (first pass) — 2
1. `@param customerId` did not mention trimming/normalization → updated to
   "leading/trailing whitespace is ignored".
2. `@param amount` / `@throws` did not mention the 1000000 upper bound → added
   "at most 1000000" and the new `IllegalArgumentException` case.

Second pass returned "no inconsistencies".

### Verification
- `make build` compiles clean (javac --release 17).
- `java -jar libs/junit.jar execute --class-path build --scan-class-path`:
  3 tests started, 3 successful, 0 failed.

### Reflection
The AI sync check caught 2 stale/missing doc statements on the first pass —
both parameter-constraint details. Contract-level summaries (imperative one-liner,
`@return`) were still accurate; only constraint text drifted. Lesson: keep
`@param`/`@throws` tied to validation branches so a code change forces a doc diff.

## Session 5B – Part D reflect prompts

- JavaDoc inconsistencies the AI self-check caught on the first pass: **1**
  (Part A), plus **2** stale statements after the modification (Part D).
- Invented features the README pass produced: **4** (Spring Boot REST API,
  PostgreSQL persistence, OAuth2, Swagger UI) + 1 wrong build tool (Maven).
- Docs artefact needing the most hand editing: **README.md** — the AI draft
  invented an entire stack; every feature claim had to be grepped out. The
  API reference needed only small additions (null-customerId TODO on
  `totalFor`); the JavaDoc was largely contract-accurate after one fix pass.
