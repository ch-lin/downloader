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

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import ch.lin.downloader.backend.api.domain.TaskStatus;

class DownloadTaskDetailsTest {

    @Test
    void testGettersAndSetters() {
        DownloadTaskDetails details = new DownloadTaskDetails();
        details.setId("task-1");
        details.setJobId("job-1");
        details.setVideoId("vid-1");
        details.setTitle("Title");
        details.setThumbnailUrl("url");
        details.setDescription("desc");
        details.setStatus(TaskStatus.DOWNLOADING);
        details.setProgress(50.0);
        details.setFilePath("/path");
        details.setFileSize(1024L);
        details.setErrorMessage("err");
        OffsetDateTime now = OffsetDateTime.now();
        details.setCreatedAt(now);
        details.setUpdatedAt(now);

        assertThat(details.getId()).isEqualTo("task-1");
        assertThat(details.getJobId()).isEqualTo("job-1");
        assertThat(details.getVideoId()).isEqualTo("vid-1");
        assertThat(details.getTitle()).isEqualTo("Title");
        assertThat(details.getThumbnailUrl()).isEqualTo("url");
        assertThat(details.getDescription()).isEqualTo("desc");
        assertThat(details.getStatus()).isEqualTo(TaskStatus.DOWNLOADING);
        assertThat(details.getProgress()).isEqualTo(50.0);
        assertThat(details.getFilePath()).isEqualTo("/path");
        assertThat(details.getFileSize()).isEqualTo(1024L);
        assertThat(details.getErrorMessage()).isEqualTo("err");
        assertThat(details.getCreatedAt()).isEqualTo(now);
        assertThat(details.getUpdatedAt()).isEqualTo(now);
    }
}
