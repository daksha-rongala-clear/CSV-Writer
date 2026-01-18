# Architecture Decision Records

## ADR-001: Virtual Thread Safety

**Status**: Accepted

**Context**: Java 21 introduces virtual threads for high-throughput concurrency. However, virtual threads can pin carrier threads when using `synchronized` on long-running I/O operations, degrading performance.

**Decision**: Use `ReentrantLock` instead of `synchronized` for critical sections in `AbstractS3Writer`.

**Consequences**:
- ✅ Virtual threads avoid carrier thread pinning during S3 uploads
- ✅ Better performance with Virtual Threads
- ⚠️ Slightly more verbose code than `synchronized`
- ⚠️ Developers must remember to use `lock.lock()` and `lock.unlock()` in try-finally blocks

**Rationale**: The library is designed for enterprise-scale data exports where virtual threads will be common. Avoiding carrier thread pinning is critical for throughput.

---

## ADR-002: Composition over Inheritance for CSV Writer

**Status**: Accepted

**Context**: The CSV writer needs to manage multiple underlying S3 writers (for file splitting in non-ZIP mode) and wrap S3 streams with `ZipOutputStream` (for ZIP mode).

**Decision**: `S3CsvWriter` will use composition, containing `AbstractS3Writer` instances, rather than extending it.

**Consequences**:
- ✅ CSV writer can manage multiple S3Writers for file splitting
- ✅ Flexible wrapping of S3 streams with ZIP/format-specific streams
- ✅ Clearer separation of Storage vs. Format concerns
- ⚠️ Requires delegation methods to expose S3Writer interface

**Rationale**: Inheritance would make multi-writer scenarios (file splitting) and stream wrapping (ZIP) extremely difficult. Composition provides the flexibility needed for complex scenarios.

---

## ADR-003: No Temporary Files

**Status**: Accepted

**Context**: Streaming large datasets requires either temporary files or in-memory buffering.

**Decision**: All streaming uses in-memory buffers that flush to S3 multipart upload parts. No temporary files will be created.

**Consequences**:
- ✅ Satisfies "no temporary files" constraint
- ✅ Constant memory usage (buffer size is fixed at 5MB default)
- ✅ Simpler failure recovery (just abort multipart upload)
- ✅ Works in containerized environments without disk access
- ⚠️ Requires proper buffer size tuning for performance

**Rationale**: Temporary files add complexity (cleanup, disk space, permissions) and violate the project's explicit constraints.

---

## ADR-004: Row-Based Splitting Only

**Status**: Accepted

**Context**: File splitting can be based on row count or file size.

**Decision**: File splitting is based solely on row count, not file size.

**Consequences**:
- ✅ Deterministic splitting behavior
- ✅ Memory-efficient (no need to buffer to measure bytes)
- ✅ Simple implementation
- ⚠️ Users must estimate CSV size based on expected row count

**Rationale**: Size-based splitting requires buffering to measure bytes before writing, which conflicts with constant memory usage. Row-based splitting is deterministic and efficient.

---

## ADR-005: UTF-8 BOM Per CSV File

**Status**: Accepted

**Context**: UTF-8 BOM (Byte Order Mark) helps Excel and other tools correctly identify file encoding.

**Decision**: When `withBom=true`, the BOM is written once per CSV file:
- Single file: BOM written at the start
- Multi-file ZIP: BOM written at the start of each ZIP entry

**Consequences**:
- ✅ Correct Excel compatibility per file
- ✅ BOM is not duplicated mid-file during splitting
- ✅ Clear semantics: one BOM per logical file

**Rationale**: BOM is a file-level marker, not a stream-level marker. Each logical CSV file should have its own BOM.

---

## ADR-006: Centralized Dependency Management

**Status**: Accepted

**Context**: Multi-module Maven projects can have version conflicts if dependencies are managed inconsistently.

**Decision**: All dependency versions are defined in the parent POM's `<properties>` section and managed in `<dependencyManagement>`.

**Consequences**:
- ✅ Consistent versions across all modules
- ✅ Single source of truth for dependency versions
- ✅ Easier upgrades (change version in one place)
- ✅ Follows Maven best practices

**Rationale**: This is a Staff Engineer standard for multi-module projects and prevents version conflicts.

---

## ADR-007: SLF4J for Logging

**Status**: Accepted

**Context**: The library needs logging for observability and debugging.

**Decision**: Use SLF4J API for logging. Concrete implementation (Logback, Log4j2) is left to the consuming application.

**Consequences**:
- ✅ Library remains logging-implementation-agnostic
- ✅ Consuming applications can choose their preferred logging framework
- ✅ SLF4J is the industry standard facade
- ✅ Logback is used only for tests

**Rationale**: Libraries should never force a logging implementation on consumers. SLF4J provides the abstraction layer.

---

## ADR-008: Builder Pattern for Public API

**Status**: Accepted

**Context**: The CSV writer has many optional configuration parameters.

**Decision**: Use a Builder pattern for `S3CsvWriter` construction, backed by `S3WriterConfig`.

**Consequences**:
- ✅ Fluent, readable API
- ✅ Optional parameters with sensible defaults
- ✅ Type-safe construction
- ⚠️ Slightly more boilerplate than simple constructors

**Rationale**: Builder pattern is the cleanest way to handle multiple optional parameters and provides excellent readability for library consumers.
