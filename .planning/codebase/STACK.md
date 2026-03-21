# Technology Stack

**Analysis Date:** 2026-03-20

## Project Overview

**Project:** PrestoDB - Distributed SQL Query Engine
**Version:** 0.297-SNAPSHOT
**Description:** An open-source distributed SQL query engine for big data analytics, originally developed by Facebook

## Languages

### Primary
- **Java** 17 - Main implementation language for coordinator, worker, and most connectors
- **Python** - Build scripts, testing utilities
- **Scala** 2.13.17 - Some build and plugin configurations

### Secondary
- **C++** 20 - Native execution engine (presto-native-execution)
- **JavaScript/TypeScript** - UI components

## Build System

### Build Tool
- **Maven** 3.3.9 - Primary build system
- **CMake** 3.10+ - Native C++ execution build
- **Nix** - Development environment packaging

### Package Manager
- **Maven** - Java dependencies
- **pnpm** - JavaScript dependencies (for UI)
- **Conan** - C++ dependencies management

## Frameworks & Libraries

### Core Query Processing
- **Airlift** (0.227) - Facebook's framework for building services
- **Velox** - Native C++ execution engine (submodule in `velox/`)
- **ANTLR** 4.13.2 - SQL parsing

### Web & Networking
- **Jetty** 12.0.29 - HTTP server
- **Netty** 4.2.10.Final - Async networking
- **Reactor Netty** 1.3.3 - Reactive HTTP client
- **OkHttp** 4.12.0 - HTTP client
- **gRPC** 1.75.0 - Inter-node communication

### Dependency Injection
- **Guice** 6.0.0 - Google Guice for DI

### Data Formats
- **Apache Arrow** 18.3.0 - In-memory columnar format
- **Parquet** 1.16.0 - Columnar storage format
- **ORC** - Optimized Row Columnar format
- **Avro** 1.12.1 - Data serialization
- **Jackson** 2.15.4 - JSON processing
- **Protobuf** 4.30.2 - Protocol buffers

### Cloud Storage
- **AWS SDK** 1.12.782 / 2.32.9 - S3 and AWS services
- **Google Cloud Storage SDK** 1.9.17 - GCS support
- **Hadoop** 3.4.1-1 - HDFS and data lake connectors

### Database Connectors
- **JDBC** - Generic database connectivity
- **JDBI** 3.49.0 - Database access
- **MySQL** connector
- **PostgreSQL** connector
- **SQL Server** connector
- **Oracle** connector
- **MongoDB** driver
- **Cassandra** driver
- **Redis** driver

### Testing
- **TestNG** 7.5 - Test framework
- **AssertJ** 3.8.0 - Assertion library
- **TestContainers** 2.0.3 - Docker-based testing

### Logging & Monitoring
- **SLF4J** 2.0.16 - Logging facade
- **OpenTelemetry** 1.58.0 - Observability
- **Prometheus** - Metrics collection
- **JMX** - Management/monitoring

### Security
- **JWT** - JSON Web Token authentication
- **OAuth2** - Authentication framework

### Other Notable Dependencies
- **Guava** 32.1.0-jre - Google utilities
- **Joda-Time** 2.14.0 - Date/time library
- **Lucene** 9.12.0 - Search engine (internal usage)
- **Iceberg** 1.10.0 - Table format
- **Hudi** 0.14.0 - Data lake format
- **Delta Lake** - ACID table format support
- ** Pinot** 1.4.0 - OLAP database
- **Druid** 35.0.0 - Analytics database

## Native Execution (presto-native-execution)

### C++ Stack
- **C++ Standard** 20
- **Velox** - Facebook's vectorized execution engine
- **Folly** - Facebook's C++ library
- **fmt** - String formatting
- **gflags** - Command-line flags
- **glog** - Logging

### Build Options
- S3 support (PRESTO_ENABLE_S3)
- HDFS support (PRESTO_ENABLE_HDFS)
- GCS support (PRESTO_ENABLE_GCS)
- Azure Blob Storage (PRESTO_ENABLE_ABFS)
- Parquet support (PRESTO_ENABLE_PARQUET)
- Arrow Flight (PRESTO_ENABLE_ARROW_FLIGHT_CONNECTOR)
- JWT authentication (PRESTO_ENABLE_JWT)
- Spatial functions (PRESTO_ENABLE_SPATIAL)

## Configuration Files

- **pom.xml** - Maven parent POM (root and module POMs)
- **CMakeLists.txt** - C++ build configuration
- **checkstyle/presto-checks.xml** - Code style enforcement
- **.clang-format** - C++ code formatting
- **.pre-commit-config.yaml** - Git hooks
- **mvnw/mvnw.cmd** - Maven wrapper

## Development Environment

- **Java 17** - Required JDK
- **Maven 3.3.9+**
- **CMake 3.10+**
- **Clang/LLVM** - C++ compiler
- **Nix** (optional) - Development environment

## Platform

- **Target OS:** Linux (primary), macOS (development)
- **Deployment:** Distributed cluster (coordinator + workers)
- **Container:** Docker support for testing and deployment

---

*Stack analysis: 2026-03-20*