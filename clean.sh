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

BACKEND="downloader-backend"

# Service and Image Definitions
SVC_BACKEND="downloader-backend"
IMG_BACKEND="downloader-backend"
SVC_DB="downloader-db"
IMG_DB="downloader-database"

# Exit immediately if a command exits with a non-zero status.
set -euo pipefail

mvn_clean() {
  if command -v mvn &> /dev/null; then
    cd ${BACKEND} || {
      echo "Folder ${BACKEND} does not exist!"
      exit 1
    }
    echo "Cleaning Downloader Backend Maven project..."
    mvn clean || true
    echo "Cleaning Downloader Backend Maven project...done!"
    cd ..
  fi
}

clean_backend() {
  echo "Stopping and removing Backend container..."
  docker-compose stop ${SVC_BACKEND} 2>/dev/null || true
  docker-compose rm -f ${SVC_BACKEND} 2>/dev/null || true
  echo "Removing Backend image..."
  docker image rm ${IMG_BACKEND} 2>/dev/null || true
  mvn_clean
}

clean_db() {
  echo "Stopping dependent containers..."
  docker-compose stop ${SVC_BACKEND} 2>/dev/null || true
  echo "Stopping and removing Database containers..."
  docker-compose stop ${SVC_DB} 2>/dev/null || true
  docker-compose rm -f ${SVC_DB} 2>/dev/null || true
  echo "Removing Database image..."
  docker image rm ${IMG_DB} 2>/dev/null || true
}

clean_all() {
  echo "Stopping and removing Docker containers and networks..."
  # Use docker-compose down to stop and remove containers and networks.
  # The --remove-orphans flag cleans up any containers not defined in the compose file.
  # The || true prevents the script from exiting if the containers don't exist.
  docker-compose down --remove-orphans || true
  echo "Stopping and removing Docker containers and networks...done!"

  echo "Removing Docker images..."
  docker image rm ${IMG_BACKEND} 2>/dev/null || true
  docker image rm ${IMG_DB} 2>/dev/null || true
  echo "Removing Docker images...done!"

  mvn_clean
}

if [ $# -eq 0 ]; then
  set -- "all"
fi

DO_ALL=false
DO_BACKEND=false
DO_DB=false

for arg in "$@"; do
  case "${arg}" in
    backend) DO_BACKEND=true ;;
    downloader) DO_BACKEND=true ;;
    db) DO_DB=true ;;
    apps)
      DO_BACKEND=true
      ;;
    all) DO_ALL=true ;;
    help)
      echo "Usage: $0 {backend|downloader|db|apps|all|help} [more args...]"
      exit 0
      ;;
    *)
      echo "Unknown argument: ${arg}"
      echo "Usage: $0 {backend|downloader|db|apps|all|help} [more args...]"
      exit 1
      ;;
  esac
done

if [[ "${DO_ALL}" == "true" ]]; then
  clean_all
else
  if [[ "${DO_BACKEND}" == "true" ]]; then
    clean_backend
  fi
  if [[ "${DO_DB}" == "true" ]]; then
    clean_db
  fi
fi

exit 0
