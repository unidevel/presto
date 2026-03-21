# Feature Landscape: Dynamic Catalog Management for PrestoDB

**Domain:** Distributed SQL Query Engine — Catalog Management
**Researched:** 2026-03-20
**Confidence:** HIGH — Based on Trino reference implementation and Presto codebase analysis

## Executive Summary

Dynamic catalog management enables PrestoDB clusters to add, update, and remove catalogs at runtime without server restarts. This feature set addresses a significant operational gap in PrestoDB compared to Trino (its fork), which already provides experimental dynamic catalog support. The research identifies core requirements that must be implemented for functional dynamic catalog management, as well as differentiating features that can provide competitive advantage over Trino's current implementation.

Trino (the reference implementation) provides dynamic catalog management as an experimental feature with two storage backends: file-based and in-memory. Key insights from Trino: dropping catalogs does not interrupt running queries, workers sync catalog state from coordinator, and certain connectors (Hive, Iceberg, Delta Lake, Hudi) have known resource leakage issues when dropped. Presto's existing CatalogManager and StaticCatalogStore provide a foundation that can be extended with dynamic capabilities.

## Feature Categories Overview

| Category | Count | Definition |
|----------|-------|------------|
| Table Stakes | 7 | Features required for basic functionality — users will not adopt without these |
| Differentiators | 6 | Features that provide competitive advantage over Trino |
| Anti-Features | 3 | Explicitly NOT building — scope boundaries |

---

# Table Stakes Features

Features that must be implemented or users will not adopt this feature. These represent the minimum viable capability for dynamic catalog management.

## 1. Runtime Catalog Registration

| Attribute | Value |
|-----------|-------|
| **Complexity** | High |
| **Dependencies** | CatalogManager, ConnectorManager, CatalogStore |
| **Description** | Ability to add new catalogs to a running Presto cluster without restart |

**Why Expected:** This is the core value proposition of the entire project. Without runtime catalog registration, there is no dynamic catalog management. Users must currently restart Presto coordinators to add new data sources, causing query disruption.

**Trino Reference:** CREATE CATALOG SQL command with USING connector_name and WITH clause for properties.

**Presto Implementation Path:**
- Extend CatalogManager to accept new catalogs after initial load
- Integrate with ConnectorManager.createConnection() for connector initialization
- Add validation for required properties (connector.name) similar to StaticCatalogStore

**Key Considerations:**
- Property validation must happen before connector creation
- Must handle connector not found errors gracefully
- Should support environment variable substitution (e.g., `${ENV:VAR_NAME}`)

---

## 2. Runtime Catalog Removal

| Attribute | Value |
|-----------|-------|
| **Complexity** | High |
| **Dependencies** | CatalogManager, ConnectorManager, CatalogStore |
| **Description** | Ability to remove catalogs from a running Presto cluster without restart |

**Why Expected:** Complement to catalog registration. Users must be able to clean up unused catalogs. Trino explicitly allows dropping catalogs while queries are running.

**Trino Reference:** DROP CATALOG command — does NOT interrupt running queries but makes catalog unavailable to new queries.

**Presto Implementation Path:**
- Extend CatalogManager.removeCatalog() with additional cleanup logic
- Coordinate with ConnectorManager to properly dispose connector resources
- Update in-memory catalog state atomically

**Key Considerations:**
- Must handle connectors that don't properly release resources (Hive, Iceberg, Delta Lake, Hudi)
- Should provide warning when dropping catalog with active queries
- Need catalog pruning mechanism similar to Trino's catalog.prune.update-interval

---

## 3. Cluster-Wide Catalog State Synchronization

| Attribute | Value |
|-----------|-------|
| **Complexity** | High |
| **Dependencies** | Worker nodes, Discovery service |
| **Description** | Ensuring all nodes in the cluster have consistent catalog state after dynamic changes |

**Why Expected:** Presto is a distributed query engine. Inconsistent catalog state across coordinator and workers would cause query failures. When a catalog is added/removed on the coordinator, workers must be notified and update their local state.

**Trino Reference:** "New worker nodes joining the cluster receive the current catalog configuration from the coordinator node."

