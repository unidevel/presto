# Iceberg Metrics Implementation Summary

## Overview
Successfully implemented tracking for two Iceberg-specific metrics in Velox:
- `numIcebergSplits`: Number of Iceberg file splits processed per task
- `numIcebergDeleteRecords`: Number of delete records processed during a scan

## Implementation Details

### 1. Metric Definitions (TaskStats.h)
**Location**: Lines 130-133
- Added `numIcebergSplits` (line 130)
- Added `numIcebergDeleteRecords` (line 133)
- Both metrics are already included in the `TaskStats` structure

### 2. Runtime Statistics Infrastructure (Statistics.h)
**Location**: Lines 567-609
- Added `icebergDeleteRecords` field to `RuntimeStatistics` struct (line 569)
- Added `icebergSplits` field to `RuntimeStatistics` struct (line 570)
- Updated `toRuntimeMetricMap()` to include both metrics (lines 603-609)
- Follows the existing pattern for runtime statistics

### 3. Split Reader Member Variables (IcebergSplitReader.h)
**Location**: Lines 48-49
- Added `numDeleteRecords_` counter to track delete records per split
- Added `runtimeStats_` pointer to access RuntimeStatistics from HiveDataSource

### 4. Split Counting (IcebergSplitReader.cpp)
**Location**: Lines 76-83
- In `prepareSplit()`, sets `runtimeStats_->icebergSplits = 1` when a split is prepared
- Tracks that this split reader is processing an Iceberg split
- Uses RuntimeStatistics mechanism to propagate the count

### 5. Delete Record Counting (IcebergSplitReader.cpp)
**Location**: Lines 143-154
- In the `next()` method, counts set bits in the delete bitmap
- Uses `bits::countBits()` to efficiently count deleted rows
- Accumulates count in `numDeleteRecords_` and updates `runtimeStats_->icebergDeleteRecords`
- Properly handles the case when runtimeStats_ is available

### 6. Metric Aggregation (Task.cpp)
**Location**: Lines 2639-2649
- In `addOperatorStats()`, extracts both `icebergDeleteRecords` and `icebergSplits` from runtime stats
- Aggregates counts into `taskStats_.numIcebergDeleteRecords` and `taskStats_.numIcebergSplits`
- Follows the existing pattern for aggregating operator stats into task stats

## Data Flow

```
IcebergSplitReader::prepareSplit()
  └─> Sets runtimeStats_->icebergSplits = 1

IcebergSplitReader::next()
  └─> Counts delete records in bitmap using bits::countBits()
  └─> Accumulates in numDeleteRecords_
  └─> Updates runtimeStats_->icebergDeleteRecords

HiveDataSource
  └─> Provides RuntimeStatistics to split reader
  └─> Returns runtime stats via getRuntimeStats()

TableScan::getSplit()
  └─> Collects runtime stats from data source
  └─> Merges into operator stats

Driver::closeOperators()
  └─> Collects operator stats
  └─> Calls task()->addOperatorStats()

Task::addOperatorStats()
  └─> Extracts icebergDeleteRecords from runtime stats
  └─> Extracts icebergSplits from runtime stats
  └─> Aggregates into taskStats_.numIcebergDeleteRecords
  └─> Aggregates into taskStats_.numIcebergSplits
```

## Key Design Decisions

1. **Avoided Circular Dependencies**: Did not include Iceberg-specific headers in the exec layer (Task.cpp)
   - Removed the problematic `#include "velox/connectors/hive/iceberg/IcebergSplit.h"`
   - Removed dynamic_pointer_cast approach that required connector-specific types

2. **Split Counting via RuntimeStatistics**: Implemented split counting in IcebergSplitReader using the same RuntimeStatistics mechanism as delete records
   - Set `runtimeStats_->icebergSplits = 1` when preparing a split
   - Propagates through the same path as other runtime metrics

3. **Delete Record Counting**: Implemented in IcebergSplitReader using the existing RuntimeStatistics mechanism
   - Counts delete records efficiently using `bits::countBits()`
   - Accumulates across multiple `next()` calls for the same split

4. **Aggregation**: Leveraged the existing operator stats aggregation flow in Task::addOperatorStats()
   - Both metrics flow through RuntimeStatistics → OperatorStats → TaskStats
   - No special handling needed in the task layer

5. **Library Separation**: Maintained proper library boundaries
   - `velox_exec` library does not depend on `velox_hive_connector` library
   - All Iceberg-specific logic stays in the connector layer

## Testing Considerations

To verify the implementation:
1. Run queries on Iceberg tables with delete files
2. Check TaskStats to confirm `numIcebergSplits` is incremented correctly
3. Verify `numIcebergDeleteRecords` matches the number of deleted rows
4. Test with tables that have no deletes (should show 0 delete records)
5. Test with multiple splits to ensure proper aggregation
6. Verify metrics are correctly aggregated across multiple operators/drivers

## Files Modified

1. `velox/velox/exec/TaskStats.h` - Metric definitions (already present)
2. `velox/velox/dwio/common/Statistics.h` - Added icebergDeleteRecords and icebergSplits fields to RuntimeStatistics
3. `velox/velox/connectors/hive/iceberg/IcebergSplitReader.h` - Added member variables for tracking
4. `velox/velox/connectors/hive/iceberg/IcebergSplitReader.cpp` - Implemented split and delete record counting
5. `velox/velox/exec/Task.cpp` - Added aggregation logic for both metrics (removed circular dependency code)

## Completion Status

✅ All implementation tasks completed
✅ Follows existing Velox patterns and conventions
✅ Properly integrated with existing statistics infrastructure
✅ Avoided circular dependencies between libraries
✅ Both metrics use the same RuntimeStatistics propagation mechanism
✅ Ready for testing and code review