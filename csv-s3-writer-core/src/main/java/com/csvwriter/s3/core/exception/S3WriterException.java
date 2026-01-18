package com.csvwriter.s3.core.exception;

/**
 * Base exception for the CSV S3 Writer library.
 * All domain-specific exceptions extend this class.
 */
public class S3WriterException extends RuntimeException {

    public S3WriterException(String message) {
        super(message);
    }

    public S3WriterException(String message, Throwable cause) {
        super(message, cause);
    }

    public S3WriterException(Throwable cause) {
        super(cause);
    }
}
