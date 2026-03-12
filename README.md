# Downloader Service (YouTube Data Hub)

![Java](https://img.shields.io/badge/Java-25%2B-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![yt-dlp](https://img.shields.io/badge/yt--dlp-Wrapper-red)
![Architecture](https://img.shields.io/badge/Architecture-Microservice-blueviolet)
![Maven](https://img.shields.io/badge/Build-Maven-blue)
![Docker](https://img.shields.io/badge/Docker-Container-2496ED)
![License](https://img.shields.io/badge/License-MIT-green)

The **Downloader Service** is the heavy-lifting worker of the **YouTube Data Hub** ecosystem.

It acts as a robust wrapper around the powerful [yt-dlp](https://github.com/yt-dlp/yt-dlp) tool, abstracting command-line complexities into a clean REST API. It handles video downloading, format selection, and status reporting asynchronously, ensuring the main application remains responsive.

## 🏗️ Architecture & Role

*   **The "Worker"**: Executes long-running I/O tasks (downloading videos) off the main thread.
*   **yt-dlp Wrapper**: Provides a secure interface to execute `yt-dlp`, allowing users to define custom format selectors (e.g., `bestvideo+bestaudio`) directly from the UI.
*   **Callback Mechanism**: Implements the **Callback Pattern**. Instead of the Hub polling for status, the Downloader actively notifies the pre-configured Hub API URL when a download starts, finishes, or fails.

### Design Philosophy
*   **Fire-and-Forget**: The service accepts a download request and returns immediately (`202 Accepted`). The actual processing happens in the background.
*   **Stateless Execution**: Each download request is independent. State is reported back to the Hub, which acts as the source of truth.

## 🚀 Key Features

*   **Asynchronous Downloading**: Non-blocking execution using Java's `CompletableFuture` or `ExecutorService`.
*   **Custom Format Control**: Supports raw `yt-dlp` format strings, giving power users full control over resolution, codecs, and container formats.
*   **Cookie Support**: Supports Netscape-formatted cookies to download age-restricted or premium content (if provided).
*   **Secure Callbacks**: Authenticates with the Core Service using **Client Credentials** to report status updates securely.

## 🛠️ Tech Stack

*   **Language**: Java (25+)
*   **Framework**: Spring Boot 3.5
*   **Core Tool**: `yt-dlp` (installed in the Docker container)
*   **Database**: MySQL (for internal job tracking, if applicable)
*   **Build Tool**: Maven

## 📦 Prerequisites & Dependencies

Since this project was split from a mono-repo, it depends on the shared **Platform** library.

1.  **Platform Library**: You must build and install the `Platform` project locally before building this service.
    ```bash
    # Assuming you have the Platform repo cloned as a sibling
    cd ../Platform
    mvn clean install
    ```
2.  **yt-dlp**: If running locally (outside Docker), you must have `yt-dlp` installed and available in your system PATH.
3.  **FFmpeg**: Required by `yt-dlp` for merging video and audio streams.

## ⚙️ Configuration

Create a `.env` file in the root directory. You can copy `.env.example`.

```properties
# Server Configuration
SERVER_PORT=8084

# Database
DB_URL=jdbc:mysql://localhost:3307/downloader
DB_USERNAME=root
DB_PASSWORD=secret

# Integration URLs
AUTH_SERVICE_URL=http://localhost:8081
YOUTUBE_HUB_API_URL=http://localhost:8080

# Security (Client Credentials for Callbacks)
DOWNLOADER_CLIENT_ID=downloader-service
DOWNLOADER_CLIENT_SECRET=your-client-secret

# Paths
DOWNLOAD_PATH=/path/to/downloads
COOKIE_PATH=/path/to/cookies
```

> **Note**: The `Setup-Scripts/Init-secrets.sh` script can automatically inject the API Key and Client Credentials for you.

## 🏃‍♂️ Build & Run

### Local Development

```bash
# 1. Build the project (Multi-module structure)
cd downloader-backend
mvn clean package

# 2. Run the JAR
# The executable JAR is output to the 'bin' directory
java -jar ../bin/downloader-service.jar
```

### Docker

> **Note**: Since this service depends on the shared `Platform` library, the Docker build context must include both repositories. Ensure `Platform` and `Downloader` are siblings.

```bash
# 1. Navigate to the parent directory
cd ..

# 2. Build the image
docker build -f Downloader/downloader-backend/Dockerfile -t youtube-data-hub/downloader-service .
```

```bash
# 3. Run container
cd Downloader
docker run -d \
  -p 8084:8080 \
  --env-file .env \
  -v $(pwd)/downloads:/app/downloads \
  youtube-data-hub/downloader-service
```

## 🔐 Authentication Strategy

This service plays two roles in the security ecosystem:

1.  **Resource Server (Inbound)**: It protects its API endpoints (like `/download`) by validating JWTs issued by the Authentication Service. It uses the Auth Service's public key to verify signatures.
2.  **Machine Client (Outbound)**: When reporting status back to the Hub, it uses the **Client Credentials Flow** (RFC 6749 Section 4.4) to authenticate with the Auth Service, obtain a token, and then call the Hub's callback API.

## ⚠️ Disclaimer

1.  **User Responsibility**: This service is a wrapper around `yt-dlp`. Users assume full legal responsibility for any content downloaded.
2.  **TOS Compliance**: Users must strictly adhere to YouTube's Terms of Service. This tool should only be used for content you own or have permission to download.

## 📜 License

MIT
