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
if docker compose exec -T postgres psql -q \
    -v ON_ERROR_STOP=1 \
    -U "${POSTGRES_USER:-tanso}" -d "${POSTGRES_DB:-tanso}" \
    < ../scripts/create-test-account.sql; then
  echo "Seeded."
else
  echo "Seed failed — the account probably already exists. Continuing."
fi

echo "Seeding the five-credit developer demo..."
docker compose exec -T postgres psql -q \
  -v ON_ERROR_STOP=1 \
  -U "${POSTGRES_USER:-tanso}" -d "${POSTGRES_DB:-tanso}" \
  < ../scripts/seed-developer-demo.sql
echo "Developer demo ready."

# On schemas that still have the platform_mode column, it defaults to OBSERVE
# (read-only mode), which blocks plan/subscription operations. Flip the seeded
# account to FULL; on newer schemas without the column this is a no-op.
docker compose exec -T postgres psql -q \
  -U "${POSTGRES_USER:-tanso}" -d "${POSTGRES_DB:-tanso}" \
  -c "UPDATE account_settings SET platform_mode = 'FULL' WHERE account_id = 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467';" \
  2>/dev/null || echo "platform_mode column absent — full platform is the default, nothing to do."

cat <<EOF

Tanso is running.

  Login:    test / password
  API key:  sk_test_828df0fc77874c219f353417fbca1ef4
  API:      $API_URL
  Docs:     $API_URL/swagger-ui.html
  Demo:     demo-user has 5 AI_CREDITS for feature ai.chat

These are the dev-quickstart credentials from scripts/create-test-account.sql.
Change them before exposing this instance to anything real.

Next.js example:

  cd ..
  npm install
  cp examples/nextjs-ai-credits/.env.example examples/nextjs-ai-credits/.env.local
  npm run dev --workspace @tansohq/nextjs-ai-credits-example
EOF
