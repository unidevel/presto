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

import com.facebook.presto.common.type.TypeManager;
import com.facebook.presto.spi.ColumnMetadata;
import com.facebook.presto.spi.ConnectorTableMetadata;
import com.facebook.presto.spi.statistics.TableStatisticsMetadata;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.List;

import static com.facebook.presto.common.type.TypeSignature.parseTypeSignature;
import static com.facebook.presto.metadata.FunctionAndTypeManager.createTestFunctionAndTypeManager;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for ANALYZE functionality in Delta connector.
 * Tests the metadata-level statistics collection support.
 */
public class TestDeltaAnalyze
{
    private final TypeManager typeManager = createTestFunctionAndTypeManager();

    @Test
    public void testGetStatisticsCollectionMetadataNonPartitioned()
    {
        DeltaMetadata metadata = createDeltaMetadata();

        List<ColumnMetadata> columns = ImmutableList.of(
                ColumnMetadata.builder().setName("id").setType(typeManager.getType(parseTypeSignature("bigint"))).build(),
                ColumnMetadata.builder().setName("name").setType(typeManager.getType(parseTypeSignature("varchar"))).build(),
                ColumnMetadata.builder().setName("value").setType(typeManager.getType(parseTypeSignature("double"))).build());

        ConnectorTableMetadata tableMetadata = new ConnectorTableMetadata(
                new com.facebook.presto.spi.SchemaTableName("test", "non_partitioned_table"),
                columns,
                ImmutableMap.of());

        TableStatisticsMetadata statsMetadata = metadata.getStatisticsCollectionMetadata(null, tableMetadata);

        assertNotNull(statsMetadata, "Statistics metadata should not be null");
        assertFalse(statsMetadata.getColumnStatistics().isEmpty(), "Should have column statistics");
        assertTrue(statsMetadata.getTableStatistics().isEmpty(), "Non-partitioned tables should not have table statistics");
    }

    @Test
    public void testGetStatisticsCollectionMetadataPartitioned()
    {
        DeltaMetadata metadata = createDeltaMetadata();

        List<ColumnMetadata> columns = ImmutableList.of(
                ColumnMetadata.builder().setName("id").setType(typeManager.getType(parseTypeSignature("bigint"))).build(),
                ColumnMetadata.builder().setName("name").setType(typeManager.getType(parseTypeSignature("varchar"))).build(),
                ColumnMetadata.builder().setName("country").setType(typeManager.getType(parseTypeSignature("varchar"))).build());

        ConnectorTableMetadata tableMetadata = new ConnectorTableMetadata(
                new com.facebook.presto.spi.SchemaTableName("test", "partitioned_table"),
                columns,
                ImmutableMap.of("partitioned_by", "['country']"));

        TableStatisticsMetadata statsMetadata = metadata.getStatisticsCollectionMetadata(null, tableMetadata);

        assertNotNull(statsMetadata, "Statistics metadata should not be null");
        assertFalse(statsMetadata.getColumnStatistics().isEmpty(), "Should have column statistics for non-partitioned columns");
        assertEquals(statsMetadata.getGroupingColumns(), ImmutableList.of("country"), "Partition columns should be tracked");
    }

    @Test
    public void testGetTableHandleForStatisticsCollection()
    {
        DeltaMetadata metadata = createDeltaMetadata();
        assertNotNull(metadata, "DeltaMetadata should be created");
    }

    @Test
    public void testAnalyzePropertiesAreDefined()
    {
        DeltaAnalyzeProperties analyzeProperties = new DeltaAnalyzeProperties(typeManager);
        assertNotNull(analyzeProperties.getProperties(), "Analyze properties should be defined");
        assertFalse(analyzeProperties.getProperties().isEmpty(), "At least one analyze property should be defined");

        boolean hasPartitionsProperty = analyzeProperties.getProperties().stream()
                .anyMatch(p -> "partitions".equals(p.getName()));
        assertTrue(hasPartitionsProperty, "Should have 'partitions' property defined");
    }

    private DeltaMetadata createDeltaMetadata()
    {
        return new DeltaMetadata(
                new DeltaConnectorId("test"),
                null,
                null,
                typeManager,
                new DeltaConfig());
    }
}