**Presto Implementation Path:**
- Leverage existing inter-node communication mechanisms (gRPC per ARCHITECTURE.md)
- Add catalog sync protocol between coordinator and workers
- Handle worker joining with existing catalog state
- Implement eventual consistency with coordinator as source of truth

**Key Considerations:**
- Catalog changes must not block query execution
- Need heartbeat/keepalive for catalog state freshness
- Must handle network partitions gracefully

---

## 4. Backward Compatibility with Static Loading

| Attribute | Value |
|-----------|-------|
| **Complexity** | Medium |
| **Dependencies** | StaticCatalogStore |
| **Description** | Existing static catalog loading from .properties files must continue to work |

**Why Expected:** The PROJECT.md explicitly requires backward compatibility. Existing Presto deployments rely on static loading and cannot be required to change their operational model.

**Trino Reference:** catalog.management property supports `static` mode which reads files only on startup.

**Presto Implementation Path:**
- Maintain StaticCatalogStore behavior unchanged for static mode
- Add configuration property for catalog management mode (static vs dynamic)
- Default to static behavior for backward compatibility
- Only enable dynamic capabilities when explicitly configured

---

## 5. REST API for Catalog Management

| Attribute | Value |
|-----------|-------|
| **Complexity** | Medium |
| **Dependencies** | Server module, CatalogManager |
| **Description** | HTTP endpoints for creating, reading, updating, and deleting catalogs |

**Why Expected:** PROJECT.md explicitly lists "REST API for catalog CRUD operations" as an Active requirement. CLI and API are sufficient for v1 per Out of Scope decisions.

**Trino Reference:** Trino uses SQL commands (CREATE CATALOG, DROP CATALOG) rather than REST API, but provides HTTP server for other operations.

**Presto Implementation Path:**
- Add endpoints following Presto's existing REST patterns (using server module patterns from ARCHITECTURE.md)
- Endpoints: POST /v1/catalogs (create), GET /v1/catalogs (list), GET /v1/catalogs/{name} (get), DELETE /v1/catalogs/{name} (drop)
- Validate catalog properties before processing
- Return appropriate HTTP status codes and error messages

---

## 6. Centralized Catalog Store

| Attribute | Value |
|-----------|-------|
| **Complexity** | High |
| **Dependencies** | CatalogStore interface |
| **Description** | Single source of truth for catalog configurations accessible by all nodes |

**Why Expected:** PROJECT.md explicitly requires "Catalog centralization store as single source of truth." Without centralized storage, catalog state would be ephemeral and inconsistent.

**Trino Reference:** catalog.store property supports `file` (filesystem) and `memory` (in-memory only) modes.

**Presto Implementation Path:**
- Design CatalogStore interface/abstract class
- Implement file-based CatalogStore extending Presto's existing properties file pattern
- Optionally implement in-memory CatalogStore for testing/simple deployments
- Coordinate with StaticCatalogStore for bootstrap

**Key Considerations:**
- File-based store requires write access to catalog configuration directory
- In-memory store should ignore existing files on startup (per Trino behavior)
- Need atomic file operations for consistency

---

## 7. In-Flight Query Handling During Catalog Changes

| Attribute | Value |
|-----------|-------|
| **Complexity** | High |
| **Dependencies** | Query execution, CatalogManager |
| **Description** | Graceful handling of queries that are using a catalog when that catalog is dropped |

**Why Expected:** PROJECT.md explicitly requires "Handle in-flight queries during catalog changes." This is critical for production deployments to avoid query failures during operational changes.

**Trino Reference:** "Dropping a catalog does not interrupt any running queries that use it, but makes it unavailable to any new queries."

**Presto Implementation Path:**
- Track active queries per catalog at query scheduling time
- When dropping catalog, mark as unavailable for new queries but allow existing to complete
- Implement catalog pruning with configurable interval (similar to catalog.prune.update-interval)
- Provide query status visibility to administrators

**Key Considerations:**
- Need to prevent new queries from starting on dropped catalog
- Should warn administrators if dropping catalog with active queries
- Must handle query timeout/failure gracefully

---

# Differentiator Features

Features that provide competitive advantage over Trino's current implementation or address gaps in Trino's approach.

## 1. Version History for Catalog Configurations

