package com.csvwriter.s3.core;

import java.io.IOException;

/**
 * Core contract for S3 streaming writers.
 * Implementations must stream data directly to S3 with constant memory usage.
 * 
 * All implementations must be virtual-thread-safe.
 */
public interface S3Writer extends AutoCloseable {

    /**
     * @return The S3 bucket name
     */
    String getBucket();

    /**
     * @return The S3 object key
     */
    String getKey();

    /**
     * @return The S3 multipart upload ID, or null if upload not initiated
     */
    String getUploadId();

    /**
     * @return The S3 URI in format s3://bucket/key
     */
    String getS3Uri();

    /**
     * Flushes any buffered data to S3.
     * This method is safe to call multiple times.
     * 
     * @throws IOException if flush fails
     */
    void flush() throws IOException;

    /**
     * Aborts the S3 upload and releases resources.
     * This method should be called when an error occurs during streaming.
     * Safe to call multiple times.
     */
    void abort();

    /**
     * Closes the writer and finalizes the S3 upload.
     * This method ensures all buffered data is uploaded and the multipart upload is
     * completed.
     * 
     * @throws IOException if close or upload completion fails
     */
    @Override
    void close() throws IOException;
}
