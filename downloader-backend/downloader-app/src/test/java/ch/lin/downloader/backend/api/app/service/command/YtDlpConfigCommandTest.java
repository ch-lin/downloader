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
package ch.lin.downloader.backend.api.app.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import ch.lin.downloader.backend.api.domain.OverwriteOption;

class YtDlpConfigCommandTest {

    @Test
    void testGettersAndSetters() {
        YtDlpConfigCommand command = new YtDlpConfigCommand();
        command.setFormatFiltering("best");
        command.setFormatSorting("res");
        command.setRemuxVideo("mp4");
        command.setWriteDescription(true);
        command.setWriteSubs(true);
        command.setSubLang("en");
        command.setWriteAutoSubs(false);
        command.setSubFormat("srt");
        command.setOutputTemplate("%(title)s.%(ext)s");
        command.setKeepVideo(true);
        command.setExtractAudio(false);
        command.setAudioFormat("mp3");
        command.setAudioQuality(0);
        command.setOverwrite(OverwriteOption.FORCE);
        command.setNoProgress(true);
        command.setCookie("cookie-data");

        assertThat(command.getFormatFiltering()).isEqualTo("best");
        assertThat(command.getFormatSorting()).isEqualTo("res");
        assertThat(command.getRemuxVideo()).isEqualTo("mp4");
        assertThat(command.getWriteDescription()).isTrue();
        assertThat(command.getWriteSubs()).isTrue();
        assertThat(command.getSubLang()).isEqualTo("en");
        assertThat(command.getWriteAutoSubs()).isFalse();
        assertThat(command.getSubFormat()).isEqualTo("srt");
        assertThat(command.getOutputTemplate()).isEqualTo("%(title)s.%(ext)s");
        assertThat(command.getKeepVideo()).isTrue();
        assertThat(command.getExtractAudio()).isFalse();
        assertThat(command.getAudioFormat()).isEqualTo("mp3");
        assertThat(command.getAudioQuality()).isEqualTo(0);
        assertThat(command.getOverwrite()).isEqualTo(OverwriteOption.FORCE);
        assertThat(command.getNoProgress()).isTrue();
        assertThat(command.getCookie()).isEqualTo("cookie-data");
    }
}
