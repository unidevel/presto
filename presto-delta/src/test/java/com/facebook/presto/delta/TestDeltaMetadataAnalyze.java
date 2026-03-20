/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.delta;

import com.facebook.presto.common.type.TypeManager;
import com.facebook.presto.metadata.FunctionAndTypeManager;
import com.facebook.presto.spi.SchemaTableName;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.facebook.presto.delta.DeltaAnalyzeProperties.PARTITIONS_PROPERTY;
import static com.facebook.presto.metadata.FunctionAndTypeManager.createTestFunctionAndTypeManager;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for DeltaMetadata analyze-related behavior.
 * Tests partition detection, table handle creation, and related functionality.
 */
public class TestDeltaMetadataAnalyze
{
    private final TypeManager typeManager = createTestFunctionAndTypeManager();

    @Test
    public void testGetPartitionListForPartitionedTable()
    {
        // Test extraction of partition list for partitioned table
        List<List<String>> partitions = ImmutableList.of(
                ImmutableList.of("2023", "01"),
                ImmutableList.of("2023", "02"));
        Map<String, Object> properties = new ImmutableMap.Builder<String, Object>()
                .put(PARTITIONS_PROPERTY, partitions)
                .build();

        Optional<List<List<String>>> result = DeltaAnalyzeProperties.getPartitionList(properties);

        assertTrue(result.isPresent(), "Should return present optional");
        assertEquals(result.get().size(), 2, "Should have 2 partition lists");
    }

    @Test
    public void testGetPartitionListForEmptyProperties()
    {
        // Test that empty properties returns empty optional
        Map<String, Object> properties = new HashMap<>();
        
        Optional<List<List<String>>> result = DeltaAnalyzeProperties.getPartitionList(properties);

        assertFalse(result.isPresent(), "Should return empty optional for empty properties");
    }

    @Test
    public void testDeltaTableHandleStoresSnapshotId()
    {
        // Test that DeltaTableHandle correctly stores snapshot information
        Optional<Long> snapshotId = Optional.of(12345L);
        
        DeltaTable deltaTable = new DeltaTable(
                "schema",
                "table",
                "s3://bucket/table",
                snapshotId,
                ImmutableList.of(
                        new DeltaColumn("id", com.facebook.presto.common.type.TypeSignature.parseTypeSignature("bigint"), false, false)));

        DeltaTableHandle handle = new DeltaTableHandle("delta", deltaTable);

        assertEquals(handle.getDeltaTable().getSnapshotId(), snapshotId,
                "DeltaTableHandle should preserve snapshot ID");
    }

    @Test
    public void testDeltaTableHandleStoresSchemaTableName()
    {
        // Test that DeltaTableHandle correctly stores schema/table information
        DeltaTable deltaTable = new DeltaTable(
                "test_schema",
                "test_table",
                "s3://bucket/table",
                Optional.of(1L),
                ImmutableList.of(
                        new DeltaColumn("id", com.facebook.presto.common.type.TypeSignature.parseTypeSignature("bigint"), false, false)));

        DeltaTableHandle handle = new DeltaTableHandle("delta", deltaTable);

        SchemaTableName schemaTableName = handle.toSchemaTableName();
        assertEquals(schemaTableName.getSchemaName(), "test_schema",
                "Schema name should be preserved");
        assertEquals(schemaTableName.getTableName(), "test_table",
                "Table name should be preserved");
    }

    @Test
    public void testDeltaTableWithMultipleSnapshots()
    {
        // Test DeltaTable handling of multiple snapshots
        List<DeltaColumn> columns = ImmutableList.of(
                new DeltaColumn("id", com.facebook.presto.common.type.TypeSignature.parseTypeSignature("bigint"), false, false));

        // Test with no snapshot
        DeltaTable tableNoSnapshot = new DeltaTable(
                "schema", "table", "s3://bucket/table", Optional.empty(), columns);
        assertFalse(tableNoSnapshot.getSnapshotId().isPresent(), "Should have no snapshot");

        // Test with specific snapshot
        DeltaTable tableWithSnapshot = new DeltaTable(
                "schema", "table", "s3://bucket/table", Optional.of(100L), columns);
        assertTrue(tableWithSnapshot.getSnapshotId().isPresent(), "Should have snapshot");
        assertEquals(tableWithSnapshot.getSnapshotId().get(), Long.valueOf(100L), "Snapshot ID should match");
    }

    @Test
    public void testAnalyzePropertiesExtraction()
    {
        // Test comprehensive analyze properties extraction
        List<List<String>> partitions = ImmutableList.of(
                ImmutableList.of("2024", "01", "01"),
                ImmutableList.of("2024", "01", "02"),
                ImmutableList.of("2024", "01", "03"));

        Map<String, Object> analyzeProperties = new HashMap<>();
        analyzeProperties.put(PARTITIONS_PROPERTY, partitions);

        Optional<List<List<String>>> extractedPartitions = DeltaAnalyzeProperties.getPartitionList(analyzeProperties);

        assertTrue(extractedPartitions.isPresent(), "Should extract partition list");
        assertEquals(extractedPartitions.get().size(), 3, "Should have 3 partitions");
        
        // Verify first partition values
        assertEquals(extractedPartitions.get().get(0).get(0), "2024", "First partition year should match");
        assertEquals(extractedPartitions.get().get(0).get(1), "01", "First partition month should match");
        assertEquals(extractedPartitions.get().get(0).get(2), "01", "First partition day should match");
    }
}