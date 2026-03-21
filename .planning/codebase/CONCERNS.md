# Codebase Concerns

**Analysis Date:** 2026-03-20

## Technical Debt

### Native Execution Migration (High Priority)

**Issue:** Presto is transitioning from Java-based execution to native C++ (Velox) execution for performance
- **Files:** `presto-native-execution/` - Native implementation
- **Impact:** Dual code paths require maintenance, feature parity work
- **Fix approach:** Complete Velox integration, deprecate Java execution path

### Legacy Configuration (Medium Priority)

**Issue:** Some configuration patterns are outdated
- **Files:** `presto-main/src/main/java/com/facebook/presto/server/PrestoServer.java:185`
- **Comment:** `// TODO: remove this huge hack`
- **Impact:** Code complexity, maintenance burden
- **Fix approach:** Modernize configuration system

### Node Scheduling (Medium Priority)

**Issue:** Node scheduling configuration needs improvement
- **Files:** `presto-main/src/main/java/com/facebook/presto/server/ServerMainModule.java:431`
- **Comment:** `// TODO: remove from NodePartitioningManager and move to CoordinatorModule`
- **Impact:** Architectural inconsistency
- **Fix approach:** Refactor to proper module structure

## Known Issues / TODOs

### Query State Management
- **File:** `presto-main/src/main/java/com/facebook/presto/server/remotetask/HttpRemoteTaskWithEventLoop.java:928`
- **Issue:** Query/stage state machines depend on TaskStatus directly
- **Comment:** `// TODO: Update the query state machine and stage state machine to depend on TaskStatus instead`

### Resource Manager Decoupling
- **File:** `presto-main/src/main/java/com/facebook/presto/server/ResourceManagerModule.java:78`
- **Issue:** Query-level config not needed for Resource Manager
- **Comment:** `// TODO: decouple query-level configuration that is not needed for Resource Manager`

### Memory Pool Changes
- **File:** `presto-main/src/main/java/com/facebook/presto/memory/ClusterMemoryManager.java:463`
- **Issue:** Reserved pool being removed
- **Comment:** `// TODO once the reserved pool is removed we can remove this method`

### Failure Detection
- **File:** `presto-main/src/main/java/com/facebook/presto/metadata/DiscoveryNodeManager.java:289`
- **Issue:** Need whitelist for failure-detecting service
- **Comment:** `// TODO: make it a whitelist (a failure-detecting service selector)`

### Trace Continuation
- **Files:**
  - `presto-main/src/main/java/com/facebook/presto/server/protocol/QueuedStatementResource.java:248`
  - `presto-main/src/main/java/com/facebook/presto/server/protocol/QueuedStatementResource.java:322`
- **Issue:** Tracing not started from client
- **Comment:** `// TODO: For future cases we may want to start tracing from client`

### Query Purging
- **File:** `presto-main/src/main/java/com/facebook/presto/server/protocol/QueuedStatementResource.java:364`
- **Issue:** Retryable queries not purged slower than normal ones

### Common Place Refactoring
- **Files:**
  - `presto-main/src/main/java/com/facebook/presto/server/ResourceGroupStateInfoResource.java:217`
  - `presto-main/src/main/java/com/facebook/presto/server/QueryStateInfoResource.java:184`
- **Issue:** Duplicate patterns for resource info
- **Comment:** `//TODO move this to a common place and reuse`

## Security Considerations

### Authentication
- Multiple auth mechanisms exist (OAuth2, JWT, Kerberos, LDAP)
- Proper validation and secret management required
- Review `presto-main/src/main/java/com/facebook/presto/server/security/`

### Secret Storage
- **Current:** File-based, environment variables
- **Recommendation:** Implement dedicated secret management (Vault integration)
- **Risk:** Plaintext secrets in configuration files

### Access Control
- Connector-level access control via SPI
- Row-level filtering available but not default
- **Recommendation:** Enable row-level security for sensitive data

## Performance Concerns

### Java Execution vs Native
- **Issue:** Java-based execution lacks vectorization
- **Impact:** Performance gap compared to Velox
- **Fix:** Native execution path (ongoing)

### Memory Management
- **Current:** Cluster-wide memory manager
- **Concern:** Large queries can impact cluster stability
- **Fix:** Better memory accounting, query queuing

### Exchange Performance
- **Issue:** HTTP-based exchange between nodes
- **Concern:** Network overhead
- **Optimization:** Consider zero-copy transfer

## Fragile Areas

### Connector Classloading
- **Files:** `presto-spi/src/main/java/com/facebook/presto/spi/connector/classloader/`
- **Why fragile:** Dynamic classloading can cause class conflicts
- **Safe modification:** Use provided classloader wrappers
- **Test coverage:** Limited - requires integration testing

### Task Heartbeat
- **Files:** `presto-main/src/main/java/com/facebook/presto/failureDetector/`
- **Why fragile:** Distinguishing GC pauses from node failures
- **Comment:** `// TODO: distinguish between process unresponsiveness (e.g GC pause) and host reboot`
- **Safe modification:** Be conservative with failure detection

### Protocol Codecs
- **Files:** `presto-main/src/main/java/com/facebook/presto/server/protocol/`
- **Why fragile:** Version compatibility, serialization
- **Safe modification:** Maintain backward compatibility

## Scaling Limits

### Cluster Size
- **Current capacity:** Tested up to 1000+ workers
- **Limit:** Discovery service becomes bottleneck at scale
- **Scaling path:** Use resource manager for larger deployments

### Query Concurrency
- **Current capacity:** Depends on memory/CPU
- **Limit:** Memory-bound on coordinator for large result sets
- **Scaling path:** Use output stage pagination

### Connector Scalability
- **Varies by connector:**
  - Hive: Scales to petabytes with proper partitioning
  - RDBMS: Limited by database connection pool
  - Kafka: Scales with partition count

## Dependencies at Risk

### Velox Integration
- **Risk:** Fast-moving upstream
- **Impact:** API changes may break native execution
- **Migration plan:** Pin versions, participate in Velox development

### Airlift Framework
- **Risk:** Facebook-maintained, may diverge from community needs
- **Impact:** Breaking changes without notice
- **Migration plan:** Reduce Airlift dependencies over time

### TestContainers
- **Risk:** Version 2.0+ introduced breaking changes
- **Impact:** Integration tests may fail
- **Migration plan:** Update test infrastructure

## Missing Critical Features

### Cost-Based Optimizer Limitations
- **Problem:** Basic cost estimation
- **Blocks:** Optimal query plans for complex queries
- **Priority:** High

### Adaptive Query Execution
- **Problem:** No runtime query re-optimization
- **Blocks:** Handling data skew
- **Priority:** Medium

### Full ACID Support
- **Problem:** Depends on connector for transaction support
- **Blocks:** Strong consistency requirements
- **Priority:** Medium

## Test Coverage Gaps

### Integration Tests
- **What's not tested:** Cross-connector queries
- **Files:** `presto-tests/src/test/java/`
- **Risk:** Integration issues between components
- **Priority:** Medium

### Security Tests
- **What's not tested:** All OAuth2 flows, complex ACL scenarios
- **Files:** `presto-main/src/test/java/com/facebook/presto/server/security/`
- **Risk:** Security vulnerabilities
- **Priority:** High

### Performance Tests
- **What's not tested:** Large-scale performance benchmarks
- **Files:** `presto-benchto-benchmarks/`
- **Risk:** Performance regressions
- **Priority:** Medium

---

*Concerns audit: 2026-03-20*