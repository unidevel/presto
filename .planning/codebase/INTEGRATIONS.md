# External Integrations

**Analysis Date:** 2026-03-20

## Data Storage

### Databases

| Database | Module | Connection | Client |
|----------|--------|------------|--------|
| MySQL | `presto-mysql` | JDBC | MySQL Connector/J |
| PostgreSQL | `presto-postgresql` | JDBC | PostgreSQL JDBC Driver |
| SQL Server | `presto-sqlserver` | JDBC | MS SQL Server JDBC |
| Oracle | `presto-oracle` | JDBC | Oracle JDBC Driver |
| Cassandra | `presto-cassandra` | Native | DataStax Driver |
| MongoDB | `presto-mongodb` | MongoDB Java Driver | MongoDB Driver |
| Redis | `presto-redis` | Jedis/Lettuce | Redis Client |

### Data Lakes

| Storage | Module | Protocol |
|---------|--------|----------|
| Hive Metastore | `presto-hive` | Thrift |
| HDFS | `presto-hdfs-core` | Hadoop FS |
| Amazon S3 | `presto-hive` | AWS SDK |
| Google Cloud Storage | `presto-hive` | GCS Client |
| Azure Blob Storage | `presto-hive` | ABFS |
| Apache Iceberg | `presto-iceberg` | Iceberg API |
| Delta Lake | `presto-delta` | Delta Lake API |
| Apache Hudi | `presto-hudi` | Hudi API |

### Streaming

| Source | Module | Protocol |
|--------|--------|----------|
| Kafka | `presto-kafka` | Kafka Consumer API |

### Search

| Source | Module | Client |
|--------|--------|--------|
| Elasticsearch | `presto-elasticsearch` | Elasticsearch Java Client |

## Authentication & Identity

### Authentication Providers

| Type | Implementation | Configuration |
|------|----------------|---------------|
| OAuth2 | `presto-main` server module | `OAuth2Authentication` |
| JWT | Built-in | `JsonWebTokenAuthenticator` |
| Password | File-based | `FilePasswordAuthenticator` |
| LDAP | `presto-password-authenticators` | LDAP module |
| Kerberos | Built-in | `KerberosAuthenticator` |
| SSL/TLS | Jetty | HTTPS configuration |

### Identity Providers
- **OAuth2/OIDC**: External identity providers (Okta, Auth0, Hydra)
- **LDAP**: Corporate directory integration
- **File-based**: Simple user/pass in JSON/CSV

## Monitoring & Observability

### Metrics
- **Prometheus** - Metrics collection and exposition
  - Port: 8080 (default)
  - Path: `/v1/metrics`
- **JMX** - Java Management Extensions
  - Connect via JConsole

### Logging
- **Framework:** SLF4J with Logback
- **Output:** Log files, console
- **Structured:** JSON format for machine parsing

### Tracing
- **OpenTelemetry** - Distributed tracing
  - Integration with Jaeger, Zipkin
  - Headers propagation

### Health Checks
- **Liveness:** `/v1/info/liveness`
- **Readiness:** `/v1/info/ready`

## CI/CD & Deployment

### Build System
- **Maven** - Java build and dependency management
- **CMake** - Native C++ build

### CI Pipeline
- **GitHub Actions** - `.github/workflows/`
- **Jenkins** - `Jenkinsfile` for legacy builds

### Container
- **Docker** - `docker/` directory
- **Docker Compose** - `docker-compose.yml`
- **Helm** - Kubernetes deployment charts

### Package Registries
- **Maven Central** - Java artifacts
- **GitHub Packages** - Internal packages

## Webhooks & Callbacks

### Incoming
- **Query API:** `/v1/statement` - Execute queries
- **Query Control:** `/v1/query/{queryId}` - Manage queries
- **Cluster Info:** `/v1/cluster` - Cluster metadata

### Outgoing
- **Event Listener** - Custom plugins for query events
- **Webhook Notifications** - Query completion, failures

## External Libraries & Services

### Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Airlift | 0.227 | Framework for services |
| Guice | 6.0.0 | Dependency injection |
| Netty | 4.2.10.Final | Async networking |
| Jetty | 12.0.29 | HTTP server |
| OkHttp | 4.12.0 | HTTP client |
| Jackson | 2.15.4 | JSON processing |
| Protobuf | 4.30.2 | Serialization |
| AWS SDK | 1.12.782 / 2.32.9 | AWS services |
| Hadoop | 3.4.1-1 | HDFS, YARN |
| Arrow | 18.3.0 | In-memory format |

### Testing Infrastructure
- **TestContainers** - Docker-based integration testing
- **MySQL Test Server** - Embedded MySQL for tests
- **TestingPrestoServer** - In-memory Presto for tests

## Environment Configuration

### Required Environment Variables
- `JAVA_HOME` - Java installation path
- Configuration via `etc/` directory

### Configuration Files
- **Node config:** `etc/node.properties`
- **Coordinator config:** `etc/config.properties`
- **JVM config:** `etc/jvm.config`
- **Log config:** `etc/log.properties`

### Secrets
- **Authentication:** In config files or environment
- **Database passwords:** In connector properties
- **Encryption:** Configurable secret providers

---

*Integration audit: 2026-03-20*