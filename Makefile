SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
GRADLE := ./gradlew

.DEFAULT_GOAL := help
.PHONY: help compile build check test test-fabric test-neoforge clean

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

compile: ## Compile main + gametest sources across modules (no Minecraft launch)
	@$(GRADLE) :{common,fabric,neoforge}:compileJava :{common,fabric,neoforge}:compileGametestJava

build: ## Assemble both loaders' jars
	@$(GRADLE) assemble

check: ## Run all verification: game tests on both loaders (full log: build/test.log)
	@mkdir -p build
	@$(GRADLE) check --continue --console=plain --warning-mode=summary 2>&1 | tee build/test.log

test: check ## Alias for check

test-fabric: ## Run game tests on Fabric only
	@$(GRADLE) :fabric:runGametest

test-neoforge: ## Run game tests on NeoForge only
	@$(GRADLE) :neoforge:runGameTestServer

clean: ## Remove build outputs
	@$(GRADLE) clean
