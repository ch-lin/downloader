/*=============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 Che-Hung Lin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *===========================================================================*/
package ch.lin.downloader.backend.api.app.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.downloader.backend.api.app.service.model.AuthenticationResponse;
import ch.lin.downloader.backend.api.app.service.model.UpdateItemRequest;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.TaskStatus;
import ch.lin.platform.http.HttpClient;
import ch.lin.platform.http.Scheme;

/**
 * Service for communicating with an external API to update the status of
 * download items.
 * <p>
 * This service handles authentication with a separate auth service to obtain an
 * access token and then sends PATCH requests to the main API.
 */
@Service
public class ApiClientService {

    private static final Logger logger = LoggerFactory.getLogger(ApiClientService.class);

    private final String apiUrl;
    private final String authUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> accessToken = new AtomicReference<>();
    private final ConfigsService configsService;

    /**
     * Constructs the ApiClientService with necessary configuration properties.
     *
     * @param apiUrl The base URL of the main API.
     * @param authUrl The URL of the authentication service.
     * @param configsService The service to retrieve configuration.
     */
    public ApiClientService(
            @Value("${youtube.hub.api.url}") String apiUrl,
            @Value("${authentication.service.url}") String authUrl,
            ConfigsService configsService) {
        this.apiUrl = apiUrl;
        this.authUrl = authUrl;
        this.configsService = configsService;
    }

    /**
     * Updates an item with file information and a final status (e.g.,
     * {@link TaskStatus#DOWNLOADED}).
     *
     * @param videoId The unique ID of the video item to update.
     * @param downloadTaskId The ID of the specific download task.
     * @param fileSize The size of the downloaded file in bytes.
     * @param filePath The path to the downloaded file.
     * @param status The final status of the task.
     */
    public void updateItem(String videoId, String downloadTaskId, Long fileSize, String filePath,
            TaskStatus status) {
        UpdateItemRequest requestBody = new UpdateItemRequest(downloadTaskId, fileSize, filePath, status);
        patchItem(videoId, requestBody);
    }

    /**
     * Updates only the status of an item (e.g., to
     * {@link TaskStatus#DOWNLOADING} or {@link TaskStatus#FAILED}).
     *
     * @param videoId The unique ID of the video item to update.
     * @param downloadTaskId The ID of the specific download task.
     * @param status The new status of the task.
     */
    public void updateItemStatus(String videoId, String downloadTaskId, TaskStatus status) {
        UpdateItemRequest requestBody = new UpdateItemRequest(downloadTaskId, null, null, status);
        patchItem(videoId, requestBody);
    }

    /**
     * Sends a PATCH request to the API to update an item.
     * <p>
     * This method constructs and sends the HTTP request. If the client ID is
     * not configured, it will skip the update. Errors are logged but not
     * re-thrown, as failing to update the API should not fail the download
     * process itself.
     *
     * @param videoId The ID of the item to update.
     * @param requestBody The request body containing the update information.
     */
    private void patchItem(String videoId, UpdateItemRequest requestBody) {
        DownloaderConfig config = configsService.getResolvedConfig(null);
        String clientId = config.getClientId();

        if (clientId == null || clientId.isBlank()) {
            logger.debug("Client ID is not configured. Skipping API update for item {}.", videoId);
            return;
        }

        try {
            String token = getAccessToken();
            URI apiUri = new URI(apiUrl);

            try (HttpClient client = new HttpClient(Scheme.valueOf(apiUri.getScheme()
                    .toUpperCase()), apiUri.getHost(), apiUri.getPort())) {
                String path = "/items/" + videoId;
                String jsonBody = objectMapper.writeValueAsString(requestBody);

                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                headers.put("Content-Type", "application/json");

                client.patch(path, null, jsonBody, headers);
                logger.info("Successfully sent API update for item {} with status {}.", videoId,
                        requestBody.getStatus());
            }

        } catch (IOException | URISyntaxException e) {
            logger.error("Failed to update item {} via API: {}", videoId, e.getMessage(), e);
        }
    }

    /**
     * Retrieves an access token from the authentication service.
     * <p>
     * This is a simple implementation that requests a new token for every API
     * call. A more advanced version could cache the token and refresh it only
     * when it expires.
     *
     * @return A valid access token as a String.
     * @throws IOException if there is a problem during the HTTP request or
     * response processing.
     * @throws URISyntaxException if the configured auth URL is invalid.
     */
    private String getAccessToken() throws IOException, URISyntaxException {
        DownloaderConfig config = configsService.getResolvedConfig(null);
        String clientId = config.getClientId();
        String clientSecret = config.getClientSecret();

        logger.debug("Requesting new access token for client ID: {}", clientId);

        URI authUri = new URI(authUrl);
        try (HttpClient client = new HttpClient(Scheme.valueOf(authUri.getScheme()
                .toUpperCase()), authUri.getHost(), authUri.getPort())) {
            String path = "/api/v1/auth/client-authenticate";

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("clientId", clientId);
            requestBody.put("clientSecret", clientSecret);
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpClient.Response response = client.post(path, null, jsonBody, null);

            AuthenticationResponse authResponse = objectMapper.readValue(response.body(), AuthenticationResponse.class);

            if (authResponse.getAccessToken() == null) {
                throw new IOException("Failed to retrieve access token: token was null in response.");
            }

            this.accessToken.set(authResponse.getAccessToken());
            logger.info("Successfully obtained new access token.");
            return authResponse.getAccessToken();
        }
    }
}
