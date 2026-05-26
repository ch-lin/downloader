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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import ch.lin.downloader.backend.api.domain.SubTaskType;
import ch.lin.downloader.backend.api.domain.TaskStatus;

class DownloadSubTaskDetailsTest {

    @Test
    void testGettersAndSetters() {
        DownloadSubTaskDetails details = new DownloadSubTaskDetails();
        details.setId("subtask-1");
        details.setType(SubTaskType.VIDEO);
        details.setStatus(TaskStatus.DOWNLOADING);
        details.setProgress(75.5);
        details.setFilePath("/tmp/video.mp4");
        details.setFileSize(2048L);
        details.setErrorMessage("error");

        assertThat(details.getId()).isEqualTo("subtask-1");
        assertThat(details.getType()).isEqualTo(SubTaskType.VIDEO);
        assertThat(details.getStatus()).isEqualTo(TaskStatus.DOWNLOADING);
        assertThat(details.getProgress()).isEqualTo(75.5);
        assertThat(details.getFilePath()).isEqualTo("/tmp/video.mp4");
        assertThat(details.getFileSize()).isEqualTo(2048L);
        assertThat(details.getErrorMessage()).isEqualTo("error");
    }

    @Test
    void testAllArgsConstructor() {
        DownloadSubTaskDetails details = new DownloadSubTaskDetails("subtask-1", SubTaskType.AUDIO, TaskStatus.DOWNLOADED, 100.0, "/tmp/audio.mp3", 1024L, null);
        assertThat(details.getId()).isEqualTo("subtask-1");
        assertThat(details.getType()).isEqualTo(SubTaskType.AUDIO);
        assertThat(details.getStatus()).isEqualTo(TaskStatus.DOWNLOADED);
        assertThat(details.getProgress()).isEqualTo(100.0);
        assertThat(details.getFilePath()).isEqualTo("/tmp/audio.mp3");
        assertThat(details.getFileSize()).isEqualTo(1024L);
        assertThat(details.getErrorMessage()).isNull();
    }
}
