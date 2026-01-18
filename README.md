# Streaming S3 CSV Writer

A **production-grade Java 21+ library** for streaming massive CSV datasets directly to AWS S3. 
Designed for high-throughput environments where **constant memory usage** (O(1)) is non-negotiable.

---

## 🚀 Key Features

*   **Zero Temporary Files**: Streams directly to S3 via Multipart Uploads.
*   **Constant Memory Footprint**: Uses a fixed-size buffer (default 5MB) regardless of dataset size (1GB or 1TB).
*   **Intelligent Splitting**: Automatically splits files based on row count (e.g., 1M rows/file).
*   **ZIP Compression**: On-the-fly ZIP compression (single file or multi-file archives).
*   **Excel Compatible**: Native support for UTF-8 BOM to ensure correct Excel rendering.
*   **Virtual Thread Ready**: Non-blocking IO compatible design.

---

## 🏗️ Architecture

This library adheres to a **Strict Layered Architecture** to separate concerns and ensure reliability.

```mermaid
graph TD
    User["User Application"] -->|writeNextStrings()| Orch[Orchestration Layer]
    
    subgraph "S3CsvWriter (Orchestration & Format)"
        Orch -->|Manage Splits & State| SplitLogic{Row Threshold?}
        SplitLogic -->|No| CSV[Format Layer (OpenCSV)]
        SplitLogic -->|Yes| Rotate[Rotate File / ZipEntry]
        Rotate --> CSV
        
        CSV -->|Format Data| BOM[BOM Injector]
    end
    
    subgraph "AbstractS3Writer (Streaming & Storage)"
        BOM -->|Bytes| Buffer[In-Memory Buffer (5MB)]
        Buffer -->|Full?| S3Upload[S3 Multipart Upload]
        
        S3Upload -->|Upload Part 1| S3Bucket[(AWS S3)]
        S3Upload -->|Upload Part N| S3Bucket
        S3Upload -->|Complete| S3Bucket
    end
```

### Layer Responsibilities

1.  **Orchestration Layer** (`S3CsvWriter`): Manages file lifecycle, splitting logic, and multi-file coordination.
2.  **Format Layer** (OpenCSV + Zip): Handles CSV escaping, quoting, and ZIP entry management.
3.  **Streaming Layer** (`AbstractS3Writer`): Buffers data to optimize S3 costs (fewer requests) and network throughput.
4.  **Storage Layer** (AWS SDK): Handles the low-level S3 Multipart Upload API.

---

## 📦 Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.csvwriter.s3</groupId>
    <artifactId>csv-s3-writer-csv</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Requirements:**
*   Java 21+
*   AWS SDK for Java 2.x

---

## 🛠️ Usage Guide

### 1. Basic CSV Export
The simplest way to stream data.

```java
S3Client s3Client = S3Client.create();

try (S3CsvWriter writer = S3CsvWriter.builder()
        .client(s3Client)
        .bucket("my-reports")
        .filename("sales_report") // Becomes sales_report.csv
        .withBom(true)            // Recommended for Excel alignment
        .build()) {

    writer.addFile("sales_report", new String[]{"ID", "Product", "Amount"});
    
    // Stream millions of rows...
    writer.writeNextStrings("1", "Widget A", "29.99");
    writer.writeNextStrings("2", "Widget B", "199.50");
}
// Automatically closes and completes S3 upload
```

### 2. Automatic File Splitting
Split large datasets into multiple chunks (e.g., `data.csv`, `data_1.csv`, `data_2.csv`).

```java
S3CsvWriter.builder()
    .client(s3Client)
    .bucket("my-reports")
    .filename("large_export")
    .maxLinesPerFile(1_000_000) // New file every 1M rows
    .build();
```

*   **Behavior**:
    *   File 1: `large_export.csv` (Rows 1 - 1,000,000)
    *   File 2: `large_export_1.csv` (Rows 1,000,001 - 2,000,000)
    *   **Headers & BOM** are automatically re-applied to *every* split file.

### 3. ZIP Compression
Stream a compressed CSV file directly to S3 (`report.zip` containing `report.csv`).

```java
S3CsvWriter.builder()
    .client(s3Client)
    .bucket("my-reports")
    .filename("report")
    .compress(true)
    .build();
```

