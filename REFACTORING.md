# REFACTORING.md — Brightline Loyalty Points Service

This document explains what I changed in the Brightline Loyalty Points Service, why I made those changes, and how I handled the inherited AI-generated code and tests.

I documented the original problems and the reasoning behind my decisions in `ANALYSIS.md`, which I reference throughout this document as A1, A2, and so on.

---

## 1. The Original Design Problems

After reviewing the original implementation, I found that although the service could produce correct results for normal inputs, it had several design problems.

It could hide failures, was difficult to test properly, and was not flexible enough to support new membership tiers without modifying existing working code.

These were the main problems I identified:

| Problem | What I found in the original implementation |
|---|---|
| A1 – Hidden failures | `catch (Exception e) { return 0; }` made different failures, such as unknown members, null IDs, and repository failures, look like legitimate zero-point results. |
| A2 – Hard-wired dependency | The calculator created its own `InMemoryMemberRepository` instead of receiving it as a dependency. |
| A3 / A12 – Possible NullPointerException | The repository could return `null`, which the service attempted to use without checking. |
| A4 – String-based tiers | Membership tiers were handled using `if/else if` statements and strings such as `"GOLD"` and `"SILVER"`. Unknown tiers silently received a multiplier of 1. |
| A5 – Mixed responsibilities | The `calculatePoints` method contained `System.out.println`, mixing business logic with presentation. |
| A6 – Missing validation | Negative purchase amounts could produce negative points, while infinity and overflow could produce unreliable results. |
| A7 – Incorrect money representation | Using `double` introduced floating-point precision problems. |
| A8 / A9 – Hidden business rules | Magic numbers and two separate integer casts made important calculation rules difficult to understand. |
| B1–B3 – Weak tests | The original tests relied on weak assertions, mocked the class being tested, and used `assertNotNull` on primitive integer results. |

I decided to address these problems through a combination of better object-oriented design, dependency injection, proper exception handling, and stronger automated tests.

## 2. The New Architecture

I reorganised the application into packages based on responsibility.

```text
com.brightline.loyalty
├── domain
│   ├── Member (record)
│   └── MembershipTier (enum)
├── service
│   ├── PointsCalculator
│   ├── TierRule
│   ├── MultiplierTierRule
│   └── TierRuleRegistry
├── repository
│   ├── MemberRepository (interface)
│   └── InMemoryMemberRepository
├── exception
│   ├── InvalidPurchaseException
│   ├── LoyaltyException
│   ├── MemberNotFoundException
│   └── MemberRepositoryException
└── Demo
```

I designed the dependencies to flow in one direction.

The service layer depends on the domain, repository interface, and exception packages. The domain layer does not depend on the service or storage implementation.

I also made `Demo` responsible for creating and connecting the concrete implementations.

My reasoning was that every design pattern should solve an actual problem rather than introduce unnecessary complexity.

## 3. Responsibilities of Each Class

I separated the responsibilities of the classes to make the application easier to understand, maintain, and test.

| Class | Responsibility | My implementation decision |
|---|---|---|
| `Member` | Represent a valid member | I used a Java record and added constructor validation to prevent null or blank IDs, invalid names, and null membership tiers. |
| `MembershipTier` | Represent membership tiers | I used an enum and kept the points calculation logic outside it. |
| `TierRule` | Define how a tier affects base points | I created an interface with `tier()` and `applyTo(basePoints)`. |
| `MultiplierTierRule` | Apply a multiplier to base points | I implemented one reusable class that can represent different multiplier rules. |
| `TierRuleRegistry` | Find the rule associated with a membership tier | I made it responsible for ensuring every tier has exactly one registered rule. |
| `PointsCalculator` | Coordinate the points calculation | It validates input, retrieves the member, calculates base points, applies the tier rule and bonus, and returns the result. |
| `MemberRepository` | Define how members are retrieved | I used `Optional<Member>` as the return type of `findById(String)`. |
| `InMemoryMemberRepository` | Store members in memory | I used an immutable map and allowed members to be supplied by the caller. |
| `Demo` | Demonstrate the application | I moved object creation, output, and exception handling into this class. |

One important improvement is that `PointsCalculator` no longer creates its own dependencies, performs console output, or catches exceptions unnecessarily.

Its longest method is nine lines, which keeps the implementation easy to follow.

## 4. How Dependency Injection Improved Testability

