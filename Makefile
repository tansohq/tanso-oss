.PHONY: login build push tag-env deploy-staging deploy-prod

# Override these via the environment or `make VAR=value` for your own AWS setup.
REGION ?= us-east-1
ACC ?= 000000000000            # CHANGE ME: your AWS account ID
REPO ?= tanso-core
ECR  := $(ACC).dkr.ecr.$(REGION).amazonaws.com/$(REPO)
STG_CLUSTER ?= tanso-staging-ecs
STG_SERVICE ?= tanso-staging-app
PRD_CLUSTER ?= tanso-prod-ecs
PRD_SERVICE ?= tanso-prod-app
SANDBOX_CLUSTER ?= tanso-sandbox-ecs
SANDBOX_SERVICE ?= tanso-sandbox-app

login:
	aws ecr get-login-password --region $(REGION) | docker login --username AWS --password-stdin $(ACC).dkr.ecr.$(REGION).amazonaws.com

build:  ## VERSION= required
	@test -n "$(VERSION)" || (echo "VERSION required"; exit 1)
	docker buildx build --platform linux/arm64 -t $(REPO):$(VERSION) .
	docker tag $(REPO):$(VERSION) $(ECR):$(VERSION)

push:   ## VERSION= required
	@test -n "$(VERSION)" || (echo "VERSION required"; exit 1)
	docker push $(ECR):$(VERSION)

tag-env: ## VERSION= & ENV=(staging|prod) required
	@test -n "$(VERSION)" || (echo "VERSION required"; exit 1)
	@test -n "$(ENV)"     || (echo "ENV required (staging|prod)"; exit 1)
	aws ecr batch-get-image --region $(REGION) --repository-name $(REPO) \
	  --image-ids imageTag=$(VERSION) --query 'images[0].imageManifest' --output text \
	| aws ecr put-image --region $(REGION) --repository-name $(REPO) \
	    --image-tag $(ENV) --image-manifest file:///dev/stdin

deploy-staging:
	aws ecs update-service --cluster $(STG_CLUSTER) --service $(STG_SERVICE) --force-new-deployment

deploy-prod:
	aws ecs update-service --cluster $(PRD_CLUSTER) --service $(PRD_SERVICE) --force-new-deployment

deploy-sandbox:
	aws ecs update-service --cluster $(SANDBOX_CLUSTER) --service $(SANDBOX_SERVICE) --force-new-deployment