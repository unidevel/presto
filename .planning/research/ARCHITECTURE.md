# Architecture Patterns: Dynamic Catalog Management in PrestoDB

**Research Date:** 2026-03-20
**Domain:** Distributed SQL Query Engine - Dynamic Configuration Management

## Executive Summary

This document outlines how dynamic catalog management integrates with PrestoDB's distributed SQL query engine architecture. The existing PrestoDB codebase provides solid foundations for runtime catalog operations through the `ConnectorManager`, but lacks centralized storage, notification mechanisms, and REST APIs for dynamic management. The recommended architecture extends existing components with a catalog centralization store, notification system, and management API while preserving backward compatibility with static file-based catalog loading.

## Integration Context

### Existing PrestoDB Architecture

PrestoDB uses a coordinator-worker distributed architecture with the following key characteristics:

- **Coordinator**: Parses SQL, creates distributed query plans, schedules work to workers
- **Worker**: Executes query tasks, processes data, exchanges intermediate results
- **Discovery Service**: Worker registration and health tracking via Airlift's Announcer
- **Plugin Architecture**: SPI-based connectors, access control, event listeners

### Current Catalog Loading Model

The current catalog system operates entirely at startup:

```
StaticCatalogStore.loadCatalogs()
    ↓
ConnectorManager.createConnection()
    ↓
CatalogManager.registerCatalog()
    ↓
In-memory ConcurrentHashMap<String, Catalog>
```

Key observations:
1. Catalogs load synchronously at startup from `.properties` files
2. No runtime modification capability exists (CatalogManager is read-only after init)
3. Each node loads catalogs independently from local files
4. No synchronization mechanism across cluster nodes
5. Existing `ConnectorManager.dropConnection()` has a TODO for handling in-flight transactions

## Recommended Architecture

### Component Overview

