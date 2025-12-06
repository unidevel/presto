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

#include "presto_cpp/main/connectors/DeltaPrestoToVeloxConnector.h"
#include "presto_cpp/main/connectors/PrestoToVeloxConnectorUtils.h"
#include "presto_cpp/presto_protocol/connector/delta/DeltaConnectorProtocol.h"
#include "velox/connectors/hive/HiveConnector.h"
#include "velox/connectors/hive/delta/HiveDeltaSplit.h"
#include "velox/type/fbhive/HiveTypeParser.h"
#include "velox/connectors/hive/HiveConnectorSplit.h" // For NodeSelectionStrategy

namespace facebook::presto {

namespace {
velox::connector::hive::NodeSelectionStrategy toVeloxNodeSelectionStrategy(
    const protocol::NodeSelectionStrategy strategy) {
  if (strategy == protocol::NodeSelectionStrategy::HARD_AFFINITY) {
    return velox::connector::hive::NodeSelectionStrategy::HARD_AFFINITY;
  } else if (strategy == protocol::NodeSelectionStrategy::NO_PREFERENCE) {
    return velox::connector::hive::NodeSelectionStrategy::NO_PREFERENCE;
  }
  return velox::connector::hive::NodeSelectionStrategy::SOFT_AFFINITY;
}
} // namespace

std::unique_ptr<velox::connector::ConnectorSplit>
DeltaPrestoToVeloxConnector::toVeloxSplit(
    const protocol::ConnectorId& catalogId,
    const protocol::ConnectorSplit* connectorSplit,
    const protocol::SplitContext* splitContext) const {
  auto deltaSplit =
      dynamic_cast<const protocol::delta::DeltaSplit*>(connectorSplit);
  VELOX_CHECK_NOT_NULL(
      deltaSplit, "Unexpected split type {}", connectorSplit->_type);

  std::unordered_map<std::string, std::optional<std::string>> partitionKeys;
  for (const auto& entry : deltaSplit->partitionValues) {
    partitionKeys.emplace(entry.first, entry.second);
  }

  std::unordered_map<std::string, std::string> customSplitInfo;
  customSplitInfo["table_format"] = "hive-delta";

  return std::make_unique<velox::connector::hive::delta::HiveDeltaSplit>(
      catalogId,
      deltaSplit->filePath,
      velox::dwio::common::FileFormat::PARQUET, // Delta only supports Parquet for now.
      deltaSplit->start,
      deltaSplit->length,
      deltaSplit->fileSize, // fileSize
      partitionKeys,
      std::nullopt, // tablePartitionPath
      customSplitInfo,
      nullptr, // planNode
      splitContext->cacheable,
      toVeloxNodeSelectionStrategy(deltaSplit->nodeSelectionStrategy)); // nodeSelectionStrategy
}

std::unique_ptr<velox::connector::ColumnHandle>
DeltaPrestoToVeloxConnector::toVeloxColumnHandle(
    const protocol::ColumnHandle* column,
    const TypeParser& typeParser) const {
  auto deltaColumn =
      dynamic_cast<const protocol::delta::DeltaColumnHandle*>(column);
  VELOX_CHECK_NOT_NULL(
      deltaColumn, "Unexpected column handle type {}", column->_type);

  velox::type::fbhive::HiveTypeParser hiveTypeParser;
  auto type = stringToType(deltaColumn->type, typeParser);
  return std::make_unique<velox::connector::hive::HiveColumnHandle>(
      deltaColumn->name,
      toHiveColumnType(deltaColumn->columnType),
      type,
      type);
}

std::unique_ptr<velox::connector::ConnectorTableHandle>
DeltaPrestoToVeloxConnector::toVeloxTableHandle(
    const protocol::TableHandle& tableHandle,
    const VeloxExprConverter& exprConverter,
    const TypeParser& typeParser) const {
  auto deltaLayout = std::dynamic_pointer_cast<
      const protocol::delta::DeltaTableLayoutHandle>(
      tableHandle.connectorTableLayout);
  VELOX_CHECK_NOT_NULL(
      deltaLayout,
      "Unexpected layout type {}",
      tableHandle.connectorTableLayout->_type);

  auto deltaTableHandle =
      std::dynamic_pointer_cast<const protocol::delta::DeltaTableHandle>(
          tableHandle.connectorHandle);
  VELOX_CHECK_NOT_NULL(
      deltaTableHandle,
      "Unexpected table handle type {}",
      tableHandle.connectorHandle->_type);

  std::string tableName = deltaTableHandle->deltaTable.tableName;

  // DeltaTableLayoutHandle does not have remainingPredicate or dataColumns directly
  // like Iceberg does. For now, we pass empty subfield filters and null remaining
  // predicate, as well as an empty list of column handles.
  return std::make_unique<velox::connector::hive::HiveTableHandle>(
      tableHandle.connectorId,
      tableName,
      false, // isPushdownFilterEnabled
      velox::common::SubfieldFilters{}, // subfieldFilters
      nullptr, // remainingPredicate
      nullptr, // finalDataColumns
      std::unordered_map<std::string, std::string>{}, // extraTableProperties
      std::vector<velox::connector::hive::HiveColumnHandlePtr>{} // columnHandles
  );
}

std::unique_ptr<protocol::ConnectorProtocol>
DeltaPrestoToVeloxConnector::createConnectorProtocol() const {
  return std::make_unique<protocol::delta::DeltaConnectorProtocol>();
}

} // namespace facebook::presto
