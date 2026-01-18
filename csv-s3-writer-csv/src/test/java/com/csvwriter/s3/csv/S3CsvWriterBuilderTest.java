package com.csvwriter.s3.csv;

import com.csvwriter.s3.core.S3WriterConfig;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for S3CsvWriter.Builder validation.
 */
class S3CsvWriterBuilderTest {

    @Test
    void testBuild_MissingClient() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            S3CsvWriter.builder()
                    .bucket("test-bucket")
                    .filename("test")
                    .build();
        });

        assertEquals("S3Client is required", exception.getMessage());
    }

    @Test
    void testBuild_MissingBucket() {
        S3Client mockClient = mock(S3Client.class);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            S3CsvWriter.builder()
                    .client(mockClient)
                    .filename("test")
                    .build();
        });

        assertEquals("Bucket is required", exception.getMessage());
    }

    @Test
    void testBuild_EmptyBucket() {
        S3Client mockClient = mock(S3Client.class);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            S3CsvWriter.builder()
                    .client(mockClient)
                    .bucket("   ")
                    .filename("test")
                    .build();
        });

        assertEquals("Bucket is required", exception.getMessage());
    }

    @Test
    void testBuild_MissingFilename() {
        S3Client mockClient = mock(S3Client.class);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            S3CsvWriter.builder()
                    .client(mockClient)
                    .bucket("test-bucket")
                    .build();
        });

        assertEquals("Filename is required", exception.getMessage());
    }

    @Test
    void testBuild_EmptyFilename() {
        S3Client mockClient = mock(S3Client.class);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            S3CsvWriter.builder()
                    .client(mockClient)
                    .bucket("test-bucket")
                    .filename("  ")
                    .build();
        });

        assertEquals("Filename is required", exception.getMessage());
    }

    @Test
    void testBuild_MultiFileWithoutCompress() {
        S3Client mockClient = mock(S3Client.class);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            S3CsvWriter.builder()
                    .client(mockClient)
                    .bucket("test-bucket")
                    .filename("test")
                    .multiFile(true)
                    .compress(false)
                    .build();
        });

        assertEquals("multiFile requires compress=true", exception.getMessage());
    }

    @Test
    void testBuild_ValidMinimalConfiguration() {
        S3Client mockClient = mock(S3Client.class);

        S3CsvWriter writer = S3CsvWriter.builder()
                .client(mockClient)
                .bucket("test-bucket")
                .filename("test")
                .build();

        assertNotNull(writer);
        assertEquals("test-bucket", writer.getBucket());
    }

    @Test
    void testBuild_ValidFullConfiguration() {
        S3Client mockClient = mock(S3Client.class);

        S3CsvWriter writer = S3CsvWriter.builder()
                .client(mockClient)
                .bucket("test-bucket")
                .folderName("exports")
                .filename("data")
                .bufferSize(1024 * 1024)
                .compress(true)
                .multiFile(true)
                .maxLinesPerFile(50000)
                .withBom(true)
                .build();

        assertNotNull(writer);
        assertEquals("test-bucket", writer.getBucket());
    }

    @Test
    void testBuild_InvalidBufferSize() {
        S3Client mockClient = mock(S3Client.class);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            S3CsvWriter.builder()
                    .client(mockClient)
                    .bucket("test")
                    .filename("test")
                    .bufferSize(0)
                    .build();
        });

        // Note: verify exact message if implemented, otherwise just generic check
    }

    @Test
    void testBuild_InvalidMaxLines() {
        S3Client mockClient = mock(S3Client.class);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            S3CsvWriter.builder()
                    .client(mockClient)
                    .bucket("test")
                    .filename("test")
                    .maxLinesPerFile(-1)
                    .build();
        });
    }
}
