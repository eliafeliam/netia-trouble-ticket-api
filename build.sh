#!/usr/bin/env bash

# Build script for Trouble Ticket API
# This script builds Docker image and starts the application

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_NAME="trouble-ticket-api"
IMAGE_TAG="1.0.0"

echo "================================"
echo "Build Trouble Ticket API"
echo "================================"

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

# Build Docker image
echo "📦 Building Docker image: ${IMAGE_NAME}:${IMAGE_TAG}"
docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -f "${PROJECT_DIR}/Dockerfile" "${PROJECT_DIR}"

echo ""
echo "✅ Docker image built successfully!"
echo ""
echo "To run the application:"
echo "  1. Using Docker Compose (recommended):"
echo "     docker-compose up"
echo ""
echo "  2. Using Docker directly:"
echo "     docker run -p 8080:8080 \\\"
echo "       -e DB_HOST=<postgresql-host> \\\"
echo "       -e DB_USER=postgres \\\"
echo "       -e DB_PASSWORD=postgres \\\"
echo "       ${IMAGE_NAME}:${IMAGE_TAG}"
echo ""
echo "API will be available at: http://localhost:8080"
echo "Swagger UI: http://localhost:8080/swagger-ui.html"

