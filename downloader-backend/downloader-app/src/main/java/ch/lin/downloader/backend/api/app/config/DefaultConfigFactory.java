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
package ch.lin.downloader.backend.api.app.config;

import org.springframework.stereotype.Component;

import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.YtDlpConfig;

/**
 * Factory for creating default downloader configurations.
 * <p>
 * This component is responsible for constructing a {@link DownloaderConfig}
 * object populated with default values from
 * {@link DownloaderDefaultProperties}.
 */
@Component
public class DefaultConfigFactory {

    /**
     * Creates a default {@link DownloaderConfig} instance from the given
     * properties.
     *
     * @param properties The default properties for the downloader
     * configuration.
     * @return A new {@link DownloaderConfig} instance with default settings.
     */
    public DownloaderConfig create(DownloaderDefaultProperties properties) {
        DownloaderConfig downloaderConfig = new DownloaderConfig();
        downloaderConfig.setName("default");
        downloaderConfig.setEnabled(true);
        downloaderConfig.setDuration(properties.getDuration());
        downloaderConfig.setStartDownloadAutomatically(properties.isStartDownloadAutomatically());
        downloaderConfig.setRemoveCompletedJobAutomatically(properties.isRemoveCompletedJobAutomatically());
        downloaderConfig.setClientId(properties.getClientId());
        downloaderConfig.setClientSecret(properties.getClientSecret());
        downloaderConfig.setThreadPoolSize(properties.getThreadPoolSize());

        DownloaderDefaultProperties.Ytdlp ytdlpProps = properties.getYtdlp();
        YtDlpConfig ytDlpConfig = new YtDlpConfig();
        // The name must match the parent for the relationship to work correctly
        ytDlpConfig.setName("default");
        ytDlpConfig.setFormatFiltering(ytdlpProps.getFormatFiltering());
        ytDlpConfig.setFormatSorting(ytdlpProps.getFormatSorting());
        ytDlpConfig.setRemuxVideo(ytdlpProps.getRemuxVideo());
        ytDlpConfig.setWriteDescription(ytdlpProps.isWriteDescription());
        ytDlpConfig.setWriteSubs(ytdlpProps.isWriteSubs());
        ytDlpConfig.setSubLang(ytdlpProps.getSubLang());
        ytDlpConfig.setWriteAutoSubs(ytdlpProps.isWriteAutoSubs());
        ytDlpConfig.setSubFormat(ytdlpProps.getSubFormat());
        ytDlpConfig.setOutputTemplate(ytdlpProps.getOutputTemplate());
        ytDlpConfig.setOverwrite(ytdlpProps.getOverwrite());
        ytDlpConfig.setKeepVideo(ytdlpProps.isKeepVideo());
        ytDlpConfig.setExtractAudio(ytdlpProps.isExtractAudio());
        ytDlpConfig.setAudioFormat(ytdlpProps.getAudioFormat());
        ytDlpConfig.setAudioQuality(ytdlpProps.getAudioQuality());
        ytDlpConfig.setNoProgress(ytdlpProps.isNoProgress());
        ytDlpConfig.setUseCookie(ytdlpProps.isUseCookie());

        downloaderConfig.setYtDlpConfig(ytDlpConfig);
        return downloaderConfig;
    }
}
