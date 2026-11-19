# ANALYSIS.md — Brightline Loyalty Points Service

## Phase 1: Analysis and Code Review

---

# 1. Purpose of this analysis

Before making changes to the Brightline Loyalty Points Service, I reviewed the existing implementation to understand its behaviour, identify design weaknesses and determine what needs to be improved.

My objective in this phase was not to start rewriting the application immediately. I wanted to establish what the existing system does, identify problems that could affect its correctness and maintainability, and plan a refactoring approach that supports the company's future requirements.

I focused on three main areas:

1. The quality and structure of the existing production code.
2. Whether the existing AI-generated tests genuinely verify application behaviour.
3. How the application can be improved without unnecessarily changing its existing business rules.

No production code or test files were modified during this analysis.

## 1.1 Files inspected

I inspected the following project files:

- `pom.xml`
- `README.md`
- `.gitignore`
- `Demo.java`
- `PointsCalculator.java`
- `Member.java`
- `MemberRepository.java`
- `InMemoryMemberRepository.java`
- `PointsCalculatorTest.java`

I also examined the available Java environment and investigated the behaviour of the existing calculation logic using temporary test code outside the project.

## 1.2 Environment and verification results

| Item | Result |
|---|---|
| Java version | OpenJDK 21.0.13 available |
| Production code compilation | Successful using the JDK compiler module |
| Demo application | Successfully executed |
| Edge-case investigation | Performed using temporary test code |
| Existing JUnit tests | Successfully executed and passed  |
| Maven configuration | Build successfully |
| JaCoCo coverage | Inspect tests coverage|
| PIT mutation testing | Inspect tests (killed and survided) |


## 1.3 Existing application behaviour

I executed the existing `Demo` class without modifying the production code.

The output was:

```text
Awarded 10 points to Kofi Mensah
Result m1 (STANDARD), GHS 100 -> 10
Awarded 50 points to Ama Owusu
Result m2 (GOLD), GHS 250 -> 50
Result unknown member, GHS 100 -> 0
```

The last result immediately caught my attention.

An unknown member receives zero points, just like a member who legitimately makes a purchase that earns zero points. The application does not communicate that the calculation failed.

I investigated this further and found that the calculator catches exceptions and returns zero. This hides the difference between a successful calculation and a failed operation.

## 1.4 Business rules identified

From the existing implementation, I identified the following business rules:

1. Members earn one base point for every GHS 10 spent.
2. STANDARD members receive a 1x multiplier.
3. SILVER members receive a 1.5x multiplier.
4. GOLD members receive a 2x multiplier.
5. Purchases strictly greater than GHS 500 receive an additional 50 points.
6. The bonus is added after applying the membership multiplier.
7. A purchase of exactly GHS 500 does not qualify for the bonus.
8. Fractional points are truncated rather than rounded to the nearest integer.

For example, a SILVER member spending GHS 30 receives:

```text
Base points = 30 / 10 = 3
Tier calculation = 3 × 1.5 = 4.5
Final result = 4 points
```

I intend to preserve the existing calculation sequence during the structural refactoring so that I do not unintentionally change the loyalty programme's behaviour.

## 1.5 Edge-case investigation

I used temporary test code outside the project to investigate how the current implementation responds to different inputs.

