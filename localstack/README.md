# Local AWS Emulation Environment (LocalStack)

This directory contains the configuration to run a fully reproducible local AWS emulation environment using **Docker** and **LocalStack**.

## 🏗️ Overview

LocalStack allows you to develop and test your Java/Spring Boot applications locally without connecting to real AWS services. It provides an API-compatible interface to AWS services like S3, SQS, and DynamoDB.

## 🚀 Getting Started

### 1. Prerequisites
- [Docker](https://www.docker.com/get-started) installed and running.
- [Docker Compose](https://docs.docker.com/compose/install/) (v2+ recommended).

### 2. Start LocalStack
Run the following command from this directory:

```bash
docker compose up -d
```

### 3. Verify Initialization
LocalStack will automatically run the `init-aws.sh` script to create:
- **S3**: `my-test-bucket`
- **SQS**: `my-queue`
- **DynamoDB**: `my-table` (Partition Key: `id`)

## 🛠️ Service Verification

If you have `awslocal` installed, you can verify the services:

```bash
# List S3 buckets
awslocal s3 ls

# List SQS queues
awslocal sqs list-queues

# List DynamoDB tables
awslocal dynamodb list-tables
```

## 🧹 Stopping and Resetting

To stop the containers:
```bash
docker compose stop
```

To stop and remove containers and volumes (complete reset):
```bash
docker compose down -v
```

---

## ☕ Spring Boot Integration

To connect your Spring Boot application to this LocalStack environment, update your `application.yml`:

```yaml
cloud:
  aws:
    region:
      static: us-east-1
    credentials:
      access-key: test
      secret-key: test
    endpoint:
      uri: http://localhost:4566
```

### Java AWS SDK v2 Code Reference

```java
S3Client s3Client = S3Client.builder()
    .endpointOverride(URI.create("http://localhost:4566"))
    .credentialsProvider(
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create("test", "test")
        )
    )
    .region(Region.US_EAST_1)
    .build();
```

---

## 🚫 Constraints

- **No Real AWS Credentials**: The environment uses `test/test` credentials.
- **Idempotent**: The `init-aws.sh` script skips creation if resources already exist.
- **Port 4566**: This is the single entry point for all local AWS services.