| Attribute | Value |
|-----------|-------|
| **Complexity** | Medium |
| **Dependencies** | CatalogStore, Version tracking |
| **Description** | Maintain history of catalog configuration changes with timestamps and change metadata |

**Why Expected:** PROJECT.md lists "Version history for catalog configurations" as an Active requirement. Trino does not provide this capability.

**Value Proposition:** Audit trail for compliance, ability to rollback configuration errors, historical visibility into catalog changes.

**Implementation Approach:**
- Store catalog versions in file-based store with naming convention (e.g., catalog.properties.v1, catalog.properties.v2)
- Or use separate version metadata store
- Track: timestamp, user/action, full configuration, change type (create/update/delete)

---

## 2. Change Notification System

| Attribute | Value |
|-----------|-------|
| **Complexity** | Medium |
| **Dependencies** | Event system, CatalogManager |
| **Description** | Emit events/notifications when catalog changes occur, allowing external systems to react |

**Why Expected:** PROJECT.md lists "Notification system for catalog changes across nodes" as an Active requirement.

**Value Proposition:** Enables integration with external monitoring, auditing, and automation systems. Presto has existing event listener plugin architecture that can be extended.

**Implementation Approach:**
- Extend Presto's EventListener plugin system for catalog events
- Event types: CATALOG_CREATED, CATALOG_UPDATED, CATALOG_DROPPED
- Include catalog name, properties (sanitized), timestamp, actor
- Allow configurable handlers (log, webhook, etc.)

---

## 3. SQL Interface for Catalog Management

| Attribute | Value |
|-----------|-------|
| **Complexity** | High |
| **Dependencies** | SQL parser, StatementAnalyzer, CatalogManager |
| **Description** | CREATE CATALOG and DROP CATALOG SQL commands available to clients |

**Why Expected:** Trino provides SQL-based catalog management. Presto should match or exceed this capability.

**Value Proposition:** Familiar interface for users already writing SQL, enables programmatic catalog management through query tools.

**Implementation Approach:**
- Add CREATE CATALOG and DROP CATALOG to Presto's SQL grammar
- Implement statement parsing and validation in analyzer module
- Execute through CatalogManager for actual catalog operations
- Handle property validation and environment variable substitution

---

## 4. Catalog Property Encryption/Secret Management

| Attribute | Value |
|-----------|-------|
| **Complexity** | High |
| **Dependencies** | Security, CatalogStore |
| **Description** | Secure handling of sensitive properties (passwords, API keys) in catalog configurations |

**Why Expected:** Trino warns that CREATE CATALOG queries are logged including sensitive properties. This is a security gap that can be addressed.

**Value Proposition:** Better security story than Trino, critical for enterprise deployments with sensitive data sources.

**Implementation Approach:**
- Integrate with Presto's existing secret management (if any)
- Mask sensitive properties in logs and events
- Support external secret store integration (vault, AWS secrets manager, etc.)
- Never log full catalog configuration with secrets

---

## 5. Catalog Health Monitoring

| Attribute | Value |
|-----------|-------|
| **Complexity** | Medium |
| **Dependencies** | CatalogManager, Health check |
| **Description** | Ability to verify catalog connectivity and health status at runtime |

**Value Proposition:** Proactively identify catalog issues before they cause query failures. Trino does not provide this.

**Implementation Approach:**
- Add health check method to Connector interface (or new CatalogHealthCheck)
- Test connectivity to underlying data source
- Expose via REST API: GET /v1/catalogs/{name}/health
- Include in catalog listing: GET /v1/catalogs?includeHealth=true

---

## 6. Bulk Catalog Operations

| Attribute | Value |
|-----------|-------|
| **Complexity** | Medium |
| **Dependencies** | CatalogStore, CatalogManager |
| **Description** | Ability to create/update/remove multiple catalogs in a single atomic or batched operation |

**Value Proposition:** Enable catalog migration and bulk configuration changes. Useful for migrating between environments.

**Implementation Approach:**
- Support catalog import/export (directory of properties files)
- Transaction-like semantics for multiple catalog changes (all-or-nothing)
- Or use idempotent operations with "apply and verify" pattern

---

# Anti-Features

Features explicitly NOT building — scope boundaries defined in PROJECT.md.

## 1. UI for Catalog Management

