/**
 * Core abstractions for streaming data to AWS S3.
 * 
 * This package provides the foundation for format-specific S3 writers (CSV,
 * Parquet, Excel, etc.).
 * All implementations guarantee:
 * - Constant memory usage
 * - No temporary files
 * - Direct streaming to S3 via multipart uploads
 * - Virtual-thread safety
 * 
 * @see com.csvwriter.s3.core.S3Writer
 * @see com.csvwriter.s3.core.AbstractS3Writer
 */
package com.csvwriter.s3.core;
