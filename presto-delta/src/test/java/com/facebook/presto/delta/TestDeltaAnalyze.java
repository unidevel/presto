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
import com.facebook.presto.spi.session.PropertyMetadata;
import org.testng.annotations.Test;

import java.util.List;

import static com.facebook.presto.metadata.FunctionAndTypeManager.createTestFunctionAndTypeManager;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for ANALYZE functionality in Delta connector.
 * Focuses on DeltaAnalyzeProperties since getStatisticsCollectionMetadata
 * requires integration testing with actual Delta tables.
 */
public class TestDeltaAnalyze
{
    private final TypeManager typeManager = createTestFunctionAndTypeManager();

    @Test
    public void testAnalyzePropertiesAreDefined()
    {
        DeltaAnalyzeProperties analyzeProperties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> properties = analyzeProperties.getProperties();

        assertNotNull(properties, "Analyze properties should not be null");
        assertFalse(properties.isEmpty(), "At least one analyze property should be defined");
    }

    @Test
    public void testPartitionsPropertyExists()
    {
        DeltaAnalyzeProperties analyzeProperties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> properties = analyzeProperties.getProperties();

        boolean hasPartitionsProperty = properties.stream()
                .anyMatch(p -> "partitions".equals(p.getName()));
        assertTrue(hasPartitionsProperty, "Should have 'partitions' property defined");
    }

    @Test
    public void testPartitionsPropertyType()
    {
        DeltaAnalyzeProperties analyzeProperties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> properties = analyzeProperties.getProperties();

        PropertyMetadata<?> partitionsProperty = properties.stream()
                .filter(p -> "partitions".equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("partitions property not found"));

        assertNotNull(partitionsProperty.getSqlType(), "Property type should not be null");
        assertEquals(partitionsProperty.getSqlType().getTypeSignature().toString(), "array(array(varchar))", "Partitions should be array(array(varchar))");
    }

    @Test
    public void testPartitionsPropertyDescription()
    {
        DeltaAnalyzeProperties analyzeProperties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> properties = analyzeProperties.getProperties();

        PropertyMetadata<?> partitionsProperty = properties.stream()
                .filter(p -> "partitions".equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("partitions property not found"));

        assertNotNull(partitionsProperty.getDescription(), "Property description should not be null");
        assertEquals(partitionsProperty.getDescription(), "Partitions to be analyzed");
    }

    @Test
    public void testPartitionListExtractionWithValues()
    {
        java.util.Map<String, Object> properties = new java.util.HashMap<>();
        properties.put("partitions", java.util.Collections.singletonList(
                java.util.Collections.singletonList("2024-01")));

        java.util.Optional<java.util.List<java.util.List<String>>> result =
                DeltaAnalyzeProperties.getPartitionList(properties);

        assertTrue(result.isPresent(), "Should return partition list when provided");
        assertEquals(result.get().size(), 1, "Should have one partition");
        assertEquals(result.get().get(0).get(0), "2024-01", "Should extract partition value");
    }

    @Test
    public void testPartitionListExtractionEmpty()
    {
        java.util.Map<String, Object> properties = new java.util.HashMap<>();

        java.util.Optional<java.util.List<java.util.List<String>>> result =
                DeltaAnalyzeProperties.getPartitionList(properties);

        assertFalse(result.isPresent(), "Should return empty when no partitions property");
    }
}
