# Roadmap: presto-delta-analyze

## Overview

Add ANALYZE statement support to presto-delta connector. Users can collect column statistics (min, max, nulls) stored in Delta table metadata.

## Phases

- [x] **Phase 1: Implementation** — Core ANALYZE functionality, properties, metadata, tests
- [ ] **Phase 2: Optimizer Integration** — Query optimizer uses collected statistics

## Phase Details

### Phase 1: Implementation

**Goal**: Implement core ANALYZE functionality with stats collection and storage

**Depends on**: Nothing (first phase)

**Requirements**: ANALYZE-01, ANALYZE-02, ANALYZE-03, PART-01, PART-02, PART-03, CONN-01, CONN-02, TEST-01, TEST-02, TEST-03, TEST-04

**Success Criteria** (what must be TRUE):

1. User can run ANALYZE TABLE on a Delta table — statement executes without error
2. Column statistics (min, max, null count) stored in table metadata — verified via metadata query
3. Partition-specific analyze works with WITH (partitions = ...) — only specified partitions analyzed
4. Non-partitioned tables analyzed correctly — all data scanned
5. DeltaConnector exposes analyze properties — getAnalyzeProperties() returns valid list
6. DeltaMetadata implements getTableHandleForStatisticsCollection() — returns proper handle
7. Unit tests pass for new components — 80%+ coverage

**Plans**: 3 plans

- [x] 01-01-PLAN.md — DeltaAnalyzeProperties and connector integration
- [x] 01-02-PLAN.md — DeltaMetadata statistics collection
- [x] 01-03-PLAN.md — Testing

### Phase 2: Optimizer Integration

**Goal**: Query optimizer uses collected statistics for better query plans

**Depends on**: Phase 1

**Requirements**: CONN-03

**Success Criteria** (what must be TRUE):

1. EXPLAIN shows statistics are used — optimizer accesses column stats
2. Query plans differ with vs without statistics — cardinality estimates improve
3. Partition statistics used for partition pruning — unnecessary partitions skipped

**Plans**: TBD

## Progress

**Execution Order:** Phases execute in numeric order: 1 → 2

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Implementation | 3/3 | Complete | 2026-03-20 |
| 2. Optimizer Integration | 0/0 | Not started | - |