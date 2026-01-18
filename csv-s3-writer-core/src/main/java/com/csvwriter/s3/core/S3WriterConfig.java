package com.csvwriter.s3.core;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Configuration for S3 streaming writers.
 * Uses mutable configuration with chain = true per Staff Engineer standards.
 */
@Data
@Accessors(chain = true)
public class S3WriterConfig {

    /**
     * S3 bucket name (required)
     */
    private String bucket;

    /**
     * S3 folder/prefix (optional, can be null or empty)
     */
    private String folderName;

    /**
     * Base filename without extension (required)
     */
    private String filename;

    /**
     * Buffer size in bytes for streaming to S3.
     * Default: 5MB (5 * 1024 * 1024)
     */
    private int bufferSize = 5 * 1024 * 1024;

    /**
     * Enable ZIP compression for output files.
     * Default: false
     */
    private boolean compress;

    /**
     * Enable multiple files inside a single ZIP archive.
     * Only applicable when compress = true.
     * Default: false
     */
    private boolean multiFile;

    /**
     * Maximum number of data rows per file (excluding header).
     * When this limit is reached:
     * - Non-ZIP mode: Creates a new S3 object with suffix (_1, _2, etc.)
     * - ZIP mode: Creates a new CSV entry in the same ZIP file
     * 
     * Value of 0 or negative means no splitting.
     * Default: 0 (no splitting)
     */
    private int maxLinesPerFile;

    /**
     * Write UTF-8 BOM (Byte Order Mark) at the start of each CSV file.
     * Default: false
     */
    private boolean withBom;
}
