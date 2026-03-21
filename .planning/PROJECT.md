# presto-delta-analyze

## What This Is

Add ANALYZE statement support to the presto-delta connector, enabling users to collect table and column statistics for query optimization.

## Core Value

Users running queries on Delta tables get optimized query plans through statistics (min, max, nulls) stored in Delta table metadata.

## Requirements

### Validated

(None yet)

### Active

- [ ] Implement ANALYZE statement for presto-delta connector
- [ ] Store statistics (min, max, nulls) in Delta table metadata
- [ ] Support partition-level statistics collection
- [ ] Expose analyze properties similar to presto-hive

### Out of Scope

- [NDV statistics] — Defer to future version
- [Histogram statistics] — Complex, defer
- [Auto-analyze background job] — Manual trigger only

## Context

Presto-delta extends Hive metastore for table metadata. Need to follow presto-hive patterns:
- `HiveAnalyzeProperties` → `DeltaAnalyzeProperties`
- `HiveMetadata.getTableHandleForStatisticsCollection` → `DeltaMetadata`
- Statistics stored in table parameters (like Hive)

## Constraints

- **[Pattern]**: Follow presto-hive implementation — same API, similar storage
- **[Compatibility]**: Statistics visible to Presto query optimizer
- **[Version]**: Java 17, Presto SPI patterns

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Store in Delta metadata | Simple, no external dependency | — Pending |
| Min/Max/Nulls only | MVP scope, cover common cases | — Pending |
| Partition support | Like Hive, users expect it | — Pending |

---

*Last updated: 2026-03-20 after initialization*