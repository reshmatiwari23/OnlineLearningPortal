#!/bin/bash
set -e

ACCOUNT_ID="418272762620"
REGION="ap-south-1"
BUCKET="olp-assets-$ACCOUNT_ID"
CF_DISTRIBUTION_ID="YOUR-CLOUDFRONT-DISTRIBUTION-ID"
API_URL="http://olp-alb-1897172403.ap-south-1.elb.amazonaws.com"

echo "Building frontend..."
echo "VITE_API_BASE_URL=$API_URL" > .env
npm ci --silent
npm run build

echo "Uploading to S3..."
aws s3 sync dist/ "s3://$BUCKET/" \
  --delete \
  --cache-control "public, max-age=31536000, immutable" \
  --exclude "index.html"

aws s3 cp dist/index.html "s3://$BUCKET/index.html" \
  --cache-control "no-cache, no-store, must-revalidate"

echo "Frontend deployed!"
echo "Update CLOUDFRONT_DOMAIN in this script after creating CloudFront distribution"
