package com.csvwriter.s3.examples;

import com.csvwriter.s3.csv.S3CsvWriter;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Example of streaming large CSV datasets with automatic file splitting.
 * 
 * This example demonstrates:
 * - File splitting based on row count
 * - Multiple S3 objects (non-ZIP mode)
 * - Handling millions of rows with constant memory
 */
public class FileSplittingExample {

    public static void main(String[] args) {
        S3Client s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();

        try {
            S3CsvWriter csvWriter = S3CsvWriter.builder()
                    .client(s3Client)
                    .bucket("my-bucket")
                    .folderName("csv-exports/split")
                    .filename("large_dataset")
                    .maxLinesPerFile(100000)  // Split every 100K rows
                    .withBom(true)
                    .build();

            csvWriter.addFile("large_dataset", new String[]{"ID", "Timestamp", "Value", "Status"});

            // Simulate streaming 250,000 rows
            // This will create 3 files: large_dataset.csv, large_dataset_1.csv, large_dataset_2.csv
            System.out.println("📤 Streaming 250,000 rows with file splitting...");
            
            for (int i = 1; i <= 250000; i++) {
                csvWriter.writeNextStrings(
                        String.valueOf(i),
                        "2024-01-" + (i % 28 + 1),
                        String.valueOf(Math.random() * 1000),
                        i % 2 == 0 ? "Active" : "Inactive"
                );

                if (i % 50000 == 0) {
                    System.out.println("  ✓ Processed " + i + " rows...");
                }
            }

            csvWriter.close();

            System.out.println("✅ Large dataset uploaded successfully with automatic splitting!");
            System.out.println("📂 Files created:");
            System.out.println("   - large_dataset.csv (100K rows)");
            System.out.println("   - large_dataset_1.csv (100K rows)");
            System.out.println("   - large_dataset_2.csv (50K rows)");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            s3Client.close();
        }
    }
}