| Input | Observed result | What it tells me |
|---|---:|---|
| STANDARD, GHS 100 | 10 | Normal calculation works |
| GOLD, GHS 250 | 50 | GOLD multiplier works |
| SILVER, GHS 200 | 30 | SILVER multiplier works |
| SILVER, GHS 30 | 4 | Fractional points are truncated |
| SILVER, GHS 10 | 1 | Fractional points are truncated |
| GOLD, GHS 500 | 100 | No bonus at exactly GHS 500 |
| GOLD, GHS 500.01 | 150 | Bonus is added after the multiplier |
| STANDARD, GHS 9.99 | 0 | A legitimate zero-point result is possible |
| STANDARD, GHS 0 | 0 | Zero purchase is accepted |
| STANDARD, GHS -100 | -10 | Negative purchase produces negative points |
| STANDARD, GHS -1000 | -100 | Negative points are returned |
| STANDARD, NaN | 0 | Non-finite input is not rejected |
| STANDARD, Positive Infinity | -2147483599 | Conversion and integer overflow produce an invalid result |
| STANDARD, Negative Infinity | -2147483648 | Conversion produces an invalid result |
| GOLD, GHS 1.5e10 | -1294967246 | Integer overflow occurs |
| Unknown member, GHS 100 | 0 | Missing-member failure is hidden |
| Null member ID | 0 | Null-related failure is hidden |
| Blank member ID | 0 | Blank ID is not properly validated |

These observations show that the application can produce correct results for normal inputs while still behaving incorrectly when it receives invalid or unexpected inputs.

### Floating-point precision observation

I also investigated a floating-point precision issue.

For example, a value of `90.0` passed through certain multiplication and division operations can become:

```java
89.99999999999999
```

When a value like this is divided by 10 and converted to an integer, it can produce 8 instead of 9.

This demonstrates a potential risk with using binary floating-point values for monetary calculations.

---

# 2. Production Code Review

After reviewing the production classes, I identified the following problems. I have ranked and explained them according to their impact on correctness, maintainability, testability and future development.

## A1. Catch-all exception handling hides application failures

**Affected class:** `PointsCalculator`

The existing implementation contains:

```java
catch (Exception e) {
    return 0;
}
```

I consider this the most serious problem in the application.

The calculator catches every exception and returns zero, regardless of what caused the failure.

This creates an ambiguity because zero is already a valid result in the loyalty system.

For example:

- A customer spending GHS 9.99 can legitimately receive zero points.
- An unknown member also receives zero because the exception is swallowed.
- An unexpected repository failure could also be represented as zero.

The application therefore cannot distinguish between a successful calculation and a failed operation.

This could result in customers not receiving their correct points without the application clearly reporting the problem.

It also makes debugging difficult because the original exception and its cause are lost.

**Relevant principles and standards:** Proper exception handling, meaningful error reporting and avoiding swallowed exceptions.

**My proposed solution:** Remove the broad catch block. Introduce meaningful exceptions for expected application failures and allow unexpected failures to remain visible. Exceptions should only be caught where the application can meaningfully handle them.

## A2. The calculator creates its own repository

**Affected class:** `PointsCalculator`

The calculator creates its repository internally:

```java
private final MemberRepository repository =
        new InMemoryMemberRepository();
```

Although the field uses an interface, the calculator still chooses and creates a specific implementation.

This makes the calculator tightly coupled to the in-memory repository.

For example, if Brightline later introduces a database-backed repository, I would have to modify the calculator to use it.

It also makes unit testing difficult because I cannot easily provide a mocked repository.

This conflicts with the Dependency Inversion Principle (DIP), which encourages high-level modules to depend on abstractions rather than concrete implementations.

**My proposed solution:** Use constructor dependency injection. The calculator should receive a `MemberRepository` through its constructor instead of creating one itself.

## A3. Missing members cause accidental NullPointerExceptions

**Affected classes:** `PointsCalculator` and `InMemoryMemberRepository`

The repository returns `null` when a member cannot be found.

The calculator then attempts to access the member's information without first checking whether the member exists.

This creates a `NullPointerException`, which is subsequently hidden by the broad exception handler.

The problem is not simply that a null value exists. The repository contract does not clearly communicate that a member might be absent, and the service does not handle that situation intentionally.

**My proposed solution:** Change the repository method to return `Optional<Member>`.

When the member is not found, the service should throw a meaningful `MemberNotFoundException`.

This makes the missing-member situation explicit and prevents it from being treated as an ordinary zero-point calculation.

## A4. Membership tiers are represented using strings and conditional logic

**Affected classes:** `Member` and `PointsCalculator`

The application represents membership tiers using strings such as:

