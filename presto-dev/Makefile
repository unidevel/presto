ORG ?= prestodb
DOCKER_CMD ?= docker
CLEAN_CACHE ?= false
NUM_THREADS ?= 8
VERSION := $(shell grep -m 1 '^    <version>' ../pom.xml | sed -e 's/.*<version>\([^<]*\)<\/version>.*/\1/' -e 's/-SNAPSHOT//')
COMMIT_ID := $(shell git rev-parse --short origin/master)

.PHONY: build centos-dep ubuntu-dep centos-cpp-dev ubuntu-cpp-dev centos-dev ubuntu-dev release-prepare release-publish pull-centos pull-ubuntu tag-centos-latest tag-ubuntu-latest

build:
	$(DOCKER_CMD) compose build

centos-dep:
	cd ../presto-native-execution && make submodules && $(DOCKER_CMD) compose build centos-native-dependency

ubuntu-dep:
	cd ../presto-native-execution && make submodules && $(DOCKER_CMD) compose build ubuntu-native-dependency

centos-cpp-dev:
	$(DOCKER_CMD) compose build --build-arg CLEAN_CACHE=$(CLEAN_CACHE) --build-arg NUM_THREADS=$(NUM_THREADS) centos-cpp-dev

ubuntu-cpp-dev:
	$(DOCKER_CMD) compose build --build-arg CLEAN_CACHE=$(CLEAN_CACHE) --build-arg NUM_THREADS=$(NUM_THREADS) ubuntu-cpp-dev

centos-dev:
	$(DOCKER_CMD) compose build centos-dev

ubuntu-dev:
	$(DOCKER_CMD) compose build ubuntu-dev

release-prepare:
	ORG=$(ORG) DOCKER_CMD=$(DOCKER_CMD) ./scripts/release.sh prepare

release-publish:
	ORG=$(ORG) DOCKER_CMD=$(DOCKER_CMD) ./scripts/release.sh publish

pull-centos:
	$(DOCKER_CMD) pull ${ORG}/presto-dev:centos-latest
	$(DOCKER_CMD) tag ${ORG}/presto-dev:centos-latest presto/presto-dev:centos9

pull-ubuntu:
	$(DOCKER_CMD) pull ${ORG}/presto-dev:ubuntu-latest
	$(DOCKER_CMD) tag ${ORG}/presto-dev:ubuntu-latest presto/presto-dev:ubuntu-22.04

tag-centos-latest:
	ORG=$(ORG) DOCKER_CMD=$(DOCKER_CMD) ./scripts/release.sh manifest-latest centos

tag-ubuntu-latest:
	ORG=$(ORG) DOCKER_CMD=$(DOCKER_CMD) ./scripts/release.sh manifest-latest ubuntu

