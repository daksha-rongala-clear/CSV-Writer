package com.csvwriter.s3.csv;

import com.csvwriter.s3.core.AbstractS3Writer;
import com.csvwriter.s3.core.S3Writer;
import com.csvwriter.s3.core.S3WriterConfig;
import com.csvwriter.s3.core.exception.FileProcessingException;
import com.csvwriter.s3.csv.util.CsvWriterHelper;
import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * CSV writer that streams data directly to S3.
 * 
 * This implementation uses composition (contains AbstractS3Writer) rather than
 * inheritance
 * to allow flexibility for ZIP mode and file splitting scenarios.
 * 
 * Virtual-thread-safe design following Java 21 best practices.
 * 
 * Usage example:
 * 
 * <pre>
 * S3CsvWriter writer = S3CsvWriter.builder()
 *         .client(s3Client)
 *         .bucket("my-bucket")
 *         .filename("export")
 *         .withBom(true)
 *         .build();
 * 
 * writer.addFile("data", new String[] { "ID", "Name", "Email" });
 * writer.writeNextStrings("1", "John", "john@example.com");
 * writer.close();
 * </pre>
 */
@Slf4j
public class S3CsvWriter implements S3Writer {

    private final S3Client s3Client;
    private final S3WriterConfig config;

    // Current S3 writer instance (may change during file splitting)
    private AbstractS3Writer currentS3Writer;

    // ZIP output stream (only used in compress mode)
    private ZipOutputStream zipOutputStream;

    // CSV writer for current file
    private CSVWriter csvWriter;

    // Current file state
    private String currentFilename;
    private int currentFileNumber = 0;
    private int currentRowCount = 0;
    private boolean headerWritten = false;
    private String[] currentHeaders;

    // Lifecycle flags
    private boolean initialized = false;
    private boolean closed = false;

    /**
     * Private constructor - use builder().
     */
    private S3CsvWriter(S3Client s3Client, S3WriterConfig config) {
        this.s3Client = s3Client;
        this.config = config;
    }

    /**
     * Adds a new file to the writer.
     * For non-ZIP mode: creates a new S3 object
     * For ZIP mode: creates a new entry in the ZIP file
     * 
     * @param filename Base filename (without extension)
     * @param headers  CSV headers
     * @throws IOException if file creation fails
     */
    public void addFile(String filename, String[] headers) throws IOException {
        if (closed) {
            throw new IllegalStateException("Writer is closed");
        }

        // Close previous file if exists
        if (initialized) {
            closeCurrentFile();
        }

        this.currentFilename = filename;
        this.currentHeaders = headers;
        this.currentFileNumber = 0;
        this.currentRowCount = 0;
        this.headerWritten = false;

        // Initialize first file
        initializeFile();
        initialized = true;
    }

    /**
     * Writes the next row of CSV data.
     * Automatically handles file splitting when maxLinesPerFile is reached.
     * 
     * @param values Row values
     * @throws IOException if write fails
     */
    public void writeNextStrings(String... values) throws IOException {
        if (!initialized) {
            throw new IllegalStateException("No file has been added. Call addFile() first.");
        }
        if (closed) {
            throw new IllegalStateException("Writer is closed");
        }

        // Check if we need to split to a new file
        if (shouldSplitFile()) {
            splitToNewFile();
        }

        // Write the row
        csvWriter.writeNext(values);
        currentRowCount++;
    }

    /**
     * Initializes a new file (either first file or after split).
     */
    private void initializeFile() throws IOException {
        try {
            if (config.isCompress()) {
                initializeCompressedFile();
            } else {
                initializeUncompressedFile();
            }

            // Determine which stream to write to
            // In ZIP mode: write to zipOutputStream (inside ZIP entry)
            // In non-ZIP mode: write to S3 stream directly
            Writer targetWriter = config.isCompress()
                    ? new OutputStreamWriter(zipOutputStream, StandardCharsets.UTF_8)
                    : new OutputStreamWriter(currentS3Writer.getOutputStream(), StandardCharsets.UTF_8);

            // Write BOM if enabled
            if (config.isWithBom()) {
                log.debug("Writing BOM to file: {}", getCurrentFilename());
                CsvWriterHelper.writeBom(targetWriter);
                targetWriter.flush();
            }

            // Create CSV writer on the same stream (important: reuse the same Writer)
            csvWriter = new CSVWriter(targetWriter);

            // Write headers
            if (currentHeaders != null && currentHeaders.length > 0) {
                csvWriter.writeNext(currentHeaders);
                headerWritten = true;
            }

            csvWriter.flush();
            log.debug("Initialized CSV file: {}", getCurrentFilename());
        } catch (Exception e) {
            log.error("Failed to initialize CSV file: {}", getCurrentFilename(), e);
            throw new FileProcessingException("Failed to initialize CSV file", e);
        }
    }

