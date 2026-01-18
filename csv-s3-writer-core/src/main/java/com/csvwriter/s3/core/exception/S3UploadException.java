package com.csvwriter.s3.core.exception;

/**
 * Exception thrown when S3 upload operations fail.
 * This includes multipart upload initiation, part uploads, and completion
 * failures.
 */
public class S3UploadException extends S3WriterException {

    public S3UploadException(String message) {
        super(message);
    }

    public S3UploadException(String message, Throwable cause) {
        super(message, cause);
    }

    public S3UploadException(Throwable cause) {
        super(cause);
    }
}
