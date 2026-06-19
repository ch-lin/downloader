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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import ch.lin.downloader.backend.api.app.repository.DownloadJobRepository;
import ch.lin.downloader.backend.api.app.repository.DownloadTaskRepository;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.DownloadTask;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.JobStatus;
import ch.lin.downloader.backend.api.domain.TaskStatus;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AutoCleanupServiceImplTest {

    @Mock
    private ConfigsService configsService;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private DownloadJobRepository downloadJobRepository;
    @Mock
    private DownloadTaskRepository downloadTaskRepository;

    private AutoCleanupServiceImpl autoCleanupService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        autoCleanupService = new AutoCleanupServiceImpl(configsService, taskScheduler, downloadJobRepository,
                downloadTaskRepository);
    }

    @Test
    void init_ShouldStartScheduler_WhenConfigEnabled() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setRemoveCompletedJobAutomatically(true);
        config.setDuration(60);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(taskScheduler.scheduleWithFixedDelay(anyRunnable(), eqDuration(Duration.ofSeconds(60))))
                .thenAnswer(invocation -> future);

        autoCleanupService.init();

        verify(taskScheduler).scheduleWithFixedDelay(anyRunnable(), eqDuration(Duration.ofSeconds(60)));
        assertThat(autoCleanupService.isSchedulerRunning()).isTrue();
    }

    @Test
    void init_ShouldNotStartScheduler_WhenConfigDisabled() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setRemoveCompletedJobAutomatically(false);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        autoCleanupService.init();

        verify(taskScheduler, never()).scheduleWithFixedDelay(anyRunnable(), anyDuration());
        assertThat(autoCleanupService.isSchedulerRunning()).isFalse();
    }

    @Test
    void start_ShouldScheduleTask_WhenNotRunning() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(taskScheduler.scheduleWithFixedDelay(anyRunnable(), eqDuration(Duration.ofSeconds(30))))
                .thenAnswer(invocation -> future);

        autoCleanupService.start();

        verify(taskScheduler).scheduleWithFixedDelay(anyRunnable(), eqDuration(Duration.ofSeconds(30)));
        assertThat(autoCleanupService.isSchedulerRunning()).isTrue();
    }

    @Test
    void start_ShouldNotSchedule_WhenAlreadyRunning() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(taskScheduler.scheduleWithFixedDelay(anyRunnable(), eqDuration(Duration.ofSeconds(30))))
                .thenAnswer(invocation -> future);

        autoCleanupService.start(); // First start
        autoCleanupService.start(); // Second start

        verify(taskScheduler, times(1)).scheduleWithFixedDelay(anyRunnable(), anyDuration());
    }

    @Test
    void start_ShouldRestart_WhenTaskIsDone() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(true);
        when(taskScheduler.scheduleWithFixedDelay(anyRunnable(), eqDuration(Duration.ofSeconds(30))))
                .thenAnswer(invocation -> future);

        autoCleanupService.start(); // First start, future is done immediately
        autoCleanupService.start(); // Second start, should schedule again

        verify(taskScheduler, times(2)).scheduleWithFixedDelay(anyRunnable(), anyDuration());
    }

    @Test
    void stop_ShouldCancelTask_WhenRunning() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(taskScheduler.scheduleWithFixedDelay(anyRunnable(), eqDuration(Duration.ofSeconds(30))))
                .thenAnswer(invocation -> future);

        autoCleanupService.start();
        autoCleanupService.stop();

        verify(future).cancel(false);
    }

    @Test
    void stop_ShouldNotCancel_WhenTaskIsDone() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(true);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(anyRunnable(), eqDuration(Duration.ofSeconds(30)));

        autoCleanupService.start();
        autoCleanupService.stop();

        verify(future, never()).cancel(any(boolean.class));
    }

    @Test
    void stop_ShouldDoNothing_WhenNotRunning() {
        autoCleanupService.stop();
        // No interaction expected with taskScheduler or future
    }

    @Test
    void cleanupCompletedJobs_ShouldDeleteJobs_WhenJobsExist() {
        when(downloadJobRepository.findAllByStatusInWithTasks(
                eq((java.util.Collection<JobStatus>) List.of(JobStatus.FAILED, JobStatus.PARTIALLY_COMPLETED))))
                .thenReturn(Collections.emptyList());
        DownloadJob job = new DownloadJob("default");
        when(downloadJobRepository.findAllByStatus(JobStatus.COMPLETED)).thenReturn(List.of(job));

        autoCleanupService.cleanupCompletedJobs();

        verify(downloadJobRepository).deleteAll(List.of(job));
    }

    @Test
    void cleanupCompletedJobs_ShouldDoNothing_WhenNoJobsExist() {
        when(downloadJobRepository.findAllByStatusInWithTasks(
                eq((java.util.Collection<JobStatus>) List.of(JobStatus.FAILED, JobStatus.PARTIALLY_COMPLETED))))
                .thenReturn(Collections.emptyList());
        when(downloadJobRepository.findAllByStatus(JobStatus.COMPLETED)).thenReturn(Collections.emptyList());

        autoCleanupService.cleanupCompletedJobs();

        verify(downloadJobRepository, never()).deleteAll(any());
    }

    @Test
    void cleanupCompletedJobs_ShouldDeleteResolvedJobAndTask_WhenAllFailedTasksResolved() {
        DownloadJob oldJob = new DownloadJob("default");
        DownloadTask failedTask = DownloadTask.create(oldJob, "video123", "Title", false);
        failedTask.setStatus(TaskStatus.FAILED);
        oldJob.addTask(failedTask);

        when(downloadJobRepository.findAllByStatusInWithTasks(
                eq((java.util.Collection<JobStatus>) List.of(JobStatus.FAILED, JobStatus.PARTIALLY_COMPLETED))))
                .thenReturn(List.of(oldJob));
        when(downloadTaskRepository.findActiveVideoIds(eq(Set.of("video123")), eq(List.of(TaskStatus.DOWNLOADED))))
                .thenReturn(Set.of("video123"));
        when(downloadJobRepository.findAllByStatus(JobStatus.COMPLETED))
                .thenReturn(Collections.emptyList());

        autoCleanupService.cleanupCompletedJobs();

        verify(downloadTaskRepository).deleteAll(List.of(failedTask));
        verify(downloadTaskRepository).flush();
        verify(downloadJobRepository).deleteAll(List.of(oldJob));
    }

    @Test
    void cleanupCompletedJobs_ShouldKeepJobAndUpdateStatus_WhenSomeFailedTasksRemainUnresolved() {
        DownloadJob oldJob = new DownloadJob("default");
        oldJob.setStatus(JobStatus.FAILED);
        DownloadTask resolvedTask = DownloadTask.create(oldJob, "video1", "Title 1", false);
        resolvedTask.setStatus(TaskStatus.FAILED);
        oldJob.addTask(resolvedTask);

        DownloadTask unresolvedTask = DownloadTask.create(oldJob, "video2", "Title 2", false);
        unresolvedTask.setStatus(TaskStatus.FAILED);
        oldJob.addTask(unresolvedTask);

        when(downloadJobRepository.findAllByStatusInWithTasks(
                eq((java.util.Collection<JobStatus>) List.of(JobStatus.FAILED, JobStatus.PARTIALLY_COMPLETED))))
                .thenReturn(List.of(oldJob));
        when(downloadTaskRepository.findActiveVideoIds(eq(Set.of("video1", "video2")),
                eq(List.of(TaskStatus.DOWNLOADED))))
                .thenReturn(Set.of("video1"));
        when(downloadJobRepository.findAllByStatus(JobStatus.COMPLETED))
                .thenReturn(Collections.emptyList());

        autoCleanupService.cleanupCompletedJobs();

        verify(downloadTaskRepository).deleteAll(List.of(resolvedTask));
        verify(downloadTaskRepository).flush();
        verify(downloadJobRepository, never()).deleteAll(List.of(oldJob));
        assertThat(oldJob.getTasks()).containsExactly(unresolvedTask);
    }

    @Test
    void cleanupCompletedJobs_ShouldUpdateJobStatusToPartial_WhenRemainingTasksHaveCompletedTasks() {
        DownloadJob oldJob = new DownloadJob("default");
        oldJob.setStatus(JobStatus.FAILED);
        DownloadTask resolvedTask = DownloadTask.create(oldJob, "video1", "Title 1", false);
        resolvedTask.setStatus(TaskStatus.FAILED);
        oldJob.addTask(resolvedTask);

        DownloadTask downloadedTask = DownloadTask.create(oldJob, "video2", "Title 2", false);
        downloadedTask.setStatus(TaskStatus.DOWNLOADED);
        oldJob.addTask(downloadedTask);

        DownloadTask unresolvedTask = DownloadTask.create(oldJob, "video3", "Title 3", false);
        unresolvedTask.setStatus(TaskStatus.FAILED);
        oldJob.addTask(unresolvedTask);

        when(downloadJobRepository.findAllByStatusInWithTasks(
                eq((java.util.Collection<JobStatus>) List.of(JobStatus.FAILED, JobStatus.PARTIALLY_COMPLETED))))
                .thenReturn(List.of(oldJob));
        when(downloadTaskRepository.findActiveVideoIds(eq(Set.of("video1", "video3")),
                eq(List.of(TaskStatus.DOWNLOADED))))
                .thenReturn(Set.of("video1"));
        when(downloadJobRepository.findAllByStatus(JobStatus.COMPLETED))
                .thenReturn(Collections.emptyList());

        autoCleanupService.cleanupCompletedJobs();

        verify(downloadTaskRepository).deleteAll(List.of(resolvedTask));
        verify(downloadTaskRepository).flush();
        verify(downloadJobRepository, never()).deleteAll(List.of(oldJob));
        assertThat(oldJob.getStatus()).isEqualTo(JobStatus.PARTIALLY_COMPLETED);
        verify(downloadJobRepository).saveAll(List.of(oldJob));
    }

    @Test
    void cleanupCompletedJobs_ShouldDoNothing_WhenUnresolvedJobsIsEmpty() {
        when(downloadJobRepository.findAllByStatusInWithTasks(
                eq((java.util.Collection<JobStatus>) List.of(JobStatus.FAILED, JobStatus.PARTIALLY_COMPLETED))))
                .thenReturn(Collections.emptyList());
        when(downloadJobRepository.findAllByStatus(JobStatus.COMPLETED))
                .thenReturn(Collections.emptyList());

        autoCleanupService.cleanupCompletedJobs();

        verify(downloadTaskRepository, never()).findActiveVideoIds(any(), any());
        verify(downloadTaskRepository, never()).deleteAll(any());
        verify(downloadJobRepository, never()).deleteAll(any());
    }

    @Test
    void cleanupCompletedJobs_ShouldDoNothing_WhenNoFailedTasksExistInUnresolvedJobs() {
        DownloadJob oldJob = new DownloadJob("default");
        oldJob.setStatus(JobStatus.FAILED);
        DownloadTask task = DownloadTask.create(oldJob, "video123", "Title", false);
        task.setStatus(TaskStatus.DOWNLOADED);
        oldJob.addTask(task);

        when(downloadJobRepository.findAllByStatusInWithTasks(
                eq((java.util.Collection<JobStatus>) List.of(JobStatus.FAILED, JobStatus.PARTIALLY_COMPLETED))))
                .thenReturn(List.of(oldJob));
        when(downloadJobRepository.findAllByStatus(JobStatus.COMPLETED))
                .thenReturn(Collections.emptyList());

        autoCleanupService.cleanupCompletedJobs();

        verify(downloadTaskRepository, never()).findActiveVideoIds(any(), any());
        verify(downloadTaskRepository, never()).deleteAll(any());
        verify(downloadJobRepository, never()).deleteAll(any());
    }

    @Test
    void cleanupCompletedJobs_ShouldDoNothing_WhenNoFailedTasksAreResolved() {
        DownloadJob oldJob = new DownloadJob("default");
        oldJob.setStatus(JobStatus.FAILED);
        DownloadTask task = DownloadTask.create(oldJob, "video123", "Title", false);
        task.setStatus(TaskStatus.FAILED);
        oldJob.addTask(task);

        when(downloadJobRepository.findAllByStatusInWithTasks(
                eq((java.util.Collection<JobStatus>) List.of(JobStatus.FAILED, JobStatus.PARTIALLY_COMPLETED))))
                .thenReturn(List.of(oldJob));
        when(downloadTaskRepository.findActiveVideoIds(eq(Set.of("video123")), eq(List.of(TaskStatus.DOWNLOADED))))
                .thenReturn(Collections.emptySet());
        when(downloadJobRepository.findAllByStatus(JobStatus.COMPLETED))
                .thenReturn(Collections.emptyList());

        autoCleanupService.cleanupCompletedJobs();

        verify(downloadTaskRepository, never()).deleteAll(any());
        verify(downloadJobRepository, never()).deleteAll(any());
    }

    @Test
    void cleanupCompletedJobs_ShouldSkipJob_WhenNoTasksInThatJobAreResolved() {
        DownloadJob job1 = new DownloadJob("default");
        job1.setStatus(JobStatus.FAILED);
        DownloadTask task1 = DownloadTask.create(job1, "video1", "Title 1", false);
        task1.setStatus(TaskStatus.FAILED);
        job1.addTask(task1);

        DownloadJob job2 = new DownloadJob("default");
        job2.setStatus(JobStatus.FAILED);
        DownloadTask task2 = DownloadTask.create(job2, "video2", "Title 2", false);
        task2.setStatus(TaskStatus.FAILED);
        job2.addTask(task2);

        when(downloadJobRepository.findAllByStatusInWithTasks(
                eq((java.util.Collection<JobStatus>) List.of(JobStatus.FAILED, JobStatus.PARTIALLY_COMPLETED))))
                .thenReturn(List.of(job1, job2));

        when(downloadTaskRepository.findActiveVideoIds(eq(Set.of("video1", "video2")),
                eq(List.of(TaskStatus.DOWNLOADED))))
                .thenReturn(Set.of("video1"));
        when(downloadJobRepository.findAllByStatus(JobStatus.COMPLETED))
                .thenReturn(Collections.emptyList());

        autoCleanupService.cleanupCompletedJobs();

        verify(downloadTaskRepository).deleteAll(List.of(task1));
        verify(downloadTaskRepository).flush();
        verify(downloadJobRepository).deleteAll(List.of(job1));
        verify(downloadJobRepository, never()).deleteAll(List.of(job2));
        verify(downloadJobRepository, never()).saveAll(List.of(job2));
    }

    @Test
    void isSchedulerRunning_ShouldReturnFalse_WhenCancelled() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isCancelled()).thenReturn(true);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(anyRunnable(), eqDuration(Duration.ofSeconds(30)));

        autoCleanupService.start();
        assertThat(autoCleanupService.isSchedulerRunning()).isFalse();
    }

    @Test
    void isSchedulerRunning_ShouldReturnFalse_WhenDone() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(true);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(anyRunnable(), eqDuration(Duration.ofSeconds(30)));

        autoCleanupService.start();
        assertThat(autoCleanupService.isSchedulerRunning()).isFalse();
    }

    private Runnable anyRunnable() {
        any(Runnable.class);
        return () -> {
        };
    }

    private Duration eqDuration(Duration duration) {
        eq(duration);
        return Duration.ZERO;
    }

    private Duration anyDuration() {
        any(Duration.class);
        return Duration.ZERO;
    }
}
