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
package ch.lin.downloader.backend.api.app.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ch.lin.downloader.backend.api.app.repository.DownloadJobRepository;
import ch.lin.downloader.backend.api.app.repository.DownloadTaskRepository;
import ch.lin.downloader.backend.api.app.service.command.DownloadItem;
import ch.lin.downloader.backend.api.app.service.model.DownloadJobDetails;
import ch.lin.downloader.backend.api.app.service.model.DownloadTaskDetails;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.DownloadSubTask;
import ch.lin.downloader.backend.api.domain.DownloadTask;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.JobStatus;
import ch.lin.downloader.backend.api.domain.SubTaskType;
import ch.lin.downloader.backend.api.domain.TaskStatus;
import ch.lin.downloader.backend.api.domain.YtDlpConfig;
import ch.lin.platform.exception.InvalidRequestException;

@ExtendWith(MockitoExtension.class)
class DownloaderServiceImplTest {

    @Mock
    private DownloadJobRepository downloadJobRepository;
    @Mock
    private DownloadTaskRepository downloadTaskRepository;
    @Mock
    private ConfigsService configsService;
    @Mock
    private ExecutorService executorService;
    @Mock
    private AutoCleanupService autoCleanupService;

    @InjectMocks
    private DownloaderServiceImpl downloaderService;

    @Test
    void createDownloadJob_ShouldCreateJobAndTasks_WhenConfigIsValid() {
        String configName = "default";
        DownloaderConfig config = new DownloaderConfig(configName);
        config.setStartDownloadAutomatically(true);
        config.setRemoveCompletedJobAutomatically(true);

        YtDlpConfig ytDlpConfig = new YtDlpConfig(configName);
        ytDlpConfig.setExtractAudio(true);
        config.setYtDlpConfig(ytDlpConfig);

        when(configsService.getResolvedConfig(configName)).thenReturn(config);
        when(downloadJobRepository.save(Objects.requireNonNull(anyDownloadJob()))).thenAnswer(i -> {
            DownloadJob job = i.getArgument(0);
            ReflectionTestUtils.setField(Objects.requireNonNull(job), "id", "job-id");
            return job;
        });

        List<DownloadItem> items = new ArrayList<>();
        DownloadItem item = new DownloadItem();
        item.setVideoId("vid1");
        item.setTitle("Title 1");
        items.add(item);

        DownloadJob result = downloaderService.createDownloadJob(items, configName);

        assertThat(result.getId()).isEqualTo("job-id");
        assertThat(result.getConfigName()).isEqualTo(configName);
        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(result.getTasks()).hasSize(1);
        assertThat(result.getTasks().get(0).getVideoId()).isEqualTo("vid1");
        assertThat(result.getTasks().get(0).getSubTasks()).hasSize(2);
        assertThat(result.getTasks().get(0).getSubTask(SubTaskType.AUDIO)).isNotNull();
        assertThat(result.getTasks().get(0).getSubTask(SubTaskType.VIDEO)).isNotNull();

        verify(executorService).start();
        verify(autoCleanupService).start();
    }

    @Test
    void createDownloadJob_ShouldNotAddAudioSubTask_WhenExtractAudioIsFalseOrNull() {
        String configName = "default";
        DownloaderConfig config = new DownloaderConfig(configName);

        // Set extractAudio to false
        YtDlpConfig ytDlpConfig = new YtDlpConfig(configName);
        ytDlpConfig.setExtractAudio(false);
        config.setYtDlpConfig(ytDlpConfig);

        when(configsService.getResolvedConfig(configName)).thenReturn(config);
        when(downloadJobRepository.save(Objects.requireNonNull(anyDownloadJob()))).thenAnswer(i -> {
            DownloadJob job = i.getArgument(0);
            ReflectionTestUtils.setField(Objects.requireNonNull(job), "id", "job-id");
            return job;
        });

        List<DownloadItem> items = new ArrayList<>();
        DownloadItem item = new DownloadItem();
        item.setVideoId("vid2");
        item.setTitle("Title 2");
        items.add(item);

        DownloadJob result = downloaderService.createDownloadJob(items, configName);

        assertThat(result.getTasks()).hasSize(1);
        DownloadTask task = result.getTasks().get(0);
        assertThat(task.getSubTasks()).hasSize(1); // Only the default VIDEO
        assertThat(task.getSubTask(SubTaskType.AUDIO)).isNull();
    }

