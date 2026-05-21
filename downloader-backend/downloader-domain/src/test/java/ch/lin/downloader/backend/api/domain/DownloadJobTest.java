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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DownloadJobTest {

    @Test
    void testConstructorSettersAndGetters() {
        DownloadJob job = new DownloadJob("default-config");
        ReflectionTestUtils.setField(job, "id", "job-1");
        job.setStatus(JobStatus.PENDING);

        OffsetDateTime now = OffsetDateTime.now();
        ReflectionTestUtils.setField(job, "createdAt", now);
        ReflectionTestUtils.setField(job, "updatedAt", now);

        assertThat(job.getId()).isEqualTo("job-1");
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getConfigName()).isEqualTo("default-config");
        assertThat(job.getCreatedAt()).isEqualTo(now);
        assertThat(job.getUpdatedAt()).isEqualTo(now);
        assertThat(job.getTasks()).isNotNull().isEmpty();
    }

    @Test
    void testAddTask() {
        DownloadJob job = new DownloadJob("default-config");
        DownloadTask task = new DownloadTask(job, "vid-1", "Video Title");
        ReflectionTestUtils.setField(task, "id", "task-1");

        job.addTask(task);

        assertThat(job.getTasks()).hasSize(1).contains(task);
        assertThat(task.getJob()).isEqualTo(job);
    }

    @Test
    void testAddTask_ShouldThrowException_WhenTaskBelongsToAnotherJob() {
        DownloadJob job1 = new DownloadJob("config-1");
        DownloadJob job2 = new DownloadJob("config-2");
        DownloadTask taskForJob2 = new DownloadTask(job2, "vid-1", "Video Title");

        assertThatThrownBy(() -> job1.addTask(taskForJob2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The task must be initialized with this job instance.");
    }
}