| Attribute | Value |
|-----------|-------|
| **Why Avoid** | Explicitly out of scope per PROJECT.md |
| **What To Do Instead** | CLI and REST API sufficient for v1 |

**Rationale:** The Out of Scope section explicitly states "[UI for catalog management] — CLI and API sufficient for v1." Building a UI would extend timeline significantly without adding core value.

---

## 2. Advanced Rollback Capabilities

| Attribute | Value |
|-----------|-------|
| **Why Avoid** | Explicitly deferred to future version per PROJECT.md |
| **What To Do Instead** | Version history provides visibility; manual intervention for rollback |

**Rationale:** PROJECT.md lists "[Advanced rollback capabilities] — Defer to future version" in Out of Scope. The version history differentiator provides visibility, but automated rollback is beyond initial scope.

---

## 3. Multi-Tenant Catalog Isolation

| Attribute | Value |
|-----------|-------|
| **Why Avoid** | Beyond initial scope per PROJECT.md |
| **What To Do Instead** | Single-tenant catalog management for v1 |

**Rationale:** PROJECT.md lists "[Multi-tenant catalog isolation] — Beyond initial scope" in Out of Scope. This feature would require significant additional design work for access control and resource isolation.

---

# Feature Dependencies

```
Runtime Catalog Registration
    ├── CatalogManager extension
    ├── ConnectorManager integration
    └── CatalogStore (create operation)

Runtime Catalog Removal
    ├── CatalogManager extension  
    ├── ConnectorManager cleanup
    ├── CatalogStore (delete operation)
    └── In-Flight Query Handling

Cluster-Wide Catalog Sync
    ├── Coordinator ↔ Worker communication
    ├── Catalog state versioning
    └── Event notification system

REST API
    ├── CatalogManager
    ├── CatalogStore
    └── Validation logic

Version History
    └── CatalogStore (versioned storage)

SQL Interface
    ├── SQL parser extension
    ├── Statement analyzer
    └── CatalogManager integration

Centralized Catalog Store
    ├── File-based implementation
    ├── Atomic file operations
    └── Integration with StaticCatalogStore
```

---

# MVP Recommendation

Based on research, the recommended feature prioritization for initial release:

## Priority 1: Table Stakes (Must Have)

1. **Runtime Catalog Registration** — Core value, enables dynamic management
2. **Runtime Catalog Removal** — Complements registration
3. **Catalog State Synchronization** — Essential for distributed operation
4. **Backward Compatibility** — Required per constraints

## Priority 2: Table Stakes + Core Differentiators

5. **REST API for CRUD** — Explicitly required in PROJECT.md
6. **Centralized Catalog Store** — Foundation for persistence
7. **In-Flight Query Handling** — Production requirement

## Priority 3: Differentiators (Value-Add)

8. **Version History** — Explicitly in PROJECT.md requirements
9. **Change Notification** — Explicitly in PROJECT.md requirements
10. **SQL Interface** — Match Trino capability
11. **Secret Management** — Security differentiation

## Defer (Anti-Features)

- UI — Explicitly out of scope
- Advanced rollback — Deferred
- Multi-tenant isolation — Beyond scope

---

# Sources

- **Trino Catalog Management Documentation** — https://trino.io/docs/current/admin/properties-catalog.html (HIGH confidence — official reference implementation)
- **Trino CREATE CATALOG** — https://trino.io/docs/current/sql/create-catalog.html (HIGH confidence — official reference)
- **Trino DROP CATALOG** — https://trino.io/docs/current/sql/drop-catalog.html (HIGH confidence — official reference)
- **Presto CatalogManager** — presto-main-base/src/main/java/com/facebook/presto/metadata/CatalogManager.java (HIGH confidence — existing codebase)
- **Presto StaticCatalogStore** — presto-main-base/src/main/java/com/facebook/presto/metadata/StaticCatalogStore.java (HIGH confidence — existing codebase)
- **Presto CatalogServer** — presto-main-base/src/main/java/com/facebook/presto/catalogserver/CatalogServer.java (HIGH confidence — existing codebase)
- **Project Requirements** — .planning/PROJECT.md (HIGH confidence — project definition)
- **Architecture Context** — .planning/codebase/ARCHITECTURE.md (HIGH confidence — architecture analysis)