    @Test
    void createDownloadJob_ShouldNotAddAudioSubTask_WhenYtDlpConfigIsNull() {
        String configName = "default";
        DownloaderConfig config = new DownloaderConfig(configName);
        config.setYtDlpConfig(null); // Trigger the fail-safe branch where config.getYtDlpConfig() == null

        when(configsService.getResolvedConfig(configName)).thenReturn(config);
        when(downloadJobRepository.save(Objects.requireNonNull(anyDownloadJob()))).thenAnswer(i -> {
            DownloadJob job = i.getArgument(0);
            ReflectionTestUtils.setField(Objects.requireNonNull(job), "id", "job-id");
            return job;
        });

        List<DownloadItem> items = new ArrayList<>();
        DownloadItem item = new DownloadItem();
        item.setVideoId("vid3");
        item.setTitle("Title 3");
        items.add(item);

        DownloadJob result = downloaderService.createDownloadJob(items, configName);

        assertThat(result.getTasks()).hasSize(1);
        DownloadTask task = result.getTasks().get(0);
        assertThat(task.getSubTasks()).hasSize(1); // Only the default VIDEO
        assertThat(task.getSubTask(SubTaskType.AUDIO)).isNull();
    }

    @Test
    void createDownloadJob_ShouldThrow_WhenConfigNameMismatch() {
        String requestedConfig = "custom";
        DownloaderConfig resolvedConfig = new DownloaderConfig("default"); // Fallback happened

        when(configsService.getResolvedConfig(requestedConfig)).thenReturn(resolvedConfig);

        List<DownloadItem> items = Collections.emptyList();

        assertThatThrownBy(() -> downloaderService.createDownloadJob(items, requestedConfig))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Configuration with name 'custom' not found");
    }

    @Test
    void createDownloadJob_ShouldNotStartServices_WhenFlagsDisabled() {
        String configName = "manual";
        DownloaderConfig config = new DownloaderConfig(configName);
        config.setStartDownloadAutomatically(false);
        config.setRemoveCompletedJobAutomatically(false);

        when(configsService.getResolvedConfig(configName)).thenReturn(config);
        when(downloadJobRepository.save(Objects.requireNonNull(anyDownloadJob()))).thenAnswer(i -> i.getArgument(0));

        downloaderService.createDownloadJob(Collections.emptyList(), configName);

        verify(executorService, never()).start();
        verify(autoCleanupService, never()).start();
    }

    @Test
    void createDownloadJob_ShouldSucceed_WhenConfigNameIsNull() {
        String configName = null;
        DownloaderConfig config = new DownloaderConfig("default");

        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(downloadJobRepository.save(Objects.requireNonNull(anyDownloadJob()))).thenAnswer(i -> {
            DownloadJob job = i.getArgument(0);
            ReflectionTestUtils.setField(Objects.requireNonNull(job), "id", "job-id");
            return job;
        });

        DownloadJob result = downloaderService.createDownloadJob(Collections.emptyList(), configName);

        assertThat(result.getConfigName()).isNull();
    }

    @Test
    void createDownloadJob_ShouldSucceed_WhenConfigNameIsBlank() {
        String configName = "   ";
        DownloaderConfig config = new DownloaderConfig("default");

        when(configsService.getResolvedConfig(configName)).thenReturn(config);
        when(downloadJobRepository.save(Objects.requireNonNull(anyDownloadJob()))).thenAnswer(i -> i.getArgument(0));

        DownloadJob result = downloaderService.createDownloadJob(Collections.emptyList(), configName);

        assertThat(result.getConfigName()).isEqualTo("   ");
    }

    @Test
    void deleteAllJobs_ShouldCleanTables() {
        downloaderService.deleteAllJobs();
        verify(downloadTaskRepository).cleanTable();
        verify(downloadJobRepository).cleanTable();
    }

    @Test
    void getJobById_ShouldReturnDetails_WhenJobExists() {
        String jobId = "job-1";
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);
        job.setStatus(JobStatus.COMPLETED);

        DownloadTask task = DownloadTask.create(job, "vid", "title", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(task), "id", "task-1");
        task.setStatus(TaskStatus.DOWNLOADED);
        DownloadSubTask subTask = task.getSubTask(SubTaskType.VIDEO);
        subTask.setProgress(100.0);
        job.addTask(task);

        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        DownloadJobDetails result = downloaderService.getJobById(jobId);

        assertThat(result.getId()).isEqualTo(jobId);
        assertThat(result.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(result.getTasks()).hasSize(1);
        assertThat(result.getTasks().get(0).getId()).isEqualTo("task-1");
        assertThat(result.getTasks().get(0).getProgress()).isEqualTo(100.0);
    }

