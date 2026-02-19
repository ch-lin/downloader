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

class DownloadJobTest {

    @Test
    void testSettersAndGetters() {
        DownloadJob job = new DownloadJob();
        job.setId("job-1");
        job.setStatus(JobStatus.PENDING);
        job.setConfigName("default-config");

        OffsetDateTime now = OffsetDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);

        assertThat(job.getId()).isEqualTo("job-1");
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getConfigName()).isEqualTo("default-config");
        assertThat(job.getCreatedAt()).isEqualTo(now);
        assertThat(job.getUpdatedAt()).isEqualTo(now);
        assertThat(job.getTasks()).isNotNull().isEmpty();
    }

    @Test
    void testAddTask() {
        DownloadJob job = new DownloadJob();
        DownloadTask task = new DownloadTask();
        task.setId("task-1");

        job.addTask(task);

        assertThat(job.getTasks()).hasSize(1).contains(task);
        assertThat(task.getJob()).isEqualTo(job);
    }

    @Test
    void testPrePersist() {
        DownloadJob job = new DownloadJob();
        job.onCreate();

        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(job.getUpdatedAt()).isNotNull();
    }

    @Test
    void testPreUpdate() throws InterruptedException {
        DownloadJob job = new DownloadJob();
        job.onCreate();
        OffsetDateTime initialUpdatedAt = job.getUpdatedAt();

        Thread.sleep(10); // Ensure time advances
        job.onUpdate();

        assertThat(job.getUpdatedAt()).isAfter(initialUpdatedAt);
        assertThat(job.getCreatedAt()).isNotNull();
    }
}