```java
"STANDARD"
"SILVER"
"GOLD"
```

The calculator then uses an `if/else if` chain to select the multiplier.

I identified three problems:

1. Strings allow typing mistakes and unsupported values to enter the application.
2. The calculation method must be modified whenever another tier is introduced.
3. An unsupported tier can fall through the existing conditional logic and receive an unintended default calculation.

This makes the design difficult to extend and weakens type safety.

It also conflicts with the intention of the Open-Closed Principle (OCP): existing calculation logic should be open to extension without requiring repeated modification for every new tier.

**My proposed solution:** Introduce a `MembershipTier` enum and separate tier calculation rules managed through a registry.

The main calculator should use the appropriate rule without containing a growing collection of tier-specific conditions.

## A5. Business logic is mixed with console output

**Affected class:** `PointsCalculator`

The calculator prints messages directly:

```java
System.out.println(
    "Awarded " + points + " points to " + member.getName()
);
```

I do not think presentation belongs inside the calculation service.

The responsibility of the service should be to calculate and return the points. Printing messages is a separate responsibility.

This also makes the service less reusable. For example, a future REST API would need the calculation result without necessarily printing anything to the console.

This conflicts with the Single Responsibility Principle (SRP).

**My proposed solution:** Remove console output from `PointsCalculator` and move it to `Demo` or the application presentation layer.

## A6. The application does not properly validate its inputs

**Affected class:** `PointsCalculator`

The service does not properly validate the purchase amount or member ID.

My edge-case investigation showed that:

- Negative purchases produce negative points.
- `NaN` is accepted.
- Infinite values can produce invalid results.
- Large amounts can cause integer overflow.
- Null and blank IDs are not handled through clear validation rules.

These are correctness problems because the application can return invalid results as though they were legitimate calculations.

**My proposed solution:**

- Reject negative purchase amounts.
- Reject non-finite `double` values while the existing API still uses `double`.
- Reject null and blank member IDs.
- Accept zero as a valid purchase amount.
- Consider the range of values supported by the points calculation to prevent integer overflow.

I will use meaningful exceptions for invalid purchase amounts and appropriate argument validation for invalid IDs.

## A7. `double` is used for monetary calculations

**Affected class:** `PointsCalculator`

The application represents purchase amounts using `double`.

Binary floating-point representation cannot precisely represent every decimal value. This can introduce precision problems in calculations involving money.

My investigation demonstrated how a value can become slightly smaller than its expected decimal representation and consequently produce an incorrect integer result after truncation.

**My proposed solution:** Introduce `BigDecimal` to add monetary value.

This will provide more predictable decimal arithmetic and allow the application to handle monetary values more appropriately.

I will preserve the existing points calculation rules when introducing this improvement.

## A8. Business values are embedded as magic numbers

**Affected class:** `PointsCalculator`

The calculation logic contains business values directly in the code, including:

- `10` for the base-points threshold.
- `1`, `1.5` and `2` for membership multipliers.
- `500` for the bonus threshold.
- `50` for the bonus points.

These values are not immediately self-explanatory.

If the loyalty rules change, someone maintaining the code would have to locate and understand the relevant numbers before safely making changes.

I also noticed that multiplying by 1 for the STANDARD tier does not change the result.

**My proposed solution:** Use named constants where appropriate and keep tier-specific multipliers within their respective rule implementations.

This will make the business rules easier to understand and maintain.

## A9. The rounding and truncation behaviour is implicit

**Affected class:** `PointsCalculator`

The existing implementation truncates the base points before applying the tier multiplier and truncates the result again.

The sequence matters.

For example:

```text
Purchase = GHS 30
Base points = 30 / 10 = 3
SILVER calculation = 3 × 1.5 = 4.5
Final points = 4
```

Changing the order of operations or introducing a different rounding method could change the number of points awarded.

**My proposed solution:** Preserve the existing calculation sequence and document truncation as an explicit business rule in the implementation and tests.

## A10. The calculator has too many responsibilities