The dynamic catalog management system adds four new components that integrate with existing PrestoDB infrastructure:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Presto Coordinator                                  │
│  ┌─────────────────┐   ┌─────────────────┐   ┌────────────────────────────────┐│
│  │ CatalogManager  │   │ ConnectorManager│   │    DynamicCatalogStore        ││
│  │   (existing)    │   │   (existing)    │   │    (NEW: Central Store)       ││
│  └────────┬────────┘   └────────┬────────┘   └───────────────┬──────────────┘│
│           │                     │                             │                │
│           └──────────┬──────────┴─────────────────────────────┘                │
│                      ↓                                                            │
│           ┌───────────────────────┐                                              │
│           │ CatalogManagementAPI  │  (NEW: REST API Layer)                      │
│           │   /v1/catalogs/*      │                                              │
│           └───────────┬───────────┘                                              │
└───────────────────────│──────────────────────────────────────────────────────────┘
                        │
                        │ Notification
                        ↓
┌──────────────────────────────────────────────────────────────────────────────────┐
│                           Worker Nodes                                            │
│  ┌─────────────────┐   ┌─────────────────┐   ┌────────────────────────────────┐ │
│  │ CatalogManager  │   │ ConnectorManager│   │ CatalogNotificationListener   │ │
│  │   (existing)    │   │   (existing)    │   │     (NEW: Event Handler)      │ │
│  └─────────────────┘   └─────────────────┘   └────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Component Boundaries

| Component | Responsibility | Communicates With | Boundary |
|-----------|---------------|-------------------|-----------|
| **CatalogManager** | In-memory catalog state, thread-safe registry | ConnectorManager, MetadataManager | Existing - no changes needed |
| **ConnectorManager** | Connector lifecycle (create/drop connections) | CatalogManager, all manager classes | Existing - already supports runtime ops |
| **DynamicCatalogStore** | Persistent catalog config storage, version history | CatalogManagementAPI, NotificationService | NEW - boundaries defined below |
| **CatalogManagementAPI** | REST endpoints for CRUD operations | DynamicCatalogStore, clients | NEW - boundaries defined below |
| **CatalogNotificationService** | Propagates catalog changes to cluster | All nodes, DynamicCatalogStore | NEW - boundaries defined below |
| **CatalogNotificationListener** | Handles catalog change events on workers | CatalogNotificationService | NEW - boundaries defined below |

#### DynamicCatalogStore (NEW)

**Location:** `presto-main-base/src/main/java/com/facebook/presto/metadata/dynamic/`

**Responsibilities:**
- Persist catalog configurations to a backing store (configurable: file, database, etc.)
- Maintain version history for audit and rollback
- Provide single source of truth for catalog configurations across cluster

**Public API:**
```java
public interface CatalogStore {
    // Store operations
    void saveCatalog(CatalogConfiguration config);
    void updateCatalog(String catalogName, CatalogConfiguration config);
    void deleteCatalog(String catalogName);

    // Retrieval operations
    Optional<CatalogConfiguration> getCatalog(String catalogName);
    List<CatalogConfiguration> getAllCatalogs();
    List<CatalogConfiguration> getCatalogVersions(String catalogName);

    // Watch operations
    void addListener(CatalogChangeListener listener);
}
```

**Data Model:**
```java
public class CatalogConfiguration {
    private String catalogName;
    private String connectorName;
    private Map<String, String> properties;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
}
```

#### CatalogManagementAPI (NEW)

**Location:** `presto-main/src/main/java/com/facebook/presto/server/dynamic/`

**Responsibilities:**
- Expose REST endpoints for catalog CRUD operations
- Validate catalog configurations before persistence
- Trigger notification on catalog changes
- Handle authentication and authorization

**REST Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| GET | /v1/catalog | List all catalogs |
| GET | /v1/catalog/{name} | Get catalog details |
| POST | /v1/catalog | Create new catalog |
| PUT | /v1/catalog/{name} | Update catalog |
| DELETE | /v1/catalog/{name} | Delete catalog |
| GET | /v1/catalog/{name}/versions | Get version history |
| POST | /v1/catalog/{name}/rollback/{version} | Rollback to version |

**Integration Points:**
- Uses existing Presto REST patterns (JAX-RS with Jakarta)
- Depends on `ConnectorManager` for connector factory validation
- Uses Guice for dependency injection (following existing patterns)

#### CatalogNotificationService (NEW)

**Location:** `presto-main-base/src/main/java/com/facebook/presto/metadata/dynamic/notification/`

**Responsibilities:**
- Notify all cluster nodes of catalog changes
- Handle node failures gracefully during notification
- Provide eventual consistency model for catalog state

**Notification Types:**
```java
public enum CatalogChangeType {
    CATALOG_CREATED,
    CATALOG_UPDATED,
    CATALOG_DELETED
}

public class CatalogChangeEvent {
    private CatalogChangeType changeType;
    private String catalogName;
    private CatalogConfiguration configuration;
    private long version;
    private Instant timestamp;
}
```

**Delivery Mechanisms (in priority order):**
1. **gRPC streaming**: Low-latency, bidirectional (preferred for Presto-to-Presto)
2. **HTTP long-polling**: Fallback, uses existing HTTP infrastructure
3. **Discovery service extension**: Leverage existing Announcer mechanism

#### CatalogNotificationListener (NEW)

**Location:** `presto-main-base/src/main/java/com/facebook/presto/connector/dynamic/`

**Responsibilities:**
- Receive catalog change notifications on worker nodes
- Apply catalog changes locally via ConnectorManager
- Handle notification ordering and deduplication

## Data Flow

### Catalog Creation Flow

```
Client Request: POST /v1/catalog
        ↓
CatalogManagementAPI.validate()
        ↓
DynamicCatalogStore.saveCatalog()
        ↓
CatalogNotificationService.publish(CATALOG_CREATED)
        ↓
┌──────────────────────┬──────────────────────┐
│   Coordinator        │      Workers         │
├──────────────────────┼──────────────────────┤
│ ConnectorManager     │ CatalogNotifListener │
│ .createConnection()  │ .onCatalogCreated()  │
│ CatalogManager       │ ConnectorManager     │
│ .registerCatalog()   │ .createConnection()  │
└──────────────────────┴──────────────────────┘
        ↓
CatalogManagementAPI returns success
```

### Catalog Deletion Flow (with query handling)

```
Client Request: DELETE /v1/catalog/{name}
        ↓
CatalogManagementAPI.validate()
        ↓
Check in-flight queries using catalog
        ↓ (if queries exist)
Wait for query completion OR force-terminate (configurable)
        ↓
DynamicCatalogStore.deleteCatalog()
        ↓
CatalogNotificationService.publish(CATALOG_DELETED)
        ↓
┌──────────────────────┬──────────────────────┐
│   Coordinator        │      Workers         │
├──────────────────────┼──────────────────────┤
│ Wait for queries     │ CatalogNotifListener │
│ ConnectorManager     │ .onCatalogDeleted()  │
│ .dropConnection()   │ ConnectorManager     │
│ CatalogManager      │ .dropConnection()    │
│ .removeCatalog()    │ CatalogManager       │
└──────────────────────┴──────────────────────┘
        ↓
CatalogManagementAPI returns success
```

### Data Flow Summary

| Operation | Trigger | Storage | Notification | Local Application |
|-----------|---------|---------|--------------|-------------------|
| Create | REST API | DynamicCatalogStore.saveCatalog() | broadcast | ConnectorManager.createConnection() |
| Update | REST API | DynamicCatalogStore.updateCatalog() | broadcast | dropConnection() + createConnection() |
| Delete | REST API | DynamicCatalogStore.deleteCatalog() | broadcast | ConnectorManager.dropConnection() |
| Startup | Server init | DynamicCatalogStore.loadAll() | none | StaticCatalogStore path (backward compat) |

## Build Order and Dependencies

### Phase 1: Foundation (Prerequisite for all other work)

1. **DynamicCatalogStore interface and file-based implementation**
   - No external dependencies
   - Provides persistence foundation
   - Must be completed first

2. **CatalogManagementAPI - Read operations**
   - GET /v1/catalog, GET /v1/catalog/{name}
   - Depends on DynamicCatalogStore
   - Provides visibility into catalog state

**Rationale:** Establish the data model and read path before adding mutation complexity.

### Phase 2: Core Dynamic Operations

3. **CatalogManagementAPI - Create operation**
   - POST /v1/catalog
   - Depends on: ConnectorManager (for validation), DynamicCatalogStore
   - Integrates with existing ConnectorManager.createConnection()

4. **ConnectorManager integration for dynamic loading**
   - Ensure thread-safe createConnection() at runtime
   - Already exists but needs verification under concurrent load

**Rationale:** Basic create operations establish the core pattern before implementing update/delete.

### Phase 3: Cluster Synchronization

5. **CatalogNotificationService (coordinator side)**
   - Broadcast mechanism design
   - Depends on: CatalogManagementAPI mutations

6. **CatalogNotificationListener (worker side)**
   - Receive and apply changes locally
   - Depends on: CatalogNotificationService

**Rationale:** Notification system requires the mutation operations to notify about.

### Phase 4: Advanced Operations

7. **CatalogManagementAPI - Update operation**
   - PUT /v1/catalog/{name}
   - Depends on: create + delete (recreate pattern)

8. **CatalogManagementAPI - Delete operation**
   - DELETE /v1/catalog/{name}
   - Depends on: Query lifecycle integration

9. **Query lifecycle integration**
   - Wait for in-flight queries before catalog removal
   - Depends on: QueryManager access

**Rationale:** Update and delete require more complex coordination with existing queries.

### Phase 5: Enterprise Features

10. **Version history and rollback**
    - GET /v1/catalog/{name}/versions
    - POST /v1/catalog/{name}/rollback/{version}
    - Depends on: All CRUD operations

11. **Database-backed CatalogStore (optional)**
    - Production-grade persistence
    - Depends on: File-based implementation

**Rationale:** Version history requires stable CRUD to build upon.

## Architectural Considerations

### Backward Compatibility

The architecture preserves full backward compatibility:

1. **Static file loading continues unchanged**: StaticCatalogStore remains the default
2. **Dual-mode operation**: System can load from files, central store, or both
3. **Gradual migration**: New catalogs can use central store while existing use files
4. **Configuration option**: `catalog.loading.mode=static|dynamic|hybrid`

### Consistency Model

The notification system uses **eventual consistency**:

- Catalog changes are immediately visible on the originating coordinator
- Worker nodes receive notifications asynchronously
- Brief window where catalog state differs across nodes
- For query safety: queries use catalog state at execution start (immutable binding)

### Query Safety During Catalog Changes

| Scenario | Approach |
|----------|----------|
| Catalog deleted while queries run | Wait for queries using catalog to complete |
| Catalog updated while queries run | Queries use old connector until restart |
| Worker joins during catalog change | Pull latest catalog state on startup |

### Failure Handling

| Failure Scenario | Mitigation |
|------------------|------------|
| Notification delivery fails | Retry with exponential backoff |
| Node offline during change | Catch up on reconnection via state pull |
| Central store unavailable | Fall back to local catalog state |
| Concurrent catalog changes | Optimistic locking with version field |

## Scalability Considerations

| Concern | At 10 nodes | At 100 nodes | At 1000 nodes |
|---------|-------------|--------------|---------------|
| Notification overhead | gRPC unicast acceptable | gRPC broadcast with tree structure | Hybrid: gossip + direct |
| Catalog state memory | ~1MB per node | ~1MB per node | ~1MB per node |
| Store write throughput | Single writer sufficient | Single writer with caching | Leader election for writes |
| API latency | <10ms | <10ms | <50ms (multi-hop) |

## Key Integration Points Summary

| Existing Component | Integration Point | Direction |
|-------------------|-------------------|-----------|
| CatalogManager | registerCatalog(), removeCatalog() | Extend usage |
| ConnectorManager | createConnection(), dropConnection() | Reuse existing |
| StaticCatalogStore | loadCatalogs() | Backward compat |
| DiscoveryService (Announcer) | Service announcement | Notification channel |
| EventListener framework | Extension point | Pattern reference |
| REST resource pattern | NodeResource, QueryResource | Implementation template |

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Component boundaries | HIGH | Based on deep analysis of existing ConnectorManager and CatalogManager |
| Data flow | HIGH | Uses existing Presto patterns verified in code |
| Build order | HIGH | Dependencies naturally sequential |
| Scalability | MEDIUM | gRPC notification pattern requires verification at scale |
| Query safety | MEDIUM | Existing dropConnection() has TODO - needs implementation |

## Sources

- PrestoDB source code analysis: CatalogManager.java, ConnectorManager.java, StaticCatalogStore.java
- Existing REST resource patterns: NodeResource.java, QueryResource.java
- Discovery/Announcement mechanism: PrestoServer.java
- Event listener framework: EventListenerManager.java

---

*Architecture research completed: 2026-03-20*