    @Test
    void getJobById_ShouldThrow_WhenJobNotFound() {
        String jobId = "non-existent";
        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> downloaderService.getJobById(jobId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Job not found");
    }

    @Test
    void getJobById_ShouldCalculateProgressSafely_WhenSubTasksEmptyOrProgressNull() {
        String jobId = "job-edge-progress";
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);

        // Scenario 1: Task has no subtasks (triggers task.getSubTasks().isEmpty() -> 0.0)
        DownloadTask task1 = DownloadTask.create(job, "vid-1", "Title 1", false);
        task1.getSubTasks().clear();
        job.addTask(task1);

        // Scenario 2: Task has subtasks, but progress is null (triggers p != null ? p : 0.0)
        DownloadTask task2 = DownloadTask.create(job, "vid-2", "Title 2", false);
        task2.getSubTask(SubTaskType.VIDEO).setProgress(null);
        job.addTask(task2);

        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        DownloadJobDetails result = downloaderService.getJobById(jobId);

        assertThat(result.getTasks()).hasSize(2);
        assertThat(result.getTasks().get(0).getProgress()).isEqualTo(0.0);
        assertThat(result.getTasks().get(1).getProgress()).isEqualTo(0.0);
    }

    @Test
    void getTaskById_ShouldReturnDetails_WhenTaskExists() {
        String taskId = "task-1";
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-1");
        DownloadTask task = DownloadTask.create(job, "vid", "Title", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(task), "id", taskId);

        DownloadSubTask videoTask = task.getSubTask(SubTaskType.VIDEO);
        videoTask.setFilePath("/path/to/video.mp4");
        videoTask.setFileSize(1024L);
        videoTask.setProgress(50.0);

        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        DownloadTaskDetails result = downloaderService.getTaskById(taskId);

        assertThat(result.getId()).isEqualTo(taskId);
        assertThat(result.getVideoId()).isEqualTo("vid");
        assertThat(result.getJobId()).isEqualTo("job-1");
        assertThat(result.getSubTasks()).hasSize(1);
        assertThat(result.getSubTasks().get(0).getProgress()).isEqualTo(50.0);
        assertThat(result.getSubTasks().get(0).getFilePath()).isEqualTo("/path/to/video.mp4");
        assertThat(result.getSubTasks().get(0).getFileSize()).isEqualTo(1024L);
    }

    @Test
    void getTaskById_ShouldThrow_WhenTaskNotFound() {
        String taskId = "non-existent";
        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> downloaderService.getTaskById(taskId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Task with id " + taskId + " not found");
    }

    @Test
    void getTaskById_ShouldCalculateProgressSafely_WhenEmptyOrNull() {
        // Scenario 1: Task has no subtasks (triggers task.getSubTasks().isEmpty() -> 0.0)
        DownloadJob job = new DownloadJob("default");
        DownloadTask task1 = DownloadTask.create(job, "vid-1", "Title 1", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(task1), "id", "task-1");
        task1.getSubTasks().clear(); // Force clear subtasks

        when(downloadTaskRepository.findById("task-1")).thenReturn(Optional.of(task1));
        downloaderService.getTaskById("task-1"); // Should not throw exception when calculating progress

        // Scenario 2: Task has subtasks, but progress is null (triggers p != null ? p : 0.0)
        DownloadTask task2 = DownloadTask.create(job, "vid-2", "Title 2", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(task2), "id", "task-2");
        task2.getSubTask(SubTaskType.VIDEO).setProgress(null); // Force progress to null

        when(downloadTaskRepository.findById("task-2")).thenReturn(Optional.of(task2));
        downloaderService.getTaskById("task-2"); // Should not throw exception when calculating progress
    }

    @Test
    void deleteTaskById_ShouldDeleteTaskAndUpdateJobStatus() {
        String taskId = "task-1";
        String jobId = "job-1";

        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);

        DownloadTask taskToDelete = DownloadTask.create(job, "vid1", "title", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(taskToDelete), "id", taskId);
        taskToDelete.setStatus(TaskStatus.PENDING);

        // Setup job with remaining tasks
        DownloadTask remainingTask = DownloadTask.create(job, "vid2", "title2", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(remainingTask), "id", "task-2");
        remainingTask.setStatus(TaskStatus.DOWNLOADED);
        job.addTask(remainingTask); // Only remaining task after deletion logic (mocked)

        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.of(taskToDelete));
        // Mock finding job after task deletion to update status
        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        downloaderService.deleteTaskById(taskId);

