# Pitfalls Research

**Domain:** Dynamic Catalog Management in Distributed SQL Query Engines
**Researched:** 2026-03-20
**Confidence:** MEDIUM

## Critical Pitfalls

### Pitfall 1: Catalog State Inconsistency Across Cluster Nodes

**What goes wrong:** After adding a catalog via the coordinator, workers may not immediately see the new catalog. Queries submitted to workers immediately after catalog creation fail with "catalog not found" errors, even though the catalog was successfully registered on the coordinator.

**Why it happens:** The existing CatalogManager stores catalog state in-memory on each node. When a catalog is added dynamically, the coordinator updates its local state but there's no mechanism to propagate this change to workers. Workers continue using their stale in-memory catalog list until they are restarted or receive an explicit notification. The existing CatalogServer provides metadata lookup but doesn't handle catalog lifecycle synchronization.

**How to avoid:** Implement a catalog change notification system that broadcasts catalog add/remove events to all nodes. Use a reliable delivery mechanism (gRPC streaming or database-backed polling) that guarantees eventual consistency. Workers should subscribe to catalog change events and update their local CatalogManager accordingly. Consider adding a version number to catalog state and having workers check for updates periodically as a fallback.

**Warning signs:**
- Adding a catalog works but queries fail with "Catalog 'X' does not exist" on workers
- Worker logs show queries referencing catalogs that were added after worker startup
- Inconsistent query results between coordinator and worker execution paths

**Phase to address:** Phase 2 (Catalog Change Notification System)

---

### Pitfall 2: Query Failures During Mid-Execution Catalog Removal

**What goes wrong:** Removing a catalog while queries are actively using it causes query failures, task crashes, or hung tasks. Workers may hold references to connectors that have been unloaded, leading to NullPointerException or connection errors.

**Why it happens:** The existing CatalogManager.removeCatalog() immediately removes the catalog from the ConcurrentHashMap without checking for active usage. In a distributed system, queries in flight on workers may have already scheduled splits against tables in the catalog being removed. There's no coordination between catalog removal and query lifecycle.

**How to avoid:** Implement a catalog deprecation lifecycle: first mark the catalog as "deprecated" (new queries cannot use it), wait for existing queries to complete (with a configurable timeout), then actually remove the catalog. Add usage tracking to monitor which catalogs are in active use. On workers, implement graceful connector shutdown that waits for pending operations before cleaning up resources.

**Warning signs:**
- Query failures increase after catalog removal operations
- Worker logs show NullPointerException or connection errors after catalog changes
- Tasks enter hung state after catalog removal

**Phase to address:** Phase 3 (Catalog Lifecycle Management)

---

### Pitfall 3: Race Conditions in Compound Catalog Operations

**What goes wrong:** Creating a catalog involves multiple steps: validating connector exists, creating connector instance, registering in CatalogManager. If two operations run concurrently or if there's a failure mid-operation, the system can end up with partially created catalogs, duplicate registrations, or orphaned connectors.

**Why it happens:** The existing CatalogManager.registerCatalog() uses synchronized but only protects the final put operation. The compound operation of connector creation + registration isn't atomic from the perspective of the entire system. Concurrent requests could create duplicate connectors or cause the "Catalog is already registered" error when it shouldn't occur.

**How to avoid:** Use distributed locking for catalog operations (e.g., using the centralized catalog store for lock coordination). Implement idempotent operations where possible - if a catalog already exists with the same configuration, treat it as success rather than failure. Add transaction boundaries around multi-step catalog operations with rollback capability.

**Warning signs:**
- Intermittent "Catalog already registered" errors during normal operations
- Connector instances created multiple times for the same catalog
- Inconsistent state between CatalogManager and connector factory

**Phase to address:** Phase 1 (Dynamic Catalog Loading Core)

---

### Pitfall 4: Metadata Cache Staleness After Catalog Changes

**What goes wrong:** After adding or updating a catalog, queries use stale metadata (table list, schema definitions, etc.). Users see old table definitions, missing tables, or incorrect metadata that was cached before the catalog change.

**Why it happens:** Presto caches metadata extensively for performance. Connectors and MetadataResolver instances cache table lists, schema information, and type mappings. When a catalog is dynamically added, there's no mechanism to invalidate these caches across the cluster. The existing metadata caching doesn't have invalidation hooks for catalog lifecycle events.

**How to avoid:** Implement a cache invalidation strategy that triggers on catalog change events. Consider using cache versioning - when a catalog changes, increment its version number and have caching layers check version before returning cached data. Alternatively, implement a time-based invalidation with short TTL for catalog-related metadata. Ensure that new catalog registrations clear any relevant caches.

**Warning signs:**
- New tables in a recently added catalog not showing up in queries
- Deleted tables still appearing in queries
- Schema changes not reflected in query results

**Phase to address:** Phase 2 (Catalog Change Notification System)

---

### Pitfall 5: Connection Pool Leaks During Catalog Removal

**What goes wrong:** When removing a catalog, database connections and other resources held by the connector are not properly released. Over time with multiple catalog add/remove cycles, connection pools fill up, new connections fail to acquire, and the cluster becomes unresponsive.

**Why it happens:** Connector shutdown requires proper lifecycle management - closing connection pools, stopping background threads, releasing file handles. The existing ConnectorFactory.create() method returns a Connector but there's no formal shutdown contract. Connectors may not implement proper cleanup, or cleanup may not be triggered during dynamic removal.

