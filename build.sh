#!/bin/bash

# The MIT License (MIT)
#
# Copyright (c) 2025 Che-Hung Lin
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.

BACKEND_DIR="downloader-backend"

# Exit immediately if a command exits with a non-zero status.
set -euo pipefail

ENV_FILE=${1:-}

build_all() {
  if command -v mvn &> /dev/null; then
    echo "Building Downloader Backend Maven project..."
    # Run Maven build in a subshell to avoid changing the script's current directory.
    (cd "${BACKEND_DIR}" && mvn clean package install) || {
      echo "Maven build failed in ${BACKEND_DIR}!"
      exit 1
    }
    echo "Building Downloader Backend Maven project...done!"
  else
    echo "Maven not found, skipping Maven build."
  fi

  echo "Building Docker images using docker-compose..."
  # docker-compose will read the docker-compose.yml file and build all services.
  if [[ -n "${ENV_FILE}" ]]; then
    echo "Using env file: ${ENV_FILE}"
    docker-compose --env-file "${ENV_FILE}" up -d --wait
  else
    docker-compose up -d --wait
  fi
  echo "Building Docker images...done!"
}

build_all
exit 0
