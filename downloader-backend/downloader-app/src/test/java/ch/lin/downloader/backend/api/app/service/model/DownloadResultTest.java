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
package ch.lin.downloader.backend.api.app.service.model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DownloadResultTest {

    @Test
    void testGettersAndSetters() {
        DownloadResult result = new DownloadResult();
        result.setVideoId("vid-1");
        result.setSuccess(true);
        result.setFilePath("/path");
        result.setFileSize(100L);
        result.setErrorMessage("none");
        result.setWarnings(List.of("warn1"));

        assertThat(result.getVideoId()).isEqualTo("vid-1");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFilePath()).isEqualTo("/path");
        assertThat(result.getFileSize()).isEqualTo(100L);
        assertThat(result.getErrorMessage()).isEqualTo("none");
        assertThat(result.getWarnings()).contains("warn1");
    }

    @Test
    void testAddWarning() {
        DownloadResult result = new DownloadResult();
        result.addWarning("warning 1");
        assertThat(result.getWarnings()).contains("warning 1");
    }
}
