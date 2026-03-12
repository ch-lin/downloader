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

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DownloadTaskTest {

    @Test
    void testSettersAndGetters() {
        DownloadTask task = new DownloadTask();
        task.setId("task-1");
        DownloadJob job = new DownloadJob();
        task.setJob(job);
        task.setVideoId("vid-1");
        task.setTitle("Video Title");
        task.setThumbnailUrl("http://thumb.url");
        task.setDescription("Description");
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0.0);
        task.setFilePath("/path/to/file");
        task.setFileSize(1024L);
        task.setErrorMessage("Error");

        OffsetDateTime now = OffsetDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        assertThat(task.getId()).isEqualTo("task-1");
        assertThat(task.getJob()).isEqualTo(job);
        assertThat(task.getVideoId()).isEqualTo("vid-1");
        assertThat(task.getTitle()).isEqualTo("Video Title");
        assertThat(task.getThumbnailUrl()).isEqualTo("http://thumb.url");
        assertThat(task.getDescription()).isEqualTo("Description");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(task.getProgress()).isEqualTo(0.0);
        assertThat(task.getFilePath()).isEqualTo("/path/to/file");
        assertThat(task.getFileSize()).isEqualTo(1024L);
        assertThat(task.getErrorMessage()).isEqualTo("Error");
        assertThat(task.getCreatedAt()).isEqualTo(now);
        assertThat(task.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void testPrePersist() {
        DownloadTask task = new DownloadTask();
        task.onCreate();

        assertThat(task.getCreatedAt()).isNotNull();
        assertThat(task.getUpdatedAt()).isNotNull();
    }

    @Test
    void testPreUpdate() throws InterruptedException {
        DownloadTask task = new DownloadTask();
        task.onCreate();
        OffsetDateTime initialUpdatedAt = task.getUpdatedAt();

        Thread.sleep(10); // Ensure time advances
        task.onUpdate();

        assertThat(task.getUpdatedAt()).isAfter(initialUpdatedAt);
        assertThat(task.getCreatedAt()).isNotNull();
    }
}
