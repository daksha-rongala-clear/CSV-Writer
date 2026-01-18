package com.csvwriter.s3.csv;

import com.csvwriter.s3.core.exception.S3UploadException;
import com.csvwriter.s3.core.exception.FileProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive scenario tests covering Format, Error Handling, and State
 * layers.
 */
class S3CsvWriterComprehensiveScenarioTest {

    private S3Client s3Client;
    private S3CsvWriter.Builder builder;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);

        // Default successful mocks
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("test-id").build());
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("etag").build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().build());

        builder = S3CsvWriter.builder()
                .client(s3Client)
                .bucket("test-bucket")
                .filename("test");
    }

    // --- Format Layer Scenarios ---

    @Test
    void testSpecialCharactersInCsv() throws IOException {
        S3CsvWriter writer = builder.build();
        writer.addFile("special", new String[] { "Col" });

        // Comma, Quote, Newline, Emoji
        String complexValue = "Line1,Line2\n\"Quoted\" 🚀";
        writer.writeNextStrings(complexValue);
        writer.close();

        // Verify content
        ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, atLeastOnce()).uploadPart(any(UploadPartRequest.class), captor.capture());

        String content = new String(captor.getValue().contentStreamProvider().newStream().readAllBytes(),
                StandardCharsets.UTF_8);

        // OpenCSV should quote the field containing special chars
        assertTrue(content.contains("\"Line1,Line2\n\"\"Quoted\"\" 🚀\""),
                "CSV should technically handle special chars. Content: " + content);
    }

    @Test
    void testMismatchedColumnCounts() throws IOException {
        S3CsvWriter writer = builder.build();
        writer.addFile("mismatch", new String[] { "A", "B" }); // 2 cols

        // Write 3 cols - OpenCSV allows this but it might define downstream issues.
        // The library shouldn't crash.
        assertDoesNotThrow(() -> writer.writeNextStrings("1", "2", "3"));

        // Write 1 col
        assertDoesNotThrow(() -> writer.writeNextStrings("1"));

        writer.close();
    }

    // --- Error Handling & Storage Layer ---

    @Test
    void testS3UploadFailurePropagates() throws IOException {
        // Mock S3 failure on uploadPart
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Simulated S3 Error").build());

        S3CsvWriter writer = builder.bufferSize(10).build(); // Small buffer to force flush
        // Write enough to trigger flush
        // Note: With bufferSize=10, addFile (writing BOM + Header) might trigger flush
        // immediately
        FileProcessingException ex = assertThrows(FileProcessingException.class, () -> {
            writer.addFile("fail", new String[] { "H" });
            for (int i = 0; i < 100; i++) {
                writer.writeNextStrings("Enough data to fill buffer and trigger upload");
            }
        });

        // The cause should be S3UploadException, which caused by S3Exception
        assertTrue(ex.getCause() instanceof S3UploadException);
        assertTrue(ex.getCause().getCause().getMessage().contains("Simulated S3 Error"));

        // User expectation: Abort upload
        // In current implementation, user must call close/abort on failure.
        writer.abort();

        // Ensure abort was called
        verify(s3Client).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    // --- State Protection ---

    @Test
    void testWriteAfterCloseThrowsException() throws IOException {
        S3CsvWriter writer = builder.build();
        writer.addFile("data", new String[] { "H" });
        writer.close();

        assertThrows(IllegalStateException.class, () -> {
            writer.writeNextStrings("Should fail");
        });
    }

    @Test
    void testAddFileAfterCloseThrowsException() throws IOException {
        S3CsvWriter writer = builder.build();
        writer.close();

        assertThrows(IllegalStateException.class, () -> {
            writer.addFile("newfile", null);
        });
    }
}
