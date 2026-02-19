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

# --- Build Stage ---
# Use a Maven image with Eclipse Temurin 25 to build the application.
FROM maven:3.9-eclipse-temurin-25 AS builder

LABEL maintainer="Che-Hung Lin <edward730109@gmail.com>"

# Set the working directory for the build stage.
WORKDIR /build

# Copy the Platform project and build it first.
# This assumes the Docker build context is the parent directory of both 'platform' and 'downloader'.
COPY platform /build/platform
RUN mvn -f /build/platform/pom.xml clean install -Dmaven.test.skip=true

# Copy the Downloader backend project.
COPY downloader/downloader-backend /build/downloader-backend

# Set the working directory to the Downloader backend project for its build.
WORKDIR /build/downloader-backend

# Build the application, skipping tests for a faster build.
RUN mvn clean package -pl downloader -am -Dmaven.test.skip=true

# Extract Layers
WORKDIR /build/downloader-backend/bin
RUN java -Djarmode=layertools -jar downloader.jar extract --destination extracted

# --- Runtime Stage ---
# Use a secure and minimal base image with a Java 25 JRE for the final image.
FROM eclipse-temurin:25-jre-jammy AS final

LABEL maintainer="Che-Hung Lin <edward730109@gmail.com>"

# Define a build argument for the download path with a default value.
ARG DOWNLOAD_PATH_ARG=/app/downloads
# Define a build argument for the cookie path with a default value.
ARG COOKIE_PATH_ARG=/app/cookies

# Create a non-root user and group first.
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

# Set the working directory inside the container.
WORKDIR /app

# Install dependencies and yt-dlp into a user-writable directory.
# - ffmpeg is a strongly recommended dependency for yt-dlp.
# - curl is used to download the yt-dlp binary.
# - python3 is required to execute the yt-dlp script.
# - nodejs 20+ is required for yt-dlp JS interpretation.
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl gnupg && \
    mkdir -p /etc/apt/keyrings && \
    curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg && \
    echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" | tee /etc/apt/sources.list.d/nodesource.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg python3 nodejs && \
    mkdir -p /app/bin && \
    curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /app/bin/yt-dlp && \
    chmod a+rx /app/bin/yt-dlp && \
    mkdir -p ${DOWNLOAD_PATH_ARG} ${COOKIE_PATH_ARG} && \
    chown -R appuser:appgroup /app ${DOWNLOAD_PATH_ARG} ${COOKIE_PATH_ARG} && \
    rm -rf /var/lib/apt/lists/*

# Add the user-writable bin directory to the PATH.
ENV PATH="/app/bin:${PATH}"

# Copy Layers
COPY --from=builder /build/downloader-backend/bin/extracted/dependencies/ ./
COPY --from=builder /build/downloader-backend/bin/extracted/spring-boot-loader/ ./
COPY --from=builder /build/downloader-backend/bin/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/downloader-backend/bin/extracted/application/ ./

# Switch to the non-root user.
USER appuser

EXPOSE 8080

# The command to run the application.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]