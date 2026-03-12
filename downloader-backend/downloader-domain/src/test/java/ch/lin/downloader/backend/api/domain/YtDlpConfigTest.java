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

class YtDlpConfigTest {

    @Test
    void testNoArgsConstructorAndSetters() {
        YtDlpConfig config = new YtDlpConfig();
        config.setName("default");
        config.setFormatFiltering("best");
        config.setFormatSorting("res:1080");
        config.setRemuxVideo("mp4");
        config.setWriteDescription(true);
        config.setWriteSubs(true);
        config.setSubLang("en");
        config.setWriteAutoSubs(false);
        config.setSubFormat("srt");
        config.setOutputTemplate("%(title)s.%(ext)s");
        config.setOverwrite(OverwriteOption.FORCE);
        config.setKeepVideo(true);
        config.setExtractAudio(false);
        config.setAudioFormat("mp3");
        config.setAudioQuality(0);
        config.setNoProgress(true);
        config.setUseCookie(true);
        config.setCookie("cookie-content");

        assertThat(config.getName()).isEqualTo("default");
        assertThat(config.getFormatFiltering()).isEqualTo("best");
        assertThat(config.getFormatSorting()).isEqualTo("res:1080");
        assertThat(config.getRemuxVideo()).isEqualTo("mp4");
        assertThat(config.getWriteDescription()).isTrue();
        assertThat(config.getWriteSubs()).isTrue();
        assertThat(config.getSubLang()).isEqualTo("en");
        assertThat(config.getWriteAutoSubs()).isFalse();
        assertThat(config.getSubFormat()).isEqualTo("srt");
        assertThat(config.getOutputTemplate()).isEqualTo("%(title)s.%(ext)s");
        assertThat(config.getOverwrite()).isEqualTo(OverwriteOption.FORCE);
        assertThat(config.getKeepVideo()).isTrue();
        assertThat(config.getExtractAudio()).isFalse();
        assertThat(config.getAudioFormat()).isEqualTo("mp3");
        assertThat(config.getAudioQuality()).isEqualTo(0);
        assertThat(config.getNoProgress()).isTrue();
        assertThat(config.getUseCookie()).isTrue();
        assertThat(config.getCookie()).isEqualTo("cookie-content");
    }

    @Test
    void testAllArgsConstructor() {
        YtDlpConfig config = new YtDlpConfig(
                "default",
                "best",
                "res:1080",
                "mp4",
                true,
                true,
                "en",
                false,
                "srt",
                "%(title)s.%(ext)s",
                OverwriteOption.FORCE,
                true,
                false,
                "mp3",
                0,
                true,
                true,
                "cookie-content"
        );

        assertThat(config.getName()).isEqualTo("default");
        assertThat(config.getFormatFiltering()).isEqualTo("best");
        assertThat(config.getUseCookie()).isTrue();
        assertThat(config.getCookie()).isEqualTo("cookie-content");
    }

    @Test
    void testEqualsAndHashCode() {
        YtDlpConfig config1 = new YtDlpConfig();
        config1.setName("config1");
        YtDlpConfig config2 = new YtDlpConfig();
        config2.setName("config1");

        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }
}
