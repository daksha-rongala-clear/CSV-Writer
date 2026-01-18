package com.csvwriter.s3.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for S3WriterConfig.
 */
class S3WriterConfigTest {

    @Test
    void testDefaultValues() {
        S3WriterConfig config = new S3WriterConfig();

        assertEquals(5 * 1024 * 1024, config.getBufferSize(), "Default buffer size should be 5MB");
        assertFalse(config.isCompress(), "Compression should be disabled by default");
        assertFalse(config.isMultiFile(), "Multi-file should be disabled by default");
        assertEquals(0, config.getMaxLinesPerFile(), "Max lines per file should be 0 (no splitting) by default");
        assertFalse(config.isWithBom(), "BOM should be disabled by default");
    }

    @Test
    void testChainedSetters() {
        S3WriterConfig config = new S3WriterConfig()
                .setBucket("test-bucket")
                .setFolderName("data")
                .setFilename("export")
                .setBufferSize(10 * 1024 * 1024)
                .setCompress(true)
                .setMultiFile(true)
                .setMaxLinesPerFile(100000)
                .setWithBom(true);

        assertEquals("test-bucket", config.getBucket());
        assertEquals("data", config.getFolderName());
        assertEquals("export", config.getFilename());
        assertEquals(10 * 1024 * 1024, config.getBufferSize());
        assertTrue(config.isCompress());
        assertTrue(config.isMultiFile());
        assertEquals(100000, config.getMaxLinesPerFile());
        assertTrue(config.isWithBom());
    }

    @Test
    void testSettersReturnThis() {
        S3WriterConfig config = new S3WriterConfig();
        S3WriterConfig result = config.setBucket("test");

        assertSame(config, result, "Setter should return the same instance for chaining");
    }
}
