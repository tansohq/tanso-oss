# 🧱 Tanso Core – Build & Deploy Guide

This project uses **GNU Make** to build, tag, push, and deploy Docker images to **AWS ECR** and **ECS**.

---

## ⚙️ Requirements

- macOS with [Homebrew](https://brew.sh)
- AWS CLI configured with appropriate credentials
- Docker / Docker Desktop
- Java 21 (for local builds)
- Maven
- **GNU Make (modern version)**

> macOS ships an outdated Make. Install and use the modern one:
>
> ```bash
> brew install make
> gmake --version
> ```
>
> If you see `GNU Make 4.x`, you’re good.
>
> You can alias it for convenience:
> ```bash
> echo 'alias make="gmake"' >> ~/.zshrc
> source ~/.zshrc
> ```

---

## 📂 Project structure

```
tanso-core/
├── src/
├── pom.xml
├── Dockerfile
└── Makefile
```

---

## 🚀 Common commands

All commands should be run from the project root (`tanso-core/`).

### 1️⃣ Login to ECR
Authenticate Docker to your AWS ECR registry.

```bash
gmake login
```

✅ You should see: `Login Succeeded`

---

### 2️⃣ Build the Docker image
Build your app’s JAR and package it into a Docker image.

```bash
gmake build VERSION=2025-10-08
```

- Builds and tags `tanso-core:2025-10-08` locally
- Re-tags for ECR:
  ```
  435254857358.dkr.ecr.us-east-1.amazonaws.com/tanso-core:2025-10-08
  ```

Check your image:
```bash
docker images | grep tanso-core
```

---

### 3️⃣ Push the image to AWS ECR
Upload your new image.

```bash
gmake push VERSION=2025-10-08
```

Verify in **AWS Console → ECR → tanso-core → Images**.

---

### 4️⃣ Tag for an environment
Create stable tags that ECS uses (`staging`, `prod`).

```bash
# For staging
gmake tag-env VERSION=2025-10-08 ENV=staging

# For production
gmake tag-env VERSION=2025-10-08 ENV=prod
```

Now you’ll have:
```
tanso-core:staging
tanso-core:prod
```

---

### 5️⃣ Deploy to ECS
Force ECS to pull and run the latest tagged image.

```bash
# Staging
gmake deploy-staging

# Production
gmake deploy-prod
```

ECS will start new tasks automatically.

---

## 🔄 Typical full flow

```bash
cd ~/projects/tanso-core

# 1. Authenticate to ECR
gmake login

# 2. Build new version
gmake build VERSION=2025-10-08

# 3. Push to ECR
gmake push VERSION=2025-10-08

# 4. Tag image for environment
gmake tag-env VERSION=2025-10-08 ENV=staging

# 5. Trigger ECS rollout
gmake deploy-staging
```

Optional for prod:

```bash
gmake tag-env VERSION=2025-10-08 ENV=prod
gmake deploy-prod
```

---

## 🧠 Notes

- `VERSION` can be any unique string (date, semantic version, or git hash).
- Terraform defines all infrastructure (ECS, ALB, DB, etc.).  
  The Makefile only ships new application code.
- You do **not** need to run Terraform for each deploy — just use the Makefile.

---

### ✅ Quick reference

| Command | Purpose |
|----------|----------|
| `gmake login` | Log in to AWS ECR |
| `gmake build VERSION=x` | Build image |
| `gmake push VERSION=x` | Push image to ECR |
| `gmake tag-env VERSION=x ENV=staging` | Tag image for environment |
| `gmake deploy-staging` | Roll out staging ECS service |
| `gmake deploy-prod` | Roll out production ECS service |

---

### 🧰 Troubleshooting

**Error:** `commands commence before first target`  
→ Ensure Makefile lines use **real TAB characters** (not spaces).

**Error:** `No VERSION provided`  
→ Use `gmake build VERSION=2025-10-08` (always specify a version).
