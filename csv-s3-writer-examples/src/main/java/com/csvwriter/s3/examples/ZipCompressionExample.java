package com.csvwriter.s3.examples;

import com.csvwriter.s3.csv.S3CsvWriter;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Example of creating a ZIP archive with a single CSV file.
 * 
 * This example demonstrates:
 * - ZIP compression
 * - Reduced storage and transfer costs
 * - Single CSV inside ZIP
 */
public class ZipCompressionExample {

    public static void main(String[] args) {
        S3Client s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();

        try {
            S3CsvWriter csvWriter = S3CsvWriter.builder()
                    .client(s3Client)
                    .bucket("my-bucket")
                    .folderName("csv-exports/compressed")
                    .filename("sales_report")
                    .compress(true)  // Enable ZIP compression
                    .withBom(true)
                    .build();

            csvWriter.addFile("sales_report", new String[]{"Date", "Product", "Quantity", "Revenue"});

            // Stream sales data
            csvWriter.writeNextStrings("2024-01-01", "Widget A", "150", "1500.00");
            csvWriter.writeNextStrings("2024-01-02", "Widget B", "200", "3000.00");
            csvWriter.writeNextStrings("2024-01-03", "Widget C", "75", "2250.00");
            
            // In production: stream millions of rows from database
            // Orders.stream().forEach(order -> csvWriter.writeNextStrings(...));

            csvWriter.close();

            System.out.println("✅ Compressed CSV uploaded successfully!");
            System.out.println("📦 ZIP file: sales_report.zip");
            System.out.println("📍 S3 URI: " + csvWriter.getS3Uri());

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            s3Client.close();
        }
    }
}
