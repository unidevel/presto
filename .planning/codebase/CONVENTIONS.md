# Coding Conventions

**Analysis Date:** 2026-03-20

## General Guidelines

Based on `AGENTS.md` and `src/checkstyle/presto-checks.xml`:

- **No `@NotNull` annotations** - null is the default
- **One top-level class per file**
- **No star imports**
- **No mutable exceptions**
- **Equals and hashCode must be implemented together**

## Naming Patterns

### Files
- **Java classes**: `PascalCase.java` (e.g., `SqlQueryManager.java`)
- **Test classes**: `Test` prefix (e.g., `TestSqlQueryManager.java`)
- **Integration tests**: `Test*IntegrationSmokeTest.java`

### Type Parameters
- **Class type parameters**: Single uppercase letter (`T`, `K`, `V`)
- **Method type parameters**: Single uppercase letter (`T`, `K`, `V`)
- **Example**: `class SomeClass<T> { <K> void method(K input) {} }`

### Variables and Methods
- **Variables**: `camelCase` (e.g., `queryId`, `maxMemory`)
- **Methods**: `camelCase` (e.g., `getQuery()`, `executeTask()`)
- **Constants**: `UPPER_SNAKE_CASE` (e.g., `DEFAULT_TIMEOUT`)

### Packages
- **Format**: `com.facebook.presto.[module].[feature]`
- **Directories**: Lowercase, no separators

## Code Style

### Formatting Rules
Enforced via `src/checkstyle/presto-checks.xml`:
- **No trailing whitespace**
- **LF line endings only** (no CRLF)
- **No blank lines after opening brace** (`{` cannot be followed by blank line)
- **No blank lines before closing brace** (cannot have blank line before `}`)
- **No whitespace before closing parenthesis** (`) {` is invalid)

### Brace Style
- **K&R style**: Opening brace on same line
- **Example**:
```java
public void method()
{
    if (condition) {
        doSomething();
    }
}
```

## Import Organization

### Order (separated by blank lines)
1. `*` imports (wildcard)
2. `javax` imports
3. `java` imports
4. Blank line between groups

### Alphabetical Sorting
- Imports within each group are sorted alphabetically

### Static Imports
**Allowed:**
- `String.format`
- `Objects.requireNonNull`
- `Math.toIntExact`
- TestNG assertions (from `org.testng.Assert`)

**Forbidden:**
- `of`, `copyOf`, `valueOf` (from any class)
- `all`, `none` (from any class)
- `Optional.*` (any static method)
- `java.util.Format` (except `java.lang.String.format`)

### Annotation Usage
- **Prefer**: `jakarta.annotation.Nullable` over `org.jetbrains.annotations.Nullable`
- **Avoid**: `@NotNull` (null is default)

## Error Handling

### Null Checks
```java
import static java.util.Objects.requireNonNull;

// Use static import
requireNonNull(value, "value is required");
```

### Numeric Conversions
```java
import static java.lang.Math.toIntExact;

// Use static import
int intValue = toIntExact(longValue);
```

### Exceptions
- No mutable exceptions
- Use specific exception types
- Include meaningful error messages

## Test Conventions

### Framework
- **TestNG** (not JUnit)
- **AssertJ** for fluent assertions
- Static imports from `org.testng.Assert`

### Test Structure
```java
import static org.testng.Assert.*;

public class TestSomeClass
{
    @Test
    public void testMethod()
    {
        // Test implementation
    }
}
```

### Test Utilities
- Use `presto-testng-services` for test infrastructure
- Use `TestingPrestoServer` for integration tests
- Use `TestingHydraIdentityProvider` for OAuth testing

## Logging

### Framework
- **SLF4J** - Logging facade
- Use `airlift-logging` for configuration

### Patterns
```java
import com.facebook.airlift.log.Logger;

private static final Logger LOG = Logger.get(SomeClass.class);

// Usage
LOG.info("Query started: %s", queryId);
LOG.debug("Processing %d rows", rowCount);
```

## Javadoc / Comments

### When to Comment
- Public API methods
- Complex business logic
- Non-obvious workarounds
- TODO items with explanations

### Style
- Use Javadoc for public APIs
- Inline comments for complex code
- Avoid obvious comments (e.g., `// increment i`)

### License Header
Every file must include Apache 2.0 license header:
```java
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

## Function Design

### Size
- Keep methods focused and single-purpose
- Large methods should be refactored into smaller ones

### Parameters
- Use interfaces over concrete types when possible
- Validate parameters with `requireNonNull`
- Consider builder pattern for many parameters

### Return Values
- Return empty collections instead of null
- Use `Optional` when absence is meaningful
- Prefer immutable collections

## Module Design

### Exports
- Minimize public APIs
- Use package-private where possible
- Document stable vs. unstable APIs

### Dependencies
- SPI should have minimal dependencies
- Avoid circular dependencies
- Use interfaces for loose coupling

### Barrel Files
- Avoid barrel files (index classes)
- Import directly from implementation files

---

*Convention analysis: 2026-03-20*