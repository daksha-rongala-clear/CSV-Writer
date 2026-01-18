/**
 * CSV implementation for streaming data to AWS S3.
 * 
 * This package provides CSV-specific writers built on top of the core S3 streaming abstractions.
 * 
 * Key features:
 * - Row-by-row streaming with constant memory usage
 * - OpenCSV integration for proper CSV formatting
 * - UTF-8 BOM support for Excel compatibility
 * - Optional ZIP compression
 * - Multi-file ZIP archives
 * - Automatic file splitting based on row count
 * 
 * @see com.csvwriter.s3.csv.S3CsvWriter
 */
package com.csvwriter.s3.csv;
