# Contributing to Tanso Core

Thanks for your interest in contributing! This document explains how to get set
up, the conventions we follow, and how to submit changes.

By contributing, you agree that your contributions will be licensed under the
project's [GNU AGPL-3.0](LICENSE).

---

## Getting started

1. **Fork** the repository and clone your fork.
2. Follow the [README](README.md#getting-started) to run the app locally
   (Java 21, a local PostgreSQL, and `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`).
3. Create a branch for your work:

   ```bash
   git checkout -b feat/short-description
   ```

## Development workflow

- **Build:** `./mvnw clean package`
- **Run tests:** `./mvnw test`
- **Run manual database tests:** `./mvnw test -Pmanual-tests -Dgroups=manual`
- **Run locally:** `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

Please make sure the build and the full test suite pass before opening a pull
request, and add tests for any new behavior. Manual tests use the PostgreSQL
connection in `src/test/resources/application-test.yaml` and execute scheduler
jobs against its current state; they are excluded from the default suite.

## Coding standards

This is a Spring Boot codebase. A few conventions to keep things consistent:

- **Data isolation:** every query must be scoped by `accountId`. Never trust a
  raw UUID from a request without verifying ownership.
- **MapStruct:** use mappers for all Entity ↔ DTO conversions.
- **Lombok:** used throughout to reduce boilerplate (`@Data`,
  `@RequiredArgsConstructor`, etc.).
- **Liquibase:** all schema changes go in a **new** YAML changelog file under
  `src/main/resources/db/changelog/`. Never modify an existing changelog.
- **No secrets in the repo:** configuration is supplied via environment
  variables. Do not commit API keys, tokens, real account IDs, or `.env` files.
- Match the style, naming, and structure of the surrounding code.

## Submitting a pull request

1. Keep pull requests focused — one logical change per PR.
2. Write a clear description of **what** changed and **why**.
3. Reference any related issue (e.g. `Closes #123`).
4. Ensure CI/build and tests pass.
5. Be responsive to review feedback.

Use clear, imperative commit messages (e.g. `Add graduated pricing to invoice
calculation`).

## Reporting bugs & requesting features

Open a [GitHub issue](../../issues). For bugs, please include:

- What you expected to happen and what actually happened
- Steps to reproduce
- Relevant logs or stack traces (with secrets redacted)
- Your environment (Java version, OS, profile)

## Security

See [SECURITY.md](SECURITY.md) for how to report vulnerabilities.

## Questions

For anything else, reach out at **me@dougbaek.com**.

---

Thanks for helping make Tanso Core better!
