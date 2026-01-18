package com.csvwriter.s3.csv.util;

import java.io.IOException;
import java.io.Writer;

/**
 * Utility class for CSV-specific operations.
 * 
 * Per Staff Engineer standards, this class has a private no-args constructor
 * to prevent instantiation.
 */
public final class CsvWriterHelper {

    /**
     * UTF-8 BOM (Byte Order Mark) bytes.
     * Helps Excel and other tools correctly identify file encoding.
     */
    private static final byte[] UTF8_BOM = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    /**
     * Private constructor to prevent instantiation.
     */
    private CsvWriterHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Writes UTF-8 BOM to the writer.
     * 
     * @param writer Writer to write BOM to
     * @throws IOException if write fails
     */
    public static void writeBom(Writer writer) throws IOException {
        writer.write('\uFEFF');
    }

    /**
     * Formats a filename for S3 with optional suffix.
     * 
     * Examples:
     * - formatFilename("export", "csv", null, false) -> "export.csv"
     * - formatFilename("export", "csv", 1, false) -> "export_1.csv"
     * - formatFilename("export", "zip", null, true) -> "export.zip"
     * - formatFilename("data", "csv", 2, true) -> "data_2.csv"
     * 
     * @param baseName   Base filename without extension
     * @param extension  File extension (without dot)
     * @param fileSuffix Optional suffix number (null for no suffix)
     * @param compress   Whether output is compressed (affects suffix placement)
     * @return Formatted filename
     */
    public static String formatFilename(String baseName, String extension, Integer fileSuffix, boolean compress) {
        // Sanitize baseName to remove path traversal (simple replacement)
        // 1. Remove ".." to prevent traversal
        // 2. Replace slashes and backslashes with single underscore
        String sanitizedBase = baseName;
        if (sanitizedBase != null) {
            sanitizedBase = sanitizedBase.replace("..", "");
            sanitizedBase = sanitizedBase.replaceAll("[/\\\\]+", "_");

            // Remove leading underscores if created by slash removal at start
            if (sanitizedBase.startsWith("_")) {
                sanitizedBase = sanitizedBase.substring(1);
            }
        }

        if (fileSuffix == null || fileSuffix == 0) {
            return sanitizedBase + "." + extension;
        }

        // For compressed files, suffix goes in the base name
        // For non-compressed, suffix is part of the filename
        return String.format("%s_%d.%s", sanitizedBase, fileSuffix, extension);
    }

    /**
     * Constructs S3 key from folder name and filename.
     * 
     * @param folderName Optional folder/prefix (can be null or empty)
     * @param filename   Filename
     * @return S3 key
     */
    public static String constructS3Key(String folderName, String filename) {
        if (folderName == null || folderName.trim().isEmpty()) {
            return filename;
        }

        // Ensure folder name ends with /
        String normalizedFolder = folderName.endsWith("/") ? folderName : folderName + "/";
        return normalizedFolder + filename;
    }
}