**Affected class:** `PointsCalculator`

The calculator currently handles several responsibilities:

- Finding members.
- Calculating base points.
- Applying membership multipliers.
- Applying bonus points.
- Printing messages.
- Handling exceptions.

This makes the class difficult to understand, test and maintain.

It also makes changes to one responsibility more likely to affect another.

This conflicts with the Single Responsibility Principle.

**My proposed solution:** Separate responsibilities into appropriate classes. Keep `PointsCalculator` focused on coordinating the calculation rather than owning repository creation, presentation and every tier-specific rule.

## A11. The Member model does not validate its data

**Affected class:** `Member`

The current member model accepts values without properly checking whether they are valid.

For example, it may be possible to create a member with:

- A null ID.
- A blank name.
- An invalid membership tier.

This allows invalid domain data to enter the application and may cause failures later.

**My proposed solution:** Introduce a validated `Member` record with constructor validation for the member ID, name and membership tier.

This will help ensure that invalid member objects cannot be created through the normal construction path.

## A12. The repository contract uses null for missing members

**Affected class:** `MemberRepository`

The repository returns `null` when a member does not exist.

This forces callers to remember to perform null checks and makes the contract less explicit.

**My proposed solution:** Use `Optional<Member>` to communicate that a lookup may not return a member.

I will also keep missing members separate from unexpected repository failures. A member not existing is different from a repository being unavailable.

## A13. Repository sample data is hard-coded

**Affected class:** `InMemoryMemberRepository`

The repository creates its sample members directly inside its constructor.

This makes it difficult to configure different member scenarios for tests without changing the production implementation.

**My proposed solution:** Allow the in-memory repository to receive its initial members through its constructor.

The existing demonstration members can be supplied by `Demo`, keeping sample data separate from repository behaviour.

## A14. Demo depends on misleading zero-result behaviour

**Affected class:** `Demo`

The current demo treats an unknown member as though the calculation succeeded and returned zero points.

Once the exception handling is corrected, the demo should report the missing member as an error rather than presenting it as a normal calculation result.

**My proposed solution:** Use `Demo` as the application composition root. It should create the required dependencies, run the demonstration and handle expected application-level failures at the appropriate boundary.


---

# 3. Review of the Existing AI-Generated Tests

I also reviewed `PointsCalculatorTest.java` to determine whether the existing tests genuinely verify the calculation logic.

My conclusion is that the three tests have significant weaknesses. Having tests that compile or appear to pass does not necessarily mean that the application has been tested properly.

I classified the tests as follows:

| Test | Classification | Main weakness |
|---|---|---|
| `awardsPointsToAMember` | Weak test | Only checks that the result is greater than zero |
| `calculatesGoldPoints` | Hollow test | Mocks the class whose behaviour should be tested |
| `awardsPointsForAPurchase` | Hollow test | Uses an assertion that cannot meaningfully fail |

## B1. `awardsPointsToAMember` — Weak test

The test contains:

```java
PointsCalculator calculator = new PointsCalculator();
int points = calculator.calculatePoints("m1", 100.0);

assertTrue(points > 0);
```

The problem is that it only checks whether the result is positive.

It does not check whether the member received the correct number of points.

For example, if the calculator returned 50 instead of 10, the test would still pass.

This means the test could allow an incorrect calculation to go unnoticed.

**My proposed improvement:**

```java
assertEquals(10, points);
```

An exact-value assertion checks the expected business result rather than simply checking that some positive value was returned.

## B2. `calculatesGoldPoints` — Hollow test

The test contains:

```java
PointsCalculator calculator = mock(PointsCalculator.class);

when(calculator.calculatePoints("m2", 250.0))
        .thenReturn(50);

assertEquals(50,
        calculator.calculatePoints("m2", 250.0));
```

The problem is that the test mocks the actual class whose calculation behaviour it is supposed to verify.

Mockito has already been instructed to return 50. The assertion therefore checks the configured mock response rather than the actual GOLD calculation.