**How to avoid:** Define a Connector.close() or shutdown() method in the SPI that all connectors must implement. Ensure CatalogManager.removeCatalog() calls this shutdown method. Add resource tracking to monitor connection pool usage and alert on potential leaks. Consider using reference counting to ensure connectors aren't prematurely garbage collected while still in use.

**Warning signs:**
- Connection pool metrics show increasing connections over time
- New catalog additions fail with "Too many connections"
- Worker out-of-memory errors after many catalog add/remove cycles

**Phase to address:** Phase 3 (Catalog Lifecycle Management)

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Skip connector shutdown on removal | Faster catalog removal, simpler code | Connection leaks, resource exhaustion | Never - must implement proper cleanup |
| Use in-memory-only catalog store | Simpler implementation, no external dependencies | Data loss on cluster restart, no single source of truth | Only for development/testing |
| No catalog version tracking | Avoids implementing versioning logic | Cannot detect stale catalogs, impossible to rollback | Never - version tracking is essential |
| Synchronous catalog operations on coordinator | Simpler to implement | Blocks API responses, potential timeouts | Never - use async operations with callbacks |
| Skip notification delivery verification | Avoids implementing acknowledgment logic | Silent failures, inconsistent state | Never - must verify notification delivery |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Centralized catalog store (database) | Using same database as catalog data source | Use separate database instance or namespace for catalog store |
| gRPC for inter-node communication | Not handling node restarts or network partitions | Implement reconnection logic with exponential backoff |
| Connector configuration validation | Validating only on coordinator | Validate on all nodes that will load the connector |
| External secret management | Storing secrets in catalog properties files | Reference secrets from external secret store (vault, AWS Secrets Manager) |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Catalog list fetch blocking query planning | Queries hang during planning phase | Cache catalog list, use async fetching | At 50+ catalogs |
| Large catalog configurations blocking startup | Cluster startup takes minutes | Lazy load connector configurations | With catalog configs > 1MB each |
| Frequent catalog changes causing cache thrashing | High CPU, low cache hit rate | Batch catalog changes, use longer cache TTL | With >10 catalog changes per minute |
| Synchronous catalog operations on hot path | API timeouts, high latency | Move catalog operations to background threads | Under load with catalog API traffic |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Allowing catalog configuration without authentication | Unauthorized catalog creation/ modification | Require authentication for all catalog management APIs |
| Storing connector credentials in plain text | Credential exposure in logs, memory dumps | Use encrypted secret store, never log credentials |
| No authorization check on catalog operations | Users can modify catalogs they shouldn't access | Implement access control checks before catalog operations |
| No audit logging for catalog changes | Cannot track who made changes, compliance issues | Log all catalog operations with user identity and timestamp |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No feedback during catalog creation | Users don't know if operation succeeded | Show progress, return operation ID for status tracking |
| Silent catalog creation failures | Users assume catalog exists when it doesn't | Return detailed error messages, suggest fixes |
| No rollback capability | Bad catalog config breaks cluster, requires restart | Implement rollback to previous catalog version |
| No catalog health status | Cannot tell if catalog is working without running query | Show connector health status in API response |

---

## "Looks Done But Isn't" Checklist

- [ ] **Catalog creation:** Often missing verification that connector actually loaded — verify by querying a table from the new catalog
- [ ] **Catalog removal:** Often missing connector shutdown verification — verify connection pools are empty
- [ ] **Notification system:** Often missing delivery confirmation — verify workers received and applied changes
- [ ] **API error handling:** Often missing specific error codes — verify all error paths return useful messages
- [ ] **Backward compatibility:** Often missing testing with static catalogs — verify existing deployments still work

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Catalog state inconsistency | HIGH | Restart all workers, or implement forced catalog refresh API |
| Query failures during removal | MEDIUM | Implement graceful catalog deprecation (wait for queries) |
| Connection pool leaks | MEDIUM | Restart affected workers, then fix cleanup code |
| Stale metadata cache | LOW | Call cache invalidation API, or wait for TTL expiration |
| Partial catalog creation | MEDIUM | Clean up failed catalog entry, retry with idempotent operation |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Catalog State Inconsistency | Phase 2: Catalog Change Notification System | Add catalog, verify it appears on all workers within 5 seconds |
| Query Failures During Removal | Phase 3: Catalog Lifecycle Management | Remove catalog with active query, verify query completes successfully |
| Race Conditions | Phase 1: Dynamic Catalog Loading Core | Concurrent catalog operations should all succeed |
| Metadata Cache Staleness | Phase 2: Catalog Change Notification System | Add catalog, immediately query for new tables |
| Connection Pool Leaks | Phase 3: Catalog Lifecycle Management | Add and remove catalog 10 times, verify no connection growth |

---

## Sources

- PrestoDB CatalogManager implementation (`presto-main-base/src/main/java/com/facebook/presto/metadata/CatalogManager.java`)
- PrestoDB CatalogServer (`presto-main-base/src/main/java/com/facebook/presto/catalogserver/CatalogServer.java`)
- PrestoDB ConnectorFactory SPI (`presto-spi/src/main/java/com/facebook/presto/spi/connector/ConnectorFactory.java`)
- Trino Administration Documentation (https://trino.io/docs/current/admin.html)
- PrestoDB Connector Documentation (https://www.prestodb.io/docs/current/connector.html)
- Distributed configuration management best practices
- Community discussions on PrestoDB GitHub issues related to dynamic catalog

---

*Pitfalls research for: Dynamic Catalog Management in Distributed SQL Query Engines*
*Researched: 2026-03-20*