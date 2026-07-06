#!/bin/bash
set -euo pipefail

# Load .env if present so ADMIN_EMAIL / ADMIN_PASSWORD / API_URL can be provided there.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$SCRIPT_DIR/.env"
  set +a
fi

API_URL="${API_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
ORG_NAME="${ORG_NAME:-My Organization}"

# Never fall back to a weak hardcoded password. Generate a strong one if none was provided.
GENERATED_PASSWORD=false
if [ -z "${ADMIN_PASSWORD:-}" ]; then
  ADMIN_PASSWORD="$(openssl rand -base64 24)"
  GENERATED_PASSWORD=true
fi

echo "Waiting for API to be ready..."
until curl -sf "$API_URL/actuator/health" > /dev/null 2>&1; do
  sleep 2
done
echo "API is ready."

echo "Creating admin account..."
SIGNUP_RESPONSE=$(curl -sf -X POST "$API_URL/public/v1/signup" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerDetails\": {
      \"email\": \"$ADMIN_EMAIL\",
      \"firstName\": \"Admin\",
      \"lastName\": \"User\"
    },
    \"organizationName\": \"$ORG_NAME\",
    \"password\": \"$ADMIN_PASSWORD\"
  }") || {
    echo "Signup failed. The account may already exist."
    echo "If you need a new API key, log in and rotate one: POST $API_URL/api/v1/account/api-key"
    exit 0
  }

TOKEN=$(printf '%s' "$SIGNUP_RESPONSE" | grep -o '"token":"[^"]*"' | head -n1 | sed 's/"token":"//; s/"$//' || true)

echo ""
echo "Admin account created."
echo "  Email:    $ADMIN_EMAIL"
if [ "$GENERATED_PASSWORD" = true ]; then
  echo "  Password: $ADMIN_PASSWORD"
  echo "  (auto-generated — store it now, it will not be shown again)"
else
  echo "  Password: (the ADMIN_PASSWORD you provided in the environment)"
fi
echo "  Dashboard: http://localhost:3000"

if [ -z "$TOKEN" ]; then
  echo ""
  echo "Could not read an auth token from the signup response; skipping API key issuance."
  echo "Log in and rotate one: POST $API_URL/api/v1/account/api-key"
  exit 0
fi

echo ""
echo "Issuing an API key through the app..."
# The app hashes the key at rest; the raw value below is returned exactly once and never stored.
KEY_RESPONSE=$(curl -sf -X POST "$API_URL/api/v1/account/api-key" \
  -H "Authorization: Bearer $TOKEN") || {
    echo "API key issuance failed. Log in and rotate one: POST $API_URL/api/v1/account/api-key"
    exit 0
  }

API_KEY=$(printf '%s' "$KEY_RESPONSE" | grep -o '"apiKey":"[^"]*"' | head -n1 | sed 's/"apiKey":"//; s/"$//' || true)

echo ""
echo "API key (store this now, it will not be shown again):"
echo "  $API_KEY"
echo ""
echo "Change your password after first login."
