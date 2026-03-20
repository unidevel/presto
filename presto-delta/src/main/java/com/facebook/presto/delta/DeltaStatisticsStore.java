/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.delta;

import com.facebook.presto.hive.metastore.PartitionStatistics;
import com.facebook.presto.spi.SchemaTableName;
import com.google.common.collect.ImmutableMap;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.facebook.presto.hive.HiveBasicStatistics.createEmptyStatistics;
import static java.util.Objects.requireNonNull;

/**
 * In-memory storage for Delta table statistics.
 * Delta tables store their schema in Delta Lake metadata (transaction log), not in HMS,
 * so statistics are managed separately in memory rather than in the Hive Metastore.
 */
public class DeltaStatisticsStore
{
    private final Map<SchemaTableName, PartitionStatistics> tableStatistics = new HashMap<>();

    private final Map<PartitionKey, PartitionStatistics> partitionStatistics = new HashMap<>();

    @Inject
    public DeltaStatisticsStore()
    {
    }

    /**
     * Get statistics for a table.
     */
    public synchronized PartitionStatistics getTableStatistics(String databaseName, String tableName)
    {
        SchemaTableName schemaTableName = new SchemaTableName(databaseName, tableName);
        PartitionStatistics statistics = tableStatistics.get(schemaTableName);
        if (statistics == null) {
            statistics = new PartitionStatistics(createEmptyStatistics(), ImmutableMap.of());
        }
        return statistics;
    }

    /**
     * Update statistics for a table using a function.
     */
    public synchronized void updateTableStatistics(
            String databaseName,
            String tableName,
            Function<PartitionStatistics, PartitionStatistics> update)
    {
        SchemaTableName schemaTableName = new SchemaTableName(databaseName, tableName);
        PartitionStatistics currentStats = getTableStatistics(databaseName, tableName);
        PartitionStatistics newStats = update.apply(currentStats);
        tableStatistics.put(schemaTableName, newStats);
    }

    /**
     * Get statistics for a partition.
     */
    public synchronized PartitionStatistics getPartitionStatistics(
            String databaseName,
            String tableName,
            String partitionName)
    {
        PartitionKey key = new PartitionKey(databaseName, tableName, partitionName);
        PartitionStatistics statistics = partitionStatistics.get(key);
        if (statistics == null) {
            statistics = new PartitionStatistics(createEmptyStatistics(), ImmutableMap.of());
        }
        return statistics;
    }

    /**
     * Update statistics for a partition using a function.
     */
    public synchronized void updatePartitionStatistics(
            String databaseName,
            String tableName,
            String partitionName,
            Function<PartitionStatistics, PartitionStatistics> update)
    {
        PartitionKey key = new PartitionKey(databaseName, tableName, partitionName);
        PartitionStatistics currentStats = getPartitionStatistics(databaseName, tableName, partitionName);
        PartitionStatistics newStats = update.apply(currentStats);
        partitionStatistics.put(key, newStats);
    }

    /**
     * Clear all statistics (useful for testing).
     */
    public synchronized void clear()
    {
        tableStatistics.clear();
        partitionStatistics.clear();
    }

    /**
     * Key for partition statistics map.
     */
    private static class PartitionKey
    {
        private final String databaseName;
        private final String tableName;
        private final String partitionName;

        public PartitionKey(String databaseName, String tableName, String partitionName)
        {
            this.databaseName = requireNonNull(databaseName, "databaseName is null");
            this.tableName = requireNonNull(tableName, "tableName is null");
            this.partitionName = requireNonNull(partitionName, "partitionName is null");
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PartitionKey that = (PartitionKey) o;
            return Objects.equals(databaseName, that.databaseName) &&
                    Objects.equals(tableName, that.tableName) &&
                    Objects.equals(partitionName, that.partitionName);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(databaseName, tableName, partitionName);
        }
    }
}
