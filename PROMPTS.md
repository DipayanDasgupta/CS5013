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
