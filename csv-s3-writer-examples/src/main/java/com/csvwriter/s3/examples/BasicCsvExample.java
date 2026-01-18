package com.csvwriter.s3.examples;

import com.csvwriter.s3.csv.S3CsvWriter;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Basic example of streaming CSV data to S3.
 * 
 * This example demonstrates:
 * - Simple CSV file upload to S3
 * - UTF-8 BOM for Excel compatibility
 * - Row-by-row streaming with constant memory
 */
public class BasicCsvExample {

    public static void main(String[] args) {
        // Configure S3 client
        S3Client s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();

        try {
            // Create CSV writer
            S3CsvWriter csvWriter = S3CsvWriter.builder()
                    .client(s3Client)
                    .bucket("my-bucket")
                    .folderName("csv-exports")
                    .filename("users")
                    .withBom(true)  // Excel-compatible UTF-8 BOM
                    .build();

            // Add file with headers
            csvWriter.addFile("users", new String[]{"ID", "Name", "Email", "City"});

            // Stream data rows (memory usage stays constant)
            csvWriter.writeNextStrings("1", "Alice Johnson", "alice@example.com", "New York");
            csvWriter.writeNextStrings("2", "Bob Smith", "bob@example.com", "San Francisco");
            csvWriter.writeNextStrings("3", "Charlie Brown", "charlie@example.com", "London");
            csvWriter.writeNextStrings("4", "Diana Prince", "diana@example.com", "Paris");

            // In real scenario, you would stream from database/API:
            // resultSet.forEach(row -> csvWriter.writeNextStrings(row.getId(), row.getName(), ...));

            // Close and finalize S3 upload
            csvWriter.close();

            System.out.println("✅ CSV uploaded successfully!");
            System.out.println("📍 S3 URI: " + csvWriter.getS3Uri());
            System.out.println("🆔 Upload ID: " + csvWriter.getUploadId());

        } catch (Exception e) {
            System.err.println("❌ Error uploading CSV: " + e.getMessage());
            e.printStackTrace();
        } finally {
            s3Client.close();
        }
    }
}
