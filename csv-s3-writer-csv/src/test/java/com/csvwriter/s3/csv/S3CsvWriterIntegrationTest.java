package com.csvwriter.s3.csv;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3CsvWriterIntegrationTest {

    private static final String BUCKET_NAME = "integration-test-bucket";

    private S3Client s3Client;

    @BeforeEach
    void setUp() throws Exception {
        // Setup AWS SDK Client to talk to LocalStack
        s3Client = S3Client.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .forcePathStyle(true) // Required for LocalStack/Mock S3
                .build();

        // Ensure bucket exists and is empty
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
        } catch (Exception e) {
            // Bucket might already exist
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (s3Client != null) {
            // Cleanup bucket if needed (optional for local dev)
            s3Client.close();
        }
    }

    @Test
    void testFullLifecycle_BasicCsv() throws IOException {
        // 1. Setup
        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());

        // 2. Stream Large Data (> 5MB to satisfy S3 Mock MPU requirements)
        // We use default buffer size (5MB). We write ~6MB.
        try (S3CsvWriter writer = S3CsvWriter.builder()
                .client(s3Client)
                .bucket(BUCKET_NAME)
                .filename("users")
                .bufferSize(5 * 1024 * 1024) // 5MB buffer
                .build()) {

            // Payload ~1KB per row
            String padding = "X".repeat(1024);
            writer.addFile("users", new String[] { "ID", "Name", "Padding" });

            // Write 12000 rows => ~12MB
            for (int i = 0; i < 12000; i++) {
                writer.writeNextStrings(String.valueOf(i), "User " + i, padding);
            }
        }

        // 3. Verify
        String key = "users.csv";
        S3Object obj = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET_NAME).build())
                .contents().stream()
                .filter(o -> o.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("File not found in S3"));

        // Size check (12000 rows * ~1KB is roughly 11.5MB - 12MB)
        // Using 11MB as a safe lower bound for binary MB (11 * 1024 * 1024)
        assertTrue(obj.size() > 11 * 1024 * 1024, "File size should be > 11MB");

        // Download and verify start/end
        String content = new String(s3Client.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key(key).build())
                .readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(content.startsWith("\"ID\",\"Name\",\"Padding\""), "Header missing");
        assertTrue(content.contains("\"11999\",\"User 11999\""), "Last row missing");
    }

    @Test
    void testLifecycle_ZipAndSplit() throws IOException {
        // 1. Setup
        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());

        // 2. Stream Data with Splitting
        // Row size ~10KB. Max 600 rows/file => 6MB file size.
        // Write 1500 rows => File 1 (600), File 2 (600), File 3 (300).
        try (S3CsvWriter writer = S3CsvWriter.builder()
                .client(s3Client)
                .bucket(BUCKET_NAME)
                .filename("data")
                .compress(true)
                .multiFile(true)
                .maxLinesPerFile(600)
                .build()) {

            String padding = "Z".repeat(10240); // 10KB
            writer.addFile("data", new String[] { "ID", "Padding" });
            for (int i = 0; i < 1500; i++) {
                writer.writeNextStrings(String.valueOf(i), padding);
            }
        }

        // 3. Verify
        List<S3Object> contents = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET_NAME).build())
                .contents().stream()
                .filter(o -> o.key().equals("data.zip"))
                .toList();

        // In ZIP + multiFile mode, we expect ONE S3 object containing multiple ZIP
        // entries
        assertEquals(1, contents.size(), "Should have exactly 1 ZIP file in S3");
        assertEquals("data.zip", contents.get(0).key());

        // Check integrity of the ZIP file and its entries
        String zipKey = "data.zip";
        try (ZipInputStream zis = new ZipInputStream(
                s3Client.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key(zipKey).build()))) {

            // Collect entries
            int entryCount = 0;
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                String expectedName = (entryCount == 1) ? "data.csv" : "data_" + (entryCount - 1) + ".csv";
                assertEquals(expectedName, entry.getName(), "ZIP entry name mismatch at index " + (entryCount - 1));

                // Verify content of the second entry (data_1.csv)
                if (entryCount == 2) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
                    String header = reader.readLine();
                    assertEquals("\"ID\",\"Padding\"", header);

                    // First data row in File 2 (split at 600) should be ID 600
                    String firstRow = reader.readLine();
                    assertTrue(firstRow.startsWith("\"600\""), "First row of split file correct");
                }
                zis.closeEntry();
            }
            assertEquals(3, entryCount, "Should have 3 split entries inside the ZIP container");
        }
    }
}
