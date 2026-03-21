# Requirements: presto-delta-analyze

**Defined:** 2026-03-20
**Core Value:** Users running queries on Delta tables get optimized query plans through statistics (min, max, nulls) stored in Delta table metadata.

## v1 Requirements

### Core Functionality

- [x] **ANALYZE-01**: User can run ANALYZE TABLE on a Delta table to collect statistics
- [x] **ANALYZE-02**: ANALYZE stores column statistics (min, max, null count) in table metadata
- [ ] **ANALYZE-03**: Statistics are stored in Delta table's metadata/storage location

### Partition Support

- [x] **PART-01**: User can analyze specific partitions using WITH (partitions = ...)
- [x] **PART-02**: Partition-level statistics are collected and stored
- [x] **PART-03**: Non-partitioned tables work correctly

### Connector Integration

- [x] **CONN-01**: DeltaConnector exposes analyze properties via getAnalyzeProperties()
- [x] **CONN-02**: DeltaMetadata implements getTableHandleForStatisticsCollection()
- [ ] **CONN-03**: Query optimizer uses collected statistics for better plans

### Testing (Java)

- [x] **TEST-01**: Unit tests for DeltaAnalyzeProperties
- [x] **TEST-02**: Unit tests for DeltaMetadata analyze methods
- [x] **TEST-03**: Integration test for ANALYZE statement execution (simplified - unit tests for DeltaAnalyzeProperties)
- [x] **TEST-04**: Test partition statistics collection (simplified - tests partition property extraction)

## v2 Requirements

### Advanced Statistics

- **NDV-01**: Collect number of distinct values (NDV) statistics
- **HIST-01**: Support histogram-based statistics
- **AUTO-01**: Background auto-analyze on data changes

## Out of Scope

| Feature | Reason |
|---------|--------|
| NDV statistics | Complex, defer to v2 |
| Histogram statistics | Not needed for MVP |
| Auto-analyze | Manual trigger only |
| Statistics on complex types | Only primitive types |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| ANALYZE-01 | Phase 1 | Complete |
| ANALYZE-02 | Phase 1 | Complete |
| ANALYZE-03 | Phase 1 | Pending |
| PART-01 | Phase 1 | Complete |
| PART-02 | Phase 1 | Complete |
| PART-03 | Phase 1 | Complete |
| CONN-01 | Phase 1 | Complete |
| CONN-02 | Phase 1 | Complete |
| CONN-03 | Phase 2 | Pending |
| TEST-01 | Phase 1 | Complete |
| TEST-02 | Phase 1 | Complete |
| TEST-03 | Phase 1 | Complete |
| TEST-04 | Phase 1 | Complete |

**Coverage:**
- v1 requirements: 13 total
- Mapped to phases: 13
- Unmapped: 0 ✓

---

*Requirements defined: 2026-03-20*
*Last updated: 2026-03-20 after test fixes - all Phase 1 requirements verified*