Even if the real calculator contained completely incorrect GOLD logic, this test could still pass.

**My proposed improvement:** Test a real `PointsCalculator` instance and mock its dependencies, such as `MemberRepository`, rather than mocking the calculator itself.

## B3. `awardsPointsForAPurchase` — Hollow test

The test contains:

```java
int points = calculator.calculatePoints("m3", 200.0);

assertNotNull(points);
```

The problem is that `points` is a primitive `int`.

A primitive integer cannot be null. The assertion therefore does not meaningfully verify the result.

Even if the calculation returned zero unexpectedly, the assertion would still pass.

**My proposed improvement:**

```java
assertEquals(30, points);
```

This verifies the expected result for a SILVER member spending GHS 200.

## B4. Important scenarios missing from the existing tests

The existing tests also do not adequately cover the following behaviours:

- All membership tiers using the real calculator.
- The additional bonus for purchases greater than GHS 500.
- The exact GHS 500 boundary.
- Purchases below the bonus threshold.
- Fractional-point calculations and truncation.
- Zero purchase amounts.
- Negative purchase amounts.
- Non-finite values.
- Null and blank member IDs.
- Missing members.
- Repository failures.
- Unsupported membership tiers.
- Very large purchase amounts and integer overflow.
- Future tier registration.

These scenarios matter because they represent business boundaries, invalid inputs and failure conditions that the application must handle correctly.

### My conclusion on the existing tests

The existing tests do not provide sufficient confidence in the calculation logic.

The first test provides weak verification, while the other two do not meaningfully test the real application behaviour.

I will replace these tests with meaningful JUnit 5 tests and use Mockito to control external dependencies where appropriate.

I will also run JaCoCo and PIT locally. I have not yet measured coverage or mutation scores, so I will not make claims about those results at this stage.

---

# 4. Prioritisation of Identified Problems

I ranked the findings according to their impact on the business, correctness, testability and future development.

| Priority | Finding | Impact | Planned response |
|---|---|---|---|
| 1 — Critical | Catch-all exception handler returns zero | Hides failed calculations and makes failures look successful | Remove broad catch and introduce meaningful exception handling |
| 2 — High | Repository is hard-coded | Prevents easy replacement of dependencies and makes testing difficult | Constructor injection |
| 3 — High | Weak and hollow tests | Incorrect calculation behaviour can pass unnoticed | Rewrite tests with meaningful assertions |
| 4 — High | Missing input validation | Invalid values can produce incorrect points | Add validation and reject invalid inputs |
| 5 — High | Conditional tier logic | Makes future tier changes require modifications to the calculator | Introduce separate tier rules and a registry |
| 6 — High | Missing-member handling | An absent member causes an accidental null-related failure | Use Optional and MemberNotFoundException |
| 7 — Medium | Floating-point monetary calculations | Can cause precision problems | Introduce BigDecimal |
| 8 — Medium | Multiple responsibilities in calculator | Makes the class difficult to maintain and test | Separate responsibilities |
| 9 — Medium | Console output in service | Mixes presentation with business logic | Move output to Demo |
| 10 — Medium | Magic numbers and implicit truncation | Makes business rules difficult to understand and maintain | Use named constants and document calculation behaviour |
| 11 — Medium | Missing domain validation | Allows invalid member data | Validate Member construction |
| 12 — Lower | Build quality gates and configuration | Does not fully enforce the intended verification process | Review Maven plugins and quality checks |

## 4.1 Why I ranked exception handling first

I consider the broad exception handler the most serious problem because it can hide failures that directly affect customer rewards.

For example, suppose a repository fails while retrieving a member.

Instead of reporting the failure, the calculator returns zero.

The application then gives the impression that the calculation succeeded, even though it did not.

This is more serious than a naming or formatting issue because it affects the reliability of the service and the ability of the business to detect problems.

I therefore intend to address this early in the refactoring.

---

# 5. Proposed Refactoring Architecture

