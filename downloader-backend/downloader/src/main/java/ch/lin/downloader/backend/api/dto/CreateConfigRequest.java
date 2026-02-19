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
package ch.lin.downloader.backend.api.dto;

import ch.lin.downloader.backend.api.app.config.DownloaderDefaultProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for creating a new
 * {@code ch.lin.downloader.backend.api.domain.DownloaderConfig}.
 * <p>
 * This class represents the request body for the POST /configs endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateConfigRequest {

    /**
     * The unique name of the configuration.
     */
    @NotBlank(message = "Configuration name cannot be blank.")
    private String name;

    /**
     * Indicates whether the configuration is enabled. Defaults to false if not
     * provided.
     */
    private Boolean enabled = false;

    /**
     * The duration in seconds for the scheduler interval.
     */
    private Integer duration = DownloaderDefaultProperties.DEFAULT_DURATION;

    /**
     * Whether to start downloading automatically when a job is created.
     */
    private Boolean startDownloadAutomatically;

    /**
     * Whether to remove completed jobs automatically.
     */
    private Boolean removeCompletedJobAutomatically;

    /**
     * The client ID for accessing the YouTube Hub's REST API.
     */
    private String clientId;

    /**
     * The client secret for accessing the YouTube Hub's REST API.
     */
    private String clientSecret;

    /**
     * The size of the thread pool for concurrent downloads.
     */
    private Integer threadPoolSize = DownloaderDefaultProperties.DEFAULT_THREAD_POOL_SIZE;

    /**
     * The yt-dlp specific configuration settings.
     */
    @Valid
    private YtDlpConfigDto ytDlpConfig;
}
