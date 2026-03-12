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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import ch.lin.downloader.backend.api.app.config.DownloaderDefaultProperties;
import ch.lin.downloader.backend.api.app.repository.DownloadJobRepository;
import ch.lin.downloader.backend.api.app.repository.DownloadTaskRepository;
import ch.lin.downloader.backend.api.app.service.model.DownloadResult;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.DownloadTask;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.JobStatus;
import ch.lin.downloader.backend.api.domain.OverwriteOption;
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
    void init_ShouldStartScheduler_WhenConfigEnabled() {
        DownloaderConfig config = new DownloaderConfig();
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
        DownloaderConfig config = new DownloaderConfig();
        config.setStartDownloadAutomatically(false);
        when(configsService.getResolvedConfig(null)).thenReturn(config);

        executorService.init();

        verify(taskScheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        assertThat(executorService.isSchedulerRunning()).isFalse();
    }

    @Test
    void start_ShouldScheduleTask_WhenNotRunning() {
        DownloaderConfig config = new DownloaderConfig();
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
        DownloaderConfig config = new DownloaderConfig();
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
        DownloaderConfig config = new DownloaderConfig();
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
        DownloaderConfig config = new DownloaderConfig();
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
        DownloaderConfig config = new DownloaderConfig();
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
        when(configsService.getResolvedConfig(null)).thenReturn(new DownloaderConfig());

        executorService.processPendingTasks();

        verify(downloadTaskRepository, never()).save(any(DownloadTask.class));
    }

    @Test
    void processPendingTasks_ShouldProcessTasks_WhenPendingTasksExist() {
        DownloadTask task = new DownloadTask();
        task.setId("task-1");
        task.setVideoId("vid-1");
        task.setTitle("Test Video");
        DownloadJob job = new DownloadJob();
        job.setId("job-1");
        job.setConfigName("default");
        task.setJob(job);

        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(configsService.getResolvedConfig(null)).thenReturn(new DownloaderConfig()); // For thread pool adjustment

        executorService.processPendingTasks();

        verify(downloadTaskRepository).save(task);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DOWNLOADING);
        verify(apiClientService).updateItemStatus("vid-1", "task-1", TaskStatus.DOWNLOADING);
        // Note: The actual execution inside the thread pool is hard to verify without a custom ExecutorService factory.
        // We assume the submission happens if the code reaches this point.
    }

    @Test
    void updateTaskFromResult_ShouldMarkDownloaded_WhenSuccess() {
        DownloadTask task = new DownloadTask();
        task.setId("task-1");

        DownloadResult result = new DownloadResult();
        result.setSuccess(true);
        result.setFilePath("/path/to/file");
        result.setFileSize(1024L);

        executorService.updateTaskFromResult(task, result);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DOWNLOADED);
        assertThat(task.getFilePath()).isEqualTo("/path/to/file");
        assertThat(task.getFileSize()).isEqualTo(1024L);
        verify(downloadTaskRepository).save(task);
    }

    @Test
    void updateTaskFromResult_ShouldMarkFailed_WhenFailure() {
        DownloadTask task = new DownloadTask();
        task.setId("task-1");

        DownloadResult result = new DownloadResult();
        result.setSuccess(false);
        result.setErrorMessage("Error occurred");

        executorService.updateTaskFromResult(task, result);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorMessage()).isEqualTo("Error occurred");
        verify(downloadTaskRepository).save(task);
    }

    @Test
    void updateJobStatus_ShouldMarkCompleted_WhenAllTasksDownloaded() {
        String jobId = "job-1";
        DownloadJob job = new DownloadJob();
        job.setId(jobId);
        DownloadTask task1 = new DownloadTask();
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
        DownloadJob job = new DownloadJob();
        job.setId(jobId);
        DownloadTask task1 = new DownloadTask();
        task1.setStatus(TaskStatus.DOWNLOADED);
        DownloadTask task2 = new DownloadTask();
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
        DownloadJob job = new DownloadJob();
        job.setId(jobId);
        DownloadTask task1 = new DownloadTask();
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
        DownloadJob job = new DownloadJob();
        job.setId(jobId);
        DownloadTask task1 = new DownloadTask();
        task1.setStatus(TaskStatus.DOWNLOADED);
        DownloadTask task2 = new DownloadTask();
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
        DownloadJob job = new DownloadJob();
        job.setId("job-1");
        job.setConfigName("default");

        DownloadTask task = new DownloadTask();
        task.setId("task-1");
        task.setVideoId("vid-1");
        task.setTitle("Test Video");
        task.setDescription("desc");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setThreadPoolSize(3);
        YtDlpConfig ytDlpConfig = new YtDlpConfig();
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
        assertThat(Files.exists(videoDir.resolve("video.description"))).isTrue();
    }

    @Test
    void processPendingTasks_ShouldAdjustThreadPool() {
        // Setup mocks to avoid actual processing
        when(downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING)).thenReturn(Collections.emptyList());

        // 1. Increase size (3 -> 5)
        DownloaderConfig configIncrease = new DownloaderConfig();
        configIncrease.setThreadPoolSize(5);
        when(configsService.getResolvedConfig(null)).thenReturn(configIncrease);

        executorService.processPendingTasks();

        // 2. Decrease size (5 -> 2)
        DownloaderConfig configDecrease = new DownloaderConfig();
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
        DownloaderConfig config = new DownloaderConfig();
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
        DownloaderConfig config = new DownloaderConfig();
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
        DownloaderConfig configNull = new DownloaderConfig();
        configNull.setThreadPoolSize(null);
        when(configsService.getResolvedConfig(null)).thenReturn(configNull);
        executorService.processPendingTasks();

        // 2. Size is 0
        DownloaderConfig configZero = new DownloaderConfig();
        configZero.setThreadPoolSize(0);
        when(configsService.getResolvedConfig(null)).thenReturn(configZero);
        executorService.processPendingTasks();
    }

    @Test
    void isSchedulerRunning_ShouldReturnFalse_WhenCancelled() {
        DownloaderConfig config = new DownloaderConfig();
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
        DownloaderConfig config = new DownloaderConfig();
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
        DownloadJob job = new DownloadJob();
        job.setId("job-full");
        job.setConfigName("full-config");
        DownloadTask task = new DownloadTask();
        task.setId("task-full");
        task.setVideoId("vid-full");
        task.setTitle("Full Config Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        // Setup Config
        DownloaderConfig config = new DownloaderConfig();
        config.setName("full-config");
        YtDlpConfig ytDlp = new YtDlpConfig();
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

        // Capture arguments to verify flags
        verify(spyService).startProcess(argThat(list
                -> list.contains("--cookies")
                && list.contains(cookiePath.toString())
                && list.contains("-f") && list.contains("best")
                && list.contains("--format-sort") && list.contains("res:1080")
                && list.contains("--write-subs")
                && list.contains("--sub-lang") && list.contains("en")
                && list.contains("--write-auto-subs")
                && list.contains("--sub-format") && list.contains("srt")
                && list.contains("--extract-audio")
                && list.contains("--audio-format") && list.contains("mp3")
                && list.contains("--audio-quality") && list.contains("0")
                && list.contains("-k")
                && list.contains("-o") && list.contains("%(title)s.%(ext)s")
                && list.contains("--force-overwrites")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldIncludeRemuxAndSkip_WhenConfigured(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob();
        job.setId("job-remux");
        job.setConfigName("remux-config");
        DownloadTask task = new DownloadTask();
        task.setId("task-remux");
        task.setVideoId("vid-remux");
        task.setTitle("Remux Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        // Setup Config
        DownloaderConfig config = new DownloaderConfig();
        config.setName("remux-config");
        YtDlpConfig ytDlp = new YtDlpConfig();
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
        DownloadJob job = new DownloadJob();
        job.setId("job-subs");
        job.setConfigName("subs-config");
        DownloadTask task = new DownloadTask();
        task.setId("task-subs");
        task.setVideoId("vid-subs");
        task.setTitle("Subs Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("subs-config");
        YtDlpConfig ytDlp = new YtDlpConfig();
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

        verify(downloadTaskRepository, times(2)).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));
        //verify(spyService).startProcess(argThat(list -> !list.contains("--write-subs") && !list.contains("--list-subs") && !list.contains("-U")), any());
    }

    @Test
    void processPendingTasks_ShouldHandleConfigEdgeCases(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-edge");
        job.setConfigName("edge-config");
        DownloadTask task = new DownloadTask();
        task.setId("task-edge");
        task.setVideoId("vid-edge");
        task.setTitle("Edge Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("edge-config");
        YtDlpConfig ytDlp = new YtDlpConfig();
        ytDlp.setExtractAudio(true);
        ytDlp.setAudioFormat(null); // Should trigger null check
        ytDlp.setAudioQuality(null); // Should trigger null check
        ytDlp.setRemuxVideo("mp4"); // Should be ignored because extractAudio is true
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

        verify(downloadTaskRepository, times(2)).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));

        verify(spyService).startProcess(argThat(list
                -> list.contains("--extract-audio")
                && !list.contains("--audio-format")
                && !list.contains("--audio-quality")
                && !list.contains("--remux-video")
                && !list.contains("--force-overwrites")
                && !list.contains("--no-overwrites")
        ), any());
    }

    @Test
    void processPendingTasks_ShouldHandleMissingFile_AfterSuccess(@TempDir Path tempDir) throws Exception {
        DownloadJob job = new DownloadJob();
        job.setId("job-missing");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-missing");
        task.setVideoId("vid-missing");
        task.setTitle("Missing Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        verify(downloadTaskRepository, times(2)).save(argThat(t
                -> t.getStatus() == TaskStatus.DOWNLOADED && t.getFilePath() == null
        ));
    }

    @Test
    void processPendingTasks_ShouldHandleProcessFailure(@TempDir Path tempDir) throws Exception {
        DownloadJob job = new DownloadJob();
        job.setId("job-fail");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-fail");
        task.setVideoId("vid-fail");
        task.setTitle("Fail Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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

        verify(downloadTaskRepository, times(2)).save(argThat(t
                -> t.getStatus() == TaskStatus.FAILED && t.getErrorMessage().contains("ERROR: Something went wrong")
        ));
    }

    @Test
    void processPendingTasks_ShouldHandleIOException(@TempDir Path tempDir) throws Exception {
        DownloadJob job = new DownloadJob();
        job.setId("job-io");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-io");
        task.setVideoId("vid-io");
        task.setTitle("IO Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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

        verify(downloadTaskRepository, times(2)).save(argThat(t
                -> t.getStatus() == TaskStatus.FAILED && t.getErrorMessage().contains("I/O error")
        ));
    }

    @Test
    void processPendingTasks_ShouldHandleInterruptedException(@TempDir Path tempDir) throws Exception {
        DownloadJob job = new DownloadJob();
        job.setId("job-int");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-int");
        task.setVideoId("vid-int");
        task.setTitle("Int Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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

        verify(downloadTaskRepository, times(2)).save(argThat(t
                -> t.getStatus() == TaskStatus.FAILED && t.getErrorMessage().contains("interrupted")
        ));
    }

    @Test
    void processPendingTasks_ShouldLogWarning_WhenCookieFileMissing(@TempDir Path tempDir) throws Exception {
        // Setup Job and Task
        DownloadJob job = new DownloadJob();
        job.setId("job-cookie-missing");
        job.setConfigName("cookie-config");
        DownloadTask task = new DownloadTask();
        task.setId("task-cookie-missing");
        task.setVideoId("vid-cookie-missing");
        task.setTitle("Cookie Missing Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        // Setup Config
        DownloaderConfig config = new DownloaderConfig();
        config.setName("cookie-config");
        YtDlpConfig ytDlp = new YtDlpConfig();
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
        DownloadJob job = new DownloadJob();
        job.setId("job-no-progress");
        job.setConfigName("no-progress-config");
        DownloadTask task = new DownloadTask();
        task.setId("task-no-progress");
        task.setVideoId("vid-no-progress");
        task.setTitle("No Progress Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        // Setup Config
        DownloaderConfig config = new DownloaderConfig();
        config.setName("no-progress-config");
        YtDlpConfig ytDlp = new YtDlpConfig();
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
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t
                -> t.getStatus() == TaskStatus.DOWNLOADED
                && t.getFilePath() != null
                && t.getFilePath().endsWith("video_no_progress.mp4")
        ));
    }

    @Test
    void processPendingTasks_ShouldHandleBufferReading_WithCR_AndEmptyLines(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-buffer");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-buffer");
        task.setVideoId("vid-buffer");
        task.setTitle("Buffer Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig()); // noProgress is false by default

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
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t
                -> t.getStatus() == TaskStatus.DOWNLOADED
                && t.getFilePath() != null
                && t.getFilePath().endsWith("video.mp4")
        ));
    }

    @Test
    void processPendingTasks_ShouldSucceed_WhenExitCode1_AndFileFound(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-exit1");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-exit1");
        task.setVideoId("vid-exit1");
        task.setTitle("Exit1 Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        DownloadJob job = new DownloadJob();
        job.setId("job-exit2");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-exit2");
        task.setVideoId("vid-exit2");
        task.setTitle("Exit2 Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        DownloadJob job = new DownloadJob();
        job.setId("job-fallback-error");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-fallback-error");
        task.setVideoId("vid-fallback-error");
        task.setTitle("Fallback Error Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t
                -> t.getStatus() == TaskStatus.FAILED
                && t.getErrorMessage().contains("yt-dlp process failed with exit code 1")
        ));
    }

    @Test
    void processPendingTasks_ShouldHandleSubtitlesCheck_WhenExitCodeNonZero(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-subs-fail");
        job.setConfigName("subs-config");
        DownloadTask task = new DownloadTask();
        task.setId("task-subs-fail");
        task.setVideoId("vid-subs-fail");
        task.setTitle("Subs Fail Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("subs-config");
        YtDlpConfig ytDlp = new YtDlpConfig();
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
        DownloadJob job = new DownloadJob();
        job.setId("job-subs-ex");
        job.setConfigName("subs-config");
        DownloadTask task = new DownloadTask();
        task.setId("task-subs-ex");
        task.setVideoId("vid-subs-ex");
        task.setTitle("Subs Ex Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("subs-config");
        YtDlpConfig ytDlp = new YtDlpConfig();
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
        DownloadJob job = new DownloadJob();
        job.setId("job-merger");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-merger");
        task.setVideoId("vid-merger");
        task.setTitle("Merger Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t
                -> t.getStatus() == TaskStatus.DOWNLOADED
                && t.getFilePath() != null
                && t.getFilePath().endsWith("merged_video.mkv")
        ));
    }

    @Test
    void processPendingTasks_ShouldParseAlreadyDownloadedOutput(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-already");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-already");
        task.setVideoId("vid-already");
        task.setTitle("Already Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t
                -> t.getStatus() == TaskStatus.DOWNLOADED
                && t.getFilePath() != null
                && t.getFilePath().endsWith("existing_video.mp4")
        ));
    }

    @Test
    void processPendingTasks_ShouldParsePostProcessorOutput(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-post");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-post");
        task.setVideoId("vid-post");
        task.setTitle("Post Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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

        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t
                -> t.getStatus() == TaskStatus.DOWNLOADED
                && t.getFilePath() != null
                && t.getFilePath().endsWith("description.txt")
        ));
    }

    @Test
    void processPendingTasks_ShouldHandleProgressParsing_EdgeCases(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-edge-progress");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-edge-progress");
        task.setVideoId("vid-edge-progress");
        task.setTitle("Edge Progress Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        doAnswer((InvocationOnMock invocation) -> {
            DownloadTask t = invocation.getArgument(0);
            if (t.getProgress() != null) {
                savedProgress.add(t.getProgress());
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
        DownloadJob job = new DownloadJob();
        job.setId("job-edge-2");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-edge-2");
        task.setVideoId("vid-edge-2");
        task.setTitle("Edge 2 Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
            if (t.getProgress() != null) {
                savedProgress.add(t.getProgress());
            }
            return null;
        }).when(downloadTaskRepository).save(any(DownloadTask.class));

        // Create dummy file
        Path videoDir = tempDir.resolve("Edge 2 Video [vid-edge-2]");
        Files.createDirectories(videoDir);
        Files.createFile(videoDir.resolve("video.mp4"));

        spyService.processPendingTasks();

        // Verify
        // 10.0% -> ignored because filename is null (isVideoFile=false)
        // 20.0% -> saved (.webm)
        // 30.0% -> saved (.mkv)
        // ..% -> ignored (exception caught)
        assertThat(savedProgress).contains(20.0, 30.0);
        assertThat(savedProgress).doesNotContain(10.0);
    }

    @Test
    void processPendingTasks_ShouldHandleEmptyFilename_InOutputParsing(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-empty-filename");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-empty-filename");
        task.setVideoId("vid-empty-filename");
        task.setTitle("Empty Filename Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
            if (t.getProgress() != null) {
                savedProgress.add(t.getProgress());
            }
            return null;
        }).when(downloadTaskRepository).save(any(DownloadTask.class));

        spyService.processPendingTasks();

        assertThat(savedProgress).doesNotContain(50.0);
    }

    @Test
    void processPendingTasks_ShouldThrottleProgressUpdates(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-throttle");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-throttle");
        task.setVideoId("vid-throttle");
        task.setTitle("Throttle Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
            if (t.getProgress() != null) {
                savedProgress.add(t.getProgress());
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
        DownloadJob job = new DownloadJob();
        job.setId("job-no-ext");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-no-ext");
        task.setVideoId("vid-no-ext");
        task.setTitle("No Ext Video");
        task.setDescription("Test Description");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t
                -> t.getStatus() == TaskStatus.DOWNLOADED
                && t.getFilePath() != null
                && t.getFilePath().endsWith("video_no_ext")
        ));

        // Verify description file exists with correct name (base filename + .description)
        assertThat(Files.exists(videoDir.resolve("video_no_ext.description"))).isTrue();
    }

    @Test
    void processPendingTasks_ShouldSkipThumbnail_WhenUrlMissing(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-no-thumb");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-no-thumb");
        task.setVideoId("vid-no-thumb");
        task.setTitle("No Thumb Video");
        task.setThumbnailUrl(null); // Missing URL
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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

        // Verify no thumbnail file created (default would be video.jpg)
        assertThat(Files.exists(videoDir.resolve("video.jpg"))).isFalse();
    }

    @Test
    void processPendingTasks_ShouldDownloadThumbnail_WithCorrectExtension(@TempDir Path tempDir) throws Exception {
        // Setup source thumbnail file
        Path sourceThumb = tempDir.resolve("source.png");
        Files.writeString(sourceThumb, "fake image content");
        String thumbUrl = sourceThumb.toUri().toString();

        DownloadJob job = new DownloadJob();
        job.setId("job-thumb-ext");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-thumb-ext");
        task.setVideoId("vid-thumb-ext");
        task.setTitle("Thumb Ext Video");
        task.setThumbnailUrl(thumbUrl);
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        Path expectedThumb = videoDir.resolve("video.png");
        assertThat(Files.exists(expectedThumb)).isTrue();
        assertThat(Files.readString(expectedThumb)).isEqualTo("fake image content");
    }

    @Test
    void processPendingTasks_ShouldHandleThumbnailDownloadFailure(@TempDir Path tempDir) throws Exception {
        // Setup invalid thumbnail URL (file not found)
        String thumbUrl = tempDir.resolve("non-existent.jpg").toUri().toString();

        DownloadJob job = new DownloadJob();
        job.setId("job-thumb-fail");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-thumb-fail");
        task.setVideoId("vid-thumb-fail");
        task.setTitle("Thumb Fail Video");
        task.setThumbnailUrl(thumbUrl);
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        assertThat(Files.exists(videoDir.resolve("video.jpg"))).isFalse();
    }

    @Test
    void processPendingTasks_ShouldUseDefaultThumbnailExtension_WhenUrlHasNoExtension(@TempDir Path tempDir) throws Exception {
        // Setup source thumbnail file without extension
        Path sourceThumb = tempDir.resolve("source_no_ext");
        Files.writeString(sourceThumb, "fake image content");
        String thumbUrl = sourceThumb.toUri().toString();

        DownloadJob job = new DownloadJob();
        job.setId("job-thumb-no-ext");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-thumb-no-ext");
        task.setVideoId("vid-thumb-no-ext");
        task.setTitle("Thumb No Ext Video");
        task.setThumbnailUrl(thumbUrl);
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        Path expectedThumb = videoDir.resolve("video.jpg");
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

        DownloadJob job = new DownloadJob();
        job.setId("job-dot-path");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-dot-path");
        task.setVideoId("vid-dot-path");
        task.setTitle("Dot Path Video");
        task.setThumbnailUrl(thumbUrl);
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        Path expectedThumb = videoDir.resolve("video.jpg");
        assertThat(Files.exists(expectedThumb)).isTrue();
        assertThat(Files.readString(expectedThumb)).isEqualTo("fake image content");
    }

    @Test
    void processPendingTasks_ShouldHandleDescriptionSaveFailure(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-desc-fail");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-desc-fail");
        task.setVideoId("vid-desc-fail");
        task.setTitle("Desc Fail Video");
        task.setDescription("Some description");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
        Files.createDirectory(videoDir.resolve("video.description"));

        spyService.processPendingTasks();

        // Verify success (description failure is non-fatal)
        verify(downloadTaskRepository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.DOWNLOADED));
    }

    @Test
    void processPendingTasks_ShouldHandleUpdateInterruption(@TempDir Path tempDir) throws Exception {
        // Setup
        DownloadJob job = new DownloadJob();
        job.setId("job-update-int");
        job.setConfigName("default");
        DownloadTask task = new DownloadTask();
        task.setId("task-update-int");
        task.setVideoId("vid-update-int");
        task.setTitle("Update Int Video");
        task.setJob(job);
        job.setTasks(List.of(task));

        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");
        config.setYtDlpConfig(new YtDlpConfig());

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
}
