package com.csvwriter.s3.csv.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsvWriterHelper utility class.
 */
class CsvWriterHelperTest {

    @Test
    void testWriteBom() throws IOException {
        StringWriter stringWriter = new StringWriter();
        CsvWriterHelper.writeBom(stringWriter);

        String result = stringWriter.toString();

        // Should write single BOM character \uFEFF
        assertEquals(1, result.length(), "BOM should be 1 character (\\uFEFF)");
        assertEquals('\uFEFF', result.charAt(0), "BOM character incorrect");
    }

    @Test
    void testFormatFilename_NoSuffix() {
        String result = CsvWriterHelper.formatFilename("export", "csv", null, false);
        assertEquals("export.csv", result);
    }

    @Test
    void testFormatFilename_WithZeroSuffix() {
        String result = CsvWriterHelper.formatFilename("export", "csv", 0, false);
        assertEquals("export.csv", result);
    }

    @Test
    void testFormatFilename_WithSuffix() {
        String result = CsvWriterHelper.formatFilename("export", "csv", 1, false);
        assertEquals("export_1.csv", result);
    }

    @Test
    void testFormatFilename_Sanitization() {
        // Test basic path traversal
        String result = CsvWriterHelper.formatFilename("../../etc/passwd", "csv", null, false);
        // .. removed -> /etc/passwd -> _etc_passwd (and leading _ removed) ->
        // etc_passwd.csv
        assertEquals("etc_passwd.csv", result, "Should remove .. and replace slashes");

        // Test backslashes (Windows style)
        String resultWin = CsvWriterHelper.formatFilename("..\\windows\\system32", "csv", null, false);
        assertEquals("windows_system32.csv", resultWin, "Should handle backslashes");
    }

    @Test
    void testFormatFilename_WithMultipleSuffix() {
        String result = CsvWriterHelper.formatFilename("data", "csv", 5, false);
        assertEquals("data_5.csv", result);
    }

    @Test
    void testFormatFilename_ZipFile() {
        String result = CsvWriterHelper.formatFilename("archive", "zip", null, true);
        assertEquals("archive.zip", result);
    }

    @Test
    void testConstructS3Key_NoFolder() {
        String result = CsvWriterHelper.constructS3Key(null, "file.csv");
        assertEquals("file.csv", result);
    }

    @Test
    void testConstructS3Key_EmptyFolder() {
        String result = CsvWriterHelper.constructS3Key("", "file.csv");
        assertEquals("file.csv", result);
    }

    @Test
    void testConstructS3Key_WithFolder() {
        String result = CsvWriterHelper.constructS3Key("data", "file.csv");
        assertEquals("data/file.csv", result);
    }

    @Test
    void testConstructS3Key_FolderWithTrailingSlash() {
        String result = CsvWriterHelper.constructS3Key("data/", "file.csv");
        assertEquals("data/file.csv", result);
    }

    @Test
    void testConstructS3Key_NestedFolder() {
        String result = CsvWriterHelper.constructS3Key("exports/csv-data", "file.csv");
        assertEquals("exports/csv-data/file.csv", result);
    }

    @Test
    void testUtilityClassCannotBeInstantiated() {
        Exception exception = assertThrows(Exception.class, () -> {
            // Use reflection to try instantiating
            var constructor = CsvWriterHelper.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });

        // The reflection wraps UnsupportedOperationException in
        // InvocationTargetException
        assertTrue(exception instanceof java.lang.reflect.InvocationTargetException);
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof UnsupportedOperationException);
        assertEquals("Utility class cannot be instantiated", cause.getMessage());
    }
}
