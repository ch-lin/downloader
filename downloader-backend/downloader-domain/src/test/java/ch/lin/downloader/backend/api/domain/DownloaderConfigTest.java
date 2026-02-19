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
package ch.lin.downloader.backend.api.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DownloaderConfigTest {

    @Test
    void testNoArgsConstructorAndSetters() {
        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setEnabled(true);
        config.setDuration(3600);
        config.setStartDownloadAutomatically(true);
        config.setRemoveCompletedJobAutomatically(false);
        config.setClientId("client-id");
        config.setClientSecret("client-secret");
        config.setThreadPoolSize(5);

        YtDlpConfig ytDlpConfig = new YtDlpConfig();
        ytDlpConfig.setName("default");
        config.setYtDlpConfig(ytDlpConfig);

        assertThat(config.getName()).isEqualTo("default");
        assertThat(config.getEnabled()).isTrue();
        assertThat(config.getDuration()).isEqualTo(3600);
        assertThat(config.getStartDownloadAutomatically()).isTrue();
        assertThat(config.getRemoveCompletedJobAutomatically()).isFalse();
        assertThat(config.getClientId()).isEqualTo("client-id");
        assertThat(config.getClientSecret()).isEqualTo("client-secret");
        assertThat(config.getThreadPoolSize()).isEqualTo(5);
        assertThat(config.getYtDlpConfig()).isEqualTo(ytDlpConfig);
    }

    @Test
    void testAllArgsConstructor() {
        YtDlpConfig ytDlpConfig = new YtDlpConfig();
        ytDlpConfig.setName("test-config");

        DownloaderConfig config = new DownloaderConfig(
                "test",
                true,
                120,
                false,
                true,
                "cid",
                "csec",
                10,
                ytDlpConfig
        );

        assertThat(config.getName()).isEqualTo("test");
        assertThat(config.getEnabled()).isTrue();
        assertThat(config.getDuration()).isEqualTo(120);
        assertThat(config.getStartDownloadAutomatically()).isFalse();
        assertThat(config.getRemoveCompletedJobAutomatically()).isTrue();
        assertThat(config.getClientId()).isEqualTo("cid");
        assertThat(config.getClientSecret()).isEqualTo("csec");
        assertThat(config.getThreadPoolSize()).isEqualTo(10);
        assertThat(config.getYtDlpConfig()).isEqualTo(ytDlpConfig);
    }

    @Test
    void testEqualsAndHashCode() {
        YtDlpConfig ytDlpConfig1 = new YtDlpConfig();
        ytDlpConfig1.setName("config1");

        DownloaderConfig config1 = new DownloaderConfig("default", true, 3600, true, false, "id", "secret", 5, ytDlpConfig1);
        DownloaderConfig config2 = new DownloaderConfig("default", true, 3600, true, false, "id", "secret", 5, ytDlpConfig1);
        DownloaderConfig config3 = new DownloaderConfig("other", true, 3600, true, false, "id", "secret", 5, ytDlpConfig1);

        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
        assertThat(config1).isNotEqualTo(config3);
    }
}
