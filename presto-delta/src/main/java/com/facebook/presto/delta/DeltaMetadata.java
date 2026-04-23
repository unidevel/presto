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
import com.facebook.presto.common.predicate.TupleDomain;
import com.facebook.presto.common.type.ArrayType;
import com.facebook.presto.common.type.MapType;
import com.facebook.presto.common.type.RowType;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.common.type.TypeManager;
import com.facebook.presto.hive.HdfsContext;
import com.facebook.presto.hive.HdfsEnvironment;
import com.facebook.presto.hive.HiveBasicStatistics;
import com.facebook.presto.hive.HiveStorageFormat;
import com.facebook.presto.hive.metastore.ExtendedHiveMetastore;
import com.facebook.presto.hive.metastore.HiveColumnStatistics;
import com.facebook.presto.hive.metastore.HivePrivilegeInfo;
import com.facebook.presto.hive.metastore.MetastoreContext;
import com.facebook.presto.hive.metastore.MetastoreUtil;
import com.facebook.presto.hive.metastore.PartitionStatistics;
import com.facebook.presto.hive.metastore.PrestoTableType;
import com.facebook.presto.hive.metastore.PrincipalPrivileges;
import com.facebook.presto.hive.metastore.Storage;
import com.facebook.presto.hive.metastore.Table;
import com.facebook.presto.spi.ColumnHandle;
import com.facebook.presto.spi.ColumnMetadata;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorTableHandle;
import com.facebook.presto.spi.ConnectorTableLayout;
import com.facebook.presto.spi.ConnectorTableLayoutHandle;
import com.facebook.presto.spi.ConnectorTableLayoutResult;
import com.facebook.presto.spi.ConnectorTableMetadata;
import com.facebook.presto.spi.Constraint;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.SchemaTablePrefix;
import com.facebook.presto.spi.TableNotFoundException;
import com.facebook.presto.spi.connector.ConnectorMetadata;
import com.facebook.presto.spi.security.PrestoPrincipal;
import com.facebook.presto.spi.statistics.ColumnStatisticMetadata;
import com.facebook.presto.spi.statistics.ColumnStatisticType;
import com.facebook.presto.spi.statistics.ColumnStatistics;
import com.facebook.presto.spi.statistics.ComputedStatistics;
import com.facebook.presto.spi.statistics.Estimate;
import com.facebook.presto.spi.statistics.TableStatistics;
import com.facebook.presto.spi.statistics.TableStatisticsMetadata;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import jakarta.inject.Inject;
import org.apache.hadoop.fs.Path;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.facebook.presto.common.type.BigintType.BIGINT;
import static com.facebook.presto.common.type.BooleanType.BOOLEAN;
import static com.facebook.presto.common.type.DateType.DATE;
import static com.facebook.presto.common.type.Decimals.isLongDecimal;
import static com.facebook.presto.common.type.Decimals.isShortDecimal;
import static com.facebook.presto.common.type.DoubleType.DOUBLE;
import static com.facebook.presto.common.type.IntegerType.INTEGER;
import static com.facebook.presto.common.type.RealType.REAL;
import static com.facebook.presto.common.type.SmallintType.SMALLINT;
import static com.facebook.presto.common.type.TimestampType.TIMESTAMP;
import static com.facebook.presto.common.type.TinyintType.TINYINT;
import static com.facebook.presto.common.type.VarbinaryType.VARBINARY;
import static com.facebook.presto.delta.DeltaColumnHandle.ColumnType.PARTITION;
import static com.facebook.presto.delta.DeltaColumnHandle.ColumnType.REGULAR;
import static com.facebook.presto.delta.DeltaExpressionUtils.splitPredicate;
import static com.facebook.presto.delta.DeltaTableProperties.EXTERNAL_LOCATION_PROPERTY;
import static com.facebook.presto.delta.DeltaTableProperties.getTableStorageFormat;
import static com.facebook.presto.delta.DeltaTableProperties.isExternalTable;
import static com.facebook.presto.hive.HiveColumnConverterProvider.DEFAULT_COLUMN_CONVERTER_PROVIDER;
import static com.facebook.presto.hive.HiveStatisticsUtil.createPartitionStatistics;
import static com.facebook.presto.hive.metastore.MetastoreUtil.toPartitionValues;
import static com.facebook.presto.hive.metastore.PrestoTableType.EXTERNAL_TABLE;
import static com.facebook.presto.hive.metastore.PrestoTableType.MANAGED_TABLE;
import static com.facebook.presto.hive.metastore.Statistics.createComputedStatisticsToPartitionMap;
import static com.facebook.presto.hive.metastore.Statistics.createEmptyPartitionStatistics;
import static com.facebook.presto.hive.metastore.StorageFormat.fromHiveStorageFormat;
import static com.facebook.presto.spi.StandardErrorCode.NOT_SUPPORTED;
import static com.facebook.presto.spi.security.PrincipalType.USER;
import static com.facebook.presto.spi.statistics.ColumnStatisticType.MAX_VALUE;
import static com.facebook.presto.spi.statistics.ColumnStatisticType.MAX_VALUE_SIZE_IN_BYTES;
import static com.facebook.presto.spi.statistics.ColumnStatisticType.MIN_VALUE;
import static com.facebook.presto.spi.statistics.ColumnStatisticType.NUMBER_OF_DISTINCT_VALUES;
import static com.facebook.presto.spi.statistics.ColumnStatisticType.NUMBER_OF_NON_NULL_VALUES;
import static com.facebook.presto.spi.statistics.ColumnStatisticType.NUMBER_OF_TRUE_VALUES;
import static com.facebook.presto.spi.statistics.ColumnStatisticType.TOTAL_SIZE_IN_BYTES;
import static com.facebook.presto.spi.statistics.TableStatisticType.ROW_COUNT;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Suppliers.memoize;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Collections.emptyList;
import static java.util.Locale.US;
import static java.util.Objects.requireNonNull;

