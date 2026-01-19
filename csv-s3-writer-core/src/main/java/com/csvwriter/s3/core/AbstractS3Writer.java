package com.csvwriter.s3.core;

import com.csvwriter.s3.core.exception.S3UploadException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract base class for S3 streaming writers.
 * Manages S3 multipart upload lifecycle with buffered OutputStream.
 * 
 * This class is virtual-thread-safe using ReentrantLock to avoid carrier thread
 * pinning.
 * 
 * Subclasses should implement format-specific logic (CSV, Parquet, etc.).
 */
@Slf4j
public abstract class AbstractS3Writer implements S3Writer {

    protected final S3Client s3Client;
    protected final S3WriterConfig config;
    protected final String bucket;
    protected final String key;

    private String uploadId;
    private final List<CompletedPart> completedParts = new ArrayList<>();
    private int partNumber = 1;

    private final ByteArrayOutputStream buffer;
    private final ReentrantLock uploadLock = new ReentrantLock();
    private boolean closed = false;
    private boolean aborted = false;

    /**
     * Constructor for subclasses.
     * Initializes S3 multipart upload.
     * 
     * @param s3Client AWS S3 client
     * @param config   Writer configuration
     * @param key      S3 object key (including prefix/suffix)
     */
    protected AbstractS3Writer(S3Client s3Client, S3WriterConfig config, String key) {
        this.s3Client = s3Client;
        this.config = config;
        this.bucket = config.getBucket();
        this.key = key;
        this.buffer = new ByteArrayOutputStream(config.getBufferSize());

        initiateMultipartUpload();
    }

    /**
     * Initiates S3 multipart upload.
     */
    private void initiateMultipartUpload() {
        try {
            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
            this.uploadId = response.uploadId();

            log.info("Initiated multipart upload: bucket={}, key={}, uploadId={}", bucket, key, uploadId);
        } catch (Exception e) {
            log.error("Failed to initiate multipart upload: bucket={}, key={}", bucket, key, e);
            throw new S3UploadException("Failed to initiate multipart upload", e);
        }
    }

    /**
     * Returns an OutputStream that buffers data and flushes to S3 when threshold is
     * reached.
     * Subclasses and composition-based implementations should wrap this stream with
     * format-specific
     * writers (CSVWriter, ZipOutputStream, etc.).
     * 
     * @return Buffered OutputStream that flushes to S3
     */
    private OutputStream outputStream;

    /**
     * Returns an OutputStream that buffers data and flushes to S3 when threshold is
     * reached.
     * Subclasses and composition-based implementations should wrap this stream with
     * format-specific
     * writers (CSVWriter, ZipOutputStream, etc.).
     * 
     * @return Buffered OutputStream that flushes to S3
     */
    public synchronized OutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    uploadLock.lock();
                    try {
                        buffer.write(b);
                        flushIfNeededLocked();
                    } finally {
                        uploadLock.unlock();
                    }
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    uploadLock.lock();
                    try {
                        buffer.write(b, off, len);
                        flushIfNeededLocked();
                    } finally {
                        uploadLock.unlock();
                    }
                }

                @Override
                public void flush() throws IOException {
                    uploadLock.lock();
                    try {
                        flushIfNeededLocked();
                    } finally {
                        uploadLock.unlock();
                    }
                }

                @Override
                public void close() throws IOException {
                    // Close is handled by AbstractS3Writer.close()
                }
            };
        }
        return outputStream;
    }

    /**
     * Flushes buffer to S3 if size exceeds threshold.
     * Assumes uploadLock is held.
     */
    private void flushIfNeededLocked() throws IOException {
        if (buffer.size() >= config.getBufferSize()) {
            flushBufferLocked();
        }
    }

    /**
     * Flushes buffered data to S3 as a multipart upload part.
     * Assumes uploadLock is held.
     */
    private void flushBufferLocked() throws IOException {
        if (buffer.size() == 0) {
            return;
        }

        if (aborted) {
            throw new S3UploadException("Upload has been aborted");
        }

        byte[] data = buffer.toByteArray();
        buffer.reset();

        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        try {
            UploadPartResponse response = s3Client.uploadPart(
                    uploadPartRequest,
                    RequestBody.fromBytes(data));

            CompletedPart part = CompletedPart.builder()
                    .partNumber(partNumber)
                    .eTag(response.eTag())
                    .build();

            completedParts.add(part);
            partNumber++;

            log.info("Successfully uploaded part {}: bucket={}, key={}, size={} bytes", part.partNumber(), bucket, key,
                    data.length);
        } catch (Exception e) {
            log.error("Failed to upload part to S3: bucket={}, key={}, partNumber={}", bucket, key, partNumber, e);
            throw new S3UploadException("Failed to upload part to S3", e);
        }
    }

    @Override
    public void flush() throws IOException {
        uploadLock.lock();
        try {
            flushIfNeededLocked();
        } finally {
            uploadLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        uploadLock.lock();
        try {
            if (closed || aborted) {
                return;
            }

            // Flush any remaining buffered data
            flushBufferLocked();

            // Complete multipart upload
            completeMultipartUpload();

            closed = true;
            log.info("S3 upload completed: bucket={}, key={}, uploadId={}, parts={}", bucket, key, uploadId,
                    completedParts.size());
        } finally {
            uploadLock.unlock();
        }
    }

    /**
     * Completes the S3 multipart upload.
     */
    private void completeMultipartUpload() {
        try {
            CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();

            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(completedUpload)
                    .build();

            s3Client.completeMultipartUpload(completeRequest);
        } catch (Exception e) {
            log.error("Failed to complete multipart upload: bucket={}, key={}, uploadId={}", bucket, key, uploadId, e);
            throw new S3UploadException("Failed to complete multipart upload", e);
        }
    }

    @Override
    public void abort() {
        uploadLock.lock();
        try {
            if (aborted || closed) {
                return;
            }

            try {
                AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .build();

                s3Client.abortMultipartUpload(abortRequest);
                log.warn("Aborted multipart upload: bucket={}, key={}, uploadId={}", bucket, key, uploadId);
            } catch (Exception e) {
                log.error("Failed to abort multipart upload: bucket={}, key={}, uploadId={}", bucket, key, uploadId, e);
            } finally {
                aborted = true;
            }
        } finally {
            uploadLock.unlock();
        }
    }

    @Override
    public String getBucket() {
        return bucket;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getUploadId() {
        return uploadId;
    }

    @Override
    public String getS3Uri() {
        return String.format("s3://%s/%s", bucket, key);
    }
}
