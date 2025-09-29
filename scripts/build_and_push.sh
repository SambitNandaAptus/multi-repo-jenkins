#!/bin/bash
set -euo pipefail

IMAGE_TAG="$DOCKER_USER/$1"       # e.g., apstech/fastapi-test:feature-branch
REGISTRY="$2"                      # e.g., docker.io

USERNAME="${DOCKER_USER:?Please set DOCKER_USER}"
PASSWORD="${DOCKER_PASS:?Please set DOCKER_PASS}"

SERVICE_NAME=$(echo "$IMAGE_TAG" | cut -d':' -f1)
LATEST_TAG="latest"

log() { echo -e "\033[1;34m[BUILD]\033[0m $1"; }

log "Logging into Docker registry: $REGISTRY"
echo "$PASSWORD" | docker login "$REGISTRY" -u "$USERNAME" --password-stdin

log "Building Docker image: $IMAGE_TAG"
docker build -t "$REGISTRY/$IMAGE_TAG" .

log "Tagging image also as: $SERVICE_NAME:$LATEST_TAG"
docker tag "$REGISTRY/$IMAGE_TAG" "$REGISTRY/$SERVICE_NAME:$LATEST_TAG"

log "Pushing Docker image: $IMAGE_TAG"
docker push "$REGISTRY/$IMAGE_TAG"

log "Pushing Docker image: $SERVICE_NAME:$LATEST_TAG"
docker push "$REGISTRY/$SERVICE_NAME:$LATEST_TAG"

log "✅ Docker images pushed successfully"