public class DeltaMetadata
        implements ConnectorMetadata
{
    private static final Logger log = Logger.get(DeltaMetadata.class);

    /**
     * Special schema used when querying a Delta table by storage location.
     * Ex. SELECT * FROM delta."$PATH$"."s3://bucket/path/to/table". User is not able to list any tables
     * in this schema. It is just used to query a Delta table by storage location.
     */
    private static final String PATH_SCHEMA = "$PATH$";

    private final String connectorId;
    private final DeltaClient deltaClient;
    private final ExtendedHiveMetastore metastore;
    private final TypeManager typeManager;
    private final DeltaConfig config;
    private final HdfsEnvironment hdfsEnvironment;
    private final DeltaStatisticsStore statisticsStore;

    @Inject
    public DeltaMetadata(
            DeltaConnectorId connectorId,
            DeltaClient deltaClient,
            ExtendedHiveMetastore metastore,
            TypeManager typeManager,
            DeltaConfig config,
            HdfsEnvironment hdfsEnvironment,
            DeltaStatisticsStore statisticsStore)
    {
        this.connectorId = requireNonNull(connectorId, "connectorId is null").toString();
        this.deltaClient = requireNonNull(deltaClient, "deltaClient is null");
        this.metastore = requireNonNull(metastore, "metastore is null");
        this.typeManager = requireNonNull(typeManager, "typeManager is null");
        this.config = requireNonNull(config, "config is null");
        this.hdfsEnvironment = requireNonNull(hdfsEnvironment, "hdfsEnvironment is null");
        this.statisticsStore = requireNonNull(statisticsStore, "statisticsStore is null");
    }

    @Override
    public List<String> listSchemaNames(ConnectorSession session)
    {
        ArrayList<String> schemas = new ArrayList<>();
        schemas.addAll(metastore.getAllDatabases(metastoreContext(session)));
        schemas.add(PATH_SCHEMA.toLowerCase(US));
        return schemas;
    }

    @Override
    public void createTable(ConnectorSession session, ConnectorTableMetadata tableMetadata, boolean ignoreExisting)
    {
        PrestoTableType tableType = isExternalTable(tableMetadata.getProperties()) ? EXTERNAL_TABLE : MANAGED_TABLE;
        Table table = prepareTable(session, tableMetadata, tableType);
        PrincipalPrivileges principalPrivileges = buildInitialPrivilegeSet(table.getOwner());

        metastore.createTable(
                metastoreContext(session),
                table,
                principalPrivileges,
                emptyList());
    }

    @Override
    public void dropTable(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        DeltaTableHandle handle = (DeltaTableHandle) tableHandle;
        MetastoreContext metastoreContext = metastoreContext(session);

        Optional<Table> target = metastore.getTable(metastoreContext, handle.getDeltaTable().getSchemaName(), handle.getDeltaTable().getTableName());
        if (!target.isPresent()) {
            throw new TableNotFoundException(handle.toSchemaTableName());
        }

        metastore.dropTable(
                metastoreContext,
                handle.getDeltaTable().getSchemaName(),
                handle.getDeltaTable().getTableName(),
                false);
    }

    private static PrincipalPrivileges buildInitialPrivilegeSet(String tableOwner)
    {
        PrestoPrincipal owner = new PrestoPrincipal(USER, tableOwner);
        return new PrincipalPrivileges(
                ImmutableMultimap.<String, HivePrivilegeInfo>builder()
                        .put(tableOwner, new HivePrivilegeInfo(HivePrivilegeInfo.HivePrivilege.SELECT, true, owner, owner))
                        .build(),
                ImmutableMultimap.of());
    }

    private Table prepareTable(ConnectorSession session, ConnectorTableMetadata tableMetadata, PrestoTableType tableType)
    {
        String schemaName = tableMetadata.getTable().getSchemaName();
        String tableName = tableMetadata.getTable().getTableName();

        if (!tableType.equals(EXTERNAL_TABLE)) {
            throw new PrestoException(NOT_SUPPORTED, "Cannot create managed Delta table");
        }

        Table.Builder tableBuilder = Table.builder()
                .setDatabaseName(schemaName)
                .setTableName(tableName)
                .setOwner(session.getUser())
                .setTableType(tableType);

        Map<String, Object> tableProperties = tableMetadata.getProperties();

        HiveStorageFormat hiveStorageFormat = getTableStorageFormat(tableMetadata.getProperties());
        String tableLocation = tableProperties.get(EXTERNAL_LOCATION_PROPERTY).toString();

        // Need to check for path existence to avoid inconsistent metastore
        // as Delta connector does not support managed tables
        Path targetPath = MetastoreUtil.getExternalPath(
                hdfsEnvironment,
                new HdfsContext(session, schemaName, tableName, tableLocation, true),
                tableLocation);
        log.debug("Creating external table with location: '%s'", targetPath.toString());

        tableBuilder.getStorageBuilder()
                .setStorageFormat(fromHiveStorageFormat(hiveStorageFormat))
                .setLocation(tableLocation);
        return tableBuilder.build();
    }

    @Override
    public DeltaTableHandle getTableHandle(ConnectorSession session, SchemaTableName schemaTableName)
    {
        String schemaName = schemaTableName.getSchemaName();
        String tableName = schemaTableName.getTableName();
        if (!listSchemaNames(session).contains(schemaName)) {
            return null; // indicates table doesn't exist
        }

        DeltaTableName deltaTableName = DeltaTableName.from(tableName);
        String tableLocation;
        if (PATH_SCHEMA.equalsIgnoreCase(schemaName)) {
            tableLocation = deltaTableName.getTableNameOrPath();
        }
        else {
            Optional<Table> metastoreTable = metastore.getTable(metastoreContext(session), schemaName, deltaTableName.getTableNameOrPath());
            if (!metastoreTable.isPresent()) {
                return null; // indicates table doesn't exist
            }

            Map<String, String> tableParameters = metastoreTable.get().getParameters();
            Storage storage = metastoreTable.get().getStorage();
            tableLocation = storage.getLocation();

            // Delta table written using Spark and Hive have set the table parameter
            // "spark.sql.sources.provider = delta". If this property is found table
            // location is found in SerDe properties with key "path".
            if ("delta".equalsIgnoreCase(tableParameters.get("spark.sql.sources.provider"))) {
                tableLocation = storage.getSerdeParameters().get("path");
                if (Strings.isNullOrEmpty(tableLocation)) {
                    log.warn("Location key ('path') is missing in SerDe properties for table %s. " +
                            "Using the 'location' attribute as the table location.", schemaTableName);
                    // fallback to using the location attribute
                    tableLocation = storage.getLocation();
                }
            }
        }

        Optional<DeltaTable> table = deltaClient.getTable(
                config,
                session,
                schemaTableName,
                tableLocation,
                deltaTableName.getSnapshotId(),
                deltaTableName.getTimestampMillisUtc());
        if (table.isPresent()) {
            return new DeltaTableHandle(connectorId, table.get());
        }
        return null;
    }

    @Override
    public ConnectorTableHandle getTableHandleForStatisticsCollection(
            ConnectorSession session,
            SchemaTableName tableName,
            Map<String, Object> analyzeProperties)
    {
        DeltaTableHandle handle = getTableHandle(session, tableName);
        if (handle == null) {
            return null;
        }

        // Extract partition list from analyze properties (PART-01)
        Optional<List<List<String>>> partitionValuesList = DeltaAnalyzeProperties.getPartitionList(analyzeProperties);

        // Validate: if partition list is specified, table must be partitioned (PART-02)
        if (partitionValuesList.isPresent()) {
            DeltaTable deltaTable = handle.getDeltaTable();
            boolean isPartitioned = deltaTable.getColumns().stream()
                    .anyMatch(DeltaColumn::isPartition);
            if (!isPartitioned) {
                throw new PrestoException(NOT_SUPPORTED, "Only partitioned table can be analyzed with a partition list");
            }
        }

        return handle;
    }

    @Override
    public TableStatisticsMetadata getStatisticsCollectionMetadata(ConnectorSession session, ConnectorTableMetadata tableMetadata)
    {
        List<String> partitionedBy = tableMetadata.getProperties().entrySet().stream()
                .filter(e -> "partitioned_by".equals(e.getKey()))
                .map(e -> e.getValue().toString())
                .findFirst()
                .map(list -> list.replace("[", "").replace("]", "").split(","))
                .map(parts -> ImmutableList.copyOf(parts))
                .orElse(ImmutableList.of());

        Set<ColumnStatisticMetadata> columnStatistics = tableMetadata.getColumns().stream()
                .filter(column -> !partitionedBy.contains(column.getName()))
                .filter(column -> !column.isHidden())
                .flatMap(column -> getColumnStatisticMetadata(column.getName(), column.getType()).stream())
                .collect(toImmutableSet());

        return new TableStatisticsMetadata(columnStatistics, ImmutableSet.of(ROW_COUNT), partitionedBy);
    }

    private List<ColumnStatisticMetadata> getColumnStatisticMetadata(String columnName, com.facebook.presto.common.type.Type type)
    {
        return getSupportedColumnStatistics(type).stream()
                .map(statisticType -> statisticType.getColumnStatisticMetadata(columnName))
                .collect(toImmutableList());
    }

    private Set<ColumnStatisticType> getSupportedColumnStatistics(Type type)
    {
        if (type.equals(BOOLEAN)) {
            return ImmutableSet.of(NUMBER_OF_NON_NULL_VALUES, NUMBER_OF_TRUE_VALUES);
        }
        if (isNumericType(type) || type.equals(DATE) || type.equals(TIMESTAMP)) {
            return ImmutableSet.of(MIN_VALUE, MAX_VALUE, NUMBER_OF_DISTINCT_VALUES, NUMBER_OF_NON_NULL_VALUES);
        }
        if (type instanceof com.facebook.presto.common.type.VarcharType || type instanceof com.facebook.presto.common.type.CharType) {
            // VARCHAR types don't support min/max statistics in Hive metastore
            return ImmutableSet.of(NUMBER_OF_NON_NULL_VALUES, NUMBER_OF_DISTINCT_VALUES, TOTAL_SIZE_IN_BYTES, MAX_VALUE_SIZE_IN_BYTES);
        }
        if (type.equals(VARBINARY)) {
            return ImmutableSet.of(NUMBER_OF_NON_NULL_VALUES, TOTAL_SIZE_IN_BYTES, MAX_VALUE_SIZE_IN_BYTES);
        }
        if (type instanceof ArrayType || type instanceof RowType || type instanceof MapType) {
            return ImmutableSet.of(NUMBER_OF_NON_NULL_VALUES, TOTAL_SIZE_IN_BYTES);
        }
        // Default column statistics for unknown data types
        return ImmutableSet.of(NUMBER_OF_NON_NULL_VALUES, TOTAL_SIZE_IN_BYTES);
    }

    private static boolean isNumericType(Type type)
    {
        return type.equals(BIGINT) || type.equals(INTEGER) || type.equals(SMALLINT) || type.equals(TINYINT) ||
                type.equals(DOUBLE) || type.equals(REAL) ||
                isShortDecimal(type) || isLongDecimal(type);
    }

    @Override
    public ConnectorTableHandle beginStatisticsCollection(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        DeltaTableHandle handle = (DeltaTableHandle) tableHandle;
        MetastoreContext metastoreContext = metastoreContext(session);

        Optional<Table> table = metastore.getTable(
                metastoreContext,
                handle.getDeltaTable().getSchemaName(),
                handle.getDeltaTable().getTableName());
        if (!table.isPresent()) {
            throw new TableNotFoundException(handle.toSchemaTableName());
        }
        return handle;
    }

    @Override
    public void finishStatisticsCollection(
            ConnectorSession session,
            ConnectorTableHandle tableHandle,
            Collection<com.facebook.presto.spi.statistics.ComputedStatistics> computedStatistics)
    {
        DeltaTableHandle handle = (DeltaTableHandle) tableHandle;
        SchemaTableName tableName = handle.toSchemaTableName();
        MetastoreContext metastoreContext = metastoreContext(session);

        Table table = metastore.getTable(metastoreContext, tableName.getSchemaName(), tableName.getTableName())
                .orElseThrow(() -> new TableNotFoundException(tableName));

        if (computedStatistics.isEmpty()) {
            // Store empty statistics if computedStatistics is empty (per STATS-02)
            handleEmptyStatistics(session, handle, table, metastoreContext);
            return;
        }

        // Get partition column names from DeltaTable
        DeltaTable deltaTable = handle.getDeltaTable();
        List<String> partitionColumnNames = deltaTable.getColumns().stream()
                .filter(DeltaColumn::isPartition)
                .map(DeltaColumn::getName)
                .collect(toImmutableList());

        // Build column types map from DeltaTable columns (non-partitioned columns only)
        Map<String, Type> columnTypes = createDeltaColumnTypes(deltaTable);

        // Map computed statistics to partitions
        Map<List<String>, ComputedStatistics> computedStatisticsMap = createComputedStatisticsToPartitionMap(
                computedStatistics, partitionColumnNames, columnTypes);

        if (partitionColumnNames.isEmpty()) {
            // Non-partitioned table: store statistics in-memory
            ComputedStatistics tableStats = computedStatisticsMap.get(ImmutableList.of());
            if (tableStats != null) {
                DateTimeZone timeZone = DateTimeZone.forID(session.getTimeZoneKey().getId());
                PartitionStatistics partitionStatistics = createPartitionStatistics(
                        session, columnTypes, tableStats, timeZone);
                statisticsStore.updateTableStatistics(
                        table.getDatabaseName(),
                        table.getTableName(),
                        oldStats -> partitionStatistics);
            }
            else {
                // Store empty statistics
                handleEmptyStatistics(session, handle, table, metastoreContext);
            }
        }
        else {
            // Partitioned table: store statistics in-memory
            handlePartitionedStatistics(session, handle, table, deltaTable, columnTypes,
                    partitionColumnNames, computedStatisticsMap, metastoreContext);
        }
    }

    private void handleEmptyStatistics(ConnectorSession session, DeltaTableHandle handle,
            Table table, MetastoreContext metastoreContext)
    {
        DeltaTable deltaTable = handle.getDeltaTable();
        List<String> partitionColumnNames = deltaTable.getColumns().stream()
                .filter(DeltaColumn::isPartition)
                .map(DeltaColumn::getName)
                .collect(toImmutableList());

        Map<String, Type> columnTypes = createDeltaColumnTypes(deltaTable);

        if (partitionColumnNames.isEmpty()) {
            // Non-partitioned table: store empty table statistics in-memory
            PartitionStatistics emptyStats = createEmptyPartitionStatistics(
                    columnTypes, getColumnStatisticTypes(columnTypes));
            statisticsStore.updateTableStatistics(
                    table.getDatabaseName(),
                    table.getTableName(),
                    oldStats -> emptyStats);
        }
        // For partitioned tables, we don't store empty stats - just return
    }

    private Map<String, Type> createDeltaColumnTypes(DeltaTable deltaTable)
    {
        return deltaTable.getColumns().stream()
                .filter(column -> !column.isPartition())
                .collect(toImmutableMap(
                        DeltaColumn::getName,
                        column -> typeManager.getType(column.getType())));
    }

    private Map<String, Set<ColumnStatisticType>> getColumnStatisticTypes(Map<String, Type> columnTypes)
    {
        ImmutableMap.Builder<String, Set<ColumnStatisticType>> builder = ImmutableMap.builder();
        for (Map.Entry<String, Type> entry : columnTypes.entrySet()) {
            builder.put(entry.getKey(), ImmutableSet.of(MIN_VALUE, MAX_VALUE, NUMBER_OF_NON_NULL_VALUES));
        }
        return builder.build();
    }

    private void handlePartitionedStatistics(ConnectorSession session, DeltaTableHandle handle,
            Table table, DeltaTable deltaTable, Map<String, Type> columnTypes,
            List<String> partitionColumnNames, Map<List<String>, ComputedStatistics> computedStatisticsMap,
            MetastoreContext metastoreContext)
    {
        // Get all partition names from metastore
        List<com.facebook.presto.hive.PartitionNameWithVersion> partitionNamesWithVersion = metastore.getPartitionNames(metastoreContext,
                deltaTable.getSchemaName(), deltaTable.getTableName())
                .orElseThrow(() -> new TableNotFoundException(handle.toSchemaTableName()));

        List<String> partitionNames = partitionNamesWithVersion.stream()
                .map(com.facebook.presto.hive.PartitionNameWithVersion::getPartitionName)
                .collect(toImmutableList());

        // Build column statistic types
        Map<String, Set<ColumnStatisticType>> columnStatisticTypes = getColumnStatisticTypes(columnTypes);

        Supplier<PartitionStatistics> emptyPartitionStatistics = memoize(
                () -> createEmptyPartitionStatistics(columnTypes, columnStatisticTypes));

        List<com.facebook.presto.hive.metastore.Column> partitionColumns = table.getPartitionColumns();
        List<Type> partitionTypes = partitionColumns.stream()
                .map(column -> columnTypes.get(column.getName()))
                .collect(toImmutableList());

        int usedComputedStatistics = 0;
        for (String partitionName : partitionNames) {
            List<String> partitionValues = toPartitionValues(partitionName);

            ComputedStatistics collectedStatistics = computedStatisticsMap.containsKey(partitionValues)
                    ? computedStatisticsMap.get(partitionValues)
                    : computedStatisticsMap.get(canonicalizePartitionValues(partitionName, partitionValues, partitionTypes));

            if (collectedStatistics == null) {
                // Store empty partition statistics in-memory
                PartitionStatistics emptyStats = emptyPartitionStatistics.get();
                statisticsStore.updatePartitionStatistics(
                        table.getDatabaseName(),
                        table.getTableName(),
                        partitionName,
                        oldStats -> emptyStats);
            }
            else {
                usedComputedStatistics++;
                // Store partition statistics in-memory
                DateTimeZone timeZone = DateTimeZone.forID(session.getTimeZoneKey().getId());
                PartitionStatistics partitionStats = createPartitionStatistics(
                        session, columnTypes, collectedStatistics, timeZone);
                statisticsStore.updatePartitionStatistics(
                        table.getDatabaseName(),
                        table.getTableName(),
                        partitionName,
                        oldStats -> partitionStats);
            }
        }

        // Verify all computed statistics were used
        checkArgument(usedComputedStatistics == computedStatisticsMap.size() || computedStatisticsMap.isEmpty(),
                "All computed statistics must be used");
    }

    private List<String> canonicalizePartitionValues(String partitionName, List<String> partitionValues, List<Type> partitionTypes)
    {
        // Match HiveMetadata pattern - use partition values as-is if already in map
        return partitionValues;
    }

    @Override
    public TableStatistics getTableStatistics(
            ConnectorSession session,
            ConnectorTableHandle tableHandle,
            Optional<ConnectorTableLayoutHandle> tableLayoutHandle,
            List<ColumnHandle> columnHandles,
            Constraint<ColumnHandle> constraint)
    {
        DeltaTableHandle handle = (DeltaTableHandle) tableHandle;
        SchemaTableName tableName = handle.toSchemaTableName();
        MetastoreContext metastoreContext = metastoreContext(session);

        // Get table statistics from in-memory store
        PartitionStatistics tableStatistics = statisticsStore.getTableStatistics(
                tableName.getSchemaName(),
                tableName.getTableName());

        // Return empty statistics if ANALYZE hasn't been run (empty basic stats indicate no data)
        if (tableStatistics == null || tableStatistics.getBasicStatistics().equals(HiveBasicStatistics.createEmptyStatistics())) {
            return TableStatistics.empty();
        }

        HiveBasicStatistics basicStats = tableStatistics.getBasicStatistics();

        // Build TableStatistics result
        TableStatistics.Builder result = TableStatistics.builder();

        // Set row count from basic statistics
        long rowCount = 0;
        if (basicStats.getRowCount().isPresent()) {
            rowCount = basicStats.getRowCount().getAsLong();
            result.setRowCount(Estimate.of(rowCount));
        }
        else {
            result.setRowCount(Estimate.of(0));
        }

        for (Map.Entry<String, HiveColumnStatistics> entry : tableStatistics.getColumnStatistics().entrySet()) {
            String columnName = entry.getKey();
            HiveColumnStatistics hiveColumnStats = entry.getValue();

            // Find the column handle matching this column name
            ColumnHandle columnHandle = columnHandles.stream()
                    .filter(ch -> ch instanceof DeltaColumnHandle)
                    .filter(ch -> ((DeltaColumnHandle) ch).getName().equals(columnName))
                    .findFirst()
                    .orElse(null);

            if (columnHandle == null) {
                continue;
            }

            ColumnStatistics columnStatistics = createColumnStatistics(hiveColumnStats, rowCount);
            result.setColumnStatistics(columnHandle, columnStatistics);
        }

        return result.build();
    }

    private ColumnStatistics createColumnStatistics(HiveColumnStatistics hiveStats, long rowCount)
    {
        ColumnStatistics.Builder builder = ColumnStatistics.builder();

        // Set nulls fraction if nulls count is present
        if (hiveStats.getNullsCount().isPresent()) {
            long nullsCount = hiveStats.getNullsCount().getAsLong();
            double nullsFraction = (double) nullsCount / rowCount;
            builder.setNullsFraction(Estimate.of(nullsFraction));
        }
        else {
            builder.setNullsFraction(Estimate.unknown());
        }

        // Set distinct values count if present
        if (hiveStats.getDistinctValuesCount().isPresent()) {
            builder.setDistinctValuesCount(Estimate.of(hiveStats.getDistinctValuesCount().getAsLong()));
        }
        else {
            builder.setDistinctValuesCount(Estimate.unknown());
        }

        // Set data size if present
        if (hiveStats.getTotalSizeInBytes().isPresent()) {
            builder.setDataSize(Estimate.of(hiveStats.getTotalSizeInBytes().getAsLong()));
        }
        else {
            builder.setDataSize(Estimate.unknown());
        }

        // Extract min/max values from the various statistics types
        // Integer statistics
        if (hiveStats.getIntegerStatistics().isPresent()) {
            com.facebook.presto.hive.metastore.IntegerStatistics intStats = hiveStats.getIntegerStatistics().get();
            if (intStats.getMin().isPresent() && intStats.getMax().isPresent()) {
                double min = intStats.getMin().getAsLong();
                double max = intStats.getMax().getAsLong();
                builder.setRange(new com.facebook.presto.spi.statistics.DoubleRange(min, max));
            }
        }
        // Double statistics
        else if (hiveStats.getDoubleStatistics().isPresent()) {
            com.facebook.presto.hive.metastore.DoubleStatistics dblStats = hiveStats.getDoubleStatistics().get();
            if (dblStats.getMin().isPresent() && dblStats.getMax().isPresent()) {
                double min = dblStats.getMin().getAsDouble();
                double max = dblStats.getMax().getAsDouble();
                builder.setRange(new com.facebook.presto.spi.statistics.DoubleRange(min, max));
            }
        }
        // Decimal statistics
        else if (hiveStats.getDecimalStatistics().isPresent()) {
            com.facebook.presto.hive.metastore.DecimalStatistics decStats = hiveStats.getDecimalStatistics().get();
            if (decStats.getMin().isPresent() && decStats.getMax().isPresent()) {
                double min = decStats.getMin().get().doubleValue();
                double max = decStats.getMax().get().doubleValue();
                builder.setRange(new com.facebook.presto.spi.statistics.DoubleRange(min, max));
            }
        }
        // Date statistics
        else if (hiveStats.getDateStatistics().isPresent()) {
            com.facebook.presto.hive.metastore.DateStatistics dateStats = hiveStats.getDateStatistics().get();
            if (dateStats.getMin().isPresent() && dateStats.getMax().isPresent()) {
                // Convert LocalDate to epoch day (long) then to double for DoubleRange
                double min = dateStats.getMin().get().toEpochDay();
                double max = dateStats.getMax().get().toEpochDay();
                builder.setRange(new com.facebook.presto.spi.statistics.DoubleRange(min, max));
            }
        }

        return builder.build();
    }

    @Override
    public ConnectorTableLayoutResult getTableLayoutForConstraint(
            ConnectorSession session,
            ConnectorTableHandle table,
            Constraint<ColumnHandle> constraint,
            Optional<Set<ColumnHandle>> desiredColumns)
    {
        DeltaTableHandle tableHandle = (DeltaTableHandle) table;

        // Split the predicate into partition column predicate and other column predicates
        // Only the partition column predicate is fully enforced. Other predicate is partially enforced (best effort).
        List<TupleDomain<ColumnHandle>> predicate = splitPredicate(constraint.getSummary());
        TupleDomain<ColumnHandle> unenforcedPredicate = predicate.get(1);

        DeltaTableLayoutHandle newDeltaTableLayoutHandle = new DeltaTableLayoutHandle(
                tableHandle,
                constraint.getSummary().transform(DeltaColumnHandle.class::cast),
                Optional.of(constraint.getSummary().toString(session.getSqlFunctionProperties())));

        ConnectorTableLayout newLayout = new ConnectorTableLayout(
                newDeltaTableLayoutHandle,
                Optional.empty(),
                constraint.getSummary(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ImmutableList.of(),
                Optional.empty());

        return new ConnectorTableLayoutResult(newLayout, unenforcedPredicate);
    }

    @Override
    public ConnectorTableLayout getTableLayout(ConnectorSession session, ConnectorTableLayoutHandle handle)
    {
        return new ConnectorTableLayout(handle);
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(ConnectorSession session, ConnectorTableHandle table)
    {
        DeltaTableHandle deltaTableHandle = (DeltaTableHandle) table;
        checkConnectorId(deltaTableHandle);
        return getTableMetadata(session, deltaTableHandle.toSchemaTableName());
    }

    @Override
    public List<SchemaTableName> listTables(ConnectorSession session, Optional<String> schemaName)
    {
        List<String> schemaNames = schemaName.<List<String>>map(ImmutableList::of)
                .orElseGet(() -> listSchemaNames(session));
        ImmutableList.Builder<SchemaTableName> tableNames = ImmutableList.builder();
        for (String schema : schemaNames) {
            for (String tableName : metastore.getAllTables(metastoreContext(session), schema).orElse(emptyList())) {
                tableNames.add(new SchemaTableName(schema, tableName));
            }
        }
        return tableNames.build();
    }

    @Override
    public Map<String, ColumnHandle> getColumnHandles(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        DeltaTableHandle deltaTableHandle = (DeltaTableHandle) tableHandle;
        checkConnectorId(deltaTableHandle);

        ImmutableMap.Builder<String, ColumnHandle> columnHandles = ImmutableMap.builder();
        for (DeltaColumn column : deltaTableHandle.getDeltaTable().getColumns()) {
            columnHandles.put(
                    column.getName(),
                    new DeltaColumnHandle(
                            column.getName(),
                            column.getType(),
                            column.isPartition() ? PARTITION : REGULAR,
                            Optional.empty()));
        }
        return columnHandles.build();
    }

    @Override
    public Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(ConnectorSession session, SchemaTablePrefix prefix)
    {
        requireNonNull(prefix, "prefix is null");
        ImmutableMap.Builder<SchemaTableName, List<ColumnMetadata>> columns = ImmutableMap.builder();
        for (SchemaTableName tableName : listTables(session, prefix)) {
            ConnectorTableMetadata tableMetadata = getTableMetadata(session, tableName);
            // table can disappear during listing operation
            if (tableMetadata != null) {
                columns.put(tableName, tableMetadata.getColumns());
            }
        }
        return columns.build();
    }

    private ConnectorTableMetadata getTableMetadata(ConnectorSession session, SchemaTableName tableName)
    {
        DeltaTableHandle tableHandle = getTableHandle(session, tableName);
        if (tableHandle == null) {
            return null;
        }

        DeltaTable deltaTable = tableHandle.getDeltaTable();

        // External location property
        Map<String, Object> properties = new HashMap<>(1);
        if (deltaTable.getTableLocation() != null) {
            properties.put(DeltaTableProperties.EXTERNAL_LOCATION_PROPERTY, deltaTable.getTableLocation());
        }

        List<ColumnMetadata> columnMetadata = deltaTable.getColumns().stream()
                .map(column -> getColumnMetadata(session, column))
                .collect(Collectors.toList());

        return new ConnectorTableMetadata(tableName, columnMetadata, properties);
    }

    @Override
    public ColumnMetadata getColumnMetadata(ConnectorSession session, ConnectorTableHandle tableHandle, ColumnHandle columnHandle)
    {
        return getColumnMetadata(columnHandle);
    }

    private ColumnMetadata getColumnMetadata(ColumnHandle columnHandle)
    {
        DeltaColumnHandle deltaColumnHandle = (DeltaColumnHandle) columnHandle;
        return ColumnMetadata.builder()
                .setName(deltaColumnHandle.getName())
                .setType(typeManager.getType(deltaColumnHandle.getDataType()))
                .build();
    }

    private List<SchemaTableName> listTables(ConnectorSession session, SchemaTablePrefix prefix)
    {
        if (prefix.getSchemaName() == null) {
            return listTables(session, prefix.getSchemaName());
        }
        return ImmutableList.of(new SchemaTableName(prefix.getSchemaName(), prefix.getTableName()));
    }

    private ColumnMetadata getColumnMetadata(ConnectorSession session, DeltaColumn deltaColumn)
    {
        return ColumnMetadata.builder()
                .setName(normalizeIdentifier(session, deltaColumn.getName()))
                .setType(typeManager.getType(deltaColumn.getType()))
                .build();
    }

    private MetastoreContext metastoreContext(ConnectorSession session)
    {
        return new MetastoreContext(
                session.getIdentity(),
                session.getQueryId(),
                session.getClientInfo(),
                session.getClientTags(),
                session.getSource(),
                Optional.empty(),
                false,
                DEFAULT_COLUMN_CONVERTER_PROVIDER,
                session.getWarningCollector(),
                session.getRuntimeStats());
    }

    private void checkConnectorId(DeltaTableHandle tableHandle)
    {
        checkArgument(tableHandle.getConnectorId().equals(connectorId), "table handle is not for this connector");
    }
}
