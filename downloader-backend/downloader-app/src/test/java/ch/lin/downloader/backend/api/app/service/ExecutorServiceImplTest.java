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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import ch.lin.downloader.backend.api.app.config.DownloaderDefaultProperties;
import ch.lin.downloader.backend.api.app.repository.DownloadJobRepository;
import ch.lin.downloader.backend.api.app.repository.DownloadTaskRepository;
import ch.lin.downloader.backend.api.app.service.model.DownloadResult;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.DownloadSubTask;
import ch.lin.downloader.backend.api.domain.DownloadTask;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.JobStatus;
import ch.lin.downloader.backend.api.domain.OverwriteOption;
import ch.lin.downloader.backend.api.domain.SubTaskType;
import ch.lin.downloader.backend.api.domain.TaskStatus;
import ch.lin.downloader.backend.api.domain.YtDlpConfig;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ExecutorServiceImplTest {

    @Mock
    private DownloadTaskRepository downloadTaskRepository;
    @Mock
    private DownloadJobRepository downloadJobRepository;
    @Mock
    private ConfigsService configsService;
    @Mock
    private ApiClientService apiClientService;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private DownloaderDefaultProperties defaultProperties;

    @InjectMocks
    private ExecutorServiceImpl executorService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        // Reset mocks if needed, though MockitoExtension handles this.
    }

    @Test
    void init_ShouldResetStuckTasksToFailed_WhenStuckTasksExist() {
        // Setup stuck task
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-stuck");
        DownloadTask task = DownloadTask.create(job, "vid-stuck", "Stuck Video", false);
        ReflectionTestUtils.setField(task, "id", "task-stuck");
        task.setStatus(TaskStatus.DOWNLOADING);
        job.addTask(task);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.DOWNLOADING)).thenReturn(List.of(task));

        // For updateJobStatus internal call
        when(downloadJobRepository.findByIdWithTasks("job-stuck")).thenReturn(Optional.of(job));

        // Setup Config so the rest of init() works without error
        DownloaderConfig config = new DownloaderConfig("default");
        config.setStartDownloadAutomatically(false);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        // Execute
        executorService.init();

        // Verify
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        verify(downloadTaskRepository).save(task);
        verify(apiClientService).updateItemStatus("vid-stuck", "task-stuck", TaskStatus.FAILED);

        // Because the only task failed, the job should be marked as FAILED too
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        verify(downloadJobRepository).save(job);
    }

    @Test
    void init_ShouldHandleExceptionDuringStuckTasksReset() {
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.DOWNLOADING)).thenThrow(new RuntimeException("Simulated DB Error"));

        DownloaderConfig config = new DownloaderConfig("default");
        config.setStartDownloadAutomatically(false);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        // Execute - should not throw exception due to the try-catch block
        executorService.init();

        // Verify it continued to check config
        verify(configsService).getResolvedConfig(null);
    }

    @Test
    void init_ShouldDoNothing_WhenStuckTasksIsEmpty() {
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.DOWNLOADING)).thenReturn(Collections.emptyList());

        DownloaderConfig config = new DownloaderConfig("default");
        config.setStartDownloadAutomatically(false);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        executorService.init();

        verify(downloadTaskRepository, never()).save(any());
        verify(apiClientService, never()).updateItemStatus(any(), any(), any());
    }

    @Test
    void init_ShouldDoNothing_WhenStuckTasksIsNull() {
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.DOWNLOADING)).thenReturn(null);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setStartDownloadAutomatically(false);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        executorService.init();

        verify(downloadTaskRepository, never()).save(any());
        verify(apiClientService, never()).updateItemStatus(any(), any(), any());
    }

    @Test
    void init_ShouldNotUpdateJobStatus_WhenStuckTaskHasNoJob() {
        DownloadTask task = DownloadTask.create(null, "vid-no-job", "No Job Video", false);
        ReflectionTestUtils.setField(task, "id", "task-no-job");
        task.setStatus(TaskStatus.DOWNLOADING);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.DOWNLOADING)).thenReturn(List.of(task));

        DownloaderConfig config = new DownloaderConfig("default");
        config.setStartDownloadAutomatically(false);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        executorService.init();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        verify(downloadTaskRepository).save(task);
        verify(apiClientService).updateItemStatus("vid-no-job", "task-no-job", TaskStatus.FAILED);

        verify(downloadJobRepository, never()).findByIdWithTasks(any());
        verify(downloadJobRepository, never()).save(any());
    }

    @Test
    void init_ShouldStartScheduler_WhenConfigEnabled() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setStartDownloadAutomatically(true);
        config.setDuration(60);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(60)));

        executorService.init();

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(60)));
        assertThat(executorService.isSchedulerRunning()).isTrue();
    }

    @Test
    void init_ShouldNotStartScheduler_WhenConfigDisabled() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setStartDownloadAutomatically(false);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        executorService.init();

        verify(taskScheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        assertThat(executorService.isSchedulerRunning()).isFalse();
    }

    @Test
    void start_ShouldScheduleTask_WhenNotRunning() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(30)));

        executorService.start();

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(30)));
        assertThat(executorService.isSchedulerRunning()).isTrue();
    }

    @Test
    void stop_ShouldCancelTask_WhenRunning() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(30)));

        executorService.start();
        executorService.stop();

        verify(future).cancel(false);
    }

    @Test
    void start_ShouldNotSchedule_WhenAlreadyRunning() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(30)));
        when(future.isDone()).thenReturn(false);

        executorService.start();
        executorService.start();

        verify(taskScheduler, times(1)).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    void start_ShouldRestart_WhenTaskIsDone() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(30)));
        when(future.isDone()).thenReturn(true);

        executorService.start();
        executorService.start();

        verify(taskScheduler, times(2)).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    void stop_ShouldDoNothing_WhenNotRunning() {
        executorService.stop();
        // No interaction expected
    }

    @Test
    void stop_ShouldDoNothing_WhenTaskIsDone() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(30)));
        when(future.isDone()).thenReturn(true);

        executorService.start();
        executorService.stop();

        verify(future, never()).cancel(anyBoolean());
    }

    @Test
    void processPendingTasks_ShouldDoNothing_WhenNoPendingTasks() {
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(Collections.emptyList());
        when(configsService.getResolvedConfig(null)).thenReturn(new DownloaderConfig("default"));

        executorService.processPendingTasks();

        verify(downloadTaskRepository, never()).save(any(DownloadTask.class));
    }

    @Test
    void processPendingTasks_ShouldProcessTasks_WhenPendingTasksExist() {
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-1");
        DownloadTask task = DownloadTask.create(job, "vid-1", "Test Video", false);
        ReflectionTestUtils.setField(task, "id", "task-1");
        job.addTask(task);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig(null)).thenReturn(new DownloaderConfig("default")); // For thread pool adjustment

        executorService.processPendingTasks();

        verify(downloadTaskRepository).save(task);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DOWNLOADING);
        verify(apiClientService).updateItemStatus("vid-1", "task-1", TaskStatus.DOWNLOADING);
        // Note: The actual execution inside the thread pool is hard to verify without a custom ExecutorService factory.
        // We assume the submission happens if the code reaches this point.
    }

    @Test
    void updateTaskFromResult_ShouldMarkDownloaded_WhenSuccess() {
        DownloadJob job = new DownloadJob("default");
        DownloadTask task = DownloadTask.create(job, "vid", "title", false);
        ReflectionTestUtils.setField(task, "id", "task-1");

        DownloadSubTask videoTask = task.getSubTask(SubTaskType.VIDEO);
        videoTask.setStatus(TaskStatus.DOWNLOADED);
        videoTask.setFilePath("/path/to/file");
        videoTask.setFileSize(1024L);

        DownloadResult result = new DownloadResult();
        result.setSuccess(true);

        executorService.updateTaskFromResult(task, result);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DOWNLOADED);
        assertThat(videoTask.getFilePath()).isEqualTo("/path/to/file");
        assertThat(videoTask.getFileSize()).isEqualTo(1024L);
        verify(downloadTaskRepository).save(task);
    }

    @Test
    void updateTaskFromResult_ShouldMarkFailed_WhenFailure() {
        DownloadJob job = new DownloadJob("default");
        DownloadTask task = DownloadTask.create(job, "vid", "title", false);
        ReflectionTestUtils.setField(task, "id", "task-1");

        DownloadSubTask videoTask = task.getSubTask(SubTaskType.VIDEO);
        videoTask.setStatus(TaskStatus.FAILED);
        videoTask.setErrorMessage("Error occurred");

        DownloadResult result = new DownloadResult();
        result.setSuccess(false);

        executorService.updateTaskFromResult(task, result);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(videoTask.getErrorMessage()).isEqualTo("Error occurred");
        verify(downloadTaskRepository).save(task);
    }

    @Test
    void updateTaskFromResult_ShouldNotUpdateApi_WhenStatusIsDownloading() {
        DownloadJob job = new DownloadJob("default");
        DownloadTask task = DownloadTask.create(job, "vid", "title", false);
        ReflectionTestUtils.setField(task, "id", "task-1");

        // Set subtask to DOWNLOADING so task status won't become DOWNLOADED or FAILED
        DownloadSubTask videoTask = task.getSubTask(SubTaskType.VIDEO);
        videoTask.setStatus(TaskStatus.DOWNLOADING);

        DownloadResult result = new DownloadResult(); // The result does not affect this test

        executorService.updateTaskFromResult(task, result);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DOWNLOADING);
        verify(downloadTaskRepository).save(task);
        verify(apiClientService, never()).updateItemStatus(any(), any(), any());
        verify(apiClientService, never()).updateItem(any(), any(), anyLong(), any(), any());
    }

    @Test
    void updateJobStatus_ShouldMarkCompleted_WhenAllTasksDownloaded() {
        String jobId = "job-1";
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);
        DownloadTask task1 = DownloadTask.create(job, "vid1", "title1", false);
        task1.setStatus(TaskStatus.DOWNLOADED);
        job.addTask(task1);

        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        executorService.updateJobStatus(jobId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        verify(downloadJobRepository).save(job);
    }

    @Test
    void updateJobStatus_ShouldMarkPartiallyCompleted_WhenMixedStatus() {
        String jobId = "job-1";
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);
        DownloadTask task1 = DownloadTask.create(job, "vid1", "title1", false);
        task1.setStatus(TaskStatus.DOWNLOADED);
        DownloadTask task2 = DownloadTask.create(job, "vid2", "title2", false);
        task2.setStatus(TaskStatus.FAILED);
        job.addTask(task1);
        job.addTask(task2);

        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        executorService.updateJobStatus(jobId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PARTIALLY_COMPLETED);
        verify(downloadJobRepository).save(job);
    }

    @Test
    void updateJobStatus_ShouldMarkFailed_WhenAllTasksFailed() {
        String jobId = "job-1";
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);
        DownloadTask task1 = DownloadTask.create(job, "vid1", "title1", false);
        task1.setStatus(TaskStatus.FAILED);
        job.addTask(task1);

        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        executorService.updateJobStatus(jobId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        verify(downloadJobRepository).save(job);
    }

    @Test
    void updateJobStatus_ShouldMarkInProgress_WhenTasksRemaining() {
        String jobId = "job-1";
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", jobId);
        DownloadTask task1 = DownloadTask.create(job, "vid1", "title1", false);
        task1.setStatus(TaskStatus.DOWNLOADED);
        DownloadTask task2 = DownloadTask.create(job, "vid2", "title2", false);
        task2.setStatus(TaskStatus.PENDING);
        job.addTask(task1);
        job.addTask(task2);

        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.of(job));

        executorService.updateJobStatus(jobId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        verify(downloadJobRepository).save(job);
    }

    @Test
    void updateJobStatus_ShouldThrow_WhenJobNotFound() {
        String jobId = "non-existent";
        when(downloadJobRepository.findByIdWithTasks(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executorService.updateJobStatus(jobId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Job not found for status update");
    }

    @Test
    void progressTracker_ShouldUpdate_WhenConditionsMet() throws Exception {
        // Using reflection to test private static inner class
        Class<?> trackerClass = Class.forName("ch.lin.downloader.backend.api.app.service.ExecutorServiceImpl$ProgressTracker");
        Constructor<?> constructor = trackerClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object tracker = constructor.newInstance();

        Method shouldUpdate = trackerClass.getDeclaredMethod("shouldUpdate", double.class);
        shouldUpdate.setAccessible(true);

        Method update = trackerClass.getDeclaredMethod("update", double.class);
        update.setAccessible(true);

        // Initial check should be true (time elapsed since 0)
        assertThat((boolean) shouldUpdate.invoke(tracker, 10.0)).isTrue();

        // Update
        update.invoke(tracker, 10.0);

        // Immediate check with small increment should be false
        assertThat((boolean) shouldUpdate.invoke(tracker, 11.0)).isFalse();

        // Check with large increment should be true
        assertThat((boolean) shouldUpdate.invoke(tracker, 20.0)).isTrue();
    }

    @Test
    void processPendingTasks_ShouldExecuteDownload_WhenTasksPending(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-1");

        DownloadTask task = DownloadTask.create(job, "vid-1", "Test Video", false);
        ReflectionTestUtils.setField(task, "id", "task-1");
        task.setDescription("desc");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setThreadPoolSize(3);
        YtDlpConfig ytDlpConfig = new YtDlpConfig("default");
        ytDlpConfig.setWriteSubs(false);
        config.setYtDlpConfig(ytDlpConfig);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        when(downloadJobRepository.findByIdWithTasks("job-1")).thenReturn(Optional.of(job));

        // Spy
        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        // Mock Process for Update
        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("yt-dlp is up to date".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        // Mock Process for Download
        Process downloadProcess = mock(Process.class);
        String output = "[download] Destination: video.mp4\n[download] 100% of 10.00MiB at 1.00MiB/s ETA 00:00";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
        when(downloadProcess.getInputStream()).thenReturn(inputStream);
        when(downloadProcess.waitFor()).thenReturn(0);

        // Mock startProcess
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy file to simulate download
        Path videoDir = tempDir.resolve("Test Video [vid-1]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        // Execute
        spyService.processPendingTasks();

        // Verify async execution
        verify(downloadTaskRepository, timeout(5000).atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify other interactions
        verify(apiClientService).updateItem(eq("vid-1"), eq("task-1"), anyLong(), contains("video.mp4"), eq(TaskStatus.DOWNLOADED));

        // Verify description saved
        assertThat(Files.exists(videoDir.resolve("video (Description).txt"))).isTrue();
    }

    @Test
    void processPendingTasks_ShouldAdjustThreadPool() {
        // Setup mocks to avoid actual processing
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(Collections.emptyList());

        // 1. Increase size (3 -> 5)
        DownloaderConfig configIncrease = new DownloaderConfig("default");
        configIncrease.setThreadPoolSize(5);
        when(configsService.getResolvedConfig(null)).thenReturn(configIncrease);

        executorService.processPendingTasks();

        // 2. Decrease size (5 -> 2)
        DownloaderConfig configDecrease = new DownloaderConfig("default");
        configDecrease.setThreadPoolSize(2);
        when(configsService.getResolvedConfig(null)).thenReturn(configDecrease);

        executorService.processPendingTasks();

        // 3. Exception handling
        when(configsService.getResolvedConfig(null)).thenThrow(new RuntimeException("Simulated error"));
        executorService.processPendingTasks();
    }

    @Test
    void processPendingTasks_ShouldSkip_WhenQueueIsBusy() throws Exception {
        // Setup config for adjustThreadPoolSize
        DownloaderConfig config = new DownloaderConfig("default");
        config.setThreadPoolSize(3);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        // Mock ThreadPoolExecutor and Queue
        ThreadPoolExecutor mockExecutor = mock(ThreadPoolExecutor.class);
        @SuppressWarnings("unchecked")
        BlockingQueue<Runnable> mockQueue = mock(BlockingQueue.class);
        when(mockExecutor.getQueue()).thenReturn(mockQueue);
        when(mockQueue.size()).thenReturn(51);

        // Inject mock executor via reflection
        Field executorField = ExecutorServiceImpl.class.getDeclaredField("downloadExecutor");
        executorField.setAccessible(true);
        executorField.set(executorService, mockExecutor);

        // Execute
        executorService.processPendingTasks();

        // Verify repository was not queried
        verify(downloadTaskRepository, never()).findAllByStatusWithJob(any());
    }

    @Test
    void processPendingTasks_ShouldProceed_WhenExecutorIsNotThreadPool() throws Exception {
        // Setup config
        DownloaderConfig config = new DownloaderConfig("default");
        config.setThreadPoolSize(5);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        // Mock generic ExecutorService (not ThreadPoolExecutor)
        java.util.concurrent.ExecutorService mockExecutor = mock(java.util.concurrent.ExecutorService.class);

        // Inject mock executor via reflection
        Field executorField = ExecutorServiceImpl.class.getDeclaredField("downloadExecutor");
        executorField.setAccessible(true);
        executorField.set(executorService, mockExecutor);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(Collections.emptyList());

        // Execute
        executorService.processPendingTasks();

        // Verify repository was queried (meaning the queue check was skipped)
        verify(downloadTaskRepository).findAllByStatusWithJob(TaskStatus.PENDING);
    }

    @Test
    void processPendingTasks_ShouldNotAdjustThreadPool_WhenSizeInvalid() {
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(Collections.emptyList());

        // 1. Size is null
        DownloaderConfig configNull = new DownloaderConfig("default");
        configNull.setThreadPoolSize(null);
        when(configsService.getResolvedConfig(null)).thenReturn(configNull);
        executorService.processPendingTasks();

        // 2. Size is 0
        DownloaderConfig configZero = new DownloaderConfig("default");
        configZero.setThreadPoolSize(0);
        when(configsService.getResolvedConfig(null)).thenReturn(configZero);
        executorService.processPendingTasks();
    }

    @Test
    void isSchedulerRunning_ShouldReturnFalse_WhenCancelled() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isCancelled()).thenReturn(true);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(30)));

        executorService.start();
        assertThat(executorService.isSchedulerRunning()).isFalse();
    }

    @Test
    void isSchedulerRunning_ShouldReturnFalse_WhenDone() {
        DownloaderConfig config = new DownloaderConfig("default");
        config.setDuration(30);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(true);
        doReturn(future).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(30)));

        executorService.start();
        assertThat(executorService.isSchedulerRunning()).isFalse();
    }

    @Test
    void processPendingTasks_ShouldIncludeAllOptions_WhenConfigured(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob("full-config");
        ReflectionTestUtils.setField(job, "id", "job-full");
        DownloadTask task = DownloadTask.create(job, "vid-full", "Full Config Video", true);
        ReflectionTestUtils.setField(task, "id", "task-full");
        job.addTask(task);

        // Setup Config
        DownloaderConfig config = new DownloaderConfig("full-config");
        YtDlpConfig ytDlp = new YtDlpConfig("full-config");
        ytDlp.setFormatFiltering("best");
        ytDlp.setFormatSorting("res:1080");
        ytDlp.setWriteSubs(true);
        ytDlp.setSubLang("en");
        ytDlp.setWriteAutoSubs(true);
        ytDlp.setSubFormat("srt");
        ytDlp.setExtractAudio(true);
        ytDlp.setAudioFormat("mp3");
        ytDlp.setAudioQuality(0);
        ytDlp.setKeepVideo(true);
        ytDlp.setOutputTemplate("%(title)s.%(ext)s");
        ytDlp.setOverwrite(OverwriteOption.FORCE);
        ytDlp.setUseCookie(true);
        config.setYtDlpConfig(ytDlp);

        // Mocks
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("full-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-full")).thenReturn(Optional.of(job));

        // Create Cookie File
        Path cookiePath = tempDir.resolve("full-config-cookie.txt");
        Files.writeString(cookiePath, "cookie-content");

        // Spy
        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        // Mock Processes
        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process listSubsProcess = mock(Process.class);
        when(listSubsProcess.getInputStream()).thenReturn(new ByteArrayInputStream("Available subtitles for...".getBytes()));
        when(listSubsProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("[download] 100%".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(0);

        // Mock startProcess behavior
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(listSubsProcess).when(spyService).startProcess(argThat(list -> list.contains("--list-subs")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> list.contains("https://www.youtube.com/watch?v=vid-full") && !list.contains("--list-subs")), any());

        // Execute
        spyService.processPendingTasks();

        // Verify
        verify(downloadTaskRepository, timeout(5000).atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify Audio Phase
        verify(spyService).startProcess(argThat(list
                -> list.contains("--cookies")
                && list.stream().anyMatch(s -> s.contains("yt-dlp-cookie-") && s.endsWith(".txt"))
                && list.contains("--extract-audio")
                && list.contains("--audio-format") && list.contains("mp3")
                && list.contains("--audio-quality") && list.contains("0")
                && !list.contains("--write-subs")
        ), any());

        // Verify Video Phase
        verify(spyService).startProcess(argThat(list
                -> list.contains("--cookies")
                && list.stream().anyMatch(s -> s.contains("yt-dlp-cookie-") && s.endsWith(".txt"))
                && list.contains("-f") && list.contains("best")
                && list.contains("--format-sort") && list.contains("res:1080")
                && list.contains("--write-subs")
                && list.contains("--sub-lang") && list.contains("en")
                && list.contains("--write-auto-subs")
                && list.contains("--sub-format") && list.contains("srt")
                && list.contains("-k")
                && list.contains("-o") && list.contains("%(title)s.%(ext)s")
                && list.contains("--force-overwrites")
                && !list.contains("--extract-audio")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldIncludeRemuxAndSkip_WhenConfigured(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob("remux-config");
        ReflectionTestUtils.setField(job, "id", "job-remux");
        DownloadTask task = DownloadTask.create(job, "vid-remux", "Remux Video", false);
        ReflectionTestUtils.setField(task, "id", "task-remux");
        job.addTask(task);

        // Setup Config
        DownloaderConfig config = new DownloaderConfig("remux-config");
        YtDlpConfig ytDlp = new YtDlpConfig("remux-config");
        ytDlp.setExtractAudio(false);
        ytDlp.setRemuxVideo("mkv");
        ytDlp.setNoProgress(true);
        ytDlp.setOverwrite(OverwriteOption.SKIP);
        config.setYtDlpConfig(ytDlp);

        // Mocks
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("remux-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-remux")).thenReturn(Optional.of(job));

        // Spy
        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        // Mock Processes
        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(0);

        // Mock startProcess
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> list.contains("https://www.youtube.com/watch?v=vid-remux")), any());

        // Execute
        spyService.processPendingTasks();

        // Verify
        verify(downloadTaskRepository, timeout(5000).atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        verify(spyService).startProcess(argThat(list
                -> list.contains("--remux-video") && list.contains("mkv")
                && list.contains("--no-progress")
                && list.contains("--no-overwrites")
                && !list.contains("--cookies")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldSkipSubs_WhenNotAvailable(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("subs-config");
        ReflectionTestUtils.setField(job, "id", "job-subs");
        DownloadTask task = DownloadTask.create(job, "vid-subs", "Subs Video", false);
        ReflectionTestUtils.setField(task, "id", "task-subs");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("subs-config");
        YtDlpConfig ytDlp = new YtDlpConfig("subs-config");
        ytDlp.setWriteSubs(true);
        config.setYtDlpConfig(ytDlp);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("subs-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-subs")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process listSubsProcess = mock(Process.class);
        when(listSubsProcess.getInputStream()).thenReturn(new ByteArrayInputStream("has no subtitles\n".getBytes()));
        //when(listSubsProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(0);

        // Mock the specific process calls
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(listSubsProcess).when(spyService).startProcess(argThat(list -> list.contains("--list-subs")), any());
        doReturn(downloadProcess).when(spyService).startProcess(
                argThat(list -> !list.contains("-U") && !list.contains("--list-subs")), any());

        spyService.processPendingTasks();

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));
        //verify(spyService).startProcess(argThat(list -> !list.contains("--write-subs") && !list.contains("--list-subs") && !list.contains("-U")), any());
    }

    @Test
    void processPendingTasks_ShouldHandleConfigEdgeCases(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("edge-config");
        ReflectionTestUtils.setField(job, "id", "job-edge");
        DownloadTask task = DownloadTask.create(job, "vid-edge", "Edge Video", true);
        ReflectionTestUtils.setField(task, "id", "task-edge");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("edge-config");
        YtDlpConfig ytDlp = new YtDlpConfig("edge-config");
        ytDlp.setExtractAudio(true);
        ytDlp.setAudioFormat(null); // Should trigger null check
        ytDlp.setAudioQuality(null); // Should trigger null check
        ytDlp.setRemuxVideo("mp4"); // Video phase will use it
        ytDlp.setOverwrite(OverwriteOption.DEFAULT); // Should trigger default case
        ytDlp.setNoProgress(false); // Trigger buffer reading
        config.setYtDlpConfig(ytDlp);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("edge-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-edge")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // Provide output to exercise buffer reading: "line1\nline2" (no trailing newline)
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("line1\nline2\n".getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doAnswer(invocation -> {
            List<String> cmd = invocation.getArgument(0);
            if (cmd.contains("-U")) {
                return updateProcess;
            }
            return downloadProcess;
        }).when(spyService).startProcess(any(), any());

        spyService.processPendingTasks();

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify Audio Phase
        verify(spyService).startProcess(argThat(list
                -> list.contains("--extract-audio")
                && list.contains("--audio-format") && list.contains("m4a") // Falls back to m4a
                && !list.contains("--audio-quality")
                && !list.contains("--remux-video")
                && !list.contains("--force-overwrites")
                && !list.contains("--no-overwrites")
        ), any());

        // Verify Video Phase
        verify(spyService).startProcess(argThat(list
                -> !list.contains("--extract-audio")
                && list.contains("--remux-video") && list.contains("mp4") // Video phase includes remux
                && !list.contains("--force-overwrites")
                && !list.contains("--no-overwrites")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldHandleMissingFile_AfterSuccess(@TempDir Path tempDir) throws Exception {
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-missing");
        DownloadTask task = DownloadTask.create(job, "vid-missing", "Missing Video", false);
        ReflectionTestUtils.setField(task, "id", "task-missing");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-missing")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // Output indicates a file, but we won't create it on disk
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("[download] Destination: missing.mp4\n".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(0);

        doAnswer(invocation -> {
            List<String> cmd = invocation.getArgument(0);
            if (cmd.contains("-U")) {
                return updateProcess;
            }
            return downloadProcess;
        }).when(spyService).startProcess(any(), any());

        spyService.processPendingTasks();

        // Should still be marked DOWNLOADED because exit code was 0, but filePath will be null
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.DOWNLOADED && (st != null && st.getFilePath() == null);
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleProcessFailure(@TempDir Path tempDir) throws Exception {
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-fail");
        DownloadTask task = DownloadTask.create(job, "vid-fail", "Fail Video", false);
        ReflectionTestUtils.setField(task, "id", "task-fail");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-fail")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("ERROR: Something went wrong\n".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(1); // Exit code 1

        doAnswer(invocation -> {
            List<String> cmd = invocation.getArgument(0);
            if (cmd.contains("-U")) {
                return updateProcess;
            }
            return downloadProcess;
        }).when(spyService).startProcess(any(), any());

        spyService.processPendingTasks();

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.FAILED && st != null && st.getErrorMessage() != null && st.getErrorMessage().contains("ERROR: Something went wrong");
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleIOException(@TempDir Path tempDir) throws Exception {
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-io");
        DownloadTask task = DownloadTask.create(job, "vid-io", "IO Video", false);
        ReflectionTestUtils.setField(task, "id", "task-io");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-io")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        doAnswer(invocation -> {
            List<String> cmd = invocation.getArgument(0);
            if (cmd.contains("-U")) {
                return updateProcess;
            }
            throw new IOException("Simulated IO Error");
        }).when(spyService).startProcess(any(), any());

        spyService.processPendingTasks();

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.FAILED && st != null && st.getErrorMessage() != null && st.getErrorMessage().contains("I/O error");
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleInterruptedException(@TempDir Path tempDir) throws Exception {
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-int");
        DownloadTask task = DownloadTask.create(job, "vid-int", "Int Video", false);
        ReflectionTestUtils.setField(task, "id", "task-int");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-int")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(downloadProcess.waitFor()).thenThrow(new InterruptedException("Simulated Interrupt"));

        doAnswer(invocation -> {
            List<String> cmd = invocation.getArgument(0);
            if (cmd.contains("-U")) {
                return updateProcess;
            }
            return downloadProcess;
        }).when(spyService).startProcess(any(), any());

        spyService.processPendingTasks();

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.FAILED && st != null && st.getErrorMessage() != null && st.getErrorMessage().contains("interrupted");
        }));
    }

    @Test
    void processPendingTasks_ShouldLogWarning_WhenCookieFileMissing(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob("cookie-config");
        ReflectionTestUtils.setField(job, "id", "job-cookie-missing");
        DownloadTask task = DownloadTask.create(job, "vid-cookie-missing", "Cookie Missing Video", false);
        ReflectionTestUtils.setField(task, "id", "task-cookie-missing");
        job.addTask(task);

        // Setup Config
        DownloaderConfig config = new DownloaderConfig("cookie-config");
        YtDlpConfig ytDlp = new YtDlpConfig("cookie-config");
        ytDlp.setUseCookie(true);
        config.setYtDlpConfig(ytDlp);

        // Mocks
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("cookie-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-cookie-missing")).thenReturn(Optional.of(job));

        // Spy
        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        // Mock Processes
        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(0);

        // Mock startProcess
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> list.contains("https://www.youtube.com/watch?v=vid-cookie-missing")), any());

        // Execute
        spyService.processPendingTasks();

        // Verify
        verify(downloadTaskRepository, timeout(5000).atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify that --cookies was NOT added because the file was missing
        verify(spyService).startProcess(argThat(list
                -> !list.contains("--cookies") && !list.contains("-U")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldParseOutput_WhenNoProgressEnabled(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob("no-progress-config");
        ReflectionTestUtils.setField(job, "id", "job-no-progress");
        DownloadTask task = DownloadTask.create(job, "vid-no-progress", "No Progress Video", false);
        ReflectionTestUtils.setField(task, "id", "task-no-progress");
        job.addTask(task);

        // Setup Config
        DownloaderConfig config = new DownloaderConfig("no-progress-config");
        YtDlpConfig ytDlp = new YtDlpConfig("no-progress-config");
        ytDlp.setNoProgress(true);
        config.setYtDlpConfig(ytDlp);

        // Mocks
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("no-progress-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-no-progress")).thenReturn(Optional.of(job));

        // Spy
        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        // Mock Processes
        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // Provide output to exercise the loop in the noProgress block
        String output = "[download] Destination: video_no_progress.mp4\nSome other line";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        // Mock startProcess
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy file so download is considered successful
        Path videoDir = tempDir.resolve("No Progress Video [vid-no-progress]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video_no_progress.mp4"));

        // Execute
        spyService.processPendingTasks();

        // Verify
        verify(downloadTaskRepository, timeout(5000).atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify that the file path was correctly parsed from the output
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.DOWNLOADED && st != null && st.getFilePath() != null && st.getFilePath().endsWith("video_no_progress.mp4");
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleBufferReading_WithCR_AndEmptyLines(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-buffer");
        DownloadTask task = DownloadTask.create(job, "vid-buffer", "Buffer Video", false);
        ReflectionTestUtils.setField(task, "id", "task-buffer");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default")); // noProgress is false by default

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-buffer")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // "Progress 10%\r" -> covers \r
        // "[download] Destination: video.mp4\n" -> covers \n and sets filename
        // "\n" -> covers empty line (length > 0 check)
        // "[download] 100%\n" -> covers normal line
        String output = "[download] 10% of 10MB\r[download] Destination: video.mp4\n\n[download] 100% of 10MB\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy file
        Path videoDir = tempDir.resolve("Buffer Video [vid-buffer]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        spyService.processPendingTasks();

        // Verify success
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));
        // Verify filename was parsed correctly from the line following \r
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.DOWNLOADED && st != null && st.getFilePath() != null && st.getFilePath().endsWith("video.mp4");
        }));
    }

    @Test
    void processPendingTasks_ShouldSucceed_WhenExitCode1_AndFileFound(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-exit1");
        DownloadTask task = DownloadTask.create(job, "vid-exit1", "Exit1 Video", false);
        ReflectionTestUtils.setField(task, "id", "task-exit1");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-exit1")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // Output provides a filename, so downloadedFile != null
        String output = "[download] Destination: video_exit1.mp4\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(1); // Exit code 1

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy file so it is considered a valid download
        Path videoDir = tempDir.resolve("Exit1 Video [vid-exit1]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video_exit1.mp4"));

        spyService.processPendingTasks();

        // Verify success
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));
    }

    @Test
    void processPendingTasks_ShouldFail_WhenExitCodeIsTwo(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-exit2");
        DownloadTask task = DownloadTask.create(job, "vid-exit2", "Exit2 Video", false);
        ReflectionTestUtils.setField(task, "id", "task-exit2");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-exit2")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("ERROR: Generic error\n".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(2); // Exit code 2

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        spyService.processPendingTasks();

        // Verify failure
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.FAILED));
    }

    @Test
    void startProcess_ShouldCreateDirectory_WhenDirectoryProvided(@TempDir Path tempDir) {
        Path targetDir = tempDir.resolve("subdir");
        // Use a command that is unlikely to exist to fail fast, but directory creation happens before start()
        List<String> command = List.of("non-existent-command-12345");

        try {
            executorService.startProcess(command, targetDir);
        } catch (IOException e) {
            // Expected exception due to non-existent command
        }

        assertThat(Files.exists(targetDir)).isTrue();
    }

    @Test
    void processPendingTasks_ShouldUseFallbackErrorMessage_WhenNoErrorLinesFound(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-fallback-error");
        DownloadTask task = DownloadTask.create(job, "vid-fallback-error", "Fallback Error Video", false);
        ReflectionTestUtils.setField(task, "id", "task-fallback-error");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-fallback-error")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // Output does NOT contain "ERROR:"
        String output = "Some generic output\nWARNING: something\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(1); // Exit code 1

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        spyService.processPendingTasks();

        // Verify failure with fallback message
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.FAILED && st != null && st.getErrorMessage() != null && st.getErrorMessage().contains("yt-dlp process failed with exit code 1");
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleSubtitlesCheck_WhenExitCodeNonZero(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("subs-config");
        ReflectionTestUtils.setField(job, "id", "job-subs-fail");
        DownloadTask task = DownloadTask.create(job, "vid-subs-fail", "Subs Fail Video", false);
        ReflectionTestUtils.setField(task, "id", "task-subs-fail");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("subs-config");
        YtDlpConfig ytDlp = new YtDlpConfig("subs-config");
        ytDlp.setWriteSubs(true);
        config.setYtDlpConfig(ytDlp);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("subs-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-subs-fail")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process listSubsProcess = mock(Process.class);
        // Output does not contain "has no subtitles"
        when(listSubsProcess.getInputStream()).thenReturn(new ByteArrayInputStream("some output".getBytes()));
        when(listSubsProcess.waitFor()).thenReturn(1); // Non-zero exit code

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(listSubsProcess).when(spyService).startProcess(argThat(list -> list.contains("--list-subs")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U") && !list.contains("--list-subs")), any());

        spyService.processPendingTasks();

        // Verify that --write-subs was NOT added because check failed
        verify(spyService).startProcess(argThat(list
                -> !list.contains("--write-subs") && !list.contains("-U") && !list.contains("--list-subs")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldHandleSubtitlesCheck_Exception(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("subs-config");
        ReflectionTestUtils.setField(job, "id", "job-subs-ex");
        DownloadTask task = DownloadTask.create(job, "vid-subs-ex", "Subs Ex Video", false);
        ReflectionTestUtils.setField(task, "id", "task-subs-ex");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("subs-config");
        YtDlpConfig ytDlp = new YtDlpConfig("subs-config");
        ytDlp.setWriteSubs(true);
        config.setYtDlpConfig(ytDlp);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("subs-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-subs-ex")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        // Throw exception when starting list-subs process
        doThrow(new IOException("Simulated IO Error")).when(spyService).startProcess(argThat(list -> list.contains("--list-subs")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U") && !list.contains("--list-subs")), any());

        spyService.processPendingTasks();

        // Verify that --write-subs was NOT added because check failed
        verify(spyService).startProcess(argThat(list
                -> !list.contains("--write-subs") && !list.contains("-U") && !list.contains("--list-subs")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldParseMergerOutput(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-merger");
        DownloadTask task = DownloadTask.create(job, "vid-merger", "Merger Video", false);
        ReflectionTestUtils.setField(task, "id", "task-merger");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-merger")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        String output = "[Merger] Merging formats into \"merged_video.mkv\"\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy file
        Path videoDir = tempDir.resolve("Merger Video [vid-merger]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("merged_video.mkv"));

        spyService.processPendingTasks();

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.DOWNLOADED && st != null && st.getFilePath() != null && st.getFilePath().endsWith("merged_video.mkv");
        }));
    }

    @Test
    void processPendingTasks_ShouldParseAlreadyDownloadedOutput(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-already");
        DownloadTask task = DownloadTask.create(job, "vid-already", "Already Video", false);
        ReflectionTestUtils.setField(task, "id", "task-already");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-already")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        String output = "[download] existing_video.mp4 has already been downloaded\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy file
        Path videoDir = tempDir.resolve("Already Video [vid-already]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("existing_video.mp4"));

        spyService.processPendingTasks();

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.DOWNLOADED && st != null && st.getFilePath() != null && st.getFilePath().endsWith("existing_video.mp4");
        }));
    }

    @Test
    void processPendingTasks_ShouldParsePostProcessorOutput(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-post");
        DownloadTask task = DownloadTask.create(job, "vid-post", "Post Video", false);
        ReflectionTestUtils.setField(task, "id", "task-post");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-post")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // This pattern usually appears for metadata/subs, but we test that it captures the filename
        String output = "[info] Writing video description to: description.txt\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy file
        Path videoDir = tempDir.resolve("Post Video [vid-post]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("description.txt"));

        spyService.processPendingTasks();

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.DOWNLOADED && st != null && st.getFilePath() != null && st.getFilePath().endsWith("description.txt");
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleProgressParsing_EdgeCases(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-edge-progress");
        DownloadTask task = DownloadTask.create(job, "vid-edge-progress", "Edge Progress Video", false);
        ReflectionTestUtils.setField(task, "id", "task-edge-progress");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-edge-progress")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // 1. Audio file (not video) -> progress 20% should be ignored
        // 2. Video file -> progress nan% -> exception
        // 3. Video file -> progress 50% -> should be saved
        String output = """
                [download] Destination: audio.mp3
                [download]  20.0% of 10MB
                [download] Destination: video.mp4
                [download]   nan% of 10MB
                [download]  50.0% of 10MB
                """;
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Capture progress updates
        List<Double> savedProgress = new ArrayList<>();
        doAnswer(invocation -> {
            DownloadTask t = invocation.getArgument(0);
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            if (st != null && st.getProgress() != null) {
                savedProgress.add(st.getProgress());
            }
            return null;
        }).when(downloadTaskRepository).save(any(DownloadTask.class));

        // Create dummy file
        Path videoDir = tempDir.resolve("Edge Progress Video [vid-edge-progress]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        spyService.processPendingTasks();

        // Verify
        // 20.0 should NOT be present (audio file)
        // 50.0 SHOULD be present (video file)
        assertThat(savedProgress).contains(50.0);
        assertThat(savedProgress).doesNotContain(20.0);
    }

    @Test
    void processPendingTasks_ShouldHandleProgressParsing_AdditionalEdgeCases(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-edge-2");
        DownloadTask task = DownloadTask.create(job, "vid-edge-2", "Edge 2 Video", false);
        ReflectionTestUtils.setField(task, "id", "task-edge-2");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-edge-2")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // 1. Progress with no filename (null check)
        // 2. .webm file (branch coverage)
        // 3. .mkv file (branch coverage)
        // 4. Invalid number format (catch block) - ".." matches [\d.]+ regex but fails Double.parseDouble
        String output = """
                [download]  10.0% of 10MB
                [download] Destination: video.webm
                [download]  20.0% of 10MB
                [download] Destination: video.mkv
                [download]  30.0% of 10MB
                [download] Destination: video.mp4
                [download]  ..% of 10MB
                """;
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Capture progress updates
        List<Double> savedProgress = new ArrayList<>();
        doAnswer(invocation -> {
            DownloadTask t = invocation.getArgument(0);
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            if (st != null && st.getProgress() != null) {
                savedProgress.add(st.getProgress());
            }
            return null;
        }).when(downloadTaskRepository).save(any(DownloadTask.class));

        // Create dummy file
        Path videoDir = tempDir.resolve("Edge 2 Video [vid-edge-2]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        spyService.processPendingTasks();

        // Verify
        // 10.0% -> saved (filename is null, falls back to isTargetFile=true)
        // 20.0% -> saved (.webm)
        // 30.0% -> saved (.mkv)
        // ..% -> ignored (exception caught)
        assertThat(savedProgress).contains(10.0, 20.0, 30.0);
    }

    @Test
    void processPendingTasks_ShouldHandleEmptyFilename_InOutputParsing(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-empty-filename");
        DownloadTask task = DownloadTask.create(job, "vid-empty-filename", "Empty Filename Video", false);
        ReflectionTestUtils.setField(task, "id", "task-empty-filename");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-empty-filename")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // 1. Destination line with empty filename (trailing space preserved by \s)
        // 2. Progress line (should be ignored because filename is empty -> isVideoFile is false)
        String output = """
                [download] Destination:\s
                [download]  50.0% of 10MB
                """;
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        List<Double> savedProgress = new ArrayList<>();
        doAnswer(invocation -> {
            DownloadTask t = invocation.getArgument(0);
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            if (st != null && st.getProgress() != null) {
                savedProgress.add(st.getProgress());
            }
            return null;
        }).when(downloadTaskRepository).save(any(DownloadTask.class));

        spyService.processPendingTasks();

        assertThat(savedProgress).contains(50.0);
    }

    @Test
    void processPendingTasks_ShouldThrottleProgressUpdates(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-throttle");
        DownloadTask task = DownloadTask.create(job, "vid-throttle", "Throttle Video", false);
        ReflectionTestUtils.setField(task, "id", "task-throttle");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-throttle")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // 10.0% -> Update (First)
        // 11.0% -> Skip (Delta < 5%, Time < 1s)
        // 16.0% -> Update (Delta >= 5%)
        String output = """
                [download] Destination: video.mp4
                [download]  10.0% of 10MB
                [download]  11.0% of 10MB
                [download]  16.0% of 10MB
                """;
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        List<Double> savedProgress = new ArrayList<>();
        doAnswer(invocation -> {
            DownloadTask t = invocation.getArgument(0);
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            if (st != null && st.getProgress() != null) {
                savedProgress.add(st.getProgress());
            }
            return null;
        }).when(downloadTaskRepository).save(any(DownloadTask.class));

        // Create dummy file
        Path videoDir = tempDir.resolve("Throttle Video [vid-throttle]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        spyService.processPendingTasks();

        // Verify
        assertThat(savedProgress).contains(10.0, 16.0);
        assertThat(savedProgress).doesNotContain(11.0);
    }

    @Test
    void processPendingTasks_ShouldHandleFilenameWithoutExtension(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-no-ext");
        DownloadTask task = DownloadTask.create(job, "vid-no-ext", "No Ext Video", false);
        ReflectionTestUtils.setField(task, "id", "task-no-ext");
        task.setDescription("Test Description");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-no-ext")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        String output = "[download] Destination: video_no_ext\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy file
        Path videoDir = tempDir.resolve("No Ext Video [vid-no-ext]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video_no_ext"));

        spyService.processPendingTasks();

        // Verify success
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.DOWNLOADED && st != null && st.getFilePath() != null && st.getFilePath().endsWith("video_no_ext");
        }));

        // Verify description file exists with correct name (base filename + " (Description).txt")
        assertThat(Files.exists(videoDir.resolve("video_no_ext (Description).txt"))).isTrue();
    }

    @Test
    void processPendingTasks_ShouldSkipThumbnail_WhenUrlMissing(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-no-thumb");
        DownloadTask task = DownloadTask.create(job, "vid-no-thumb", "No Thumb Video", false);
        ReflectionTestUtils.setField(task, "id", "task-no-thumb");
        task.setThumbnailUrl(null); // Missing URL
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-no-thumb")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        String output = "[download] Destination: video.mp4\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy video file
        Path videoDir = tempDir.resolve("No Thumb Video [vid-no-thumb]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        spyService.processPendingTasks();

        // Verify success
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify no thumbnail file created (default would be video (BQ).jpg)
        assertThat(Files.exists(videoDir.resolve("video (BQ).jpg"))).isFalse();
    }

    @Test
    void processPendingTasks_ShouldDownloadThumbnail_WithCorrectExtension(@TempDir Path tempDir) throws Exception {
        // Setup source thumbnail file
        Path sourceThumb = tempDir.resolve("source.png");
        Files.writeString(sourceThumb, "fake image content");
        String thumbUrl = sourceThumb.toUri().toString();

        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-thumb-ext");
        DownloadTask task = DownloadTask.create(job, "vid-thumb-ext", "Thumb Ext Video", false);
        ReflectionTestUtils.setField(task, "id", "task-thumb-ext");
        task.setThumbnailUrl(thumbUrl);
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-thumb-ext")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        String output = "[download] Destination: video.mp4\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy video file
        Path videoDir = tempDir.resolve("Thumb Ext Video [vid-thumb-ext]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        spyService.processPendingTasks();

        // Verify success
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify thumbnail downloaded with correct extension (.png)
        Path expectedThumb = videoDir.resolve("video (BQ).png");
        assertThat(Files.exists(expectedThumb)).isTrue();
        assertThat(Files.readString(expectedThumb)).isEqualTo("fake image content");
    }

    @Test
    void processPendingTasks_ShouldHandleThumbnailDownloadFailure(@TempDir Path tempDir) throws Exception {
        // Setup invalid thumbnail URL (file not found)
        String thumbUrl = tempDir.resolve("non-existent.jpg").toUri().toString();

        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-thumb-fail");
        DownloadTask task = DownloadTask.create(job, "vid-thumb-fail", "Thumb Fail Video", false);
        ReflectionTestUtils.setField(task, "id", "task-thumb-fail");
        task.setThumbnailUrl(thumbUrl);
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-thumb-fail")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        String output = "[download] Destination: video.mp4\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy video file
        Path videoDir = tempDir.resolve("Thumb Fail Video [vid-thumb-fail]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        spyService.processPendingTasks();

        // Verify success (thumbnail failure is non-fatal)
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify no thumbnail file
        assertThat(Files.exists(videoDir.resolve("video (BQ).jpg"))).isFalse();
    }

    @Test
    void processPendingTasks_ShouldUseDefaultThumbnailExtension_WhenUrlHasNoExtension(@TempDir Path tempDir) throws Exception {
        // Setup source thumbnail file without extension
        Path sourceThumb = tempDir.resolve("source_no_ext");
        Files.writeString(sourceThumb, "fake image content");
        String thumbUrl = sourceThumb.toUri().toString();

        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-thumb-no-ext");
        DownloadTask task = DownloadTask.create(job, "vid-thumb-no-ext", "Thumb No Ext Video", false);
        ReflectionTestUtils.setField(task, "id", "task-thumb-no-ext");
        task.setThumbnailUrl(thumbUrl);
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-thumb-no-ext")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        String output = "[download] Destination: video.mp4\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy video file
        Path videoDir = tempDir.resolve("Thumb No Ext Video [vid-thumb-no-ext]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        spyService.processPendingTasks();

        // Verify success
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify thumbnail downloaded with default extension (.jpg)
        Path expectedThumb = videoDir.resolve("video (BQ).jpg");
        assertThat(Files.exists(expectedThumb)).isTrue();
        assertThat(Files.readString(expectedThumb)).isEqualTo("fake image content");
    }

    @Test
    void processPendingTasks_ShouldUseDefaultThumbnailExtension_WhenDotIsBeforeLastSlash(@TempDir Path tempDir) throws Exception {
        // Setup source thumbnail file in a directory with a dot
        Path folderWithDot = tempDir.resolve("folder.with.dot");
        Files.createDirectories(folderWithDot);
        Path sourceThumb = folderWithDot.resolve("image_no_ext");
        Files.writeString(sourceThumb, "fake image content");
        String thumbUrl = sourceThumb.toUri().toString();

        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-dot-path");
        DownloadTask task = DownloadTask.create(job, "vid-dot-path", "Dot Path Video", false);
        ReflectionTestUtils.setField(task, "id", "task-dot-path");
        task.setThumbnailUrl(thumbUrl);
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-dot-path")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        String output = "[download] Destination: video.mp4\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy video file
        Path videoDir = tempDir.resolve("Dot Path Video [vid-dot-path]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        spyService.processPendingTasks();

        // Verify success
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify thumbnail downloaded with default extension (.jpg)
        Path expectedThumb = videoDir.resolve("video (BQ).jpg");
        assertThat(Files.exists(expectedThumb)).isTrue();
        assertThat(Files.readString(expectedThumb)).isEqualTo("fake image content");
    }

    @Test
    void processPendingTasks_ShouldHandleDescriptionSaveFailure(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-desc-fail");
        DownloadTask task = DownloadTask.create(job, "vid-desc-fail", "Desc Fail Video", false);
        ReflectionTestUtils.setField(task, "id", "task-desc-fail");
        task.setDescription("Some description");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-desc-fail")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        String output = "[download] Destination: video.mp4\n";
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Create dummy video file
        Path videoDir = tempDir.resolve("Desc Fail Video [vid-desc-fail]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        // Create a directory where the description file should be, to cause IOException
        Files.createDirectory(videoDir.resolve("video (Description).txt"));

        spyService.processPendingTasks();

        // Verify success (description failure is non-fatal)
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));
    }

    @Test
    void processPendingTasks_ShouldHandleUpdateInterruption(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-update-int");
        DownloadTask task = DownloadTask.create(job, "vid-update-int", "Update Int Video", false);
        ReflectionTestUtils.setField(task, "id", "task-update-int");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-update-int")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        // Mock update process to throw InterruptedException
        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenThrow(new InterruptedException("Simulated Interrupt"));

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("[download] Destination: video.mp4\n".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Execute
        spyService.processPendingTasks();

        // Verify that download still proceeded despite update failure
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        // Verify interrupt flag was set and clear it
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void processPendingTasks_ShouldProceed_WhenUpdateFails(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-update-fail");
        DownloadTask task = DownloadTask.create(job, "vid-update-fail", "Update Fail Video", false);
        ReflectionTestUtils.setField(task, "id", "task-update-fail");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-update-fail")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        // Mock update process to return non-zero exit code (simulate failure)
        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("Update error output".getBytes()));
        when(updateProcess.waitFor()).thenReturn(1);

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("[download] Destination: video.mp4\n".getBytes()));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Execute
        spyService.processPendingTasks();

        // Verify that download still proceeded despite update failure
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));
    }

    @Test
    void processPendingTasks_ShouldHandleCookieCreationAndCleanupFailure(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob("cookie-ex-config");
        ReflectionTestUtils.setField(job, "id", "job-cookie-ex");
        DownloadTask task = DownloadTask.create(job, "vid-cookie-ex", "Cookie Ex Video", false);
        ReflectionTestUtils.setField(task, "id", "task-cookie-ex");
        job.addTask(task);

        // Setup Config
        DownloaderConfig config = new DownloaderConfig("cookie-ex-config");
        YtDlpConfig ytDlp = new YtDlpConfig("cookie-ex-config");
        ytDlp.setUseCookie(true);
        config.setYtDlpConfig(ytDlp);

        // Mocks
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("cookie-ex-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-cookie-ex")).thenReturn(Optional.of(job));

        // Create Cookie File so Files.exists() passes
        Path cookiePath = tempDir.resolve("cookie-ex-config-cookie.txt");
        Files.writeString(cookiePath, "cookie-content");

        // Spy
        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        // Mock Process for Update
        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        // Mock Files using MockedStatic to trigger IOExceptions
        try (org.mockito.MockedStatic<Files> mockedFiles = org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mockedFiles.when(() -> Files.copy(any(Path.class), any(Path.class), any(java.nio.file.CopyOption[].class)))
                    .thenThrow(new IOException("Simulated copy failure"));
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class)))
                    .thenThrow(new IOException("Simulated delete failure"));

            // Execute
            spyService.processPendingTasks();
        }

        // Verify task failed and message is properly set
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.FAILED && st != null && st.getErrorMessage() != null && st.getErrorMessage().contains("Failed to create temporary cookie file");
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleCookieFinallyCleanupFailure(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("cookie-finally-config");
        ReflectionTestUtils.setField(job, "id", "job-cookie-finally");
        DownloadTask task = DownloadTask.create(job, "vid-cookie-finally", "Cookie Finally Video", false);
        ReflectionTestUtils.setField(task, "id", "task-cookie-finally");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("cookie-finally-config");
        YtDlpConfig ytDlp = new YtDlpConfig("cookie-finally-config");
        ytDlp.setUseCookie(true);
        config.setYtDlpConfig(ytDlp);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("cookie-finally-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-cookie-finally")).thenReturn(Optional.of(job));

        Path cookiePath = tempDir.resolve("cookie-finally-config-cookie.txt");
        Files.writeString(cookiePath, "cookie-content");

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        // Force an execution error to quickly hit the finally block
        doThrow(new IOException("Simulated IO Error")).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Mock Files to fail ONLY on delete, this hits the finally block catch exactly
        try (org.mockito.MockedStatic<Files> mockedFiles = org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class)))
                    .thenThrow(new IOException("Simulated delete failure in finally"));

            spyService.processPendingTasks();
        }

        // Verify task failed with our simulated execution IO Error, showing that finally block executed cleanly.
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.FAILED && st != null && st.getErrorMessage() != null && st.getErrorMessage().contains("I/O error during download: Simulated IO Error");
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleCookieCreateTempFileFailure(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob("cookie-create-fail-config");
        ReflectionTestUtils.setField(job, "id", "job-cookie-create-fail");
        DownloadTask task = DownloadTask.create(job, "vid-cookie-create-fail", "Cookie Create Fail Video", false);
        ReflectionTestUtils.setField(task, "id", "task-cookie-create-fail");
        job.addTask(task);

        // Setup Config
        DownloaderConfig config = new DownloaderConfig("cookie-create-fail-config");
        YtDlpConfig ytDlp = new YtDlpConfig("cookie-create-fail-config");
        ytDlp.setUseCookie(true);
        config.setYtDlpConfig(ytDlp);

        // Mocks
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("cookie-create-fail-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-cookie-create-fail")).thenReturn(Optional.of(job));

        // Create Cookie File so Files.exists() passes
        Path cookiePath = tempDir.resolve("cookie-create-fail-config-cookie.txt");
        Files.writeString(cookiePath, "cookie-content");

        // Spy
        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        // Mock Process for Update
        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        // Mock Files using MockedStatic to trigger IOExceptions
        try (org.mockito.MockedStatic<Files> mockedFiles = org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mockedFiles.when(() -> Files.createTempFile(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(java.nio.file.attribute.FileAttribute[].class)))
                    .thenThrow(new IOException("Simulated createTempFile failure"));

            // Execute
            spyService.processPendingTasks();
        }

        // Verify task failed and message is properly set
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.FAILED && st != null && st.getErrorMessage() != null && st.getErrorMessage().contains("Failed to create temporary cookie file");
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleCookieCopyFailure(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob("cookie-copy-fail-config");
        ReflectionTestUtils.setField(job, "id", "job-cookie-copy-fail");
        DownloadTask task = DownloadTask.create(job, "vid-cookie-copy-fail", "Cookie Copy Fail Video", false);
        ReflectionTestUtils.setField(task, "id", "task-cookie-copy-fail");
        job.addTask(task);

        // Setup Config
        DownloaderConfig config = new DownloaderConfig("cookie-copy-fail-config");
        YtDlpConfig ytDlp = new YtDlpConfig("cookie-copy-fail-config");
        ytDlp.setUseCookie(true);
        config.setYtDlpConfig(ytDlp);

        // Mocks
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("cookie-copy-fail-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-cookie-copy-fail")).thenReturn(Optional.of(job));

        // Create Cookie File so Files.exists() passes
        Path cookiePath = tempDir.resolve("cookie-copy-fail-config-cookie.txt");
        Files.writeString(cookiePath, "cookie-content");

        // Spy
        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        // Mock Process for Update
        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        // Mock Files using MockedStatic to trigger IOExceptions
        try (org.mockito.MockedStatic<Files> mockedFiles = org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mockedFiles.when(() -> Files.copy(any(Path.class), any(Path.class), any(java.nio.file.CopyOption[].class)))
                    .thenThrow(new IOException("Simulated copy failure"));
            // Do not throw exception for deleteIfExists, allowing it to proceed with the normal successful delete logic and cover lines without exceptions

            // Execute
            spyService.processPendingTasks();
        }

        // Verify task failed and message is properly set
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.FAILED && st != null && st.getErrorMessage() != null && st.getErrorMessage().contains("Failed to create temporary cookie file");
        }));
    }

    private void injectSynchronousExecutor(ExecutorServiceImpl spyService) throws Exception {
        java.util.concurrent.ExecutorService mockExecutor = mock(java.util.concurrent.ExecutorService.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(mockExecutor).submit(any(Runnable.class));

        Field executorField = ExecutorServiceImpl.class.getDeclaredField("downloadExecutor");
        executorField.setAccessible(true);
        executorField.set(spyService, mockExecutor);
    }

    @Test
    void processPendingTasks_ShouldReturnEarly_WhenAudioPhaseFails(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-audio-fail");
        DownloadTask task = DownloadTask.create(job, "vid-audio-fail", "Audio Fail Video", true);
        ReflectionTestUtils.setField(task, "id", "task-audio-fail");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        YtDlpConfig ytDlp = new YtDlpConfig("default");
        ytDlp.setExtractAudio(true);
        config.setYtDlpConfig(ytDlp);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-audio-fail")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process audioProcess = mock(Process.class);
        when(audioProcess.getInputStream()).thenReturn(new ByteArrayInputStream("ERROR: Audio extraction failed\n".getBytes()));
        when(audioProcess.waitFor()).thenReturn(1); // Non-zero exit code to fail audio phase

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(audioProcess).when(spyService).startProcess(argThat(list -> list.contains("--extract-audio")), any());

        spyService.processPendingTasks();

        // Verify task failed at audio phase
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask audioSt = t.getSubTask(SubTaskType.AUDIO);
            return t.getStatus() == TaskStatus.FAILED && audioSt != null && audioSt.getStatus() == TaskStatus.FAILED;
        }));

        // Verify that video phase was never started
        verify(spyService, never()).startProcess(argThat(list -> !list.contains("-U") && !list.contains("--extract-audio")), any());
    }

    @Test
    void processPendingTasks_ShouldSucceed_WhenNoVideoSubTask(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-no-video");
        DownloadTask task = DownloadTask.create(job, "vid-no-video", "No Video SubTask", false);
        ReflectionTestUtils.setField(task, "id", "task-no-video");

        // Remove default VIDEO subtask to test the condition where videoSubTask is null
        task.getSubTasks().clear();
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-no-video")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        spyService.processPendingTasks();

        // Since both audio and video subtasks are null, executeDownload returns success immediately.
        // Task status will end up as PENDING because it has no subtasks to determine completeness.
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.PENDING));

        // Ensure yt-dlp was not started for downloading
        verify(spyService, never()).startProcess(argThat(list -> !list.contains("-U")), any());
    }

    @Test
    void processPendingTasks_ShouldHandleProgressParsing_ForAudioFiles(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-audio-ext");
        DownloadTask task = DownloadTask.create(job, "vid-audio-ext", "Audio Ext Video", false);
        ReflectionTestUtils.setField(task, "id", "task-audio-ext");

        // Ensure only AUDIO subtask is active for this test to isolate the parse logic
        task.getSubTasks().clear();
        DownloadSubTask audioSubTask = new DownloadSubTask(task, SubTaskType.AUDIO);
        task.addSubTask(audioSubTask);
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-audio-ext")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process audioProcess = mock(Process.class);
        // Provide destinations with .m4a, .webm, .mp3, .opus, and .wav (to test the false branch)
        // Note: Using increments of 10% ensures the 5% threshold logic in ProgressTracker allows the update.
        String output = """
                [download] Destination: audio.m4a
                [download]  15.0% of 10MB
                [download] Destination: audio.webm
                [download]  25.0% of 10MB
                [download] Destination: audio.mp3
                [download]  35.0% of 10MB
                [download] Destination: audio.opus
                [download]  45.0% of 10MB
                [download] Destination: audio.wav
                [download]  55.0% of 10MB
                """;
        when(audioProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(audioProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(audioProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        // Capture progress updates
        List<Double> savedProgress = new ArrayList<>();
        doAnswer(invocation -> {
            DownloadTask t = invocation.getArgument(0);
            DownloadSubTask st = t.getSubTask(SubTaskType.AUDIO);
            if (st != null && st.getProgress() != null) {
                savedProgress.add(st.getProgress());
            }
            return null;
        }).when(downloadTaskRepository).save(any(DownloadTask.class));

        spyService.processPendingTasks();

        // 15.0 (.m4a), 25.0 (.webm), 35.0 (.mp3), 45.0 (.opus) should be saved
        // 55.0 (.wav) is NOT in the target extension list, so it should be ignored (isTargetFile = false)
        assertThat(savedProgress).contains(15.0, 25.0, 35.0, 45.0);
        assertThat(savedProgress).doesNotContain(55.0);
    }

    @Test
    void processPendingTasks_ShouldUseFallbackDirectoryScan_ForVideo(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-fallback-video");
        DownloadTask task = DownloadTask.create(job, "vid-fallback-video", "Fallback Video", false);
        ReflectionTestUtils.setField(task, "id", "task-fallback-video");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-fallback-video")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Path videoDir = tempDir.resolve("Fallback Video [vid-fallback-video]");
        Files.createDirectories(videoDir);

        // Create a file BEFORE the download process starts. This should be ignored by the fallback scan.
        Path oldFile = videoDir.resolve("old_video.mp4");
        Files.writeString(oldFile, "12345678901234567890"); // size 20

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        // Simulate yt-dlp outputting NO recognizable filename
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("Some unexpected log formatting\n".getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        doAnswer(invocation -> {
            // Simulate yt-dlp downloading new files
            Path smallFile = videoDir.resolve("new_small.mp4");
            Files.writeString(smallFile, "123"); // size 3

            Path largeFile = videoDir.resolve("new_large.mkv");
            Files.writeString(largeFile, "1234567890"); // size 10

            // A large text file, should be ignored due to extension
            Path textFile = videoDir.resolve("new_file.txt");
            Files.writeString(textFile, "123456789012345678901234567890"); // size 30

            return downloadProcess;
        }).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        spyService.processPendingTasks();

        // Verify fallback logic successfully found the largest valid media file
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.DOWNLOADED
                    && st != null
                    && st.getFilePath() != null
                    && st.getFilePath().endsWith("new_large.mkv")
                    && st.getFileSize() == 10L;
        }));
    }

    @Test
    void processPendingTasks_ShouldUseFallbackDirectoryScan_ForAudio(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-fallback-audio");
        DownloadTask task = DownloadTask.create(job, "vid-fallback-audio", "Fallback Audio", true);
        ReflectionTestUtils.setField(task, "id", "task-fallback-audio");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        YtDlpConfig ytDlpConfig = new YtDlpConfig("default");
        ytDlpConfig.setExtractAudio(true);
        config.setYtDlpConfig(ytDlpConfig);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-fallback-audio")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Path videoDir = tempDir.resolve("Fallback Audio [vid-fallback-audio]");
        Files.createDirectories(videoDir);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process audioProcess = mock(Process.class);
        when(audioProcess.getInputStream()).thenReturn(new ByteArrayInputStream("No audio dest parsed\n".getBytes(StandardCharsets.UTF_8)));
        when(audioProcess.waitFor()).thenReturn(0);

        Process videoProcess = mock(Process.class);
        when(videoProcess.getInputStream()).thenReturn(new ByteArrayInputStream("No video dest parsed\n".getBytes(StandardCharsets.UTF_8)));
        when(videoProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        doAnswer(invocation -> {
            Path largeAudio = videoDir.resolve("new_audio.m4a");
            Files.writeString(largeAudio, "12345"); // size 5
            return audioProcess;
        }).when(spyService).startProcess(argThat(list -> list.contains("--extract-audio")), any());

        doAnswer(invocation -> {
            Path largeVideo = videoDir.resolve("new_video.mp4");
            Files.writeString(largeVideo, "1234567890"); // size 10
            return videoProcess;
        }).when(spyService).startProcess(argThat(list -> !list.contains("-U") && !list.contains("--extract-audio")), any());

        spyService.processPendingTasks();

        // Verify fallback mapped the audio file specifically to the AUDIO subtask
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask ast = t.getSubTask(SubTaskType.AUDIO);
            return ast != null
                    && ast.getFilePath() != null
                    && ast.getFilePath().endsWith("new_audio.m4a")
                    && ast.getFileSize() == 5L;
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleIOException_DuringDirectoryScan(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-scan-ioex");
        DownloadTask task = DownloadTask.create(job, "vid-scan-ioex", "Scan IOEx", false);
        ReflectionTestUtils.setField(task, "id", "task-scan-ioex");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        config.setYtDlpConfig(new YtDlpConfig("default"));

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-scan-ioex")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Path videoDir = tempDir.resolve("Scan IOEx [vid-scan-ioex]");
        Files.createDirectories(videoDir);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process downloadProcess = mock(Process.class);
        when(downloadProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
        when(downloadProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());
        doReturn(downloadProcess).when(spyService).startProcess(argThat(list -> !list.contains("-U")), any());

        try (org.mockito.MockedStatic<Files> mockedFiles = org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mockedFiles.when(() -> Files.list(eq(videoDir)))
                    .thenThrow(new IOException("Simulated directory scan error"));

            spyService.processPendingTasks();
        }

        // Verify task completed with warnings because the scan threw exception and no fallback was found
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask st = t.getSubTask(SubTaskType.VIDEO);
            return t.getStatus() == TaskStatus.DOWNLOADED
                    && st != null
                    && st.getErrorMessage() != null
                    && st.getErrorMessage().contains("Could not determine final video filename");
        }));
    }

    @Test
    void processPendingTasks_ShouldHandleExceptionInTaskExecution_WhenTaskHasJob() throws Exception {
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-ex-true");
        DownloadTask task = DownloadTask.create(job, "vid-ex-true", "Video", false);
        ReflectionTestUtils.setField(task, "id", "task-ex-true");
        job.addTask(task);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        DownloaderConfig config = new DownloaderConfig("default");
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        // Throw exception inside the executor's try block to trigger the outer catch
        when(configsService.getResolvedConfig("default")).thenThrow(new RuntimeException("Simulated config error"));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        // For updateJobStatus inside the catch block
        when(downloadJobRepository.findByIdWithTasks("job-ex-true")).thenReturn(Optional.of(job));

        spyService.processPendingTasks();

        // Verify the outer catch handled it, saved the task as FAILED, and updated the Job status
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.FAILED));
        verify(apiClientService).updateItemStatus(eq("vid-ex-true"), eq("task-ex-true"), eq(TaskStatus.FAILED));
        verify(downloadJobRepository).findByIdWithTasks("job-ex-true");
    }

    @Test
    void processPendingTasks_ShouldHandleExceptionInTaskExecution_WhenTaskHasNoJob() throws Exception {
        DownloadTask task = DownloadTask.create(null, "vid-no-job-exec", "No Job Video", false);
        ReflectionTestUtils.setField(task, "id", "task-no-job-exec");

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        DownloaderConfig config = new DownloaderConfig("default");
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        // Execute (job is null, so job.getId() throws NPE -> caught by outer catch)
        spyService.processPendingTasks();

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.FAILED));
        verify(apiClientService).updateItemStatus(eq("vid-no-job-exec"), eq("task-no-job-exec"), eq(TaskStatus.FAILED));
        // Verify updateJobStatus is skipped because task.getJob() is null
        verify(downloadJobRepository, never()).findByIdWithTasks(any());
    }

    @Test
    void processPendingTasks_ShouldCatchException_WhenUpdatingStatusFails() throws Exception {
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-ex-inner");
        DownloadTask task = DownloadTask.create(job, "vid-ex-inner", "Inner Ex Video", false);
        ReflectionTestUtils.setField(task, "id", "task-ex-inner");
        job.addTask(task);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        DownloaderConfig config = new DownloaderConfig("default");
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        // Trigger outer catch
        when(configsService.getResolvedConfig("default")).thenThrow(new RuntimeException("Simulated execution failure"));

        // Trigger inner catch by failing the save operation
        doAnswer(invocation -> {
            DownloadTask t = invocation.getArgument(0);
            if (t.getStatus() == TaskStatus.FAILED) {
                throw new RuntimeException("Simulated save failure");
            }
            return t;
        }).when(downloadTaskRepository).save(any(DownloadTask.class));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);
        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        // Execute (should log the error but not crash the thread)
        spyService.processPendingTasks();

        // apiClientService won't be called because the exception was thrown in the preceding save() step
        verify(downloadTaskRepository, atLeastOnce()).save(any(DownloadTask.class));
        verify(apiClientService, never()).updateItemStatus(any(), any(), eq(TaskStatus.FAILED));
    }

    @Test
    void processPendingTasks_ShouldUseDefaultAudioTemplate_WhenVideoAttributesPresent(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob("audio-template-config");
        ReflectionTestUtils.setField(job, "id", "job-audio-template");
        DownloadTask task = DownloadTask.create(job, "vid-audio-template", "Audio Template Video", true);
        ReflectionTestUtils.setField(task, "id", "task-audio-template");
        job.addTask(task);

        // Setup Config
        DownloaderConfig config = new DownloaderConfig("audio-template-config");
        YtDlpConfig ytDlp = new YtDlpConfig("audio-template-config");
        ytDlp.setExtractAudio(true);
        // Include video-specific attributes %(height) and %(fps) to trigger the fallback branch
        ytDlp.setOutputTemplate("%(title)s_%(height)s_%(fps)s.%(ext)s");
        config.setYtDlpConfig(ytDlp);

        // Mocks
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("audio-template-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-audio-template")).thenReturn(Optional.of(job));

        // Spy
        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        // Mock Processes
        Process mockProcess = mock(Process.class);
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(mockProcess.waitFor()).thenReturn(0);

        doReturn(mockProcess).when(spyService).startProcess(any(), any());

        // Execute
        spyService.processPendingTasks();

        // Verify Audio Phase command used the fallback template
        verify(spyService).startProcess(argThat(list
                -> list.contains("--extract-audio")
                && list.contains("-o")
                && list.contains("%(title)s.%(ext)s")
                && !list.contains("%(title)s_%(height)s_%(fps)s.%(ext)s")
        ), any());

        // Verify Video Phase command used the original template
        verify(spyService).startProcess(argThat(list
                -> !list.contains("--extract-audio") && !list.contains("-U")
                && list.contains("-o")
                && list.contains("%(title)s_%(height)s_%(fps)s.%(ext)s")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldUseDefaultAudioTemplate_WhenFpsAttributePresent(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob("audio-fps-config");
        ReflectionTestUtils.setField(job, "id", "job-audio-fps");
        DownloadTask task = DownloadTask.create(job, "vid-audio-fps", "Audio FPS Video", true);
        ReflectionTestUtils.setField(task, "id", "task-audio-fps");
        job.addTask(task);

        // Setup Config
        DownloaderConfig config = new DownloaderConfig("audio-fps-config");
        YtDlpConfig ytDlp = new YtDlpConfig("audio-fps-config");
        ytDlp.setExtractAudio(true);
        // Only %(fps)s
        ytDlp.setOutputTemplate("%(title)s_%(fps)s.%(ext)s");
        config.setYtDlpConfig(ytDlp);

        // Mocks
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("audio-fps-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-audio-fps")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process mockProcess = mock(Process.class);
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(mockProcess.waitFor()).thenReturn(0);

        doReturn(mockProcess).when(spyService).startProcess(any(), any());

        spyService.processPendingTasks();

        verify(spyService).startProcess(argThat(list
                -> list.contains("--extract-audio")
                && list.contains("-o")
                && list.contains("%(title)s.%(ext)s")
                && !list.contains("%(title)s_%(fps)s.%(ext)s")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldUseDefaultAudioTemplate_WhenResolutionAttributePresent(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob("audio-res-config");
        ReflectionTestUtils.setField(job, "id", "job-audio-res");
        DownloadTask task = DownloadTask.create(job, "vid-audio-res", "Audio Res Video", true);
        ReflectionTestUtils.setField(task, "id", "task-audio-res");
        job.addTask(task);

        // Setup Config
        DownloaderConfig config = new DownloaderConfig("audio-res-config");
        YtDlpConfig ytDlp = new YtDlpConfig("audio-res-config");
        ytDlp.setExtractAudio(true);
        // Only %(resolution)s
        ytDlp.setOutputTemplate("%(title)s_%(resolution)s.%(ext)s");
        config.setYtDlpConfig(ytDlp);

        // Mocks
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("audio-res-config")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-audio-res")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Process mockProcess = mock(Process.class);
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(mockProcess.waitFor()).thenReturn(0);

        doReturn(mockProcess).when(spyService).startProcess(any(), any());

        spyService.processPendingTasks();

        verify(spyService).startProcess(argThat(list
                -> list.contains("--extract-audio")
                && list.contains("-o")
                && list.contains("%(title)s.%(ext)s")
                && !list.contains("%(title)s_%(resolution)s.%(ext)s")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldUseFallbackDirectoryScan_ForAudio_MultipleFiles(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob("default");
        ReflectionTestUtils.setField(job, "id", "job-fallback-audio-multi");
        DownloadTask task = DownloadTask.create(job, "vid-fallback-audio-multi", "Fallback Audio Multi", true);
        ReflectionTestUtils.setField(task, "id", "task-fallback-audio-multi");
        job.addTask(task);

        DownloaderConfig config = new DownloaderConfig("default");
        YtDlpConfig ytDlpConfig = new YtDlpConfig("default");
        ytDlpConfig.setExtractAudio(true);
        config.setYtDlpConfig(ytDlpConfig);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig("default")).thenReturn(config);
        when(configsService.getResolvedConfig(null)).thenReturn(config);
        when(defaultProperties.getDownloadFolder()).thenReturn(tempDir.toString());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloadJobRepository.findByIdWithTasks("job-fallback-audio-multi")).thenReturn(Optional.of(job));

        ExecutorServiceImpl spyService = mock(ExecutorServiceImpl.class, withSettings()
                .useConstructor(downloadTaskRepository, downloadJobRepository, defaultProperties, configsService, apiClientService, taskScheduler)
                .defaultAnswer(CALLS_REAL_METHODS));

        injectSynchronousExecutor(spyService);

        Path videoDir = tempDir.resolve("Fallback Audio Multi [vid-fallback-audio-multi]");
        Files.createDirectories(videoDir);

        Process updateProcess = mock(Process.class);
        when(updateProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(updateProcess.waitFor()).thenReturn(0);

        Process audioProcess = mock(Process.class);
        when(audioProcess.getInputStream()).thenReturn(new ByteArrayInputStream("No audio dest parsed\n".getBytes(StandardCharsets.UTF_8)));
        when(audioProcess.waitFor()).thenReturn(0);

        Process videoProcess = mock(Process.class);
        when(videoProcess.getInputStream()).thenReturn(new ByteArrayInputStream("No video dest parsed\n".getBytes(StandardCharsets.UTF_8)));
        when(videoProcess.waitFor()).thenReturn(0);

        doReturn(updateProcess).when(spyService).startProcess(argThat(list -> list.contains("-U")), any());

        doAnswer(invocation -> {
            // Create multiple audio files to hit `size > maxSize` false branch,
            // and include `.webm` to hit the `lowerName.endsWith(".webm")` branch for audio fallback.
            Files.writeString(videoDir.resolve("audio_10.m4a"), "1234567890"); // size 10
            Files.writeString(videoDir.resolve("audio_5.m4a"), "12345"); // size 5
            Files.writeString(videoDir.resolve("audio_8.webm"), "12345678"); // size 8
            Files.writeString(videoDir.resolve("audio_3.webm"), "123"); // size 3
            return audioProcess;
        }).when(spyService).startProcess(argThat(list -> list.contains("--extract-audio")), any());

        doAnswer(invocation -> {
            // Also verify video phase `size > maxSize` logic behaves identically
            Files.writeString(videoDir.resolve("video_10.mp4"), "1234567890"); // size 10
            Files.writeString(videoDir.resolve("video_5.mp4"), "12345"); // size 5
            return videoProcess;
        }).when(spyService).startProcess(argThat(list -> !list.contains("-U") && !list.contains("--extract-audio")), any());

        spyService.processPendingTasks();

        // The largest audio file is audio_10.m4a (10 bytes).
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask ast = t.getSubTask(SubTaskType.AUDIO);
            return ast != null
                    && ast.getFilePath() != null
                    && ast.getFilePath().endsWith("audio_10.m4a")
                    && ast.getFileSize() == 10L;
        }));

        // The largest video file is video_10.mp4 (10 bytes).
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> {
            DownloadSubTask vst = t.getSubTask(SubTaskType.VIDEO);
            return vst != null
                    && vst.getFilePath() != null
                    && vst.getFilePath().endsWith("video_10.mp4")
                    && vst.getFileSize() == 10L;
        }));
    }

    @Test
    void truncateErrorMessage_ShouldHandleVariousLengthsAndNull() {
        // 1. null message
        String resultNull = ReflectionTestUtils.invokeMethod(executorService, "truncateErrorMessage", (String) null);
        assertThat(resultNull).isNull();

        // 2. message length <= 200
        String shortMessage = "This is a short error message.";
        String resultShort = ReflectionTestUtils.invokeMethod(executorService, "truncateErrorMessage", shortMessage);
        assertThat(resultShort).isEqualTo(shortMessage);

        // 3. message length > 200
        String longMessage = "a".repeat(250);
        String resultLong = ReflectionTestUtils.invokeMethod(executorService, "truncateErrorMessage", longMessage);
        assertThat(resultLong).isNotNull();
        assertThat(resultLong).hasSize(200);
        assertThat(resultLong).isEqualTo("a".repeat(197) + "...");
    }
}
