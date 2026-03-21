# Testing Patterns

**Analysis Date:** 2026-03-20

## Test Framework

### Runner
- **Framework:** TestNG 7.5
- **Configuration:** Maven surefire plugin
- **Key Configuration:** `presto-testng-services`

### Assertion Libraries
- **Primary:** TestNG assertions (`org.testng.Assert`)
- **Enhanced:** AssertJ 3.8.0 for fluent assertions

### Run Commands
```bash
# Full build with tests
./mvnw clean install

# Build without tests (faster)
./mvnw clean install -DskipTests

# Build specific module
./mvnw clean install -pl presto-main

# Run single test
./mvnw test -pl presto-main -Dtest=TestClassName

# Run tests with pattern
./mvnw test -pl presto-main -Dtest="com.facebook.presto.server.security.oauth2.*"

# Run with specific thread count
./mvnw test -DthreadCount=4 -Dparallel=methods
```

## Test File Organization

### Location
- **Pattern:** Co-located with source code
- **Directory:** `src/test/java/` alongside `src/main/java/`
- **Example:** `com.facebook.presto.operator.TestExchangeClient` in `presto-main/src/test/java/com/facebook/presto/operator/`

### Naming
- **Unit tests:** `Test` + ClassName + `.java`
- **Integration smoke tests:** `Test` + Feature + `IntegrationSmokeTest.java`
- **Base test classes:** `Base` + ClassName + `.java`

### Structure
```
presto-main/
├── src/
│   ├── main/java/.../
│   └── test/java/
│       └── com/facebook/presto/
│           └── operator/
│               ├── TestExchangeClient.java      # Unit test
│               ├── TestExchangeOperator.java    # Unit test
│               └── BaseOperatorTest.java        # Base class
```

## Test Structure

### Basic Test Class
```java
/*
 * Licensed under the Apache License, Version 2.0...
 */
package com.facebook.presto.operator;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TestExchangeClient
{
    @Test
    public void testBasic()
    {
        // Test implementation
    }
}
```

### Test with Setup/Teardown
```java
public class TestSomething
{
    private SomeService service;

    @BeforeClass
    public void setUp()
    {
        service = new SomeService();
    }

    @AfterClass
    public void tearDown()
    {
        service.close();
    }

    @Test
    public void testMethod()
    {
        // Test
    }
}
```

### Integration Test with Server
```java
public class TestIntegrationSmokeTest
{
    private TestingPrestoServer server;

    @BeforeClass
    public void setUp()
    {
        server = new TestingPrestoServer();
        server.start();
    }

    @AfterClass
    public void tearDown()
    {
        server.close();
    }

    @Test
    public void testQuery()
    {
        // Test query execution
    }
}
```

## Mocking

### Framework
- **Mockito** - Included via dependencies
- **Guava** - `MoreObjects`, `ImmutableList` for test data

### Patterns
```java
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Mock
private SomeService mockService;

@BeforeMethod
public void setUp()
{
    MockitoAnnotations.openMocks(this);
}

@Test
public void testWithMock()
{
    when(mockService.method(any())).thenReturn(expected);
    // Test
}
```

### What to Mock
- External services
- Network calls (HTTP, gRPC)
- Database connections
- File system operations

### What NOT to Mock
- Core business logic being tested
- Simple value objects
- Test fixtures

## Fixtures and Factories

### Test Data
- Use factory methods for complex objects
- Create test utilities in `testing/` packages

### Location
- Test utilities: `src/test/java/com/facebook/presto/testing/`
- Fixtures: Inline or in test class

### Example
```java
// Factory method
private static Page createTestPage(Block... blocks)
{
    return new Page(blocks);
}

// In test
Page page = createTestPage(createIntBlock(1, 2, 3));
```

## Coverage

### Requirements
- No enforced coverage target
- High coverage expected for core modules

### View Coverage
```bash
# Generate coverage report
./mvnw test -Dcoverage -DskipTests=false

# HTML report in target/site/jacoco/
```

## Test Types

### Unit Tests
- **Scope:** Single class or method
- **Location:** `src/test/java/` in module
- **Characteristics:**
  - Fast execution
  - No external dependencies
  - Use mocks

### Integration Tests
- **Scope:** Multiple components
- **Location:** `presto-tests/` module
- **Characteristics:**
  - Real server execution
  - Real database connections
  - Slower but comprehensive

### Smoke Tests
- **Scope:** Connector functionality
- **Location:** Each connector module
- **Naming:** `Test*IntegrationSmokeTest.java`
- **Purpose:** Verify basic operations work

## Common Patterns

### Async Testing
```java
@Test
public void testAsyncOperation()
{
    CompletableFuture<String> future = asyncMethod();
    
    // With timeout
    String result = future.get(5, TimeUnit.SECONDS);
    
    assertEquals(result, expected);
}
```

### Error Testing
```java
@Test(expectedExceptions = IllegalArgumentException.class)
public void testInvalidInput()
{
    method(null); // Should throw
}
```

### Resource Testing
```java
@BeforeClass
public void setUp()
{
    Logging logging = Logging.initialize();
    logging.setMinimumLevel(Level.DEBUG);
}

@AfterClass
public void tearDown()
{
    Logging.reset();
}
```

## TestNG Features Used

### Groups
```java
@Test(groups = {"slow", "integration"})
public void testSlow()
{
    // ...
}
```

### Data Providers
```java
@DataProvider
public Object[][] testData()
{
    return new Object[][]{
        {value1, expected1},
        {value2, expected2}
    };
}

@Test(dataProvider = "testData")
public void testParameterized(Object input, Object expected)
{
    // ...
}
```

### Dependencies
```java
@Test(dependsOnMethods = {"testFirst"})
public void testSecond()
{
    // ...
}
```

### Parallel Execution
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <parallel>methods</parallel>
        <threadCount>4</threadCount>
    </configuration>
</plugin>
```

---

*Testing analysis: 2026-03-20*