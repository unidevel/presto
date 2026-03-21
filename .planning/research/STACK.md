# Stack Research

**Domain:** Dynamic Catalog Management for Distributed SQL Engines
**Researched:** 2026-03-20
**Confidence:** HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **etcd** | 3.6.x | Centralized catalog configuration store with watch API | Industry standard for distributed configuration (used by Kubernetes). Provides MVCC, linearizable reads, watch notifications for real-time updates, and built-in leader election. Natural fit for Presto's ecosystem (Facebook-born, like etcd). |
| **jetcd** | 0.5.4 | Java client for etcd | Official Java client library. Already partially present in Presto dependency tree. Provides synchronous and asynchronous API, watch support, and lease management. |
| **gRPC** | 1.75.0 | Inter-node notification system | Already deep in Presto codebase (v1.75.0). Use gRPC streaming for efficient push-based catalog change notifications between coordinator and workers. |
| **Jackson** | 2.15.4 | JSON serialization for catalog configs | Already in Presto stack. Use for serializing catalog properties to/from etcd. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **Guava** | 32.1.0-jre | Caching, concurrency utilities | For local catalog configuration caching on each node. |
| **SLF4J** | 2.0.16 | Logging | Already standard in Presto for logging catalog operations. |
| **Airlift** | 0.227 | HTTP server, discovery | For REST API implementation. Airlift's HttpServerBinder integrates with Jetty. |
| **JAX-RS (Jersey)** | - | RESTful APIs | Already in Airlift/Presto for catalog server REST endpoints. Use for CRUD API. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **Maven** | Build system | Already Presto's build system (v3.3.9+). |
| **Java 17** | Language | Presto's baseline. Required for modern concurrency features. |
| **TestNG** | Testing framework | Already Presto's testing standard. |

## Installation

```bash
# In Presto's pom.xml, add/update dependencies:

# etcd Java client (jetcd)
<dependency>
    <groupId>io.etcd</groupId>
    <artifactId>jetcd-core</artifactId>
    <version>0.5.4</version>
</dependency>

# gRPC (already present at 1.75.0)
# No additional dependency needed - already in stack

# For REST API (already present via Airlift)
# No additional dependency needed
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| **etcd** | ZooKeeper | Only if existing ZooKeeper infrastructure exists. etcd wins on: MVCC, watch API simplicity, REST HTTP gateway, cleaner Java client. |
| **etcd** | Consul | Only if already running Consul for service discovery. Consul's KV is secondary to its service mesh capabilities. Adds unnecessary complexity for catalog-only use. |
| **etcd** | PostgreSQL/MySQL | Only if strong RDBMS preference exists. Database lacks built-in watch/notification - would require polling or triggers. etcd's watch API is purpose-built for this. |
| **gRPC streaming** | Redis Pub/Sub | Redis is already in Presto (for caching). However, gRPC streaming provides: better typed contract, built-in connection pooling, no additional service needed. Use Redis if already deployed for other purposes. |
| **gRPC streaming** | Kafka | Overkill for catalog change notifications. Adds message persistence overhead not needed for this use case. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **ZooKeeper (new deployment)** | No MVCC, no linearizable reads by default, Java-centric curator recipes only, more complex API. | etcd 3.6.x |
| **Redis (as primary store)** | While Redis is already in Presto, using it as the primary catalog store adds another external dependency. It lacks native KV versioning that etcd provides. | Use Redis only for caching, etcd for primary store |
| **Database-backed config (new)** | Adding PostgreSQL/MySQL just for catalog config introduces unnecessary operational overhead and lacks native change notification. | etcd |
| **File-based with polling** | Static file approach defeats the purpose of dynamic catalog management. Would require complex file synchronization. | etcd with watch API |

## Stack Patterns by Variant

**If catalog changes are infrequent (< 1/minute):**
- Use simple etcd watch with full catalog reload on change
- Simpler implementation, acceptable latency

**If catalog changes are frequent (> 1/minute):**
- Use etcd watch with granular connector-level updates
- Implement local cache invalidation rather than full reload
- Consider batching notifications during rapid changes

**If operating outside Kubernetes:**
- Deploy etcd cluster manually (3-5 nodes recommended)
- Use etcd's built-in TLS and authentication
- Consider etcd-proxy for read scaling

**If operating inside Kubernetes:**
- Use etcd as StatefulSet or managed service (AWS etcd, GKE, AKS)
- Leverage Kubernetes operator for lifecycle management

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| jetcd-core 0.5.x | etcd 3.5.x, 3.6.x | Latest stable. Supports etcd v3 API. |
| gRPC 1.75.0 | Java 17 | Already in Presto stack. |
| Jackson 2.15.4 | Java 17 | Already in Presto stack. |
| Airlift 0.227 | Java 17 | Already in Presto stack. |

## Architecture Integration Points

### With Existing StaticCatalogStore
- Extend `StaticCatalogStore` to optionally load from etcd instead of files
- Keep backward compatibility: if etcd unavailable, fall back to file-based loading

### With Existing CatalogManager
- CatalogManager already thread-safe (ConcurrentHashMap)
- Add watch subscription to sync with etcd changes
- No changes needed to core CatalogManager

### With Existing CatalogServer
- Current CatalogServer handles metadata queries
- Add new endpoints for catalog CRUD via REST
- Reuse existing Thrift infrastructure for worker communication

### With ConnectorManager
- `createConnection()` already supports dynamic catalog addition
- Coordinate shutdown/startup during catalog changes
- Handle in-flight queries per PROJECT.md requirements

## Sources

- **etcd official docs** (https://etcd.io/docs/v3.6/) — API documentation, comparison with ZooKeeper/Consul, watch mechanism
- **jetcd GitHub** (https://github.com/etcd-io/jetcd) — Java client library, version 0.5.4
- **Presto pom.xml** — Existing dependencies: gRPC 1.75.0, Jackson 2.15.4, Airlift 0.227, Guava 32.1.0-jre
- **etcd vs ZooKeeper comparison** — Official etcd docs provide detailed feature comparison including MVCC, linearizable reads, watch APIs

---

*Stack research for: Dynamic Catalog Management in PrestoDB*
*Researched: 2026-03-20*