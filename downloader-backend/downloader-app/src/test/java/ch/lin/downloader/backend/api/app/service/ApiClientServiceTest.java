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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.TaskStatus;
import ch.lin.platform.http.HttpClient;

@ExtendWith(MockitoExtension.class)
class ApiClientServiceTest {

    @Mock
    private ConfigsService configsService;

    private ApiClientService apiClientService;

    private final String apiUrl = "http://api.example.com";
    private final String authUrl = "http://auth.example.com";

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        apiClientService = new ApiClientService(apiUrl, authUrl, configsService);
    }

    @Test
    void updateItem_ShouldSkip_WhenClientIdIsMissing() {
        DownloaderConfig config = new DownloaderConfig();
        // ClientId is null by default
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        try (MockedConstruction<HttpClient> mockedHttpClient = mockConstruction(HttpClient.class)) {
            apiClientService.updateItem("vid1", "task1", 100L, "/path/to/file", TaskStatus.DOWNLOADED);

            assertThat(mockedHttpClient.constructed()).isEmpty();
        }
    }

    @Test
    void updateItem_ShouldSkip_WhenClientIdIsBlank() {
        DownloaderConfig config = new DownloaderConfig();
        config.setClientId("   ");
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        try (MockedConstruction<HttpClient> mockedHttpClient = mockConstruction(HttpClient.class)) {
            apiClientService.updateItem("vid1", "task1", 100L, "/path/to/file", TaskStatus.DOWNLOADED);

            assertThat(mockedHttpClient.constructed()).isEmpty();
        }
    }

    @Test
    void updateItem_ShouldSendPatch_WhenConfigIsValid() throws Exception {
        DownloaderConfig config = new DownloaderConfig();
        config.setClientId("client-id");
        config.setClientSecret("client-secret");
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        try (MockedConstruction<HttpClient> mockedHttpClient = mockConstruction(HttpClient.class,
                (mock, context) -> {
                    if (context.arguments().size() > 1 && "auth.example.com".equals(context.arguments().get(1))) {
                        HttpClient.Response authResponse = mock(HttpClient.Response.class);
                        when(authResponse.body()).thenReturn("{\"access_token\":\"mock-token\"}");
                        when(mock.post(anyString(), any(), anyString(), any())).thenReturn(authResponse);
                    }
                })) {

            apiClientService.updateItem("vid1", "task1", 100L, "/path/to/file", TaskStatus.DOWNLOADED);

            List<HttpClient> constructed = mockedHttpClient.constructed();
            assertThat(constructed).hasSize(2);

            // First client is for Auth
            HttpClient authClient = constructed.get(0);
            verify(authClient).post(eq("/api/v1/auth/client-authenticate"), any(), contains("client-id"), any());

            // Second client is for API
            HttpClient apiClient = constructed.get(1);
            verify(apiClient).patch(eq("/items/vid1"), any(), contains("DOWNLOADED"), any());
        }
    }

    @Test
    void updateItemStatus_ShouldSendPatch_WhenConfigIsValid() throws Exception {
        DownloaderConfig config = new DownloaderConfig();
        config.setClientId("client-id");
        config.setClientSecret("client-secret");
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        try (MockedConstruction<HttpClient> mockedHttpClient = mockConstruction(HttpClient.class,
                (mock, context) -> {
                    if (context.arguments().size() > 1 && "auth.example.com".equals(context.arguments().get(1))) {
                        HttpClient.Response authResponse = mock(HttpClient.Response.class);
                        when(authResponse.body()).thenReturn("{\"access_token\":\"mock-token\"}");
                        when(mock.post(anyString(), any(), anyString(), any())).thenReturn(authResponse);
                    }
                })) {

            apiClientService.updateItemStatus("vid1", "task1", TaskStatus.DOWNLOADING);

            List<HttpClient> constructed = mockedHttpClient.constructed();
            assertThat(constructed).hasSize(2);

            HttpClient apiClient = constructed.get(1);
            verify(apiClient).patch(eq("/items/vid1"), any(), contains("DOWNLOADING"), any());
        }
    }

    @Test
    void updateItem_ShouldHandleAuthFailure() throws Exception {
        DownloaderConfig config = new DownloaderConfig();
        config.setClientId("client-id");
        config.setClientSecret("client-secret");
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        try (MockedConstruction<HttpClient> mockedHttpClient = mockConstruction(HttpClient.class,
                (mock, context) -> {
                    HttpClient.Response authResponse = mock(HttpClient.Response.class);
                    // Return empty JSON or missing token
                    when(authResponse.body()).thenReturn("{}");
                    when(mock.post(anyString(), any(), anyString(), any())).thenReturn(authResponse);
                })) {

            apiClientService.updateItem("vid1", "task1", 100L, "/path/to/file", TaskStatus.DOWNLOADED);

            List<HttpClient> constructed = mockedHttpClient.constructed();
            // Should construct auth client, fail to get token, and not construct api client
            assertThat(constructed).hasSize(1);

            HttpClient authClient = constructed.get(0);
            verify(authClient).post(anyString(), any(), anyString(), any());
        }
    }
}
