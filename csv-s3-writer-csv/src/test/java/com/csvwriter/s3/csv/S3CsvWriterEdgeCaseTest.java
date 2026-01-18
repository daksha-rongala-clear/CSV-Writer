package com.csvwriter.s3.csv;

import com.csvwriter.s3.core.exception.FileProcessingException;
import com.csvwriter.s3.csv.util.CsvWriterHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Edge case tests for S3CsvWriter covering Orchestration, Format, and Configuration layers.
 */
class S3CsvWriterEdgeCaseTest {

    private S3Client s3Client;
    private S3CsvWriter.Builder builder;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        
        // Mock successful lifecycle responses
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("test-upload-id").build());
        
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("test-etag").build());
                
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().build());

        builder = S3CsvWriter.builder()
                .client(s3Client)
                .bucket("test-bucket")
                .filename("test-file");
    }

    // --- 1. Orchestration Layer Edge Cases ---

    @Test
    void testNoDataWritten() throws IOException {
        S3CsvWriter writer = builder.build();
        writer.addFile("empty", new String[]{"Header"});
        writer.close();

        // Should complete upload with header only
        verify(s3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    void testAddFileCalledMultipleTimesWithoutWrites() throws IOException {
        S3CsvWriter writer = builder
                .compress(true)
                .multiFile(true)
                .build();

        writer.addFile("file1", new String[]{"Header1"});
        writer.addFile("file2", new String[]{"Header2"});
        writer.close();

        // Check that something was uploaded (the ZIP containing empty files)
        verify(s3Client, atLeastOnce()).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
        verify(s3Client).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    void testRowCountExactlyEqualsSplitThreshold() throws IOException {
        int maxLines = 2;
        S3CsvWriter writer = builder
                .maxLinesPerFile(maxLines)
                .build();

        writer.addFile("data", new String[]{"H"});
        
        // Write exactly 2 lines
        writer.writeNextStrings("1");
        writer.writeNextStrings("2");
        
        // Should NOT have started a 2nd file yet (only starts on *next* write)
        // We track file creations by monitoring createMultipartUpload calls
        // 1 call for initial file
        verify(s3Client, times(1)).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        
        // Write 3rd line - triggers split
        writer.writeNextStrings("3");
        
        // Now should have 2 uploads
        verify(s3Client, times(2)).createMultipartUpload(any(CreateMultipartUploadRequest.class));

        writer.close();
    }

    @Test
    void testCloseCalledMultipleTimes() throws IOException {
        S3CsvWriter writer = builder.build();
        writer.addFile("data", new String[]{"H"});
        writer.writeNextStrings("1");
        
        writer.close();
        writer.close(); // Should be safe
        
        // Verify complete called only once per upload
        verify(s3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    // --- 2. Format Layer Edge Cases ---

    @Test
    void testNullValuesInCsv() throws IOException {
        S3CsvWriter writer = builder.bufferSize(100).build(); // Small buffer
        writer.addFile("data", new String[]{"A", "B"});
        
        // Write row with nulls
        writer.writeNextStrings("val1", null);
        writer.close();
        
        ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, atLeastOnce()).uploadPart(any(UploadPartRequest.class), captor.capture());
        
        StringBuilder fullContent = new StringBuilder();
        for (RequestBody body : captor.getAllValues()) {
            fullContent.append(new String(body.contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8));
        }
        
        // OpenCSV writes empty string for null by default: "val1", or "val1",""
        String content = fullContent.toString();
        assertTrue(content.contains("\"val1\"") && (content.contains(",\"\"") || content.endsWith(",\n") || content.contains(",\r\n")), 
            "Content should contain val1 and empty field: " + content);
    }
    
    @Test
    void testBomWrittenMultipleTimesOrPerFile() throws IOException {
        // Mock client to capture multiple uploads
        S3CsvWriter writer = builder
                .withBom(true)
                .maxLinesPerFile(1) // Force split every row
                .build();

        writer.addFile("data", new String[]{"H"});
        writer.writeNextStrings("1"); // File 1
        writer.writeNextStrings("2"); // File 2
        
        writer.close();

        // Capture all upload parts across both files
        ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, atLeast(2)).uploadPart(any(UploadPartRequest.class), captor.capture());
        
        List<RequestBody> bodies = captor.getAllValues();
        int bomCount = 0;
        StringBuilder failures = new StringBuilder();
        
        for (int i = 0; i < bodies.size(); i++) {
            byte[] bytes = bodies.get(i).contentStreamProvider().newStream().readAllBytes();
            if (bytes.length >= 3 && 
                bytes[0] == (byte)0xEF && 
                bytes[1] == (byte)0xBB && 
                bytes[2] == (byte)0xBF) {
                bomCount++;
            } else {
                StringBuilder hex = new StringBuilder();
                for (byte b : bytes) {
                  hex.append(String.format("%02X ", b));
                }
                failures.append(String.format("Part %d: size=%d, hex=%s\n", i, bytes.length, hex.toString()));
            }
        }
        
        assertEquals(2, bomCount, "Each file should start with BOM. Failures:\n" + failures.toString());
    }

    // --- 5. Configuration & Security Edge Cases ---

    @Test
    void testPathInjectionSanitization() throws IOException {
        S3CsvWriter writer = builder.build();
        writer.addFile("../../secret", new String[]{"H"});
        
        // Check what key was requested
        ArgumentCaptor<CreateMultipartUploadRequest> captor = ArgumentCaptor.forClass(CreateMultipartUploadRequest.class);
        verify(s3Client).createMultipartUpload(captor.capture());
        
        String key = captor.getValue().key();
        
        // EXPECTED: Path should be sanitized or rejected. 
        // Currently checking if it contains dangerous sequence.
        // Note: The requirement says "Sanitized filenames".
        // If the implementation doesn't sanitize yet, this test serves as a verification of current behavior
        // or a requirement enforcement.
        // For now, let's assert it DOES NOT contain "../"
        assertFalse(key.contains("../"), "Filename should be sanitized");
    }

    @Test
    void testIllegalSplitConfiguration() {
        // MultiFile disabled (default) + maxLinesPerFile set
        // In this case, it should create multiple S3 objects (split mode without zip)
        // This IS allowed. 
        
        // Requirement 1.5: "maxLinesPerFile set but ZIP multi-file disabled" 
        // -> If we are in ZIP mode, split means separate entries.
        // If NOT in ZIP mode, split means separate S3 objects.
        // Both valid.
        
        // Let's check "multiFile=false + addFile() called multiple times"
        // This usually implies creating different files. 
        // In non-zip mode, addFile() closes previous and starts new.
        
        S3CsvWriter writer = builder.build(); // Non-zip
        try {
            writer.addFile("f1", null);
            writer.writeNextStrings("d");
            writer.addFile("f2", null); // Should close f1 and start f2
            writer.writeNextStrings("d");
            writer.close();
        } catch (Exception e) {
            fail("Should allow multiple addFile calls in non-zip mode (sequential uploads)");
        }
    }
}
