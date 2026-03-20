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
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.session.PropertyMetadata;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.facebook.presto.delta.DeltaAnalyzeProperties.PARTITIONS_PROPERTY;
import static com.facebook.presto.delta.DeltaAnalyzeProperties.getPartitionList;
import static com.facebook.presto.metadata.FunctionAndTypeManager.createTestFunctionAndTypeManager;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for DeltaAnalyzeProperties class.
 * Tests the analyze properties configuration for Delta tables.
 */
public class TestDeltaAnalyzeProperties
{
    private final TypeManager typeManager = createTestFunctionAndTypeManager();

    @Test
    public void testGetPropertiesReturnsNonEmptyList()
    {
        DeltaAnalyzeProperties properties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> result = properties.getProperties();

        assertNotNull(result, "Properties should not be null");
        assertFalse(result.isEmpty(), "Properties should not be empty");
    }

    @Test
    public void testPartitionColumnsPropertyExists()
    {
        DeltaAnalyzeProperties properties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> result = properties.getProperties();

        boolean found = result.stream()
                .anyMatch(p -> PARTITIONS_PROPERTY.equals(p.getName()));

        assertTrue(found, "partition_columns property should exist");
    }

    @Test
    public void testPropertyTypes()
    {
        DeltaAnalyzeProperties properties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> result = properties.getProperties();

        // The partitions property should be of type array(array(varchar))
        PropertyMetadata<?> partitionsProperty = result.stream()
                .filter(p -> PARTITIONS_PROPERTY.equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("partitions property not found"));

        assertNotNull(partitionsProperty.getSqlType(), "Property type should not be null");
    }

    @Test
    public void testPropertyDefaults()
    {
        DeltaAnalyzeProperties properties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> result = properties.getProperties();

        PropertyMetadata<?> partitionsProperty = result.stream()
                .filter(p -> PARTITIONS_PROPERTY.equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("partitions property not found"));

        // Default value should be null (analyze all partitions)
        assertTrue(partitionsProperty.getDefaultValue() == null,
                "Default value for partitions should be null");
    }

    @Test
    public void testGetPartitionListWithNullProperties()
    {
        Map<String, Object> properties = new HashMap<>();
        Optional<List<List<String>>> result = getPartitionList(properties);

        assertFalse(result.isPresent(), "Should return empty optional when partitions property is null");
    }

    @Test
    public void testGetPartitionListWithEmptyMap()
    {
        Map<String, Object> properties = new ImmutableMap.Builder<String, Object>()
                .build();
        Optional<List<List<String>>> result = getPartitionList(properties);

        assertFalse(result.isPresent(), "Should return empty optional for empty map");
    }

    @Test
    public void testGetPartitionListWithValidPartitions()
    {
        List<List<String>> partitions = ImmutableList.of(
                ImmutableList.of("2023", "01"),
                ImmutableList.of("2023", "02"));
        Map<String, Object> properties = new ImmutableMap.Builder<String, Object>()
                .put(PARTITIONS_PROPERTY, partitions)
                .build();

        Optional<List<List<String>>> result = getPartitionList(properties);

        assertTrue(result.isPresent(), "Should return present optional when partitions are specified");
        assertEquals(result.get().size(), 2, "Should have 2 partition lists");
    }

    @Test
    public void testGetPartitionListPreservesDuplicates()
    {
        // getPartitionList returns the raw input - no deduplication
        List<List<String>> partitionsWithDuplicates = ImmutableList.of(
                ImmutableList.of("2023", "01"),
                ImmutableList.of("2023", "01"),
                ImmutableList.of("2023", "02"));
        Map<String, Object> properties = new ImmutableMap.Builder<String, Object>()
                .put(PARTITIONS_PROPERTY, partitionsWithDuplicates)
                .build();

        Optional<List<List<String>>> result = getPartitionList(properties);

        assertTrue(result.isPresent(), "Should return present optional");
        // Raw input preserved - no deduplication in getPartitionList
        assertEquals(result.get().size(), 3, "Duplicates are preserved in getPartitionList");
    }

    @Test
    public void testDecoderThrowsOnNullTopLevelElement()
    {
        // Test that decoder throws when given null element in the outer list
        DeltaAnalyzeProperties properties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> propertyMetadataList = properties.getProperties();

        PropertyMetadata<?> partitionsProperty = propertyMetadataList.stream()
                .filter(p -> PARTITIONS_PROPERTY.equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("partitions property not found"));

        // Create a list with null top-level element (not null inside nested list)
        List<List<String>> partitionsWithNullElement = new ArrayList<>();
        partitionsWithNullElement.add(null);  // This should throw

        // Get the decoder function
        @SuppressWarnings("unchecked")
        java.util.function.Function<Object, List<List<String>>> decoder =
                (java.util.function.Function<Object, List<List<String>>>) partitionsProperty.getDecoder();

        // Decoder should throw on null top-level element
        expectThrows(PrestoException.class, () -> decoder.apply(partitionsWithNullElement));
    }

    @Test
    public void testDecoderDeduplicatesPartitions()
    {
        // Test that decoder deduplicates partitions
        DeltaAnalyzeProperties properties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> propertyMetadataList = properties.getProperties();

        PropertyMetadata<?> partitionsProperty = propertyMetadataList.stream()
                .filter(p -> PARTITIONS_PROPERTY.equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("partitions property not found"));

        // Create list with duplicate
        List<List<String>> partitionsWithDuplicates = ImmutableList.of(
                ImmutableList.of("2023", "01"),
                ImmutableList.of("2023", "01"),
                ImmutableList.of("2023", "02"));

        // Get the decoder function
        @SuppressWarnings("unchecked")
        java.util.function.Function<Object, List<List<String>>> decoder =
                (java.util.function.Function<Object, List<List<String>>>) partitionsProperty.getDecoder();

        // Decoder should deduplicate
        List<List<String>> result = decoder.apply(partitionsWithDuplicates);
        assertEquals(result.size(), 2, "Duplicate partitions should be removed by decoder");
    }

    @Test
    public void testDecoderReplacesNullWithDefaultPartition()
    {
        // Test that decoder replaces null partition values with default
        DeltaAnalyzeProperties properties = new DeltaAnalyzeProperties(typeManager);
        List<PropertyMetadata<?>> propertyMetadataList = properties.getProperties();

        PropertyMetadata<?> partitionsProperty = propertyMetadataList.stream()
                .filter(p -> PARTITIONS_PROPERTY.equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("partitions property not found"));

        // Create list with null value
        List<String> partitionWithNull = new ArrayList<>();
        partitionWithNull.add("2023");
        partitionWithNull.add(null);
        List<List<String>> partitionsWithNull = ImmutableList.of(partitionWithNull);

        // Get the decoder function
        @SuppressWarnings("unchecked")
        java.util.function.Function<Object, List<List<String>>> decoder =
                (java.util.function.Function<Object, List<List<String>>>) partitionsProperty.getDecoder();

        // Decoder should replace null with default
        List<List<String>> result = decoder.apply(partitionsWithNull);
        assertTrue(result.get(0).get(1).contains("__HIVE_DEFAULT_PARTITION__"),
                "Null partition value should be replaced with default");
    }
}
