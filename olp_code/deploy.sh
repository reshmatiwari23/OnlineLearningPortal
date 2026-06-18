#!/bin/bash
# ================================================================
# deploy.sh — Build, push to ECR, and deploy to ECS
#
# Usage:
#   ./deploy.sh                    — deploy all services
#   ./deploy.sh auth-service       — deploy one service only
#
# Prerequisites:
#   - AWS CLI configured (aws configure)
#   - Docker Desktop running
#   - Replace ACCOUNT_ID and REGION below with your values
# ================================================================

set -e

ACCOUNT_ID="YOUR-AWS-ACCOUNT-ID"
REGION="ap-south-1"
ECR_REGISTRY="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
CLUSTER="olp-cluster"

SERVICES=("auth-service" "course-service" "enrollment-service" "progress-service" "ai-service")

# If a service name is passed, deploy only that one
if [ -n "$1" ]; then
  SERVICES=("$1")
fi

echo "=== Logging in to ECR ==="
aws ecr get-login-password --region $REGION | \
  docker login --username AWS --password-stdin $ECR_REGISTRY

echo "=== Building Maven project with aws profile ==="
mvn clean package -DskipTests -Paws

for SERVICE in "${SERVICES[@]}"; do
  echo ""
  echo "=== Deploying $SERVICE ==="

  # Build Docker image
  echo "Building Docker image..."
  docker build -t olp/$SERVICE -f $SERVICE/Dockerfile .

  # Tag for ECR
  docker tag olp/$SERVICE $ECR_REGISTRY/olp/$SERVICE:latest

  # Push to ECR
  echo "Pushing to ECR..."
  docker push $ECR_REGISTRY/olp/$SERVICE:latest

  # Force ECS to deploy the new image
  echo "Updating ECS service..."
  aws ecs update-service \
    --cluster $CLUSTER \
    --service $SERVICE \
    --force-new-deployment \
    --region $REGION \
    --query 'service.serviceName' \
    --output text

  echo "✓ $SERVICE deployed"
done

echo ""
echo "=== All deployments triggered ==="
echo "Monitor progress: https://console.aws.amazon.com/ecs/home?region=$REGION#/clusters/$CLUSTER/services"