One of the major changes I made was introducing constructor-based dependency injection.

```java
public PointsCalculator(
    MemberRepository memberRepository,
    TierRuleRegistry tierRules
)
```

Instead of creating dependencies inside `PointsCalculator`, I now pass them through its constructor.

This allows me to use the same calculator in different situations without modifying its implementation.

| Situation | Dependencies I provide |
|---|---|
| Production / Demo | The real repository and `TierRuleRegistry.standard()` |
| Unit testing | A Mockito repository mock and the real tier registry |
| Integration testing | The real `InMemoryMemberRepository` containing test-specific members |

This change made it possible to test scenarios that were difficult or impossible to test properly in the original implementation.

For example, I can now:

- Simulate a repository failure.
- Test what happens when a member does not exist.
- Supply a member with a specific membership tier.
- Verify that invalid input never reaches the repository using `verifyNoInteractions`.
- Test the calculator independently of the storage implementation.

Previously, the calculator controlled its own repository, making these scenarios much harder to isolate.

## 5. How My Tier Strategy Supports Future Membership Tiers

I designed the calculator so that it does not contain membership-tier-specific conditions.

Instead, it retrieves the appropriate rule from `TierRuleRegistry` and applies that rule.

For example, if the business introduces a new `PLATINUM` tier, I would:

**Step 1: Add the new tier to `MembershipTier.java`.**

```java
STANDARD, SILVER, GOLD, PLATINUM
```

**Step 2: Register its rule in `TierRuleRegistry.standard()`.**

```java
new MultiplierTierRule(
    MembershipTier.PLATINUM,
    new BigDecimal("2.5")
)
```

**Step 3: Add tests covering the new tier.**

The important part is that I would not need to modify `PointsCalculator`.

This demonstrates the Open-Closed Principle: the calculator remains closed to modification while the system can be extended with new tier rules.

I also made the registry fail immediately if a tier does not have a registered rule or has duplicate rules.

For example, forgetting to register PLATINUM produces an exception during construction:

```text
No rule registered for tier(s): [PLATINUM]
```

This is much safer than the original implementation, where an unknown tier could silently receive a multiplier of 1.

I also considered what would happen if a future tier required more than a simple multiplier.

In that situation, I could create another implementation of `TierRule` and register it without changing the calculator.

To demonstrate that this flexibility works, I added a test named `shouldDelegateToTheRuleRegisteredForTheMembersTier` in `PointsCalculatorTest`.

The test registers a custom `FlatRewardRule` for GOLD. Instead of multiplying the base points, this rule adds a flat amount.

The calculator applies that rule correctly without knowing how the rule works internally.

I did not add an actual PLATINUM constant just for testing because that would introduce a product decision that was not part of the requirements.

### Why I Chose Rule Objects Instead of Putting Multipliers in the Enum

I considered placing the multiplier directly inside `MembershipTier`.

That would also have been a valid and simpler approach.

However, I chose separate rule objects because the requirements called for independently represented tier rules.

This approach also makes it easier to introduce future rules that do not follow a simple multiplication strategy.

The trade-off is that I introduced two additional types, but I considered that reasonable for the flexibility gained.

## 6. How I Handle Exceptions

My main principle when handling exceptions was that I should never convert an actual failure into a normal-looking result.

A failure should reach the part of the application that can understand it and decide what to do.

I therefore removed the original catch-all exception handling that returned zero points whenever something went wrong.

Here is how I now handle the different situations:

| Situation | Exception or outcome | Reason |
|---|---|---|
| Unknown member | `MemberNotFoundException` | Allows callers to identify a missing member and access the member ID. |
| Null or blank member ID | `IllegalArgumentException` | Represents invalid caller input. |
| Negative, null, or excessively large purchase amount | `InvalidPurchaseException` | Represents an invalid purchase in the business domain. |
| Repository infrastructure failure | `MemberRepositoryException` | Separates storage failures from missing-member errors and preserves the original cause. |
| Unexpected failure | Propagates unchanged | Prevents failures from being hidden or incorrectly translated. |
| Invalid member, rule, or registry configuration | `IllegalArgumentException` | Identifies invalid application configuration during construction. |

I made all custom exceptions unchecked because callers cannot reasonably recover from these failures in the middle of the calculation.

Using checked exceptions would also force unnecessary `throws` declarations through multiple layers.

### Why I Placed try/catch Where I Did

