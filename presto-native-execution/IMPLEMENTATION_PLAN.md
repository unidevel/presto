# Iceberg Metrics Implementation Plan

## Overview
Add two Iceberg-specific metrics to track:
1. `numIcebergSplits` - Number of Iceberg file splits processed per task
2. `numIcebergDeleteRecords` - Number of delete records processed during scan

## Metrics Location
The metrics are already added to `TaskStats.h` (lines 130-133):
```cpp
int32_t numIcebergSplits{0};
int64_t numIcebergDeleteRecords{0};
```

## Implementation Points

### 1. Track Iceberg Splits
**Location**: `velox/velox/exec/Task.cpp` - in `addSplitLocked()` method
- Detect if split is an Iceberg split (HiveIcebergSplit)
- Increment `taskStats_.numIcebergSplits`

### 2. Track Delete Records
**Location**: `velox/velox/connectors/hive/iceberg/PositionalDeleteFileReader.cpp`
- Track delete records as they are processed
- Pass count back through the split reader chain
- Accumulate in task stats

### 3. Report in Task Stats
**Location**: Already in `TaskStats.h` structure
- Metrics will be automatically included in task stats reporting

## Files to Modify
1. `velox/velox/exec/Task.cpp` - Add split counting logic
2. `velox/velox/connectors/hive/iceberg/IcebergSplitReader.cpp` - Track delete records
3. `velox/velox/connectors/hive/iceberg/IcebergSplitReader.h` - Add method to get delete count
4. `velox/velox/connectors/hive/SplitReader.h` - Add virtual method for stats
5. `velox/velox/connectors/hive/HiveDataSource.cpp` - Pass stats to task