# Architecture

**Analysis Date:** 2026-03-20

## Overview

PrestoDB is a distributed SQL query engine designed for fast analytic queries against data sources of all sizes. It follows a coordinator-worker architecture and supports two execution modes: Java-based and native C++ (Velox-based).

## Architectural Patterns

### Distributed Coordinator-Worker Model

Presto uses a master-slave architecture:
- **Coordinator** - Parses SQL, creates distributed query plan, schedules work to workers
- **Worker** - Executes query tasks, processes data, exchanges intermediate results
- **Discovery Service** - Worker registration and health tracking
- **Resource Manager** - Cluster resource management and allocation

### Plugin Architecture (SPI)

Presto uses Java's Service Provider Interface (SPI) for extensibility:
- **Connector SPI** - Data source connectors
- **Function Registry** - User-defined functions
- **Access Control** - Security plugins
- **Event Listener** - Query event monitoring
- **Authentication** - Multiple auth mechanisms

### Query Execution Flow

1. **Client Request** → CLI/JDBC/REST sends SQL to coordinator
2. **Parsing** → SQL parser (ANTLR-based) creates AST
3. **Analysis** → Semantic analysis binds names/types
4. **Planning** → Logical plan optimized and distributed
5. **Scheduling** → Coordinator assigns splits to workers
6. **Execution** → Workers process data in parallel
7. **Results** → Intermediate results exchanged, final result returned

## Core Modules/Layers

### presto-spi (Service Provider Interface)
- **Purpose:** Defines contracts for connectors and extensions
- **Location:** `presto-spi/src/main/java/com/facebook/presto/spi/`
- **Key Interfaces:**
  - `Connector` - Main connector interface
  - `ConnectorMetadata` - Table metadata operations
  - `ConnectorSplitManager` - Data splitting for parallel reads
  - `ConnectorPageSourceProvider` - Data reading
  - `ConnectorPageSinkProvider` - Data writing
- **Depends on:** Minimal (no Presto internal deps)
- **Used by:** All connectors and core modules

### presto-parser
- **Purpose:** SQL parsing and AST construction
- **Location:** `presto-parser/src/main/java/com/facebook/presto/sql/`
- **Contains:** ANTLR grammar, parser, expression parsing
- **Key Classes:** `SqlParser`, `ExpressionParser`

### presto-analyzer
- **Purpose:** Semantic analysis and type binding
- **Location:** `presto-analyzer/src/main/java/com/facebook/presto/sql/analyzer/`
- **Contains:** Symbol resolution, type checking, scope analysis

### presto-main / presto-main-base
- **Purpose:** Core query execution engine
- **Location:** `presto-main/src/main/java/com/facebook/presto/`
- **Sub-packages:**
  - `dispatcher` - Query submission and management
  - `execution` - Task execution
  - `operator` - Physical operators (joins, aggregations, etc.)
  - `metadata` - Metadata catalog
  - `server` - HTTP server, REST APIs
  - `memory` - Memory management
  - `resourcemanager` - Resource management

### presto-native-execution (C++ Native)
- **Purpose:** High-performance native query execution
- **Location:** `presto-native-execution/presto_cpp/main/`
- **Architecture:**
  - `PrestoServer.cpp` - Server implementation
  - `TaskManager.cpp` - Task orchestration
  - `operators/` - C++ operators
  - `functions/` - Native functions
- **Uses Velox** vectorized execution engine

## Data Flow

### SQL to Execution
```
SQL String
    ↓
[Parser] → AST (Abstract Syntax Tree)
    ↓
[Analyzer] → Analyzed Statement (typed, bound)
    ↓
[Planner] → Logical Plan → Optimized Plan
    ↓
[Scheduler] → Distributed Plan (stages, splits)
    ↓
[Executors] → Tasks → Drivers → Operators
    ↓
Page Data (columnar Arrow format)
```

### Distributed Execution
```
Coordinator
    ↓ creates stages
Stage 1 (Scan) → Stage 2 (Agg) → Stage 3 (Output)
    ↓ exchange    ↓ exchange
Worker 1         Worker 2         ...
```

## Key Abstractions

### Split
- Represents a unit of work for reading data
- Created by `ConnectorSplitManager`
- Contains data location and schema info

### Page
- In-memory columnar data format
- Uses Apache Arrow format
- Vectorized processing support

### Operator
- Transforms pages (filter, project, join, aggregate)
- Pipeline model for streaming execution

### Exchange
- Inter-operator data movement
- HTTP-based between nodes
- Handles buffering and throttling

## Entry Points

### Server Startup
- `presto-main/src/main/java/com/facebook/presto/server/PrestoServer.java`
  - Initializes HTTP server (Jetty)
  - Loads modules via Guice
  - Registers REST endpoints

### Query Submission
- `presto-main/src/main/java/com/facebook/presto/dispatcher/`
  - `SqlQueryManager` - Query lifecycle
  - `QueryExecutor` - Async execution

### Worker Execution
- `presto-main/src/main/java/com/facebook/presto/execution/SqlTaskExecutor.java`
  - Task creation and execution
  - Driver lifecycle management

### Native Execution
- `presto-native-execution/presto_cpp/main/PrestoMain.cpp`
  - Main entry point
  - `PrestoServer.cpp` - Server setup

## Cross-Cutting Concerns

### Logging
- Uses SLF4J
- Configured via `airlift-logging`
- Structured logging with different levels

### Validation
- Input validation in analyzer
- Type checking at binding
- Constraint validation at execution

### Authentication
- Multiple mechanisms: OAuth2, JWT, Kerberos, password
- `AuthenticationManager` in server module
- Connector-level access control

### Error Handling
- Custom exception hierarchy in `com.facebook.presto.spi`
- Error codes for query failures
- Retry logic for transient failures

### Memory Management
- `ClusterMemoryManager` - Cluster-wide memory
- `MemoryPool` - Per-query memory
- Spill-to-disk for large operations

## Connector Architecture

### Built-in Connectors
- **Hive** - HDFS/Hive data warehouse
- **Iceberg** - Apache Iceberg tables
- **Delta** - Delta Lake tables
- **MySQL, PostgreSQL, SQL Server** - RDBMS
- **Kafka** - Streaming data
- **Redis** - Key-value store
- **Memory** - In-memory test connector
- **Blackhole** - No-op connector for testing

### Connector Structure
Each connector is a separate Maven module with:
- `ConnectorFactory` - Creates connector instance
- `ConnectorMetadata` - Metadata operations
- `ConnectorSplitManager` - Data splitting
- `ConnectorPageSourceProvider` - Data reading

---

*Architecture analysis: 2026-03-20*