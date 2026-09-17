#!/bin/bash
set -euo pipefail

# First-run bootstrap: seeds the test account from scripts/create-test-account.sql.
# Run after `docker compose up -d` from the deploy/ directory.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

API_URL="http://localhost:${API_PORT:-8080}"

echo "Waiting for the API to be ready at $API_URL ..."
until curl -sf "$API_URL/actuator/health" > /dev/null 2>&1; do
  sleep 2
done
echo "API is ready."

echo "Seeding test account..."
docker compose exec -T postgres psql -q \
  -v ON_ERROR_STOP=1 \
  -U "${POSTGRES_USER:-tanso}" -d "${POSTGRES_DB:-tanso}" \
  < ../scripts/create-test-account.sql
echo "Seeded."

echo "Seeding the five-credit developer demo..."
docker compose exec -T postgres psql -q \
  -v ON_ERROR_STOP=1 \
  -U "${POSTGRES_USER:-tanso}" -d "${POSTGRES_DB:-tanso}" \
  < ../scripts/seed-developer-demo.sql
echo "Developer demo ready."

# The margin demo: paid plans, five customers on different margins, ninety days
# of metered events, and the internal-spend side, so Overview and Feature P&L
# open on real numbers instead of zeros. Set TANSO_SKIP_MARGIN_DEMO=1 to skip.
if [ -z "${TANSO_SKIP_MARGIN_DEMO:-}" ]; then
  echo "Seeding the margin demo..."
  docker compose exec -T postgres psql -q \
    -v ON_ERROR_STOP=1 \
    -U "${POSTGRES_USER:-tanso}" -d "${POSTGRES_DB:-tanso}" \
    < ../scripts/seed-margin-demo.sql
  echo "Margin demo ready."
fi

# On schemas that still have the platform_mode column, it defaults to OBSERVE
# (read-only mode), which blocks plan/subscription operations. Flip the seeded
# account to FULL; on newer schemas without the column this is a no-op.
docker compose exec -T postgres psql -q \
  -U "${POSTGRES_USER:-tanso}" -d "${POSTGRES_DB:-tanso}" \
  -c "UPDATE account_settings SET platform_mode = 'FULL' WHERE account_id = 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467';" \
  2>/dev/null || echo "platform_mode column absent — full platform is the default, nothing to do."

# Publish the catalog and open agent signup on the seeded free plan. Safe by
# default: signups are provisional (expire unpaid after agentProvisionalDays),
# capped per account per hour and per IP per hour. Safe to rerun: every PATCH
# field is an upsert of the same value. Set TANSO_SKIP_AGENT_SIGNUP=1 to skip,
# TANSO_AGENT_SLUG to pick the catalog slug (default: demo).
if [ -z "${TANSO_SKIP_AGENT_SIGNUP:-}" ]; then
  AGENT_SLUG="${TANSO_AGENT_SLUG:-demo}"
  echo "Enabling public catalog and agent signup at /public/v1/catalog/$AGENT_SLUG ..."
  LOGIN_RESPONSE="$(curl -sf -X POST "$API_URL/public/v1/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"test","password":"password"}')"
  JWT="$(printf '%s' "$LOGIN_RESPONSE" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
  if [ -z "$JWT" ]; then
    echo "Login as test/password failed; leaving agent signup off. Response: $LOGIN_RESPONSE" >&2
    exit 1
  fi
  curl -sf -X PATCH "$API_URL/api/v1/tanso/account-settings" \
    -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
    -d "{\"slug\":\"$AGENT_SLUG\",\"publicCatalogEnabled\":true,\"agentSignupDefaultPlanId\":\"22222222-2222-4222-8222-222222222222\",\"agentSignupEnabled\":true}" \
    > /dev/null
  echo "Agent signup enabled."
fi

cat <<EOF

Tanso is running.

  Login:    test / password
  API key:  sk_test_828df0fc77874c219f353417fbca1ef4
  API:      $API_URL
  Docs:     $API_URL/swagger-ui.html
  Demo:     demo-user has 5 AI_CREDITS for feature ai.chat
  Console:  npm install && npm run dev:ui, then http://localhost:3000
EOF

if [ -z "${TANSO_SKIP_AGENT_SIGNUP:-}" ]; then
  cat <<EOF

Agent signup (developer_demo plan, provisional, capped):

  Pricing:  $API_URL/public/v1/catalog/${TANSO_AGENT_SLUG:-demo}/pricing.json
  Signup:   POST $API_URL/public/v1/catalog/${TANSO_AGENT_SLUG:-demo}/signup
  Runbook:  $API_URL/agent-signup.md
EOF
fi

cat <<EOF

These are the dev-quickstart credentials from scripts/create-test-account.sql.
Change them before exposing this instance to anything real.

Next.js example:

  cd ..
  npm install
  cp examples/nextjs-ai-credits/.env.example examples/nextjs-ai-credits/.env.local
  npm run dev --workspace @tansohq/nextjs-ai-credits-example
EOF
