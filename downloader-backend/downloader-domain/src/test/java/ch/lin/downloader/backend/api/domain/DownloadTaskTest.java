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
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DownloadTaskTest {

    @Test
    void testConstructorSettersAndGetters() {
        DownloadJob job = new DownloadJob("default-config");
        DownloadTask task = DownloadTask.create(job, "vid-1", "Video Title", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(task), "id", "task-1");

        task.setThumbnailUrl("http://thumb.url");
        task.setDescription("Description");
        task.setStatus(TaskStatus.PENDING);

        OffsetDateTime now = OffsetDateTime.now();
        ReflectionTestUtils.setField(task, "createdAt", now);
        ReflectionTestUtils.setField(task, "updatedAt", now);

        assertThat(task.getId()).isEqualTo("task-1");
        assertThat(task.getJob()).isEqualTo(job);
        assertThat(task.getVideoId()).isEqualTo("vid-1");
        assertThat(task.getTitle()).isEqualTo("Video Title");
        assertThat(task.getThumbnailUrl()).isEqualTo("http://thumb.url");
        assertThat(task.getDescription()).isEqualTo("Description");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(task.getCreatedAt()).isEqualTo(now);
        assertThat(task.getUpdatedAt()).isEqualTo(now);
        assertThat(task.getSubTasks()).isNotNull().hasSize(1);
        assertThat(task.getSubTask(SubTaskType.VIDEO)).isNotNull();
    }

    @Test
    void testCreate_WithExtractAudioTrue_ShouldAddAudioSubTask() {
        DownloadJob job = new DownloadJob("default-config");
        DownloadTask task = DownloadTask.create(job, "vid-1", "Video Title", true);

        assertThat(task.getSubTasks()).hasSize(2);
        assertThat(task.getSubTask(SubTaskType.VIDEO)).isNotNull();
        assertThat(task.getSubTask(SubTaskType.AUDIO)).isNotNull();
    }

    @Test
    void testAddAndGetSubTask() {
        DownloadJob job = new DownloadJob("default-config");
        DownloadTask task = DownloadTask.create(job, "vid-1", "Video Title", false);

        assertThat(task.getSubTasks()).hasSize(1);
        assertThat(task.getSubTask(SubTaskType.AUDIO)).isNull();

        DownloadSubTask audioSubTask = new DownloadSubTask(task, SubTaskType.AUDIO);
        task.addSubTask(audioSubTask);

        assertThat(task.getSubTasks()).hasSize(2).contains(audioSubTask);
        assertThat(task.getSubTask(SubTaskType.AUDIO)).isEqualTo(audioSubTask);
    }

    @Test
    void testAddSubTask_ShouldThrowException_WhenTaskMismatch() {
        DownloadJob job = new DownloadJob("default-config");
        DownloadTask task1 = DownloadTask.create(job, "vid-1", "Video Title 1", false);
        DownloadTask task2 = DownloadTask.create(job, "vid-2", "Video Title 2", false);

        DownloadSubTask subTaskForTask2 = new DownloadSubTask(task2, SubTaskType.VIDEO);

        assertThatThrownBy(() -> task1.addSubTask(subTaskForTask2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SubTask must belong to this task.");
    }

    @Test
    void testUpdateStatusBasedOnSubTasks() {
        DownloadJob job = new DownloadJob("default-config");
        DownloadTask task = DownloadTask.create(job, "vid-1", "Video Title", false);

        // Retrieve the auto-created VIDEO subtask
        DownloadSubTask videoTask = task.getSubTask(SubTaskType.VIDEO);

        // Scenario 1: Initial state is PENDING
        task.updateStatusBasedOnSubTasks();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);

        DownloadSubTask audioTask = new DownloadSubTask(task, SubTaskType.AUDIO);
        task.addSubTask(audioTask);

        // Scenario 2: All PENDING -> PENDING
        task.updateStatusBasedOnSubTasks();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);

        // Scenario 3: One DOWNLOADED, one PENDING -> DOWNLOADING
        audioTask.setStatus(TaskStatus.DOWNLOADED);
        task.updateStatusBasedOnSubTasks();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DOWNLOADING);

        // Scenario 4: One DOWNLOADING -> DOWNLOADING
        videoTask.setStatus(TaskStatus.DOWNLOADING);
        task.updateStatusBasedOnSubTasks();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DOWNLOADING);

        // Scenario 5: All DOWNLOADED -> DOWNLOADED
        videoTask.setStatus(TaskStatus.DOWNLOADED);
        task.updateStatusBasedOnSubTasks();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DOWNLOADED);

        // Scenario 6: Any FAILED -> FAILED
        videoTask.setStatus(TaskStatus.FAILED);
        task.updateStatusBasedOnSubTasks();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);

        audioTask.setStatus(TaskStatus.DOWNLOADING); // Even if another is downloading
        task.updateStatusBasedOnSubTasks();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void testUpdateStatusBasedOnSubTasks_WhenSubTasksIsEmpty() {
        DownloadJob job = new DownloadJob("default-config");
        DownloadTask task = DownloadTask.create(job, "vid-1", "Video Title", false);
        task.getSubTasks().clear();

        task.updateStatusBasedOnSubTasks();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
    }
}