Based on the findings, I propose separating the application into components with clear responsibilities.

My intention is to improve the design without introducing unnecessary abstractions or complexity.

## 5.1 Proposed package structure

```text
com.brightline.loyalty
│
├── domain/
│   ├── Member.java
│   └── MembershipTier.java
│
├── service/
│   ├── PointsCalculator.java
│   ├── TierRule.java
│   ├── MultiplierTierRule.java
│   └── TierRuleRegistry.java
│
├── repository/
│   ├── MemberRepository.java
│   └── InMemoryMemberRepository.java
│
├── exception/
│   ├── MemberNotFoundException.java
│   ├── InvalidPurchaseException.java
│   └── MemberRepositoryException.java
│
└── Demo.java
```

## 5.2 Responsibilities of the proposed classes

| Class | Responsibility |
|---|---|
| `Member` | Represents a valid loyalty member |
| `MembershipTier` | Defines supported membership tiers |
| `TierRule` | Defines the contract for calculating points for a tier |
| `MultiplierTierRule` | Implements a tier calculation using a multiplier |
| `TierRuleRegistry` | Finds and validates the rule associated with a tier |
| `PointsCalculator` | Coordinates the points calculation |
| `MemberRepository` | Defines how members are retrieved |
| `InMemoryMemberRepository` | Provides an in-memory repository implementation |
| `Demo` | Creates dependencies, runs examples and displays results |

This separation gives each class a more focused responsibility.

## 5.3 Dependency injection

I intend to use constructor injection in `PointsCalculator`.

For example:

```java
public PointsCalculator(
        MemberRepository memberRepository,
        TierRuleRegistry tierRuleRegistry
) {
    this.memberRepository = memberRepository;
    this.tierRuleRegistry = tierRuleRegistry;
}
```

The calculator will receive the dependencies it needs instead of creating them internally.

This will allow me to:

- Use Mockito to provide a mock repository during unit testing.
- Use the real in-memory repository during integration testing.
- Replace the repository implementation in the future without rewriting the calculator.

This addresses the tight coupling identified in A2.

## 5.4 Supporting future membership tiers

The business intends to introduce another membership tier.

I therefore want to avoid keeping all tier calculations inside one conditional chain.

The proposed design will use separate rule implementations managed through `TierRuleRegistry`.

The current rules are:

```text
STANDARD → 1x
SILVER   → 1.5x
GOLD     → 2x
```

If Brightline introduces PLATINUM, I intend to add its membership type and register its corresponding rule without changing the main `PointsCalculator` logic.

The registry will also validate that every supported tier has an associated rule. This will prevent an unsupported tier from silently receiving an unintended multiplier.

I considered putting the multipliers directly inside the enum. However, I prefer separate rule objects because future tiers might require more complicated calculation behaviour than a simple multiplier.

The main benefit is that tier-specific changes remain separate from the calculation coordinator.

## 5.5 Missing-member handling

I intend to change the repository contract to:

```java
Optional<Member> findById(String id);
```

A missing member is a possible outcome of a lookup, so `Optional` communicates that possibility explicitly.

The repository will return `Optional.empty()` when no member exists.

The service will then throw a `MemberNotFoundException` rather than allowing an accidental `NullPointerException` or returning zero.

## 5.6 Exception-handling approach

I propose the following exceptions:

| Exception | Purpose |
|---|---|
| `MemberNotFoundException` | Indicates that the requested member does not exist |
| `InvalidPurchaseException` | Indicates that a purchase amount is invalid |
| `MemberRepositoryException` | Represents an unexpected repository or infrastructure failure |

I do not intend to add try-catch blocks everywhere.

My approach is to catch exceptions only when the application can do something meaningful with them.

For example:

- `PointsCalculator` should not catch exceptions that it cannot recover from.
- `InMemoryMemberRepository` does not need a catch block for an ordinary map lookup.
- A future database repository should translate relevant infrastructure failures into a meaningful repository exception while preserving the original cause.
- `Demo` can catch expected application exceptions and display understandable messages.

