# Tanso Dashboard

A Vue 3 SPA for managing the Tanso B2B SaaS monetization platform.

## Table of Contents

- [Tech Stack](#tech-stack)
- [Local Development](#local-development)
- [Environment Configuration](#environment-configuration)
- [Deployment](#deployment)
- [Available Commands](#available-commands)

---

## Tech Stack

- **Framework**: Vue 3 (Composition API)
- **Build Tool**: Vite
- **Language**: TypeScript
- **UI Library**: PrimeVue
- **State Management**: Pinia (auth/session only)
- **Data Fetching**: TanStack Vue Query
- **Form Validation**: Vee-Validate + Zod
- **Routing**: Vue Router

---

## Local Development

### Prerequisites

- **Node.js**: v20 or higher
- **npm**: v9 or higher

### Getting Started

1. **Clone the repository**
   ```bash
   cd tanso-dashboard
   ```

2. **Install dependencies**
   ```bash
   npm install
   ```

3. **Create local environment file**
   ```bash
   cp .env.example .env
   ```

4. **Configure backend URL** (edit `.env`)
   ```bash
   # Point to your local backend or a remote environment
   VITE_TANSO_BACKEND_URL=http://localhost:8080
   ```

5. **Start development server**
   ```bash
   npm run dev
   ```

6. **Open your browser**
   ```
   http://localhost:3000
   ```

### Development Commands

```bash
# Start dev server with hot reload
npm run dev

# Type check
npm run build

# Run linter
npm run lint

# Fix linting issues
npm run lint:fix

# Format code
npm run format

# Run all checks (type + lint + format)
npm run check
```

---

## Environment Configuration

The application requires the backend API URL to be configured for each environment.

### Environment Files

- `.env` - Local development (gitignored)
- `.env.example` - Template for local setup
- `.env.staging` - Staging environment configuration
- `.env.sandbox` - Sandbox environment configuration
- `.env.production` - Production environment configuration

### Environment Variable

```bash
VITE_TANSO_BACKEND_URL=<backend-api-url>
```

**Examples:**
```bash
# Local development
VITE_TANSO_BACKEND_URL=http://localhost:8080

# Staging
VITE_TANSO_BACKEND_URL=https://staging.your-tanso-host.example.com

# Sandbox
VITE_TANSO_BACKEND_URL=https://sandbox.your-tanso-host.example.com

# Production
VITE_TANSO_BACKEND_URL=https://your-tanso-host.example.com
```

---

## Deployment

The dashboard is deployed to AWS S3 + CloudFront using the provided Makefile.

### Prerequisites

Before deploying, ensure you have:

1. **AWS CLI v2** installed
   ```bash
   aws --version
   ```

2. **AWS credentials configured**
   ```bash
   aws configure
   # OR use AWS SSO
   aws sso login --profile your-profile
   ```

3. **CloudFront Distribution IDs** set as environment variables
   ```bash
   export SANDBOX_DIST_ID="E1234567890ABC"
   export STAGING_DIST_ID="E0987654321XYZ"
   export PROD_DIST_ID="E1111111111AAA"
   ```

### Deployment Commands

**Deploy to Sandbox:**
```bash
make deploy-sandbox
```

**Deploy to Staging:**
```bash
make deploy-staging
```

**Deploy to Production:**
```bash
make deploy-production
```
*Note: Production deployment requires manual confirmation.*

### What Happens During Deployment

Each deployment command:

1. ✅ Checks AWS credentials are valid
2. 📦 Installs dependencies (`npm ci`)
3. 🔨 Builds the app with environment-specific configuration
4. 📤 Syncs build artifacts to S3 bucket
5. 🔄 Invalidates CloudFront cache

### Caching Strategy

The deployment process uses optimized cache headers:

- **Static assets** (`/assets/*`): `max-age=31536000, immutable` (1 year)
- **index.html**: `max-age=0, must-revalidate` (always fresh)
- **Source maps**: Excluded from deployment

CloudFront invalidation uses `/*` to ensure all cached content is refreshed.

---

## Available Commands

Run `make help` to see all available commands:

```bash
make help
```

### Build Commands

```bash
make build-sandbox      # Build for sandbox environment
make build-staging      # Build for staging environment
make build-production   # Build for production environment
make build              # Build for production (default)
```

### Deployment Commands

```bash
make deploy-sandbox     # Deploy to sandbox
make deploy-staging     # Deploy to staging
make deploy-production  # Deploy to production (with confirmation)
```

### Utility Commands

```bash
make check-aws          # Verify AWS credentials
make clean              # Remove build artifacts
make install            # Install dependencies
make invalidate-sandbox # Invalidate CloudFront cache (sandbox)
make invalidate-staging # Invalidate CloudFront cache (staging)
make invalidate-production # Invalidate CloudFront cache (production)
```

---

## Project Structure

```
src/
├── app/              # Application bootstrap (router, App.vue)
├── features/         # Feature-based modules
│   ├── auth/
│   ├── plans/
│   ├── features/
│   ├── customers/
│   ├── subscriptions/
│   ├── invoices/
│   └── webhooks/
├── lib/              # Core utilities (API client, env)
└── shared/           # Shared components and styles
```

### Feature Module Structure

Each feature follows this pattern:

```
features/plans/
├── pages/            # Route components
├── components/       # Feature-specific components
├── queries.ts        # TanStack Query hooks (read)
├── mutations.ts      # TanStack Query hooks (write)
├── schemas.ts        # Zod validation schemas
├── types.ts          # TypeScript types (inferred from schemas)
└── api.ts            # API functions
```

---

## Development Guidelines

See [CLAUDE.md](./CLAUDE.md) for detailed development guidelines and architecture decisions.

**Key principles:**
- No server data in Pinia (use TanStack Query)
- All forms use Vee-Validate + Zod
- Composition API + `<script setup>` only
- Design tokens for consistent styling
- Explicit over clever

---

## IAM Permissions Required

For deployment, your AWS IAM user/role needs:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::tanso-dashboard-*",
        "arn:aws:s3:::tanso-dashboard-*/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "cloudfront:CreateInvalidation"
      ],
      "Resource": "*"
    }
  ]
}
```

---

## Troubleshooting

### Build fails with TypeScript errors

```bash
npm run build
```

Fix any type errors shown, then retry deployment.

### AWS credentials not working

```bash
make check-aws
```

If this fails, reconfigure your credentials:
```bash
aws configure
# OR
aws sso login --profile your-profile
```

### CloudFront invalidation fails

Ensure the distribution ID environment variable is set:
```bash
echo $SANDBOX_DIST_ID  # Should output distribution ID
```

### Changes not reflected after deployment

1. Hard refresh your browser (Cmd+Shift+R / Ctrl+Shift+R)
2. Verify invalidation completed:
   ```bash
   aws cloudfront list-invalidations --distribution-id $SANDBOX_DIST_ID
   ```

---

## Support

For questions or issues, contact the Tanso platform team.
