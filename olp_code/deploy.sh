#!/bin/bash
# =====================================================
# deploy.sh — Build, push and deploy all OLP services
# Usage:
#   ./deploy.sh              → deploy all 5 services
#   ./deploy.sh auth-service → deploy one service only
# =====================================================
set -e

ACCOUNT_ID="418272762620"
REGION="ap-south-1"
CLUSTER="olp-cluster"
ECR_BASE="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
SERVICES=("auth-service" "course-service" "enrollment-service" "progress-service" "ai-service")

if [ -n "$1" ]; then
  SERVICES=("$1")
fi

echo "================================================="
echo "OLP Deployment"
echo "Account: $ACCOUNT_ID"
echo "Region:  $REGION"
echo "Services: ${SERVICES[*]}"
echo "================================================="

echo ""
echo "Step 1 — Logging in to ECR..."
aws ecr get-login-password --region "$REGION" | \
  docker login --username AWS --password-stdin "$ECR_BASE"
echo "ECR login successful"

echo ""
echo "Step 2 — Building with Maven -Paws..."
mvn clean package -Paws -DskipTests --no-transfer-progress
echo "Maven build successful"

echo ""
echo "Step 3 — Building and pushing Docker images..."
for svc in "${SERVICES[@]}"; do
  echo ""
  echo "--- $svc ---"
  IMAGE_URI="$ECR_BASE/olp/$svc:latest"

  echo "Building: $IMAGE_URI"
  docker build -f "$svc/Dockerfile" -t "$IMAGE_URI" .

  echo "Pushing to ECR..."
  docker push "$IMAGE_URI"
  echo "Pushed: $IMAGE_URI"

  echo "Deploying to ECS..."
  aws ecs update-service \
    --cluster "$CLUSTER" \
    --service "$svc" \
    --force-new-deployment \
    --region "$REGION" \
    --query "service.serviceName" \
    --output text

  echo "$svc deployed"
done

echo ""
echo "================================================="
echo "Deployment complete!"
echo ""
echo "Monitor logs:"
for svc in "${SERVICES[@]}"; do
  echo "  aws logs tail /olp/$svc --follow --region $REGION"
done
echo "================================================="
