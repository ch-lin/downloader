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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.OverwriteOption;
import ch.lin.downloader.backend.api.domain.YtDlpConfig;

class DefaultConfigFactoryTest {

    @Test
    void shouldCreateConfigFromProperties() {
        // Given
        DownloaderDefaultProperties properties = new DownloaderDefaultProperties();
        properties.setDuration(120);
        properties.setStartDownloadAutomatically(false);
        properties.setRemoveCompletedJobAutomatically(true);
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setThreadPoolSize(5);

        DownloaderDefaultProperties.Ytdlp ytdlpProps = properties.getYtdlp();
        ytdlpProps.setFormatFiltering("best");
        ytdlpProps.setFormatSorting("res:1080");
        ytdlpProps.setRemuxVideo("mp4");
        ytdlpProps.setWriteDescription(false);
        ytdlpProps.setWriteSubs(false);
        ytdlpProps.setSubLang("en");
        ytdlpProps.setWriteAutoSubs(false);
        ytdlpProps.setSubFormat("vtt");
        ytdlpProps.setOutputTemplate("%(title)s.%(ext)s");
        ytdlpProps.setOverwrite(OverwriteOption.FORCE);
        ytdlpProps.setKeepVideo(true);
        ytdlpProps.setExtractAudio(true);
        ytdlpProps.setAudioFormat("mp3");
        ytdlpProps.setAudioQuality(5);
        ytdlpProps.setNoProgress(true);
        ytdlpProps.setUseCookie(true);

        DefaultConfigFactory factory = new DefaultConfigFactory();

        // When
        DownloaderConfig config = factory.create(properties);

        // Then
        assertThat(config).isNotNull();
        assertThat(config.getName()).isEqualTo("default");
        assertThat(config.getEnabled()).isTrue();
        assertThat(config.getDuration()).isEqualTo(120);
        assertThat(config.getStartDownloadAutomatically()).isFalse();
        assertThat(config.getRemoveCompletedJobAutomatically()).isTrue();
        assertThat(config.getClientId()).isEqualTo("client-id");
        assertThat(config.getClientSecret()).isEqualTo("client-secret");
        assertThat(config.getThreadPoolSize()).isEqualTo(5);

        YtDlpConfig ytDlpConfig = config.getYtDlpConfig();
        assertThat(ytDlpConfig).isNotNull();
        assertThat(ytDlpConfig.getName()).isEqualTo("default");
        assertThat(ytDlpConfig.getFormatFiltering()).isEqualTo("best");
        assertThat(ytDlpConfig.getFormatSorting()).isEqualTo("res:1080");
        assertThat(ytDlpConfig.getRemuxVideo()).isEqualTo("mp4");
        assertThat(ytDlpConfig.getWriteDescription()).isFalse();
        assertThat(ytDlpConfig.getWriteSubs()).isFalse();
        assertThat(ytDlpConfig.getSubLang()).isEqualTo("en");
        assertThat(ytDlpConfig.getWriteAutoSubs()).isFalse();
        assertThat(ytDlpConfig.getSubFormat()).isEqualTo("vtt");
        assertThat(ytDlpConfig.getOutputTemplate()).isEqualTo("%(title)s.%(ext)s");
        assertThat(ytDlpConfig.getOverwrite()).isEqualTo(OverwriteOption.FORCE);
        assertThat(ytDlpConfig.getKeepVideo()).isTrue();
        assertThat(ytDlpConfig.getExtractAudio()).isTrue();
        assertThat(ytDlpConfig.getAudioFormat()).isEqualTo("mp3");
        assertThat(ytDlpConfig.getAudioQuality()).isEqualTo(5);
        assertThat(ytDlpConfig.getNoProgress()).isTrue();
        assertThat(ytDlpConfig.getUseCookie()).isTrue();
    }
}
