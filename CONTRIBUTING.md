# Contributing to Tanso

## Getting Started

1. Fork the repository
2. Clone your fork
3. Run `docker compose up` from the `deploy/` directory
4. Make your changes
5. Submit a pull request

## Development Setup

### Prerequisites

- Java 21+
- Node.js 20+
- Docker and Docker Compose
- PostgreSQL 15+ (or use the Docker Compose setup)

### Backend (apps/api)

```bash
cd apps/api
SPRING_PROFILES_ACTIVE=docker ./mvnw clean spring-boot:run
```

### Frontend (apps/dashboard)

```bash
cd apps/dashboard
npm install
npm run dev
```

## Running Tests

Run these locally before opening a PR. CI runs the same checks on every pull request to
`main`, and they must pass before a PR can merge.

### Backend (apps/api)

```bash
cd apps/api
./mvnw test
```

Requires a running PostgreSQL. The Docker Compose setup in `deploy/` provides one; see
Development Setup above.

### Frontend (apps/dashboard)

```bash
cd apps/dashboard
npm ci
npx vue-tsc --noEmit   # type-check
npm run build          # type-checks and builds (runs vue-tsc, then Vite)
```

CI also builds the Docker images from `deploy/docker-compose.yml`, so make sure your
changes build cleanly in both apps.

## Pull Request Guidelines

- Keep PRs focused on a single change
- Include tests for new functionality
- Update documentation if behavior changes
- All CI checks must pass

## Code Style

### Backend (Java)
- Follow existing patterns in the codebase
- Use constructor injection
- Keep services focused and small

### Frontend (Vue 3)
- Composition API with `<script setup>` only
- Use TypeScript
- Follow the feature folder structure in `src/features/`

## License

By contributing, you agree that your contributions will be licensed under the AGPL-3.0 license.
