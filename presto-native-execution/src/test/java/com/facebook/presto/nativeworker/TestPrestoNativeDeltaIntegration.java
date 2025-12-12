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
import com.facebook.presto.delta.TestDeltaIntegration;
import com.facebook.presto.testing.QueryRunner;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestPrestoNativeDeltaIntegration
        extends TestDeltaIntegration
{
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
        QueryRunner queryRunner = PrestoNativeQueryRunnerUtils.nativeDeltaQueryRunnerBuilder()
                .build();

        // Create the test Delta tables in HMS
        for (String deltaTestTable : DELTA_TEST_TABLE_LIST) {
            registerDeltaTableInHMS(queryRunner, deltaTestTable, deltaTestTable);
        }

        return queryRunner;
    }

    // Presto delta supports partition keys being anywhere in the column list,
    // but native execution requires all partition columns to be at the end of the column list.
    // This is because velox split reader verifies the positioning of the partition columns.
    // Java reader does not verify this and reads parquet files appending in the partition columns.
    // See DeltaPageSource.getNextPage().
    // We attempted a fix that kind of worked, which was to set isRoot to true, but it did not seem
    // like a valid fix.
    @Test(dataProvider = "deltaReaderVersions")
    @Override
    public void readSimplePartitionedTableBeginningKeys(String version)
    {
        String testQuery = "SELECT * FROM \"" + getVersionPrefix(version) +
                "simple-partitioned-table\"";
        assertQueryFails(testQuery, " Delta table 'deltatables." + getVersionPrefix(version) +
                "simple-partitioned-table' has partition columns that are not at the end of the schema. " +
                "Native execution \\(Prestissimo\\) requires all partition columns to be at the end of the column list. " +
                "Please reorder the columns in your Delta table schema so that all partition columns appear after regular columns.");
    }
}