I reviewed the entire application to ensure that exception handling happens at the appropriate layer.

| Location | My decision | Reason |
|---|---|---|
| `PointsCalculator` | No catch | It cannot recover from invalid input, missing members, or repository failures. |
| `InMemoryMemberRepository` | No catch | A simple map lookup does not require exception translation. |
| Future database repository | Catch and wrap driver exceptions | The repository implementation is the appropriate layer to translate database-specific failures into `MemberRepositoryException`. |
| `Demo` | One multi-catch for three named exception types | It acts as the application boundary and is responsible for reporting failures to the user. |

There is exactly one `catch` statement in my production code.

I deliberately avoided `catch (Exception)` because it would hide unexpected problems instead of allowing them to be identified.

### A Note on MemberRepositoryException

I also recognise that `MemberRepositoryException` is the least immediately necessary of my three custom exceptions.

The current in-memory repository does not throw this exception because it has no infrastructure dependency that can fail in that way.

However, I retained it because the repository contract should provide a clear way to communicate infrastructure failures when a database-backed implementation is introduced.

I also added tests to prove that the calculator propagates this exception while preserving its original cause.

If the project does not require this contract before introducing a real database repository, the exception can be removed without affecting the rest of the design.

## 7. Why I Added or Rewrote Particular Tests

I expanded the original test suite significantly.

My final test suite contains:

- 63 test methods.
- 44 regular `@Test` methods.
- 15 `@ParameterizedTest` methods.
- 137 total test executions.

I separated the tests into `unit/` and `integration/` packages.

| Test class | What I wanted to verify | Important testing decision |
|---|---|---|
| `PointsCalculatorTest` | Business rules and calculator failures | I mock only the repository and use the real tier registry. |
| `MultiplierTierRuleTest` | Multiplication and truncation | I test fractional multipliers, large values, and constructor validation. |
| `TierRuleRegistryTest` | Rule completeness and uniqueness | I verify that missing and duplicate rules are rejected. |
| `MemberTest` | Member validation | I test null, empty, and blank IDs and names, as well as null tiers. |
| `InMemoryMemberRepositoryTest` | Repository behaviour | I test Optional results, duplicate IDs, and defensive copying. |
| `PointsCalculatorIntegrationTest` | Collaboration between real components | I use the real repository and verify that the original business behaviour is preserved. |
| `DemoIntegrationTest` | Application entry point and exception reporting | I capture console output and verify successful calculations and reported failures. |
| `LoyaltyExceptionTest` | Which failures a boundary's single catch handles|Real calculator, no mocks: unknown member, invalid purchase and repository failure are all LoyaltyExceptions (cause kept); a blank id is not|
| `PointsCalculatorInvariantsTest` | Rules that hold for any amount and every tier | The additional improvement (§9). Real calculator, fixed-seed sample, runs over MembershipTier.values()|


### Important Testing Decisions

**1. I tested boundaries rather than only normal inputs.**

For example, I tested GHS 499.99, GHS 500.00, and GHS 500.01 to verify the bonus threshold.

I also tested `Integer.MAX_VALUE` and the value immediately above it to verify overflow handling.

**2. I tested the order of calculations.**

For SILVER, a GHS 29 purchase produces 3 points.

This confirms that the base points are truncated before applying the multiplier.

If multiplication happened first, the result would be different.

I also tested GHS 30, which produces 4 points rather than a rounded 5.

**3. I verified that the bonus is applied after the multiplier.**

For example, GOLD with a GHS 600 purchase produces 170 points, not 340.

**4. I used parameterized tests where appropriate.**

Instead of writing many nearly identical test methods for different tiers, I used parameterized tests to verify several inputs against the same business rule.

**5. I used Mockito verification selectively.**

I used `verify()` to confirm that invalid input never reaches the repository and that a member lookup happens only once using the requested ID.

I also ensured that the lookup tests assert the actual points returned.

**6. I never mocked the class under test.**

I wanted my tests to exercise the actual implementation rather than a mocked version of the behaviour I was supposed to verify.

## 8. What I Did With the Original AI-Generated Code and Tests

I did not simply discard everything from the original implementation.

I reviewed each part and decided whether it should be retained, corrected, or removed.

### Production Code

