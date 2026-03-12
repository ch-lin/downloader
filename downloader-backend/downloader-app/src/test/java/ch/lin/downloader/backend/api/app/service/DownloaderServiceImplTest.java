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

import ch.lin.downloader.backend.api.app.repository.DownloadJobRepository;
import ch.lin.downloader.backend.api.app.repository.DownloadTaskRepository;
import ch.lin.downloader.backend.api.app.service.command.DownloadItem;
import ch.lin.downloader.backend.api.app.service.model.DownloadJobDetails;
import ch.lin.downloader.backend.api.app.service.model.DownloadTaskDetails;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.DownloadTask;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.JobStatus;
import ch.lin.downloader.backend.api.domain.TaskStatus;
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
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);
        config.setStartDownloadAutomatically(true);
        config.setRemoveCompletedJobAutomatically(true);

        when(configsService.getResolvedConfig(configName)).thenReturn(config);
        when(downloadJobRepository.save(Objects.requireNonNull(anyDownloadJob()))).thenAnswer(i -> {
            DownloadJob job = i.getArgument(0);
            job.setId("job-id");
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

        verify(executorService).start();
        verify(autoCleanupService).start();
    }

    @Test
    void createDownloadJob_ShouldThrow_WhenConfigNameMismatch() {
        String requestedConfig = "custom";
        DownloaderConfig resolvedConfig = new DownloaderConfig();
        resolvedConfig.setName("default"); // Fallback happened

        when(configsService.getResolvedConfig(requestedConfig)).thenReturn(resolvedConfig);

        List<DownloadItem> items = Collections.emptyList();

        assertThatThrownBy(() -> downloaderService.createDownloadJob(items, requestedConfig))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Configuration with name 'custom' not found");
    }

    @Test
    void createDownloadJob_ShouldNotStartServices_WhenFlagsDisabled() {
        String configName = "manual";
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);
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
        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");

        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(downloadJobRepository.save(Objects.requireNonNull(anyDownloadJob()))).thenAnswer(i -> {
            DownloadJob job = i.getArgument(0);
            job.setId("job-id");
            return job;
        });

        DownloadJob result = downloaderService.createDownloadJob(Collections.emptyList(), configName);

        assertThat(result.getConfigName()).isNull();
    }

    @Test
    void createDownloadJob_ShouldSucceed_WhenConfigNameIsBlank() {
        String configName = "   ";
        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");

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
        DownloadJob job = new DownloadJob();
        job.setId(jobId);
        job.setStatus(JobStatus.COMPLETED);
        job.setConfigName("default");

        DownloadTask task = new DownloadTask();
        task.setId("task-1");
        task.setStatus(TaskStatus.DOWNLOADED);
        task.setProgress(100.0);
        job.addTask(task);

        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        DownloadJobDetails result = downloaderService.getJobById(jobId);

        assertThat(result.getId()).isEqualTo(jobId);
        assertThat(result.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(result.getTasks()).hasSize(1);
        assertThat(result.getTasks().get(0).getId()).isEqualTo("task-1");
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
    void getTaskById_ShouldReturnDetails_WhenTaskExists() {
        String taskId = "task-1";
        DownloadTask task = new DownloadTask();
        task.setId(taskId);
        task.setVideoId("vid");
        DownloadJob job = new DownloadJob();
        job.setId("job-1");
        task.setJob(job);

        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        DownloadTaskDetails result = downloaderService.getTaskById(taskId);

        assertThat(result.getId()).isEqualTo(taskId);
        assertThat(result.getVideoId()).isEqualTo("vid");
        assertThat(result.getJobId()).isEqualTo("job-1");
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
    void deleteTaskById_ShouldDeleteTaskAndUpdateJobStatus() {
        String taskId = "task-1";
        String jobId = "job-1";

        DownloadJob job = new DownloadJob();
        job.setId(jobId);

        DownloadTask taskToDelete = new DownloadTask();
        taskToDelete.setId(taskId);
        taskToDelete.setJob(job);
        taskToDelete.setStatus(TaskStatus.PENDING);

        // Setup job with remaining tasks
        DownloadTask remainingTask = new DownloadTask();
        remainingTask.setId("task-2");
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

        DownloadTask taskToDelete = new DownloadTask();
        taskToDelete.setId(taskId);
        DownloadJob job = new DownloadJob();
        job.setId(jobId);
        taskToDelete.setJob(job);

        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.of(taskToDelete));

        // Job has no tasks left
        DownloadJob emptyJob = new DownloadJob();
        emptyJob.setId(jobId);
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

        DownloadTask task = new DownloadTask();
        task.setId(taskId);
        DownloadJob job = new DownloadJob();
        job.setId(jobId);
        task.setJob(job);

        when(downloadTaskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> downloaderService.deleteTaskById(taskId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Job not found for status update");

        verify(downloadTaskRepository).delete(task);
    }

    @Test
    void deleteTaskById_ShouldUpdateStatusToFailed_WhenRemainingTasksFailed() {
        String taskId = "task-1";
        String jobId = "job-1";

        DownloadTask taskToDelete = new DownloadTask();
        taskToDelete.setId(taskId);
        DownloadJob job = new DownloadJob();
        job.setId(jobId);
        taskToDelete.setJob(job);

        DownloadTask failedTask = new DownloadTask();
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

        DownloadTask taskToDelete = new DownloadTask();
        taskToDelete.setId(taskId);
        DownloadJob job = new DownloadJob();
        job.setId(jobId);
        taskToDelete.setJob(job);

        DownloadTask failedTask = new DownloadTask();
        failedTask.setStatus(TaskStatus.FAILED);
        job.addTask(failedTask);

        DownloadTask completedTask = new DownloadTask();
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
        DownloadJob job = new DownloadJob();
        job.setId(jobId);

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

        DownloadTask taskToDelete = new DownloadTask();
        taskToDelete.setId(taskId);
        DownloadJob job = new DownloadJob();
        job.setId(jobId);
        job.setStatus(JobStatus.IN_PROGRESS);
        taskToDelete.setJob(job);

        // Remaining tasks
        DownloadTask pendingTask = new DownloadTask();
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
        return new DownloadJob();
    }

    private String anyNonNullString() {
        any(String.class);
        return "";
    }
}
