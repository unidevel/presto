# Project Research Summary

**Project:** Dynamic Catalog Management for PrestoDB
**Domain:** Distributed SQL Query Engine — Dynamic Configuration Management
**Researched:** 2026-03-20
**Confidence:** HIGH

## Executive Summary

This research addresses the implementation of dynamic catalog management for PrestoDB, enabling runtime addition, modification, and removal of catalogs without cluster restarts. The current PrestoDB architecture loads catalogs statically at startup from `.properties` files, requiring coordinator restarts for any catalog changes—an operational burden that Trino (Presto's fork) has begun addressing with experimental dynamic catalog support.

The research recommends a **centralized catalog store backed by etcd** as the single source of truth, with **gRPC streaming** for low-latency cluster-wide notification of catalog changes. The architecture extends existing PrestoDB components (CatalogManager, ConnectorManager) rather than replacing them, preserving full backward compatibility with static file-based catalog loading. Seven table-stakes features must be delivered for user adoption, including runtime registration/removal, cluster synchronization, REST API, and graceful in-flight query handling. Six differentiator features can provide competitive advantage over Trino's implementation.

Key risks include catalog state inconsistency across cluster nodes (mitigated by notification system), query failures during catalog removal (mitigated by deprecation lifecycle), and connection pool leaks (mitigated by proper connector shutdown). The recommended implementation follows a 5-phase approach: Foundation → Core Dynamic Operations → Cluster Synchronization → Advanced Operations → Enterprise Features.

## Key Findings

### Recommended Stack

**Summary from STACK.md:** The recommended stack leverages existing PrestoDB infrastructure while introducing etcd as the centralized catalog configuration store. This choice provides MVCC, linearizable reads, and built-in watch notifications for real-time updates—capabilities purpose-built for distributed configuration management.

**Core technologies:**
- **etcd 3.6.x** — Centralized catalog configuration store with watch API. Industry standard (used by Kubernetes), provides MVCC, linearizable reads, watch notifications, and leader election. Natural fit for Presto's ecosystem (Facebook-born, like etcd).
- **jetcd 0.5.4** — Java client for etcd. Official library, already partially present in Presto dependency tree. Supports synchronous and asynchronous API, watch support, and lease management.
- **gRPC 1.75.0** — Inter-node notification system. Already deep in Presto codebase. Use gRPC streaming for efficient push-based catalog change notifications between coordinator and workers.
- **Jackson 2.15.4** — JSON serialization for catalog configs. Already in Presto stack for serializing catalog properties to/from etcd.

**Supporting libraries (already in stack):** Guava 32.1.0-jre (caching, concurrency), SLF4J 2.0.16 (logging), Airlift 0.227 (HTTP server), JAX-RS/Jersey (REST APIs).

**What NOT to use:**
- ZooKeeper (new deployments) — lacks MVCC and simple watch API
- Redis as primary store — lacks native KV versioning that etcd provides
- Database-backed config — introduces operational overhead, lacks native change notification
- File-based with polling — defeats purpose of dynamic management

### Expected Features

**Summary from FEATURES.md:** The feature landscape identifies 7 table-stakes features required for basic functionality, 6 differentiators that provide competitive advantage over Trino, and 3 explicitly deferred anti-features.

**Must have (table stakes):**
- **Runtime Catalog Registration** — Add catalogs to running cluster without restart. Core value proposition.
- **Runtime Catalog Removal** — Remove catalogs from running cluster without restart. Complement to registration.
- **Cluster-Wide Catalog State Synchronization** — Ensure all nodes have consistent catalog state after dynamic changes. Essential for distributed operation.
- **Backward Compatibility with Static Loading** — Existing `.properties` file loading must continue unchanged. Required per PROJECT.md.
- **REST API for Catalog Management** — HTTP endpoints for CRUD operations. Explicitly required in PROJECT.md.
- **Centralized Catalog Store** — Single source of truth for catalog configurations. Foundation for persistence.
- **In-Flight Query Handling** — Graceful handling of queries using a catalog when that catalog is dropped. Production requirement.

**Should have (competitive):**
- **Version History** — Audit trail for compliance, rollback capability. Trino lacks this.
- **Change Notification System** — Emit events for external systems to react. PROJECT.md requirement.
- **SQL Interface** — CREATE CATALOG and DROP CATALOG SQL commands. Matches Trino capability.
- **Catalog Property Encryption** — Secure handling of sensitive properties. Security differentiation.
- **Catalog Health Monitoring** — Verify connectivity at runtime. Trino lacks this.
- **Bulk Catalog Operations** — Atomic or batched multi-catalog changes.

**Defer (v2+):**
- UI for catalog management — Explicitly out of scope per PROJECT.md
- Advanced rollback capabilities — Deferred to future version
- Multi-tenant catalog isolation — Beyond initial scope

### Architecture Approach

**Summary from ARCHITECTURE.md:** The recommended architecture adds four new components that integrate with existing PrestoDB infrastructure while preserving backward compatibility. The system uses eventual consistency for catalog state across the cluster, with queries using catalog state at execution start (immutable binding) for safety.

**Major components:**
1. **DynamicCatalogStore** — Persistent catalog config storage with version history. Provides single source of truth across cluster. Configurable backing store (file, database, etcd).
2. **CatalogManagementAPI** — REST endpoints for CRUD operations. Uses existing Presto REST patterns (JAX-RS with Jakarta). Exposes `/v1/catalog/*` endpoints.
3. **CatalogNotificationService** — Propagates catalog changes to all cluster nodes. Uses gRPC streaming (preferred) or HTTP fallback.
4. **CatalogNotificationListener** — Handles catalog change events on worker nodes. Applies changes locally via ConnectorManager.

**Key integration points:**
- CatalogManager: Extend usage with registerCatalog(), removeCatalog() at runtime
- ConnectorManager: Reuse existing createConnection(), dropConnection()
- StaticCatalogStore: Backward compatibility for static mode
- DiscoveryService: Notification channel via existing Announcer mechanism

### Critical Pitfalls

**Top 5 from PITFALLS.md:**

1. **Catalog State Inconsistency Across Cluster Nodes** — Workers may not see new catalogs immediately, causing "catalog not found" errors. Avoid by implementing catalog change notification system with reliable delivery and eventual consistency. Address in Phase 2.

2. **Query Failures During Mid-Execution Catalog Removal** — Removing a catalog while queries are active causes failures, crashes, or hung tasks. Avoid by implementing catalog deprecation lifecycle: mark deprecated → wait for queries → then remove. Address in Phase 3.

3. **Race Conditions in Compound Catalog Operations** — Concurrent catalog operations cause duplicate registrations or orphaned connectors. Avoid by using distributed locking, implementing idempotent operations, and adding transaction boundaries. Address in Phase 1.

4. **Metadata Cache Staleness After Catalog Changes** — Cached metadata (table lists, schemas) becomes stale after catalog changes. Avoid by implementing cache invalidation on catalog events and cache versioning. Address in Phase 2.

5. **Connection Pool Leaks During Catalog Removal** — Database connections and resources not released on catalog removal. Avoid by defining Connector.close() lifecycle method and ensuring CatalogManager calls shutdown. Address in Phase 3.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Dynamic Catalog Loading Core

**Rationale:** Foundation must be established before any dynamic operations can work. This phase creates the data model and persistence layer that all subsequent phases depend on.

**Delivers:** DynamicCatalogStore interface with file-based implementation, read-only REST API endpoints (GET /v1/catalog, GET /v1/catalog/{name}), backward-compatible loading that falls back to static files.

**Addresses:** Table stakes features 4 (Backward Compatibility) and 6 (Centralized Catalog Store). Establishes foundation for all other features.

**Avoids:** Pitfall #3 (Race Conditions) — establishes proper locking and idempotent operations from the start.

**Uses:** etcd as backing store (or file-based fallback), existing CatalogManager patterns.

### Phase 2: Core Dynamic Operations

**Rationale:** With foundation in place, implement the core value proposition—runtime catalog registration and removal. This requires integration with ConnectorManager and establishing the notification system.

**Delivers:** POST /v1/catalog (create), DELETE /v1/catalog/{name} (drop), CatalogNotificationService for cluster-wide broadcasts, CatalogNotificationListener on workers.

**Addresses:** Table stakes features 1 (Runtime Registration), 2 (Runtime Removal), 3 (Cluster Sync), 5 (REST API). Differentiator #2 (Change Notification System).

**Avoids:** Pitfall #1 (Catalog State Inconsistency) — notification system ensures all nodes see changes. Pitfall #4 (Metadata Cache Staleness) — cache invalidation triggered on catalog events.

**Uses:** gRPC streaming for notifications, jetcd for etcd integration.

### Phase 3: Catalog Lifecycle Management

**Rationale:** Production deployments require safe catalog removal handling—queries using the catalog must complete gracefully. This phase addresses the complex coordination with query lifecycle.

**Delivers:** Catalog deprecation lifecycle (mark deprecated → wait for queries → remove), in-flight query tracking, graceful connector shutdown, PUT /v1/catalog/{name} (update via recreate).

**Addresses:** Table stakes feature 7 (In-Flight Query Handling). Critical for production stability.

**Avoids:** Pitfall #2 (Query Failures During Removal) — wait lifecycle prevents failures. Pitfall #5 (Connection Pool Leaks) — proper connector shutdown releases resources.

### Phase 4: Enterprise Features

**Rationale:** With core operations stable, add the differentiating features that provide competitive advantage over Trino and meet explicit PROJECT.md requirements.

**Delivers:** Version history (GET /v1/catalog/{name}/versions), rollback capability (POST /v1/catalog/{name}/rollback/{version}), SQL interface (CREATE/DROP CATALOG commands), change notifications via EventListener extension.

**Addresses:** Differentiators #1 (Version History), #3 (SQL Interface). PROJECT.md requirements for version history and notification system.

### Phase 5: Advanced Capabilities

**Rationale:** Final phase adds operational polish and security features that enterprises require.

**Delivers:** Secret management integration (vault, AWS Secrets Manager), catalog health monitoring (GET /v1/catalogs/{name}/health), bulk catalog operations, audit logging.

**Addresses:** Differentiators #4 (Secret Management), #5 (Health Monitoring), #6 (Bulk Operations).

### Phase Ordering Rationale

- **Dependency-driven:** Phase 1 creates the foundation that Phases 2-5 build upon. Notification system (Phase 2) requires mutation operations to notify about. Query handling (Phase 3) requires both notification and mutation working.
- **Architecture patterns:** ARCHITECTURE.md specifies sequential build order: Foundation → Core Operations → Cluster Sync → Advanced → Enterprise. This naturally groups related components.
- **Pitfall prevention:** Each phase explicitly addresses identified pitfalls. Phase 1 prevents race conditions by establishing proper patterns early. Phase 2 addresses inconsistency and cache staleness via notification system. Phase 3 addresses query failures and resource leaks.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 2 (Cluster Sync):** Complex integration with existing Discovery service. Needs research on how to extend Announcer mechanism for catalog-specific notifications. *Risk: MEDIUM — Presto's Discovery is well-documented but catalog-specific extension is novel.*
- **Phase 3 (Lifecycle Management):** Integration with QueryManager for tracking in-flight queries. Needs research on existing query lifecycle hooks. *Risk: MEDIUM — QueryManager is complex, existing dropConnection() has TODO.*

Phases with standard patterns (skip research-phase):
- **Phase 1 (Foundation):** File-based storage and REST APIs follow established Presto patterns.
- **Phase 4-5 (Enterprise/Advanced):** Version history and health monitoring are straightforward implementations once core is stable.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Based on official etcd docs, verified Presto dependency versions, clear integration points with existing code |
| Features | HIGH | Based on Trino reference implementation (official docs), Presto codebase analysis, PROJECT.md requirements |
| Architecture | HIGH | Deep analysis of existing CatalogManager, ConnectorManager, REST patterns. Build order has natural dependencies |
| Pitfalls | MEDIUM | Identified from Presto codebase analysis, Trino docs, distributed systems best practices. Some scenarios require implementation to verify |

**Overall confidence:** HIGH

### Gaps to Address

- **gap:** Integration with ConnectorManager.dropConnection() for graceful shutdown
  - **how to handle:** Existing code has TODO for handling in-flight transactions. Need to research query scheduling to understand when splits reference connector state.

- **gap:** gRPC notification scalability at 1000+ nodes
  - **how to handle:** Architecture doc identifies tree-based broadcast or gossip protocol as needed at scale. Defer optimization until benchmarking shows necessity.

- **gap:** Secret management integration specifics
  - **how to handle:** RESEARCH.md lists options (vault, AWS Secrets Manager) but doesn't prescribe. Defer to Phase 5 planning to select based on target deployment environment.

## Sources

### Primary (HIGH confidence)
- **etcd official docs** (https://etcd.io/docs/v3.6/) — API documentation, comparison with ZooKeeper/Consul, watch mechanism
- **Trino CREATE CATALOG** (https://trino.io/docs/current/sql/create-catalog.html) — Reference implementation for dynamic catalog
- **Trino DROP CATALOG** (https://trino.io/docs/current/sql/drop-catalog.html) — Query handling during catalog removal
- **Presto CatalogManager.java** — presto-main-base/src/main/java/com/facebook/presto/metadata/CatalogManager.java
- **Presto StaticCatalogStore.java** — presto-main-base/src/main/java/com/facebook/presto/metadata/StaticCatalogStore.java
- **Presto ConnectorManager.java** — presto-main-base/src/main/java/com/facebook/presto/connector/ConnectorManager.java

### Secondary (HIGH confidence)
- **jetcd GitHub** (https://github.com/etcd-io/jetcd) — Java client library, version 0.5.4
- **Presto pom.xml** — Existing dependencies: gRPC 1.75.0, Jackson 2.15.4, Airlift 0.227, Guava 32.1.0-jre
- **Trino Catalog Management Documentation** (https://trino.io/docs/current/admin/properties-catalog.html)

### Tertiary (MEDIUM confidence)
- Community discussions on PrestoDB GitHub issues related to dynamic catalog — needs validation during implementation

---

*Research completed: 2026-03-20*
*Ready for roadmap: yes*