# Phase 1: Implementation - Context

**Gathered:** 2026-03-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement core ANALYZE functionality for presto-delta connector: DeltaAnalyzeProperties, DeltaMetadata.getTableHandleForStatisticsCollection(), statistics storage, and unit tests. Follows presto-hive patterns.

</domain>

<decisions>
## Implementation Decisions

### Statistics Storage
- Store statistics in Hive Metastore table parameters (like presto-hive)
- Use existing Hive statistics pattern (not custom Delta _delta_log)
- Key format: Follow existing Hive statistics parameter naming

### Error Handling
- Skip columns that cannot be analyzed (complex types: ARRAY, STRUCT, MAP)
- Log warning for skipped columns
- Continue ANALYZE with remaining analyzable columns

### Test Structure
- Follow presto-hive pattern: TestDeltaAnalyze.java
- Similar structure to TestHiveAnalyze.java
- Unit tests for DeltaAnalyzeProperties, DeltaMetadata methods

</decisions>

<canonical_refs>
## Canonical References

### presto-hive reference implementation
- `presto-hive/src/main/java/com/facebook/presto/hive/HiveAnalyzeProperties.java` — Analyze properties definition
- `presto-hive/src/main/java/com/facebook/presto/hive/HiveMetadata.java` (line 577) — getTableHandleForStatisticsCollection
- `presto-hive/src/main/java/com/facebook/presto/hive/HiveConnector.java` (line 176) — getAnalyzeProperties
- `presto-hive/src/test/java/com/facebook/presto/hive/TestHiveAnalyze.java` — Test reference

### presto-delta existing code
- `presto-delta/src/main/java/com/facebook/presto/delta/DeltaMetadata.java` — Implement analyze here
- `presto-delta/src/main/java/com/facebook/presto/delta/DeltaTableProperties.java` — Similar pattern
- `presto-delta/src/main/java/com/facebook/presto/delta/DeltaConnector.java` — Expose analyze properties

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- DeltaTableProperties.java — Similar pattern for PropertyMetadata list
- DeltaMetadata implements ConnectorMetadata — add getTableHandleForStatisticsCollection

### Established Patterns
- presto-hive AnalyzeProperties pattern for property definition
- Hive statistics stored in table parameters
- getTableHandleForStatisticsCollection returns modified table handle

### Integration Points
- DeltaConnector.getAnalyzeProperties() — Add method to expose properties
- DeltaMetadata.getTableHandleForStatisticsCollection() — New method
- HiveMetastore.updateTable() — Store statistics in table parameters

</code_context>

<specifics>
## Specific Ideas

- Follow presto-hive exactly for consistency
- Statistics: min, max, null count only (no NDV)
- Partition support: WITH (partitions = [...]) like Hive

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-implementation*
*Context gathered: 2026-03-20*