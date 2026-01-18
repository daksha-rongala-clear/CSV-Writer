package com.csvwriter.s3.core.exception;

/**
 * Exception thrown when file processing operations fail.
 * This includes CSV formatting, ZIP compression, and file splitting errors.
 */
public class FileProcessingException extends S3WriterException {

    public FileProcessingException(String message) {
        super(message);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileProcessingException(Throwable cause) {
        super(cause);
    }
}