| Original implementation | What I did | Explanation |
|---|---|---|
| Original business rules | Retained | I preserved the one point per GHS 10 rule, tier multipliers, GHS 500 bonus, and truncation behaviour. |
| `PointsCalculator` class | Retained | I kept the familiar class name but moved it into the service package. |
| `calculatePoints` method | Retained and redesigned | I kept the method's purpose but rewrote its implementation. |
| `MemberRepository` interface | Retained and corrected | I changed its return type from `Member` to `Optional<Member>`. |
| Three sample members | Retained | I preserved them through `withSampleMembers()`. |
| Original demo scenarios | Retained and improved | I kept the original examples and added failure demonstrations. |
| `Member` class | Corrected | I replaced the original representation with a validated record and enum-based tier. |
| `InMemoryMemberRepository` | Corrected | I changed it to accept members from the caller instead of relying on hard-coded data. |
| Repository creation inside calculator | Removed | I replaced it with constructor injection. |
| `catch (Exception e) { return 0; }` | Removed | I considered this one of the most serious original defects. |
| Console printing inside calculation | Removed | I moved presentation responsibilities into `Demo`. |
| String-based tier conditions | Removed | I introduced `MembershipTier` and `TierRule` objects. |
| Magic numbers | Corrected | I introduced named constants and moved multipliers into their respective rules. |

### Original Tests

I also reviewed the three original tests and decided that none of them was strong enough to keep in its original form.

| Original test | My assessment | What I changed |
|---|---|---|
| `awardsPointsToAMember` | Weak because it only checked `points > 0`. | I replaced it with exact-value assertions for each tier. For example, STANDARD with GHS 100 must produce exactly 10 points. |
| `calculatesGoldPoints` | Hollow because it mocked the class under test. | I removed it and retained its intended GOLD calculation as a real assertion in the GOLD test table and integration test. |
| `awardsPointsForAPurchase` | Weak because it used `assertNotNull` on an integer. | I replaced the intended check with an exact assertion: SILVER with GHS 200 must produce 30 points. |

I believe these changes make the tests more meaningful because they verify actual business behaviour rather than simply checking that something happened.

## 9. My additional improvement beyond the brief: invariant tests across every tier

**Problem I wanted to address.**

One of the main goals of this project is to make it possible to introduce a new membership tier without having to rewrite the existing calculation logic.

The exact-value tests cover each existing tier, but they are written separately for each one. This means that when a new tier is introduced, it might not receive proper test coverage unless someone remembers to add specific tests for it. A mistake in the new tier's rule, such as an incorrect comparison or applying the bonus before the multiplier, could therefore go unnoticed.

**Why I considered this important to Brightline.**

Membership tiers are one of the parts of the service that the business may change in the future. If a tier is implemented incorrectly, customers could receive the wrong number of points.

I wanted to improve the safety net so that when a new tier is introduced, some important behavioural checks would automatically apply to it instead of depending entirely on a developer remembering to write additional tests.

**How I implemented it.**

I added `integration/PointsCalculatorInvariantsTest`, which runs four checks against every value returned by `MembershipTier.values()`.

The tests use the real calculator and repository, without mocks or additional dependencies. Rather than reimplementing the points calculation, they compare the results produced by the application to check whether certain rules remain true.

I used a fixed-seed random sample of 2,000 purchase amounts, together with a cent-by-cent sweep around GHS 500. Using a fixed seed makes the random test cases reproducible, and the failure messages identify the exact amount that caused a test to fail.

| Invariant | Type of mistake it helps detect |
|---|---|
| Points must never be negative | An incorrect rule or arithmetic operation that subtracts points |
| Spending more must never earn fewer points | An incorrect comparison or a bonus that replaces the points instead of being added |
| Spending above GHS 500 must produce exactly 50 bonus points for every tier | A bonus that is multiplied by the tier or applied at the wrong threshold |
| Trailing zeros must not change the points (`500`, `500.00`, `5E+2`) | The `BigDecimal` `equals()` versus `compareTo()` issue |

I also updated two areas of the existing tests. First, tests that previously created registries using a manually maintained tier list now derive that list from `MembershipTier.values()`, as explained in Section 5.

Second, I updated `TierRuleRegistryTest` so that it fails with a clear instruction whenever a tier does not have an expected value defined in the test.

I added this because invariant tests cannot confirm whether a tier has the correct multiplier. They can check general behaviour, but a developer still needs to specify the exact expected reward for each tier. The test now ensures that this is not forgotten.

**How I tested whether the additional improvement works.**

