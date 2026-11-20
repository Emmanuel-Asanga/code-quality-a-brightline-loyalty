# Brightline Loyalty — Points Service

I built this small Java service to calculate the loyalty points a member earns when making a purchase.

The service follows the business rules below:

| Rule | Behaviour |
|---|---|
| Base points | Members earn 1 point for every GHS 10 spent. Any remainder is dropped. |
| Tier multiplier | STANDARD ×1, SILVER ×1.5, GOLD ×2. Fractional points are truncated. |
| Bonus | Members receive an additional 50 points when a purchase is strictly greater than GHS 500. The bonus is added after applying the tier multiplier. |
| Zero purchase | A purchase of zero is valid and earns 0 points. |
| Invalid input | Invalid input is rejected with a clear exception instead of silently returning 0. |

I designed the service so that adding a new membership tier requires only two changes without modifying the existing calculation logic.

For more details about my design decisions and implementation, see [REFACTORING.md](REFACTORING.md).

## Requirements

To build and run this project, I use:

- **JDK 21**
- **Apache Maven 3.9+**

## Project Structure

I organised the project into separate packages to keep each responsibility clear and make the code easier to maintain and test.

```text
src/
├── main/java/com/brightline/loyalty/
│   ├── domain/        Member (record), MembershipTier (enum)
│   ├── service/       PointsCalculator, TierRule, MultiplierTierRule, TierRuleRegistry
│   ├── repository/    MemberRepository (interface), InMemoryMemberRepository
│   ├── exception/     MemberNotFoundException, InvalidPurchaseException,
│   │                  MemberRepositoryException
│   └── Demo.java      Runnable example
└── test/java/com/brightline/loyalty/
    ├── unit/          Unit tests using a Mockito repository mock
    └── integration/   Integration tests using the real in-memory repository

ANALYSIS.md            My code and test review (Phase 1), including later discoveries
REFACTORING.md         My design decisions, exception handling, and implementation changes
pom.xml                Maven project configuration
```

## Build the Project

I use the following command to clean and compile the project:

```bash
mvn clean compile
```

## Run the Application

To compile and run the demonstration, I use:

```bash
mvn compile
java -cp target/classes com.brightline.loyalty.Demo
```

The expected output is:

```text
Member m1, GHS 100 -> 10 points
Member m2, GHS 250 -> 50 points
Member m3, GHS 600 -> 140 points
Member unknown, GHS 100 -> FAILED: No member found with id 'unknown'
Member m1, GHS -5 -> FAILED: Purchase amount must not be negative: -5
```

An important behaviour I implemented is that an unknown member is reported as a failure rather than silently receiving zero points.

## Use the Service in Another Class

The following example demonstrates how I instantiate and use `PointsCalculator` from another part of the application:

```java
PointsCalculator calculator = new PointsCalculator(
        new InMemoryMemberRepository(List.of(
                new Member("m1", "Kofi Mensah", MembershipTier.GOLD))),
        TierRuleRegistry.standard());

int points = calculator.calculatePoints(
        "m1",
        new BigDecimal("250")); // 50
```

I used constructor-based dependency injection so that the calculator does not depend on a specific repository implementation.

This means another implementation of `MemberRepository`, such as a database-backed repository, can be supplied without changing the calculation logic.

## Run the Tests

I separated my tests into unit and integration tests.

To run the complete test suite:

```bash
mvn test
```

To run only the unit tests:

```bash
mvn test -Dtest='com/brightline/loyalty/unit/*Test'
```

To run only the integration tests:

```bash
mvn test -Dtest='com/brightline/loyalty/integration/*Test'
```

My unit tests use Mockito to isolate the calculator from the repository, while my integration tests use the real `InMemoryMemberRepository` without mocks.

Both test groups run through Maven without requiring external services.

## Code Coverage — JaCoCo

I use JaCoCo to measure code coverage.

To generate the coverage report, I run:

```bash
mvn verify
```

The generated report is available at:

```text
target/site/jacoco/index.html
```

I can open this file in a browser to inspect the coverage results and identify areas that need additional tests.

## Mutation Testing — PIT

I also use PIT Mutation Testing to evaluate whether my tests can detect changes in the application's behaviour.

I run:

```bash
mvn test-compile org.pitest:pitest-maven:mutationCoverage
```

The generated report is available at:

```text
target/pit-reports/index.html
```

I use this report to identify surviving mutants and investigate whether they indicate missing or weak assertions.

My goal is not simply to achieve a high mutation score. I want my tests to verify meaningful application behaviour and detect defects that could affect the loyalty points calculations.

## Documentation

I maintain two main documents to explain the work behind this project:

**1. [ANALYSIS.md](ANALYSIS.md)**

This document contains my review of the inherited code and its AI-generated tests. I identified and ranked the original problems, explained their causes, and documented additional issues I discovered during implementation.

**2. [REFACTORING.md](REFACTORING.md)**

This document explains how I redesigned the application, why I made particular architectural decisions, how I handled exceptions, and what I retained, corrected, or removed from the original implementation.

It also documents my use of `BigDecimal` for monetary calculations, the improvements I made to the test suite, and the verification steps I completed and those that still require confirmation.