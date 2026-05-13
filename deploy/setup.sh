#!/bin/bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-changeme}"
ORG_NAME="${ORG_NAME:-My Organization}"

echo "Waiting for API to be ready..."
until curl -sf "$API_URL/actuator/health" > /dev/null 2>&1; do
  sleep 2
done
echo "API is ready."

echo "Creating admin account..."
RESPONSE=$(curl -sf -X POST "$API_URL/public/v1/signup" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerDetails\": {
      \"email\": \"$ADMIN_EMAIL\",
      \"firstName\": \"Admin\",
      \"lastName\": \"User\"
    },
    \"organizationName\": \"$ORG_NAME\",
    \"password\": \"$ADMIN_PASSWORD\"
  }" 2>&1) || {
    echo "Signup failed. Account may already exist."
    echo "Response: $RESPONSE"
    exit 0
  }

echo ""
echo "Admin account created."
echo "  Email:    $ADMIN_EMAIL"
echo "  Password: $ADMIN_PASSWORD"
echo "  Dashboard: http://localhost:3000"
echo ""
echo "Change your password after first login."