        verify(downloadTaskRepository).delete(taskToDelete);
        // Since remaining task is DOWNLOADED, job should be COMPLETED
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        verify(downloadJobRepository).save(job);
    }

    @Test
    void deleteTaskById_ShouldMarkJobCompleted_WhenLastTaskDeleted() {
        String taskId = "task-1";
        String jobId = "job-1";

        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);
        DownloadTask taskToDelete = DownloadTask.create(job, "vid", "title", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(taskToDelete), "id", taskId);

        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.of(taskToDelete));

        // Job has no tasks left
        DownloadJob emptyJob = new DownloadJob("default");
        ReflectionTestUtils.setField(emptyJob, "id", jobId);
        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(emptyJob));

        downloaderService.deleteTaskById(taskId);

        verify(downloadTaskRepository).delete(taskToDelete);
        assertThat(emptyJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        verify(downloadJobRepository).save(emptyJob);
    }

    @Test
    void deleteTaskById_ShouldThrowIllegalState_WhenJobNotFoundForStatusUpdate() {
        String taskId = "task-1";
        String jobId = "job-1";

        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);
        DownloadTask task = DownloadTask.create(job, "vid", "title", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(task), "id", taskId);

        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> downloaderService.deleteTaskById(taskId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Job not found for status update");

        verify(downloadTaskRepository).delete(Objects.requireNonNull(task));
    }

    @Test
    void deleteTaskById_ShouldUpdateStatusToFailed_WhenRemainingTasksFailed() {
        String taskId = "task-1";
        String jobId = "job-1";

        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);
        DownloadTask taskToDelete = DownloadTask.create(job, "vid1", "title", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(taskToDelete), "id", taskId);

        DownloadTask failedTask = DownloadTask.create(job, "vid2", "title2", false);
        failedTask.setStatus(TaskStatus.FAILED);
        job.addTask(failedTask);

        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.of(taskToDelete));
        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        downloaderService.deleteTaskById(taskId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        verify(downloadJobRepository).save(job);
    }

    @Test
    void deleteTaskById_ShouldUpdateStatusToPartiallyCompleted_WhenMixedTasks() {
        String taskId = "task-1";
        String jobId = "job-1";

        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);
        DownloadTask taskToDelete = DownloadTask.create(job, "vid", "title", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(taskToDelete), "id", taskId);

        DownloadTask failedTask = DownloadTask.create(job, "vid2", "title", false);
        failedTask.setStatus(TaskStatus.FAILED);
        job.addTask(failedTask);

        DownloadTask completedTask = DownloadTask.create(job, "vid3", "title3", false);
        completedTask.setStatus(TaskStatus.DOWNLOADED);
        job.addTask(completedTask);

        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.of(taskToDelete));
        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        downloaderService.deleteTaskById(taskId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PARTIALLY_COMPLETED);
        verify(downloadJobRepository).save(job);
    }

    @Test
    void deleteTaskById_ShouldThrow_WhenTaskNotFound() {
        String taskId = "non-existent";
        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> downloaderService.deleteTaskById(taskId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Task with id " + taskId + " not found");
    }

    @Test
    void deleteJobById_ShouldDeleteJob_WhenJobExists() {
        String jobId = "job-1";
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);

        when(downloadJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        downloaderService.deleteJobById(jobId);

        verify(downloadJobRepository).delete(job);
    }

    @Test
    void deleteJobById_ShouldThrow_WhenJobNotFound() {
        String jobId = "non-existent";
        when(downloadJobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> downloaderService.deleteJobById(jobId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Job not found");
    }

    @Test
    void deleteJobById_ShouldDoNothing_WhenIdIsNull() {
        downloaderService.deleteJobById(null);
        verify(downloadJobRepository, never()).findById(Objects.requireNonNull(anyNonNullString()));
    }

    @Test
    void deleteTaskById_ShouldNotUpdateJobStatus_WhenTasksPending() {
        String taskId = "task-1";
        String jobId = "job-1";

        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);
        job.setStatus(JobStatus.IN_PROGRESS);
        DownloadTask taskToDelete = DownloadTask.create(job, "vid", "title", false);
        ReflectionTestUtils.setField(Objects.requireNonNull(taskToDelete), "id", taskId);

        // Remaining tasks
        DownloadTask pendingTask = DownloadTask.create(job, "vid2", "title", false);
        pendingTask.setStatus(TaskStatus.PENDING);
        job.addTask(pendingTask);

        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.of(taskToDelete));
        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        downloaderService.deleteTaskById(taskId);

        verify(downloadTaskRepository).delete(taskToDelete);
        verify(downloadJobRepository, never()).save(job);
    }

    private DownloadJob anyDownloadJob() {
        any(DownloadJob.class);
        return new DownloadJob("default");
    }

    private String anyNonNullString() {
        any(String.class);
        return "";
    }
}