I also introduced deliberate bugs in a scratch version of the project to see whether the invariant tests could detect them. These experiments were performed using my scratch harness, not real PIT mutation testing.

| Injected bug | Did the invariants detect it on their own? |
|---|---|
| Applying the bonus before the tier rule, causing GOLD's bonus to become 100 | Yes. GOLD and SILVER were flagged, while STANDARD (×1) correctly was not |
| Making the bonus replace the points instead of adding to them | Yes |
| Subtracting the bonus instead of adding it | Yes |
| Using `BigDecimal.equals()` to determine the bonus | Yes |
| Changing the bonus threshold from 500 to 400 and the bonus from 50 to 5 | Yes |
| Rounding base points up instead of down | Yes. I initially expected this bug to survive, but the test caught it because the bonus check relies on GHS 500.00 to 509.99 earning 50 base points |
| Changing the GOLD multiplier consistently from ×2 to ×3 | No. The exact-value tests are needed to detect this |

The future-tier experiments discussed in Section 5 provide additional evidence. I introduced a PLATINUM tier in a scratch copy and deliberately gave it a faulty rule. The invariant tests detected the problem and identified the tier without requiring me to write a separate PLATINUM-specific invariant test.

**Limitations I identified.**

- A tier rule that is consistently wrong, such as GOLD using ×3 instead of ×2, can still satisfy all four invariants. This is why I kept the exact-value tests. The invariants complement those tests; they do not replace them.
- The bonus invariant assumes that base points are truncated, as documented in decision D2. If the business changes the rounding rule, I will need to review this invariant.
- The random sample is large, but it does not cover every possible purchase amount.
- I deliberately left out the rule that a higher tier must never earn fewer points than STANDARD. Although this is true with the current multipliers, I did not want to introduce an additional business assumption without confirmation.
- These tests have not yet been executed using real JUnit or PIT. The results described above came from my imitation harness.

---

## 10. Verification status

I wanted the final documentation to be honest about what I actually verified, what was reported by the project owner, and what still needs to be checked. I have separated these results below so that there is no confusion.

**Checks I performed in the sandbox using real execution:**

| Check | Result |
|---|---|
| Compiled the production code using JDK 21 with `-Xlint:all` | **Successful, with 0 warnings** |
| Ran `Demo` | Printed the five expected lines: three successful results and two reported failures |
| Compared the refactored implementation against the original | 2,100,039 comparisons using `double` and 1,800,036 using `BigDecimal`, with **0 mismatches** |
| Compiled and executed the tests using a throw-away imitation of JUnit 5 and Mockito, including strict-stub checking | **137 executions, 137 passed** |
| Ran a PIT-style mutation sweep using an automatic mutator that imitates PIT's default mutators across every production class | **41 mutants, 41 killed**. When I repeated the experiment with a deliberately weak test suite containing only one test class, 36 mutants survived. This showed that the imitation could detect weaknesses in the test suite, but it was not real PIT |
| Tested future-tier changes in a scratch copy using a correct and an incorrect PLATINUM implementation | The correct implementation passed 142/142 tests after I added one expected-value line. The incorrect implementation was detected by the invariants. Before fixing the existing tests, adding the correct tier caused four existing tests to fail |
| Deliberately introduced regressions into the exception hierarchy, including detaching exceptions from `LoyaltyException`, making a blank-ID failure a `LoyaltyException`, and removing an exception cause | **5 of 5 regressions were detected** by `LoyaltyExceptionTest` |
| Checked the code style | No line exceeds 100 characters, no wildcard imports, 0 unused imports, longest method is 9 lines, and there is exactly one `catch` in production code |

**Results reported by the project owner from local runs (I did not perform these runs myself):**

| Run | Reported result |
|---|---|
| `mvn clean test` using real JUnit 5 and Mockito | Passed. However, this run was performed before the `LoyaltyException` change |
| JaCoCo using `mvn verify` | `Demo` had 14 of 15 lines covered. My assumption is that the missed line and method are most likely related to the implicit default constructor |
| PIT 1.16.1 before the `LoyaltyException` change | The `service`, `repository` and `exception` packages reported 100% line and mutation coverage. PIT generated 34 mutants, with 27 killed and none surviving among those reached. However, `Demo` had 0 of 15 lines covered, with 7 mutants not reached. The `domain` package did not appear in the report. I did not investigate this further, although it may be related to PIT's record filter rather than a missing test |
| Scratch-copy PIT experiments | Skipping JaCoCo, explicitly listing test packages, calling `Demo.main` without `System.setOut`, and moving `Demo` into a sub-package all left the 7 `Demo` mutants uncovered. Changing the catch to `catch (RuntimeException)` and, separately, introducing the `LoyaltyException` base-class design resulted in all 7 mutants being killed |

