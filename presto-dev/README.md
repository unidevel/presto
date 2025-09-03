# Presto Development Environment

This directory contains the necessary files to set up a Docker-based development environment for Presto.

## Prerequisites

*   Docker must be installed and running.

## Getting Started

This environment can be run using either CentOS or Ubuntu.

### Building the Docker images

Before running the containers for the first time, you need to build the Docker images. You can build the images using the following make commands:

*   For CentOS: `make centos-dev`
*   For Ubuntu: `make ubuntu-dev`

### Starting the development environment

To start the development environment, use one of the following commands:

*   For CentOS: `make start-centos`
*   For Ubuntu: `make start-ubuntu`

These commands will start the respective Docker containers and provide you with a shell inside the container. The source code is mounted as a volume inside the container at `/presto`.

### Connecting to the container

If the container is already running, you can get a shell inside it using:

*   For CentOS: `docker compose exec centos-dev bash`
*   For Ubuntu: `docker compose exec ubuntu-dev bash`
