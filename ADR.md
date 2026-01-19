# Architecture Decision Records (ADR) - Master Index

**Project**: S3 Streaming CSV Writer
**Version**: 1.0.0-SNAPSHOT
**Date**: 2026-01-18

This document captures the significant architectural decisions made during the development of the library. It serves as "The Why" behind the code.

---

## 1. Core Principles

### ADR-001: Strict Layered Architecture
**Decision**: The library is divided into four strict responsibility layers:
1.  **Orchestration** (`S3CsvWriter`): Manages state, splitting, and lifecycle.
2.  **Format** (OpenCSV): Handles CSV standards compliance (quoting, escaping).
3.  **Streaming** (`AbstractS3Writer`): Manages O(1) byte buffering and "Upload Part" triggers.
4.  **Storage** (AWS SDK): Low-level API interaction.

**Rationale**: Separation of concerns allows isolated testing of Format logic (Mocking storage) and Storage logic (Mocking format).

### ADR-002: Constant Memory (O(1)) Strategy
**Decision**: No dynamic resizing buffers (`ByteArrayOutputStream`) are allowed. We use a fixed `ByteBuffer` (default 5MB).
**Rationale**: Essential for running in memory-constrained environments (Lambda/K8s) where large datasets (GBs) would otherwise cause OutOfMemoryErrors.

### ADR-003: No Temporary Files
**Decision**: Zero reliance on local disk storage.
**Rationale**: Many cloud environments (Lambda, Fargate) have ephemeral or slow local storage. Streaming directly to S3 allows "Serverless" compatibility.

---

## 2. Security & Reliability

### ADR-004: Defensive Path Sanitization
**Decision**: All user-provided filenames are regex-sanitized (`[^a-zA-Z0-9._-]`). Directory traversal sequences (`../`) are stripped.
**Rationale**: Prevents malicious actors from writing files outside the intended S3 prefix/folder.

### ADR-005: Fail-Safe Resource Cleanup
**Decision**: Any exception during the write process triggers an implicit `abort()` call on the S3 Multipart Upload.
**Rationale**: S3 charges for incomplete multipart upload storage. Automatic cleanup prevents "Zombie Parts" and unexpected billing.

---

## 3. Technology Stack

### ADR-006: Java 21 & Virtual Threads
**Decision**: Target Java 21+. Use `ReentrantLock` instead of `synchronized` for I/O critical sections.
**Rationale**: Virtual Threads (Project Loom) provide high throughput for I/O-bound tasks. `synchronized` can pin the carrier thread; explicit locks do not.

### ADR-007: OpenCSV 5.9
**Decision**: Use OpenCSV for the CSV formatting engine.
**Rationale**: Reinventing CSV parsing/writing is error-prone (RFC 4180 compliance). OpenCSV is the battle-tested industry standard.

---

## 4. Implementation Details

### ADR-008: Composition over Inheritance
**Decision**: `S3CsvWriter` *contains* an `AbstractS3Writer`, it does not *extend* it.
**Rationale**: A single CSV logical stream might need multiple physical S3 streams (due to file splitting). Composition allows swapping the underlying writer at runtime seamlessly.

### ADR-009: Row-Based Splitting
**Decision**: File splitting is triggered by **Row Count**, not Byte Size.
**Rationale**: Byte-size splitting requires buffering entire rows to measure them, conflicting with the O(1) memory goal. Row counting is efficient and deterministic.

---

## 5. Testing Strategy

### ADR-010: Comprehensive Scenario Testing
**Decision**: Testing must cover cross-layer failures (e.g., specific S3 errors occurring mid-CSV-write).
**Rationale**: Unit tests alone miss integration bugs. We simulate Storage failures to verify Orchestration recovery logic.

---

**Status**: All decisions are **ACCEPTED** and **IMPLEMENTED**.