**Checks that have not yet been performed on the final code after introducing `LoyaltyException`:**

| Not yet performed | What this means |
|---|---|
| Running `mvn clean test` and PIT on the actual final project | The scratch-copy experiments used the same design, but I have not yet verified the final project using real Maven and PIT. I still need to rerun them and confirm that `Demo` is covered |
| Running a fresh `mvn verify` | I cannot claim an updated JaCoCo coverage figure until this is done |
| Running the new `PointsCalculatorInvariantsTest` and the updated `TierRuleRegistryTest` and `PointsCalculatorTest` using real JUnit | These tests have only been compiled and executed using my imitation harness. `@EnumSource` is a standard JUnit 5 feature, but I still need to confirm everything using the actual project dependencies |

**Commands I need to run for final verification:**

```bash
mvn clean test

mvn verify

mvn clean test-compile org.pitest:pitest-maven:mutationCoverage
```

The expected outputs are:

- `mvn clean test`: All tests should pass, with 137 executions.
- `mvn verify`: The JaCoCo report should be available at `target/site/jacoco/index.html`.
- PIT mutation testing: The report should be available at `target/pit-reports/index.html`.

If the actual Maven results differ from these expectations, I will treat the real output as the source of truth and investigate the failure rather than rely on the earlier imitation results.

In particular, I need to check the `Demo` section of the PIT report. Based on the changes I made, I expect it to show seven killed mutants. Any other surviving mutant should be investigated to determine whether it exposes a missing assertion or is an equivalent mutant. I do not want to weaken the production code just to achieve a particular percentage.

**Remaining limitations and business assumptions I have identified:**

- **Truncation is preserved, but it has not been confirmed as the intended business rule.** I retained the observed behaviour of truncating the base points, applying the multiplier and truncating again. This behaviour is documented and tested. If Brightline intended rounding instead, the rounding mode would need to be changed in the relevant calculation, but that would affect the points customers receive.
- **Zero-value purchases earn zero points.** I retained this behaviour rather than rejecting zero-value purchases.
- **`MemberRepositoryException` is not currently thrown by any implementation.** I kept it as part of the repository error-handling contract for a future infrastructure implementation, as explained in Section 6.
- **`Demo` has an implicit default constructor that is not used.** This is probably the reason JaCoCo reported one uncovered line. Adding `private Demo() {}` would be the conventional approach for a class that should not be instantiated, but I have left this as a decision for the project owner rather than making a production change purely to improve the coverage figure.
- **The `domain` package does not appear in the PIT report.** Its validation is covered by `MemberTest`, but PIT has not independently reported mutation coverage for that package.
- **Quality gates are not enforced in `pom.xml`.** JaCoCo currently generates a report but does not enforce a coverage threshold through a `check` rule. PIT also has no configured `mutationThreshold`. Once I have verified the real results, I can consider adding these rules to make the 100% target in Section 9 of the standard enforceable.
- **The API has changed.** I changed the purchase amount from `double` to `BigDecimal` and changed the repository's `findById` return type from `Member` to `Optional<Member>`. The only caller in this repository was `Demo`, which I updated to use the new API.

---

## Conclusion

Through this refactoring, I focused on making the Brightline Loyalty Points Service more reliable, testable, maintainable, and easier to extend.

I did not simply rewrite the original code. I first identified its weaknesses, documented the problems, considered the available design options, and then implemented changes based on those findings.

The most important improvements I made were:

- Replaced hidden failures with meaningful exception handling.
- Introduced constructor-based dependency injection.
- Separated domain, service, repository, and presentation responsibilities.
- Introduced a tier-rule strategy that supports future extensions.
- Replaced floating-point money calculations with `BigDecimal`.
- Rewrote weak tests to verify exact business outcomes.
- Added unit and integration tests covering normal behaviour, edge cases, and failures.

I have also been careful to distinguish between checks I actually executed and checks that still require verification with the real Maven dependencies.

My overall goal was to produce code that is not only functional but also understandable, maintainable, and supported by meaningful tests.