    /**
     * Initializes file in ZIP compression mode.
     */
    private void initializeCompressedFile() throws IOException {
        if (zipOutputStream == null) {
            // First file - create the S3 writer and ZIP stream
            String zipFilename = CsvWriterHelper.formatFilename(
                    config.getFilename(),
                    "zip",
                    null,
                    true);
            String s3Key = CsvWriterHelper.constructS3Key(config.getFolderName(), zipFilename);

            currentS3Writer = new AbstractS3Writer(s3Client, config, s3Key) {
            };
            zipOutputStream = new ZipOutputStream(currentS3Writer.getOutputStream());
        }

        // Create new ZIP entry for this CSV file
        String csvFilename = config.isMultiFile()
                ? CsvWriterHelper.formatFilename(currentFilename, "csv",
                        currentFileNumber > 0 ? currentFileNumber : null, false)
                : currentFilename + ".csv";

        ZipEntry zipEntry = new ZipEntry(csvFilename);
        zipOutputStream.putNextEntry(zipEntry);

        log.debug("Created ZIP entry: {}", csvFilename);
    }

    /**
     * Initializes file in uncompressed mode.
     */
    private void initializeUncompressedFile() throws IOException {
        String baseName = this.currentFilename != null ? this.currentFilename : config.getFilename();
        String csvFilename = CsvWriterHelper.formatFilename(
                baseName,
                "csv",
                currentFileNumber > 0 ? currentFileNumber : null,
                false);
        String s3Key = CsvWriterHelper.constructS3Key(config.getFolderName(), csvFilename);

        currentS3Writer = new AbstractS3Writer(s3Client, config, s3Key) {
        };

        log.debug("Created S3 writer for: {}", s3Key);
    }

    /**
     * Checks if current file should be split.
     */
    private boolean shouldSplitFile() {
        return config.getMaxLinesPerFile() > 0 && currentRowCount >= config.getMaxLinesPerFile();
    }

    /**
     * Splits to a new file when maxLinesPerFile is reached.
     */
    private void splitToNewFile() throws IOException {
        log.info("Splitting to new file: current rows = {}, max = {}", currentRowCount, config.getMaxLinesPerFile());

        // Close current CSV writer and ZIP entry if applicable
        if (csvWriter != null) {
            csvWriter.flush();
            csvWriter.close();
        }

        if (config.isCompress() && zipOutputStream != null) {
            zipOutputStream.closeEntry();
        }

        // If non-compressed, close the S3 writer and create a new one
        if (!config.isCompress() && currentS3Writer != null) {
            currentS3Writer.close();
        }

        // Increment file number and reset row count
        currentFileNumber++;
        currentRowCount = 0;

        // Initialize new file
        initializeFile();
    }

    /**
     * Closes the current file (called before starting a new file or final close).
     */
    private void closeCurrentFile() throws IOException {
        // Flush CSV writer (do not close it as it closes the underlying stream which
        // might be the ZIP stream)
        if (csvWriter != null) {
            csvWriter.flush();
            csvWriter = null;
        }

        if (config.isCompress() && zipOutputStream != null) {
            zipOutputStream.closeEntry();
        }

        if (!config.isCompress() && currentS3Writer != null) {
            currentS3Writer.close();
            currentS3Writer = null;
        }
    }

    /**
     * Gets the current filename being written.
     */
    private String getCurrentFilename() {
        if (config.isCompress()) {
            return CsvWriterHelper.formatFilename(config.getFilename(), "zip", null, true);
        } else {
            // Use the current filename (from addFile) rather than config filename
            // Config filename is just the default/base for the ZIP mode
            return CsvWriterHelper.formatFilename(
                    this.currentFilename != null ? this.currentFilename : config.getFilename(),
                    "csv",
                    currentFileNumber > 0 ? currentFileNumber : null,
                    false);
        }
    }

