package com.csvwriter.s3.examples;

import com.csvwriter.s3.csv.S3CsvWriter;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Example of creating a ZIP archive with multiple CSV files.
 * 
 * This example demonstrates:
 * - Multiple CSV files inside a single ZIP
 * - Different datasets in one archive
 * - Enterprise data export scenarios
 */
public class MultiFileZipExample {

    public static void main(String[] args) {
        S3Client s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();

        try {
            S3CsvWriter csvWriter = S3CsvWriter.builder()
                    .client(s3Client)
                    .bucket("my-bucket")
                    .folderName("csv-exports/multi")
                    .filename("monthly_report")
                    .compress(true)
                    .multiFile(true)  // Enable multiple CSV files in ZIP
                    .withBom(true)
                    .build();

            // File 1: Users
            csvWriter.addFile("users", new String[]{"ID", "Name", "Email"});
            csvWriter.writeNextStrings("1", "Alice", "alice@example.com");
            csvWriter.writeNextStrings("2", "Bob", "bob@example.com");

            // File 2: Orders
            csvWriter.addFile("orders", new String[]{"OrderID", "UserID", "Amount", "Date"});
            csvWriter.writeNextStrings("1001", "1", "99.99", "2024-01-15");
            csvWriter.writeNextStrings("1002", "2", "149.99", "2024-01-16");

            // File 3: Products
            csvWriter.addFile("products", new String[]{"ProductID", "Name", "Price", "Stock"});
            csvWriter.writeNextStrings("P001", "Widget A", "49.99", "100");
            csvWriter.writeNextStrings("P002", "Widget B", "79.99", "50");

            csvWriter.close();

            System.out.println("✅ Multi-file ZIP uploaded successfully!");
            System.out.println("📦 ZIP file: monthly_report.zip");
            System.out.println("📄 Contains:");
            System.out.println("   - users.csv");
            System.out.println("   - orders.csv");
            System.out.println("   - products.csv");
            System.out.println("📍 S3 URI: " + csvWriter.getS3Uri());

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            s3Client.close();
        }
    }
}