### 4. Multi-File ZIP Archive
Create a single ZIP archive containing multiple logical files (e.g., `export.zip` containing `users.csv` and `orders.csv`).

```java
try (S3CsvWriter writer = S3CsvWriter.builder()
        .client(s3Client)
        .bucket("my-reports")
        .filename("full_export") // Becomes full_export.zip
        .compress(true)
        .multiFile(true)         // Enable multi-entry support
        .build()) {

    // Entry 1: users.csv
    writer.addFile("users", new String[]{"ID", "Name"});
    writer.writeNextStrings("1", "Alice");

    // Entry 2: orders.csv (Implicitly closes users.csv entry)
    writer.addFile("orders", new String[]{"OrderID", "Total"});
    writer.writeNextStrings("500", "$99");
}
```

---

## ⚙️ Configuration Reference

| Builder Method | Default | Description |
| :--- | :--- | :--- |
| `bucket(String)` | **Required** | S3 Bucket name. |
| `filename(String)` | **Required** | Base filename. Extension added automatically. |
| `folderName(String)` | `""` (Root) | S3 prefix/folder path. |
| `bufferSize(int)` | `5MB` | Memory buffer size before flushing to S3. Min 5MB recommended by AWS. |
| `compress(boolean)` | `false` | Enable ZIP compression. |
| `maxLinesPerFile(int)` | `0` (No split) | Max rows per file (excluding header). |
| `withBom(boolean)` | `false` | Prepend UTF-8 Byte Order Mark (EF BB BF). |
| `multiFile(boolean)` | `false` | Enable adding multiple independent files to the same stream/ZIP. |

---

## 🧩 Edge Case Handling

We have explicitly tested and handled the following critical edge cases:

**1. UTF-8 BOM Placement**
*   **Behavior**: BOM is written exactly once at the start of *every* file (or Split file).
*   **Zip Mode**: BOM is written *inside* the Zip Entry, ensuring valid CSV extraction.

**2. Exact Split Boundaries**
*   **Scenario**: User sets limit 100, writes 100 rows.
*   **Result**: File closes efficiently. Next file only created if row 101 is written.

**3. Null Values**
*   **Behavior**: Nulls in input arrays are coerced to empty strings logic (OpenCSV default defaults).

**4. Empty Datasets**
*   **Behavior**: Creating a writer and closing it without data results in a valid file containing just the header (if provided) or empty file.

**5. Resource Cleanup**
*   **Safety**: Failing mid-stream triggers `abort()` which cancels the S3 Multipart Upload, preventing getting billed for incomplete storage fragments.

---

## 📈 Performance & Memory Logic

The library uses a **Fixed Buffer** (Array) approach:

1.  **Ingest**: `writeNextStrings()` formats CSV into an internal 8KB character buffer.
2.  **Encode**: Bytes flow into the main **5MB Byte Buffer**.
3.  **Flush**: When 5MB fills, it triggers `S3Client.uploadPart()`.
    *   This buffer is reused.
    *   **GC Pressure**: Low (very few object allocations per row).
    *   **Memory Usage**: ~5-6MB Total Heap, regardless of writing 1GB or 100GB.

---

## 🛡️ Security & Reliability

This library includes built-in protection against common vulnerabilities:

*   **Path Sanitization**: All filenames are strictly sanitized to prevent **Directory Traversal** attacks (e.g., inputs like `../../secret` become `secret.csv`).
*   **Strict Validation**: Configuration fails fast for invalid inputs (e.g., negative buffer sizes).
*   **Resource Cleanup**: Automatic `abort()` on S3 failures prevents billing for incomplete multipart uploads.
*   **Type Safety**: No raw `RuntimeException`s; specific `S3UploadException` and `FileProcessingException` are thrown.

---

## 🚦 Development Status

| Feature | Status | Notes |
| :--- | :--- | :--- |
| **Core Streaming** | ✅ Stable | Buffer logic verified (Constant O(1) Memory). |
| **CSV Format** | ✅ Stable | OpenCSV handles special chars (emojis, quotes). |
| **ZIP Support** | ✅ Stable | Multi-file and Single-file ZIPs verified. |
| **Integrity** | ✅ Verified | BOM, Splitting, and multipart logic tested. |
| **Security** | ✅ Verified | Path sanitization and strict validation active. |
| **Integration Test**| ⚠️ Disabled | Requires Docker/LocalStack (Currently mocked). |
