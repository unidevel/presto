# Codebase Structure

**Analysis Date:** 2026-03-20

## Directory Layout

```
presto/                          # Root - PrestoDB monorepo
├── pom.xml                      # Maven parent POM
├── presto-spi/                  # Service Provider Interface
├── presto-parser/               # SQL parser (ANTLR)
├── presto-analyzer/             # Semantic analyzer
├── presto-main/                 # Core execution engine
├── presto-main-base/            # Base classes for main
├── presto-main-tests/           # Tests for main
├── presto-client/               # Client libraries
├── presto-cli/                  # CLI tool
├── presto-jdbc/                 # JDBC driver
├── presto-server/               # Server packaging
├── presto-tests/                # Integration tests
├── presto-testng-services/      # Test utilities
├── presto-hive/                 # Hive connector
├── presto-mysql/                # MySQL connector
├── presto-postgresql/           # PostgreSQL connector
├── presto-delta/                # Delta Lake connector
├── presto-iceberg/              # Iceberg connector
├── presto-kafka/                # Kafka connector
├── presto-native-execution/     # C++ native execution
├── presto-native-tests/         # Native execution tests
├── src/                         # Shared resources
│   └── checkstyle/             # Checkstyle configuration
├── docker/                      # Docker configurations
├── scripts/                     # Utility scripts
└── velox/                       # Velox submodule (C++ engine)
```

## Core Module Purposes

### Query Processing
| Module | Purpose | Key Files |
|--------|---------|-----------|
| `presto-spi` | Extension interfaces | `Connector*.java`, `Type*.java` |
| `presto-parser` | SQL parsing | `SqlParser.java`, `grammar/` |
| `presto-analyzer` | Semantic analysis | `Analyzer.java`, `Symbol.java` |
| `presto-main` | Query execution | `dispatcher/`, `execution/`, `operator/` |

### Connectors
| Module | Data Source |
|--------|-------------|
| `presto-hive` | Hive Metastore, HDFS, S3 |
| `presto-iceberg` | Apache Iceberg tables |
| `presto-delta` | Delta Lake tables |
| `presto-mysql` | MySQL databases |
| `presto-postgresql` | PostgreSQL databases |
| `presto-oracle` | Oracle databases |
| `presto-sqlserver` | SQL Server |
| `presto-kafka` | Kafka streams |
| `presto-redis` | Redis key-value |
| `presto-cassandra` | Cassandra |
| `presto-mongodb` | MongoDB |
| `presto-elasticsearch` | Elasticsearch |
| `presto-bigquery` | Google BigQuery |

### Native Execution
| Directory | Purpose |
|-----------|---------|
| `presto-native-execution/presto_cpp/main/` | C++ server implementation |
| `presto-native-execution/presto_cpp/connectors/` | Native connectors |
| `presto-native-execution/velox/` | Velox execution engine |

## Source Code Structure

### Java Source Layout
Each module follows:
```
module-name/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/facebook/presto/
│   │   │   └── [module-specific packages]
│   │   └── resources/
│   │       └── [config files, META-INF]
│   └── test/
│       └── java/com/facebook/presto/
│           └── [test classes]
```

### Test Organization
- **Unit tests**: `src/test/java/` alongside source
- **Integration tests**: `presto-tests/` module
- **Smoke tests**: `Test*IntegrationSmokeTest.java` in connector modules

## Key File Locations

### Entry Points
- **Coordinator**: `presto-main/src/main/java/com/facebook/presto/server/PrestoServer.java`
- **CLI**: `presto-cli/src/main/java/com/facebook/presto/cli/Presto.java`
- **Native Server**: `presto-native-execution/presto_cpp/main/PrestoMain.cpp`

### Configuration
- **Server config**: `presto-main/src/main/resources/*`
- **Checkstyle**: `src/checkstyle/presto-checks.xml`
- **Maven settings**: Root `pom.xml`

### Connectors
- **Base classes**: `presto-base-jdbc/`
- **Hive implementation**: `presto-hive/src/main/java/com/facebook/presto/hive/`

## Naming Conventions

### Java Files
- **Classes**: `PascalCase` (e.g., `SqlQueryManager.java`)
- **Interfaces**: `PascalCase` with optional `I` prefix (e.g., `Connector`)
- **Enums**: `PascalCase` (e.g., `QueryState`)
- **Constants**: `UPPER_SNAKE_CASE`

### Packages
- **Format**: `com.facebook.presto.[module].[feature]`
- **Example**: `com.facebook.presto.operator.aggregation`

### Directories
- **Packages**: Lowercase, no separators (e.g., `dispatcher`, `execution`)
- **Tests**: Mirror source structure with `Test` prefix

### Test Classes
- **Unit tests**: `Test` + ClassName + `.java` (e.g., `TestSqlQueryManager.java`)
- **Integration tests**: `Test` + Feature + `IntegrationSmokeTest.java`

## Where to Add New Code

### New Feature
1. **Core logic**: `presto-main/src/main/java/com/facebook/presto/[feature]/`
2. **Tests**: `presto-main/src/test/java/com/facebook/presto/[feature]/`
3. **SPI extension**: `presto-spi/src/main/java/com/facebook/presto/spi/`

### New Connector
1. **Implementation**: `presto-[connector]/src/main/java/com/facebook/presto/[connector]/`
2. **Tests**: `presto-[connector]/src/test/java/`
3. **Registration**: Add to `pom.xml` modules list

### New Operator
1. **Implementation**: `presto-main/src/main/java/com/facebook/presto/operator/`
2. **Tests**: `presto-main/src/test/java/com/facebook/presto/operator/`
3. **Plan node**: `presto-main/src/main/java/com/facebook/presto/sql/planner/plan/`

### New Function
1. **SQL function**: `presto-main/src/main/java/com/facebook/presto/operator/project/`
2. **Aggregation**: `presto-main/src/main/java/com/facebook/presto/operator/aggregation/`
3. **Connector function**: In connector module

## Special Directories

| Directory | Purpose | Generated | Committed |
|-----------|---------|-----------|-----------|
| `target/` | Maven build output | Yes | No |
| `velox/` | Velox submodule | Yes (git) | Yes |
| `presto-native-execution/build/` | C++ build output | Yes | No |
| `presto-native-execution/_build/` | C++ build output | Yes | No |
| `.mvn/` | Maven wrapper | Yes | Yes |

## Resource Files

- **Config files**: `src/main/resources/` in each module
- **META-INF/services**: SPI registration files
- **Web resources**: `presto-ui/src/main/resources/`

---

*Structure analysis: 2026-03-20*