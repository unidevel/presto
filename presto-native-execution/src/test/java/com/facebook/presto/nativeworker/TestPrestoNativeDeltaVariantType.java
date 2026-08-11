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
package com.facebook.presto.nativeworker;

import com.facebook.airlift.log.Level;
import com.facebook.airlift.log.Logging;
import com.facebook.presto.Session;
import com.facebook.presto.delta.TestDeltaVariantType;
import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.testing.QueryRunner;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;

/**
 * Runs {@link TestDeltaVariantType} against a native worker, where the variant is decoded by
 * VariantColumnReader in Velox instead of by the Java Parquet reader.
 * <p>
 * The golden table holds five distinct users: alice, bob, charlie, diana and eve. The Java Parquet
 * reader returns rows 3 and 5 as copies of rows 2 and 4, so it sees bob and diana twice and never
 * sees charlie or eve. The inherited tests that count, group or join on the decoded values assert
 * that duplicated data, so they are overridden below with the contents of the file.
 */
public class TestPrestoNativeDeltaVariantType
        extends TestDeltaVariantType
{
    @Override
    protected String goldenTablePath(String tableName)
    {
        return extractedGoldenTablePath(tableName);
    }

    @BeforeClass
    public static void silenceDeltaLogging()
    {
        // Hide huge warning logs caused by not having checkpoints.
        Logging logging = Logging.initialize();
        logging.setLevel("io.delta.kernel", Level.ERROR);
    }

    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        // The variant tests read the golden table through the "$path$" schema, so the tables do not
        // need to be registered in HMS.
        return PrestoNativeQueryRunnerUtils.nativeDeltaQueryRunnerBuilder().build();
    }

    private MaterializedResult computeVariantQuery(String queryFormat)
    {
        Session session = Session.builder(getSession()).build();
        return computeActual(session, format(queryFormat, PATH_SCHEMA,
                goldenTablePathWithPrefix(DELTA_V3, "test_variant")));
    }

    /**
     * Covers every row of the table, which the inherited testVariantTypeSelectAll() does not do.
     */
    @Test
    public void testVariantTypeSelectAllRows()
    {
        MaterializedResult result =
                computeVariantQuery("SELECT * FROM \"%s\".\"%s\" ORDER BY id");
        assertEquals(result.getMaterializedRows().size(), 5);

        assertEquals(result.getMaterializedRows().get(0).getField(1),
                "{\"active\":true,\"age\":30,\"user\":\"alice\"}");
        assertEquals(result.getMaterializedRows().get(1).getField(1),
                "{\"age\":25,\"tags\":[\"admin\",\"dev\"],\"user\":\"bob\"}");
        assertEquals(result.getMaterializedRows().get(2).getField(1),
                "{\"address\":{\"city\":\"NYC\",\"zip\":\"10001\"},\"age\":35,\"user\":\"charlie\"}");
        assertEquals(result.getMaterializedRows().get(3).getField(1),
                "{\"age\":28,\"scores\":[95,87,92],\"user\":\"diana\"}");
        assertEquals(result.getMaterializedRows().get(4).getField(1),
                "{\"active\":false,\"age\":42,\"role\":\"manager\",\"user\":\"eve\"}");
    }

    @Override
    @Test
    public void testVariantTypeAggregationCount()
    {
        MaterializedResult result = computeVariantQuery(
                "SELECT " +
                "    json_extract_scalar(data, '$.user') AS user_name, " +
                "    COUNT(*) AS user_count " +
                "FROM \"%s\".\"%s\" " +
                "GROUP BY json_extract_scalar(data, '$.user') " +
                "ORDER BY user_name");

        // Each of the five users occurs once.
        assertEquals(result.getMaterializedRows().size(), 5);
        String[] users = {"alice", "bob", "charlie", "diana", "eve"};
        for (int i = 0; i < users.length; i++) {
            assertEquals(result.getMaterializedRows().get(i).getField(0), users[i]);
            assertEquals(result.getMaterializedRows().get(i).getField(1), 1L);
        }
    }

    @Override
    @Test
    public void testVariantTypeAggregationAverage()
    {
        MaterializedResult result = computeVariantQuery(
                "SELECT " +
                "    json_extract_scalar(data, '$.user') AS user_name, " +
                "    AVG(CAST(json_extract_scalar(data, '$.age') AS INTEGER)) AS avg_age, " +
                "    MIN(CAST(json_extract_scalar(data, '$.age') AS INTEGER)) AS min_age, " +
                "    MAX(CAST(json_extract_scalar(data, '$.age') AS INTEGER)) AS max_age " +
                "FROM \"%s\".\"%s\" " +
                "GROUP BY json_extract_scalar(data, '$.user') " +
                "ORDER BY user_name");

        assertEquals(result.getMaterializedRows().size(), 5);
        String[] users = {"alice", "bob", "charlie", "diana", "eve"};
        int[] ages = {30, 25, 35, 28, 42};
        for (int i = 0; i < users.length; i++) {
            assertEquals(result.getMaterializedRows().get(i).getField(0), users[i]);
            assertEquals(result.getMaterializedRows().get(i).getField(1), (double) ages[i]);
            assertEquals(result.getMaterializedRows().get(i).getField(2), ages[i]);
            assertEquals(result.getMaterializedRows().get(i).getField(3), ages[i]);
        }
    }

    @Override
    @Test
    public void testVariantTypeGroupByWithHaving()
    {
        MaterializedResult result = computeVariantQuery(
                "SELECT " +
                "    json_extract_scalar(data, '$.user') AS user_name, " +
                "    COUNT(*) AS user_count " +
                "FROM \"%s\".\"%s\" " +
                "GROUP BY json_extract_scalar(data, '$.user') " +
                "HAVING COUNT(*) > 1 " +
                "ORDER BY user_name");

        // No user occurs more than once.
        assertEquals(result.getMaterializedRows().size(), 0);
    }

    @Override
    @Test
    public void testVariantTypeGroupByAgeWithHaving()
    {
        MaterializedResult result = computeVariantQuery(
                "SELECT " +
                "    CAST(json_extract_scalar(data, '$.age') AS INTEGER) AS age, " +
                "    COUNT(*) AS count " +
                "FROM \"%s\".\"%s\" " +
                "GROUP BY CAST(json_extract_scalar(data, '$.age') AS INTEGER) " +
                "HAVING AVG(CAST(json_extract_scalar(data, '$.age') AS INTEGER)) >= 25 " +
                "ORDER BY age");

        assertEquals(result.getMaterializedRows().size(), 5);
        int[] ages = {25, 28, 30, 35, 42};
        for (int i = 0; i < ages.length; i++) {
            assertEquals(result.getMaterializedRows().get(i).getField(0), ages[i]);
            assertEquals(result.getMaterializedRows().get(i).getField(1), 1L);
        }
    }

    @Override
    @Test
    public void testVariantTypeCountDistinct()
    {
        MaterializedResult result = computeVariantQuery(
                "SELECT " +
                "    COUNT(DISTINCT json_extract_scalar(data, '$.user')) AS unique_users, " +
                "    COUNT(DISTINCT CAST(json_extract_scalar(data, '$.age') AS INTEGER)) AS unique_ages " +
                "FROM \"%s\".\"%s\"");

        assertEquals(result.getMaterializedRows().size(), 1);
        // alice, bob, charlie, diana, eve and their five distinct ages.
        assertEquals(result.getMaterializedRows().get(0).getField(0), 5L);
        assertEquals(result.getMaterializedRows().get(0).getField(1), 5L);
    }

    @Override
    @Test
    public void testVariantTypeUnnestTags()
    {
        MaterializedResult result = computeVariantQuery(
                "SELECT " +
                "    id, " +
                "    json_extract_scalar(data, '$.user') AS user_name, " +
                "    tag " +
                "FROM \"%s\".\"%s\" " +
                "CROSS JOIN UNNEST(CAST(json_extract(data, '$.tags') AS ARRAY(VARCHAR))) AS t(tag) " +
                "ORDER BY id, tag");

        // Only bob, id 2, has tags.
        assertEquals(result.getMaterializedRows().size(), 2);

        assertEquals(result.getMaterializedRows().get(0).getField(0), 2);
        assertEquals(result.getMaterializedRows().get(0).getField(1), "bob");
        assertEquals(result.getMaterializedRows().get(0).getField(2), "admin");

        assertEquals(result.getMaterializedRows().get(1).getField(0), 2);
        assertEquals(result.getMaterializedRows().get(1).getField(1), "bob");
        assertEquals(result.getMaterializedRows().get(1).getField(2), "dev");
    }

    @Override
    @Test
    public void testVariantTypeSelfJoin()
    {
        MaterializedResult result = computeVariantQuery(
                "SELECT " +
                "    t1.id AS id1, " +
                "    json_extract_scalar(t1.data, '$.user') AS user1, " +
                "    t2.id AS id2, " +
                "    json_extract_scalar(t2.data, '$.user') AS user2 " +
                "FROM \"%1$s\".\"%2$s\" t1 " +
                "JOIN \"%1$s\".\"%2$s\" t2 " +
                "    ON json_extract_scalar(t1.data, '$.user') = json_extract_scalar(t2.data, '$.user') " +
                "    AND t1.id < t2.id " +
                "ORDER BY t1.id, t2.id");

        // No user occurs in more than one row.
        assertEquals(result.getMaterializedRows().size(), 0);
    }

    @Override
    @Test
    public void testVariantTypeJoinOnAge()
    {
        MaterializedResult result = computeVariantQuery(
                "SELECT " +
                "    t1.id AS id1, " +
                "    json_extract_scalar(t1.data, '$.user') AS user1, " +
                "    t2.id AS id2, " +
                "    json_extract_scalar(t2.data, '$.user') AS user2, " +
                "    CAST(json_extract_scalar(t1.data, '$.age') AS INTEGER) AS age " +
                "FROM \"%1$s\".\"%2$s\" t1 " +
                "JOIN \"%1$s\".\"%2$s\" t2 " +
                "    ON json_extract_scalar(t1.data, '$.age') = json_extract_scalar(t2.data, '$.age') " +
                "    AND t1.id < t2.id " +
                "ORDER BY age, t1.id, t2.id");

        // No age occurs in more than one row.
        assertEquals(result.getMaterializedRows().size(), 0);
    }
}