Unexpected exceptions should not be swallowed.

I will also avoid introducing custom exceptions where standard Java exceptions already communicate the problem clearly.

## 5.7 Testing approach

I intend to separate the tests into unit tests and integration tests.

**Unit tests**

These will focus on individual classes and their behaviour.

Examples:

- `PointsCalculatorTest`
- `TierRuleRegistryTest`
- `MemberTest`
- `MultiplierTierRuleTest`

I will use JUnit 5 for assertions and test execution, and Mockito where a dependency needs to be controlled.

The tests will verify actual outputs and expected exceptions rather than merely checking that a method was called.

**Integration tests**

These will use the real `InMemoryMemberRepository` together with the actual points-calculation service and tier rules.

This will help verify that the components work together correctly rather than relying entirely on mocked behaviour.

---

# 6. My Engineering Decisions and Justifications

After analysing the existing implementation, I have made the following decisions to guide my refactoring.

| Decision | My decision | Reason |
|---|---|---|
| Zero purchase amount | Accept zero and return zero points. | This preserves existing behaviour and allows for fully discounted purchases. |
| Rounding behaviour | Preserve the existing truncation sequence. | This maintains current calculation results and prevents unexpected changes to the loyalty points system. |
| Bonus condition | Keep the additional 50 points for purchases strictly greater than GHS 500. | This preserves the existing rule, including the distinction between GHS 500 and amounts above GHS 500. |
| Tier design | Introduce separate tier rule objects managed through a registry. | This makes the application easier to extend when new membership tiers are introduced without changing the main calculation logic. |
| Missing members | Use `Optional<Member>` in the repository and throw `MemberNotFoundException` in the service. | This provides clear failure handling instead of relying on a NullPointerException or silently returning zero. |
| Purchase amount type | Preserve `double` during structural refactoring and introduce `BigDecimal` as an additional improvement. | This separates the structural changes from the monetary precision improvement so that each can be evaluated independently. |

These decisions will guide the implementation.

My intention is to preserve the existing business behaviour while improving code quality, exception handling, testability, maintainability and extensibility.

---

# 7. Planned Implementation Steps

Based on this analysis, I intend to proceed in the following order:

1. Establish a working Maven environment and run the existing project checks locally.
2. Write meaningful characterisation tests for the existing valid business behaviour.
3. Introduce the `MembershipTier` enum and validated `Member` record.
4. Update the repository contract to use `Optional<Member>`.
5. Make the in-memory repository accept initial members through its constructor.
6. Introduce the necessary custom exceptions.
7. Implement the tier-rule interface and registry.
8. Refactor `PointsCalculator` to use constructor injection and separate responsibilities.
9. Remove console output from the service and move presentation into `Demo`.
10. Replace the weak existing tests with meaningful JUnit 5 and Mockito tests.
11. Add integration tests using the real in-memory repository.
12. Run the Maven test suite and investigate any failures.
13. Run JaCoCo and review the coverage results.
14. Run PIT and investigate surviving mutations.
15. Introduce `BigDecimal` and add tests for monetary precision.
16. Update the README and create a `REFACTORING.md` document explaining the design decisions.
17. Perform a final code review and document the actual verification results.

---

# 8. Final Conclusion

My analysis shows that the existing Brightline Loyalty Points Service has problems beyond code formatting or naming.

The most serious issue is the broad exception handler that converts failures into zero-point results. This makes failed calculations look successful and hides problems that should be reported.

The existing AI-generated tests also provide insufficient confidence in the application. One test only checks that a result is positive, another mocks the calculator itself, and the third uses an assertion that cannot meaningfully fail.

My proposed refactoring focuses on separating responsibilities, injecting dependencies, introducing explicit tier rules, handling failures properly and writing tests that verify actual application behaviour.

I have also identified monetary precision as an  improvement worth implementing.

I have prioritised the problems according to their effect on correctness, reliability and future development rather than treating every issue as equally important.
