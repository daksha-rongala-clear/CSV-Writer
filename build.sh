#!/bin/bash

# Build script for CSV S3 Writer
# Ensures Maven uses Java 21 for compilation

set -e  # Exit on error

echo "========================================="
echo " CSV S3 Writer - Build Script"
echo "========================================="
echo ""

# Detect Java 21 installation
JAVA_21_HOME=""

# Check jenv first (if available)
if command -v jenv &> /dev/null; then
    echo "Detected jenv..."
    JAVA_21_HOME=$(jenv javahome 2>/dev/null || echo "")
    if [ -n "$JAVA_21_HOME" ]; then
        echo "Using jenv Java 21: $JAVA_21_HOME"
    fi
fi

# Fallback to Homebrew OpenJDK 21 (macOS)
if [ -z "$JAVA_21_HOME" ] && [ -d "/opt/homebrew/Cellar/openjdk@21" ]; then
    JAVA_21_HOME=$(find /opt/homebrew/Cellar/openjdk@21 -name "Home" -type d | head -n 1)
    echo "Using Homebrew OpenJDK 21: $JAVA_21_HOME"
fi

# Check if Java 21 was found
if [ -z "$JAVA_21_HOME" ]; then
    echo "ERROR: Java 21 not found!"
    echo "Please install Java 21 or set JAVA_HOME manually."
    exit 1
fi

# Verify Java version
JAVA_VERSION=$("$JAVA_21_HOME/bin/java" -version 2>&1 | head -n 1)
echo "Java Version: $JAVA_VERSION"
echo ""

# Run Maven build
echo "Running Maven build..."
JAVA_HOME="$JAVA_21_HOME" mvn clean install "$@"

echo ""
echo "========================================="
echo " Build Complete!"
echo "========================================="
