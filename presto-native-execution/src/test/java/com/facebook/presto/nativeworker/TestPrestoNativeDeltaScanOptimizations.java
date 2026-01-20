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
import com.facebook.presto.delta.TestDeltaScanOptimizations;
import com.facebook.presto.testing.QueryRunner;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestPrestoNativeDeltaScanOptimizations
        extends TestDeltaScanOptimizations
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
        QueryRunner queryRunner = PrestoNativeQueryRunnerUtils.nativeDeltaQueryRunnerBuilder()
                .build();

        // Create the test Delta tables in HMS
        for (String deltaTestTable : DELTA_TEST_TABLE_LIST) {
            registerDeltaTableInHMS(queryRunner, deltaTestTable, deltaTestTable);
        }
        return queryRunner;
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void filterOnRegularColumn(String version)
    {
        super.filterOnRegularColumn(version);
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void filterOnPartitionColumn(String version)
    {
        super.filterOnPartitionColumn(version);
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void filterOnMultiplePartitionColumns(String version)
    {
        super.filterOnMultiplePartitionColumns(version);
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void filterOnPartitionColumnAndRegularColumns(String version)
    {
        super.filterOnPartitionColumnAndRegularColumns(version);
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void nullPartitionFilter(String version)
    {
        super.nullPartitionFilter(version);
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void notNullPartitionFilter(String version)
    {
        super.notNullPartitionFilter(version);
    }

    @Test(dataProvider = "deltaReaderVersions")
    public void nestedColumnFilter(String version)
    {
        super.nestedColumnFilter(version);
    }
}

// Made with Bob
