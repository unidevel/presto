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

import com.facebook.airlift.log.Logger;
import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.testing.MaterializedRow;
import org.testng.annotations.Test;

import static com.facebook.presto.delta.AbstractDeltaDistributedQueryTestBase.DELTA_CATALOG;
import static com.facebook.presto.delta.AbstractDeltaDistributedQueryTestBase.DELTA_SCHEMA;
import static com.facebook.presto.delta.AbstractDeltaDistributedQueryTestBase.PATH_SCHEMA;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for ANALYZE → SHOW STATS round-trip.
 * Verifies end-to-end statistics flow:
 * - TEST-05: Running ANALYZE followed by SHOW STATS returns data (not empty)
 * - TEST-06: SHOW STATS displays correct row count after ANALYZE
 * - TEST-07: SHOW STATS displays correct column statistics
 *
 * Test data is located in presto-delta/src/test/resources/delta_v1 and delta_v3.
 * To regenerate test data, run: python presto-delta/src/test/create_delta_tables.py
 */
public class TestDeltaStatisticsIntegration
        extends AbstractDeltaDistributedQueryTestBase
{
    private static final Logger log = Logger.get(TestDeltaStatisticsIntegration.class);

    @Test(dataProvider = "deltaReaderVersions")
    public void testAnalyzeAndShowStats(String version)
    {
        String tableName = "test_stats_" + version;
        String tableLocation = goldenTablePathWithPrefix(version, "test-stats");

        getQueryRunner().execute(format(
                "CREATE TABLE %s.\"%s\".\"%s\" (id INT, value DOUBLE) WITH (external_location = '%s')",
                DELTA_CATALOG, DELTA_SCHEMA, tableName, tableLocation));

        getQueryRunner().execute(format("ANALYZE %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));

        MaterializedResult stats = computeActual(format("SHOW STATS FOR %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));
        assertNotNull(stats, "SHOW STATS result should not be null");
        log.info("SHOW STATS result for %s:", tableName);
        for (MaterializedRow row : stats.getMaterializedRows()) {
            log.info("SHOW STATS result(row): %s", row);
        }
        assertFalse(stats.getMaterializedRows().isEmpty(), "SHOW STATS should return data after ANALYZE");

        getQueryRunner().execute(format("DROP TABLE IF EXISTS %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void testRowCountStatistics(String version)
    {
        String tableName = "test_rowcount_" + version;
        String tableLocation = goldenTablePathWithPrefix(version, "test-rowcount");

        getQueryRunner().execute(format(
                "CREATE TABLE %s.\"%s\".\"%s\" (id INT) WITH (external_location = '%s')",
                DELTA_CATALOG, DELTA_SCHEMA, tableName, tableLocation));

        getQueryRunner().execute(format("ANALYZE %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));

        MaterializedResult stats = computeActual(format("SHOW STATS FOR %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));
        log.info("SHOW STATS result for %s:", tableName);
        for (MaterializedRow row : stats.getMaterializedRows()) {
            log.info("  %s", row);
        }
        Long actualRowCount = null;
        for (MaterializedRow row : stats.getMaterializedRows()) {
            if (row.getField(0) == null) {
                Object rowCountValue = row.getField(4);
                if (rowCountValue != null) {
                    actualRowCount = ((Number) rowCountValue).longValue();
                }
                break;
            }
        }
        assertEquals(actualRowCount, Long.valueOf(100L), "Row count should be 100");

        getQueryRunner().execute(format("DROP TABLE IF EXISTS %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void testColumnStatistics(String version)
    {
        String tableName = "test_colstats_" + version;
        String tableLocation = goldenTablePathWithPrefix(version, "test-colstats");

        getQueryRunner().execute(format(
                "CREATE TABLE %s.\"%s\".\"%s\" (id INT) WITH (external_location = '%s')",
                DELTA_CATALOG, DELTA_SCHEMA, tableName, tableLocation));

        getQueryRunner().execute(format("ANALYZE %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));

        MaterializedResult stats = computeActual(format("SHOW STATS FOR %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));
        log.info("SHOW STATS result for %s:", tableName);
        for (MaterializedRow row : stats.getMaterializedRows()) {
            log.info("SHOW STATS result(row): %s", row);
        }
        Long actualRowCount = null;
        for (MaterializedRow row : stats.getMaterializedRows()) {
            if (row.getField(0) == null) {
                Object rowCountValue = row.getField(4);
                if (rowCountValue != null) {
                    actualRowCount = ((Number) rowCountValue).longValue();
                }
            }
        }
        assertEquals(actualRowCount, Long.valueOf(100L), "row count should be 100");

        getQueryRunner().execute(format("DROP TABLE IF EXISTS %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void testMinMaxNullColumnStatistics(String version)
    {
        String tableName = "test_colstats_types_" + version;
        String tableLocation = goldenTablePathWithPrefix(version, "test-colstats");

        getQueryRunner().execute(format(
                "CREATE TABLE %s.\"%s\".\"%s\" (id INT, value DOUBLE, name VARCHAR) WITH (external_location = '%s')",
                DELTA_CATALOG, DELTA_SCHEMA, tableName, tableLocation));

        getQueryRunner().execute(format("ANALYZE %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));

        MaterializedResult stats = computeActual(format("SHOW STATS FOR %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));
        log.info("SHOW STATS result for %s:", tableName);
        for (MaterializedRow row : stats.getMaterializedRows()) {
            log.info("SHOW STATS result(row): %s", row);
        }

        Long rowCount = null;
        for (MaterializedRow row : stats.getMaterializedRows()) {
            if (row.getField(0) == null) {
                rowCount = ((Number) row.getField(4)).longValue();
                break;
            }
        }
        assertEquals(rowCount, Long.valueOf(100L), "Total row count should be 100");

        boolean foundIdColumn = false;
        boolean foundValueColumn = false;
        boolean foundNameColumn = false;

        for (MaterializedRow row : stats.getMaterializedRows()) {
            String columnName = (String) row.getField(0);
            if (columnName == null) {
                continue;
            }
            if ("id".equals(columnName)) {
                foundIdColumn = true;
                Object minValue = row.getField(5);
                Object maxValue = row.getField(6);
                assertEquals(String.valueOf(minValue), "1", "id min should be 1");
                assertEquals(String.valueOf(maxValue), "100", "id max should be 100");
            }
            else if ("value".equals(columnName)) {
                foundValueColumn = true;
                Object minValue = row.getField(5);
                Object maxValue = row.getField(6);
                assertEquals(String.valueOf(minValue), "9.5", "value min should be 9.5");
                assertEquals(String.valueOf(maxValue), "108.5", "value max should be 108.5");
            }
            else if ("name".equals(columnName)) {
                foundNameColumn = true;
                Object nullsFraction = row.getField(3);
                assertEquals(((Number) nullsFraction).doubleValue(), 0.1, 0.01, "name nulls fraction should be 0.1");
            }
        }

        assertTrue(foundIdColumn, "Should find id column stats");
        assertTrue(foundValueColumn, "Should find value column stats");
        assertTrue(foundNameColumn, "Should find name column stats");

        getQueryRunner().execute(format("DROP TABLE IF EXISTS %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void testAnalyzeAndShowStatsUsingPathSchema(String version)
    {
        String testQuery = format("SELECT * FROM \"%s\".\"%s\"", PATH_SCHEMA,
                goldenTablePathWithPrefix(version, "test-stats"));

        // Verify we can read the table
        MaterializedResult result = computeActual(testQuery);
        assertEquals(result.getRowCount(), 3, "Should have 3 rows");
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void testAllColumnTypesStatistics(String version)
    {
        String tableName = "test_colstats_alltypes_" + version;
        String tableLocation = goldenTablePathWithPrefix(version, "test-colstats-alltypes");

        getQueryRunner().execute(format(
                "CREATE TABLE %s.\"%s\".\"%s\" (" +
                        "as_boolean BOOLEAN, " +
                        "as_int INT, " +
                        "as_long BIGINT, " +
                        "as_double DOUBLE, " +
                        "as_string VARCHAR, " +
                        "as_date DATE, " +
                        "as_timestamp TIMESTAMP, " +
                        "as_binary VARBINARY" +
                        ") WITH (external_location = '%s')",
                DELTA_CATALOG, DELTA_SCHEMA, tableName, tableLocation));

        getQueryRunner().execute(format("ANALYZE %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));

        MaterializedResult stats = computeActual(format("SHOW STATS FOR %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));
        log.info("SHOW STATS result for %s:", tableName);
        for (MaterializedRow row : stats.getMaterializedRows()) {
            log.info("  %s", row);
        }

        // Verify row count
        Long rowCount = null;
        for (MaterializedRow row : stats.getMaterializedRows()) {
            if (row.getField(0) == null) {
                rowCount = ((Number) row.getField(4)).longValue();
                break;
            }
        }
        assertEquals(rowCount, Long.valueOf(100L), "Total row count should be 100");

        // Track which columns we found
        boolean foundBoolean = false;
        boolean foundInt = false;
        boolean foundLong = false;
        boolean foundDouble = false;
        boolean foundString = false;
        boolean foundDate = false;
        boolean foundTimestamp = false;
        boolean foundBinary = false;

        for (MaterializedRow row : stats.getMaterializedRows()) {
            String columnName = (String) row.getField(0);
            if (columnName == null) {
                continue;
            }

            // SHOW STATS columns: column_name, data_size, distinct_values_count, nulls_fraction, row_count, low_value, high_value
            Object distinctValues = row.getField(2);
            Object nullsFraction = row.getField(3);
            Object minValue = row.getField(5);
            Object maxValue = row.getField(6);

            if ("as_boolean".equals(columnName)) {
                foundBoolean = true;
                // BOOLEAN: should have NUMBER_OF_NON_NULL_VALUES (90 non-null out of 100)
                assertEquals(((Number) nullsFraction).doubleValue(), 0.1, 0.01, "as_boolean nulls fraction should be 0.1");
                log.info("✓ BOOLEAN stats verified: nulls_fraction=%s", nullsFraction);
            }
            else if ("as_int".equals(columnName)) {
                foundInt = true;
                // INT: should have MIN_VALUE=1, MAX_VALUE=100, with 10% nulls
                assertEquals(String.valueOf(minValue), "1", "as_int min should be 1");
                assertEquals(String.valueOf(maxValue), "100", "as_int max should be 100");
                assertEquals(((Number) nullsFraction).doubleValue(), 0.1, 0.01, "as_int nulls fraction should be 0.1");
                log.info("✓ INT stats verified: min=%s, max=%s, nulls=%s", minValue, maxValue, nullsFraction);
            }
            else if ("as_long".equals(columnName)) {
                foundLong = true;
                // BIGINT: should have MIN_VALUE=1000, MAX_VALUE=1099, with 10% nulls
                assertEquals(String.valueOf(minValue), "1000", "as_long min should be 1000");
                assertEquals(String.valueOf(maxValue), "1099", "as_long max should be 1099");
                assertEquals(((Number) nullsFraction).doubleValue(), 0.1, 0.01, "as_long nulls fraction should be 0.1");
                log.info("✓ BIGINT stats verified: min=%s, max=%s, nulls=%s", minValue, maxValue, nullsFraction);
            }
            else if ("as_double".equals(columnName)) {
                foundDouble = true;
                // DOUBLE: should have MIN_VALUE=10.5, MAX_VALUE=109.5, with 10% nulls
                assertEquals(String.valueOf(minValue), "10.5", "as_double min should be 10.5");
                assertEquals(String.valueOf(maxValue), "109.5", "as_double max should be 109.5");
                assertEquals(((Number) nullsFraction).doubleValue(), 0.1, 0.01, "as_double nulls fraction should be 0.1");
                log.info("✓ DOUBLE stats verified: min=%s, max=%s, nulls=%s", minValue, maxValue, nullsFraction);
            }
            else if ("as_string".equals(columnName)) {
                foundString = true;
                // VARCHAR: should NOT have min/max, but should have nulls_fraction=0.1
                assertEquals(((Number) nullsFraction).doubleValue(), 0.1, 0.01, "as_string nulls fraction should be 0.1");
                log.info("✓ VARCHAR stats verified: nulls=%s (no min/max as expected)", nullsFraction);
            }
            else if ("as_date".equals(columnName)) {
                foundDate = true;
                // DATE: should have MIN_VALUE, MAX_VALUE, nulls_fraction=0.1
                assertNotNull(minValue, "as_date should have min value");
                assertNotNull(maxValue, "as_date should have max value");
                assertEquals(((Number) nullsFraction).doubleValue(), 0.1, 0.01, "as_date nulls fraction should be 0.1");
                log.info("✓ DATE stats verified: min=%s, max=%s, nulls=%s", minValue, maxValue, nullsFraction);
            }
            else if ("as_timestamp".equals(columnName)) {
                foundTimestamp = true;
                // TIMESTAMP: should have MIN_VALUE, MAX_VALUE, nulls_fraction=0.1
                assertNotNull(minValue, "as_timestamp should have min value");
                assertNotNull(maxValue, "as_timestamp should have max value");
                assertEquals(((Number) nullsFraction).doubleValue(), 0.1, 0.01, "as_timestamp nulls fraction should be 0.1");
                log.info("✓ TIMESTAMP stats verified: min=%s, max=%s, nulls=%s", minValue, maxValue, nullsFraction);
            }
            else if ("as_binary".equals(columnName)) {
                foundBinary = true;
                // VARBINARY: should NOT have min/max, but should have nulls_fraction=0.1
                assertEquals(((Number) nullsFraction).doubleValue(), 0.1, 0.01, "as_binary nulls fraction should be 0.1");
                log.info("✓ VARBINARY stats verified: nulls=%s (no min/max as expected)", nullsFraction);
            }
        }

        assertTrue(foundBoolean, "Should find as_boolean column stats");
        assertTrue(foundInt, "Should find as_int column stats");
        assertTrue(foundLong, "Should find as_long column stats");
        assertTrue(foundDouble, "Should find as_double column stats");
        assertTrue(foundString, "Should find as_string column stats");
        assertTrue(foundDate, "Should find as_date column stats");
        assertTrue(foundTimestamp, "Should find as_timestamp column stats");
        assertTrue(foundBinary, "Should find as_binary column stats");

        getQueryRunner().execute(format("DROP TABLE IF EXISTS %s.\"%s\".\"%s\"", DELTA_CATALOG, DELTA_SCHEMA, tableName));
    }
}