    @Override
    public void flush() throws IOException {
        if (csvWriter != null) {
            csvWriter.flush();
        }
        if (zipOutputStream != null) {
            zipOutputStream.flush();
        }
        if (currentS3Writer != null) {
            currentS3Writer.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        try {
            // Close current file
            if (initialized) {
                closeCurrentFile();
            }

            // Close ZIP stream if applicable
            if (zipOutputStream != null) {
                zipOutputStream.finish();
                zipOutputStream.close();
                zipOutputStream = null;
            }

            // Close S3 writer
            if (currentS3Writer != null) {
                currentS3Writer.close();
                currentS3Writer = null;
            }

            closed = true;
            log.info("S3CsvWriter closed successfully");
        } catch (Exception e) {
            log.error("Error closing S3CsvWriter - currentFile: {}, currentFileNumber: {}, currentRowCount: {}",
                    getCurrentFilename(), currentFileNumber, currentRowCount, e);
            abort();
            throw new FileProcessingException("Failed to close S3CsvWriter", e);
        }
    }

    @Override
    public void abort() {
        try {
            // Close CSV writer if open
            if (csvWriter != null) {
                try {
                    csvWriter.close();
                } catch (Exception e) {
                    log.warn("Error closing CSV writer during abort", e);
                }
                csvWriter = null;
            }

            // Close ZIP stream if open
            if (zipOutputStream != null) {
                try {
                    zipOutputStream.close();
                } catch (Exception e) {
                    log.warn("Error closing ZIP stream during abort", e);
                }
                zipOutputStream = null;
            }

            // Abort S3 upload
            if (currentS3Writer != null) {
                currentS3Writer.abort();
                currentS3Writer = null;
            }

            closed = true;
            log.warn("S3CsvWriter aborted");
        } catch (Exception e) {
            log.error("Error during abort", e);
        }
    }

    @Override
    public String getBucket() {
        return config.getBucket();
    }

    @Override
    public String getKey() {
        return currentS3Writer != null ? currentS3Writer.getKey() : null;
    }

    @Override
    public String getUploadId() {
        return currentS3Writer != null ? currentS3Writer.getUploadId() : null;
    }

    @Override
    public String getS3Uri() {
        return currentS3Writer != null ? currentS3Writer.getS3Uri() : null;
    }

    /**
     * Creates a new builder for S3CsvWriter.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for S3CsvWriter.
     */
    public static class Builder {
        private final S3WriterConfig config = new S3WriterConfig();
        private S3Client client;

        public Builder client(S3Client client) {
            this.client = client;
            return this;
        }

        public Builder bucket(String bucket) {
            config.setBucket(bucket);
            return this;
        }

        public Builder folderName(String folderName) {
            config.setFolderName(folderName);
            return this;
        }

        public Builder filename(String filename) {
            config.setFilename(filename);
            return this;
        }

        public Builder bufferSize(int bufferSize) {
            config.setBufferSize(bufferSize);
            return this;
        }

        public Builder compress(boolean compress) {
            config.setCompress(compress);
            return this;
        }

        public Builder multiFile(boolean multiFile) {
            config.setMultiFile(multiFile);
            return this;
        }

        public Builder maxLinesPerFile(int maxLinesPerFile) {
            config.setMaxLinesPerFile(maxLinesPerFile);
            return this;
        }

        public Builder withBom(boolean withBom) {
            config.setWithBom(withBom);
            return this;
        }

        public S3CsvWriter build() {
            // Validation
            if (client == null) {
                throw new IllegalArgumentException("S3Client is required");
            }
            if (config.getBucket() == null || config.getBucket().trim().isEmpty()) {
                throw new IllegalArgumentException("Bucket is required");
            }
            if (config.getFilename() == null || config.getFilename().trim().isEmpty()) {
                throw new IllegalArgumentException("Filename is required");
            }
            if (config.getBufferSize() <= 0) {
                throw new IllegalArgumentException("Buffer size must be positive");
            }
            if (config.getMaxLinesPerFile() < 0) {
                throw new IllegalArgumentException("maxLinesPerFile must be non-negative");
            }

            if (config.isMultiFile() && !config.isCompress()) {
                throw new IllegalArgumentException("multiFile requires compress=true");
            }

            return new S3CsvWriter(client, config);
        }
    }
}
