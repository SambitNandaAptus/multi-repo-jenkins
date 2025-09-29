#!/bin/bash

SERVER="$1"
REGISTRY="$2"
IMAGE="$3"
TAG="$4"
DOCKER_USER="$5"
DOCKER_PASS="$6"

echo "[DEPLOY] Deploying ${IMAGE}:${TAG} to ${SERVER}"

# Login to Docker non-interactively
echo "${DOCKER_PASS}" | docker login "${REGISTRY}" -u "${DOCKER_USER}" --password-stdin

# Pull the latest image
docker pull "${IMAGE}:${TAG}"

# Stop and remove the existing container (if any)
docker stop ${IMAGE##*/} || true
docker rm ${IMAGE##*/} || true

# Use docker-compose to start the service
# Assumes docker-compose.yml exists on the remote server in /tmp
SERVICE_NAME="${IMAGE##*/}"
ENV_PATH="/home/aptus/pie-dev-dir/${SERVICE_NAME}/.env"

docker compose --env-file "${ENV_PATH}" -f /home/aptus/pie-dev-dir/docker-compose.yml up -d



echo "[DEPLOY] Deployment completed!"
