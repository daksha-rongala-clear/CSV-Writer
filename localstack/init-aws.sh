#!/bin/bash
set -e

echo "Starting AWS Resource Initialization..."

# S3 Bucket
echo "Creating S3 bucket: my-test-bucket..."
awslocal s3 mb s3://my-test-bucket || echo "S3 bucket already exists."

# SQS Queue
echo "Creating SQS queue: my-queue..."
awslocal sqs create-queue --queue-name my-queue || echo "SQS queue already exists."

# DynamoDB Table
echo "Creating DynamoDB table: my-table..."
awslocal dynamodb create-table \
    --table-name my-table \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST || echo "DynamoDB table already exists."

echo "AWS Resource Initialization Complete!"
