/*
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
 */
package ch.lin.downloader.backend.api.app.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import ch.lin.downloader.backend.api.app.config.DownloaderDefaultProperties;
import ch.lin.downloader.backend.api.app.repository.DownloadJobRepository;
import ch.lin.downloader.backend.api.app.repository.DownloadTaskRepository;
import ch.lin.downloader.backend.api.app.service.model.DownloadResult;
import ch.lin.downloader.backend.api.common.exception.UpdateException;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.DownloadSubTask;
import ch.lin.downloader.backend.api.domain.DownloadTask;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.JobStatus;
import ch.lin.downloader.backend.api.domain.SubTaskType;
import ch.lin.downloader.backend.api.domain.TaskStatus;
import ch.lin.downloader.backend.api.domain.YtDlpConfig;
import jakarta.annotation.PostConstruct;

/**
 * Service responsible for executing pending download tasks.
 * <p>
 * This service uses a scheduled task to periodically poll the database for
 * {@link DownloadTask} entities with a 'PENDING' status. For each pending task,
 * it executes an external {@code yt-dlp} process to download the video. After
 * the download attempt, it updates the status of the {@link DownloadTask}
 * (e.g., to 'COMPLETED' or 'FAILED') and the parent {@link DownloadJob}
 * accordingly. It uses a thread pool to process multiple downloads
 * concurrently.
 */
@Service
public class ExecutorServiceImpl implements ExecutorService {

    private static final String UPDATE_COMMAND_FAILED = "Failed to execute yt-dlp update command.";

    private static final Logger logger = LoggerFactory.getLogger(ExecutorServiceImpl.class);

    private static final Pattern PROGRESS_PATTERN = Pattern.compile(
            "\\[download\\]\\s+([\\d.]+)%");
    private static final Pattern DESTINATION_PATTERN = Pattern.compile(
            "\\[.*?\\](?:.*?)Destination:\\s+(.*)");
    private static final Pattern MERGER_DESTINATION_PATTERN = Pattern.compile(
            "\\[.*?\\] Merging formats into \"(.*)\"");
    private static final Pattern ALREADY_DOWNLOADED_PATTERN = Pattern.compile(
            "\\[download\\] (.*) has already been downloaded");
    private final java.util.concurrent.ExecutorService downloadExecutor = Executors.newFixedThreadPool(3);

    private final DownloadTaskRepository downloadTaskRepository;
    private final DownloadJobRepository downloadJobRepository;
    private final DownloaderDefaultProperties defaultProperties;
    private final ConfigsService configsService;
    private final ApiClientService apiClientService;
    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> scheduledTask;

    /**
     * Constructs a new ExecutorServiceImpl with the necessary dependencies.
     *
     * @param downloadTaskRepository Repository for DownloadTask entities.
     * @param downloadJobRepository Repository for DownloadJob entities.
     * @param defaultProperties Default properties for the downloader.
     * @param configsService Service for managing configurations.
     * @param apiClientService Service for communicating with the API.
     * @param taskScheduler Scheduler for executing tasks.
     */
    public ExecutorServiceImpl(DownloadTaskRepository downloadTaskRepository,
            DownloadJobRepository downloadJobRepository, DownloaderDefaultProperties defaultProperties,
            ConfigsService configsService, ApiClientService apiClientService, TaskScheduler taskScheduler) {
        this.downloadTaskRepository = downloadTaskRepository;
        this.downloadJobRepository = downloadJobRepository;
        this.defaultProperties = defaultProperties;
        this.configsService = configsService;
        this.apiClientService = apiClientService;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Initializes the service. Checks if the active configuration has automatic
     * downloads enabled and starts the scheduler if so.
     */
    @PostConstruct
    public void init() {
        // Clean up tasks that were left in DOWNLOADING state due to a crash or unexpected exception
        try {
            List<DownloadTask> stuckTasks = downloadTaskRepository.findAllByStatusWithJob(TaskStatus.DOWNLOADING);
            if (stuckTasks != null && !stuckTasks.isEmpty()) {
                logger.info("Found {} tasks stuck in DOWNLOADING state. Resetting them to FAILED and notifying Hub.", stuckTasks.size());
                for (DownloadTask task : stuckTasks) {
                    task.setStatus(TaskStatus.FAILED);
                    downloadTaskRepository.save(task);
                    apiClientService.updateItemStatus(task.getVideoId(), task.getId(), TaskStatus.FAILED);
                    if (task.getJob() != null) {
                        updateJobStatus(task.getJob().getId());
                    }
                }
            }

            // Also reset QUEUED tasks back to PENDING so they can be picked up again
            List<DownloadTask> queuedTasks = downloadTaskRepository.findAllByStatusWithJob(TaskStatus.QUEUED);
            if (queuedTasks != null && !queuedTasks.isEmpty()) {
                logger.info("Found {} tasks stuck in QUEUED state. Resetting them to PENDING.", queuedTasks.size());
                for (DownloadTask task : queuedTasks) {
                    task.setStatus(TaskStatus.PENDING);
                    downloadTaskRepository.save(task);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not reset stuck tasks during initialization: {}", e.getMessage());
        }

        // On application startup, check the active configuration's
        // startDownloadAutomatically flag.
        DownloaderConfig activeConfig = configsService.getResolvedConfig(null);
        if (Boolean.TRUE.equals(activeConfig.getStartDownloadAutomatically())) {
            start(); // Call the existing start method to schedule the task
        }
    }

    /**
     * Starts the scheduler to process download tasks.
     * <p>
     * This method schedules the {@link #processPendingTasks()} method to run
     * with a fixed delay.
     */
    @Override
    public void start() {
        // This method is now primarily for manual start or triggered by init().
        if (scheduledTask == null || scheduledTask.isDone()) {
            DownloaderConfig activeConfig = configsService.getResolvedConfig(null);  // getResolvedConfig ensures duration is not null.
            Duration duration = Duration.ofSeconds(activeConfig.getDuration());
            scheduledTask = taskScheduler.scheduleWithFixedDelay(this::processPendingTasks, Objects.requireNonNull(duration));
            logger.info("Download task scheduler started with a fixed delay of {} seconds.", duration);
        } else {
            logger.warn("Download task scheduler is already running.");
        }
    }

    /**
     * Stops the scheduler from processing download tasks.
     */
    @Override
    public void stop() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            // false: don't interrupt the task if it's currently running.
            scheduledTask.cancel(false);
            logger.info("Download task scheduler stopped.");
        } else {
            logger.warn("Download task scheduler is not running.");
        }
    }

    /**
     * Periodically scans for and processes pending download tasks.
     * <p>
     * This method is invoked by the scheduler. It finds pending tasks, updates
     * their status, and submits them for execution.
     */
    @Override
    @Transactional
    public void processPendingTasks() {
        // This method is now called by the TaskScheduler, not via @Scheduled
        logger.debug("Scheduler running: Checking for pending download tasks.");
        adjustThreadPoolSize();

        DownloaderConfig globalConfig = configsService.getResolvedConfig(null);

        // Backpressure: Check if the executor queue is already busy.
        if (downloadExecutor instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
            int maxQueueSize = Optional.ofNullable(globalConfig.getMaxQueueSize()).orElse(50);
            if (tpe.getQueue().size() > maxQueueSize) {
                logger.info("Executor queue is busy ({} tasks waiting). Skipping new task fetch.", tpe.getQueue().size());
                return;
            }
        }

        List<DownloadTask> pendingTasks = downloadTaskRepository.findAllByStatusWithJob(TaskStatus.PENDING);

        if (pendingTasks.isEmpty()) {
            return;
        }

        logger.info("Found {} pending tasks to process.", pendingTasks.size());
        try {
            updateYtDlp();
        } catch (UpdateException e) {
            logger.warn("yt-dlp update failed, but proceeding with downloads: {}", e.getMessage());
        }

        for (DownloadTask task : pendingTasks) {
            task.setStatus(TaskStatus.QUEUED);
            downloadTaskRepository.save(task);

            downloadExecutor.submit(() -> {
                try {
                    task.setStatus(TaskStatus.DOWNLOADING);
                    downloadTaskRepository.save(task);

                    // Update API status to DOWNLOADING
                    apiClientService.updateItemStatus(task.getVideoId(), task.getId(), TaskStatus.DOWNLOADING);

                    DownloadJob job = task.getJob();
                    logger.info("Starting download for video '{}' (Task ID: {}, Job ID: {})", task.getTitle(), task.getId(),
                            job.getId());

                    DownloaderConfig activeConfig = configsService.getResolvedConfig(job.getConfigName());
                    DownloadResult result = executeDownload(task, activeConfig);

                    updateTaskFromResult(task, result);
                    updateJobStatus(job.getId());
                } catch (Exception e) {
                    logger.error("Unexpected error executing download for task {}: {}", task.getId(), e.getMessage(), e);
                    try {
                        task.setStatus(TaskStatus.FAILED);
                        downloadTaskRepository.save(task);
                        apiClientService.updateItemStatus(task.getVideoId(), task.getId(), TaskStatus.FAILED);
                        if (task.getJob() != null) {
                            updateJobStatus(task.getJob().getId());
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to update status to FAILED and notify Hub for task {}", task.getId(), ex);
                    }
                }
            });
        }
    }

    /**
     * Adjusts the thread pool size based on the active configuration.
     */
    private void adjustThreadPoolSize() {
        try {
            DownloaderConfig activeConfig = configsService.getResolvedConfig(null);
            Integer newSize = activeConfig.getThreadPoolSize();

            if (newSize != null && newSize > 0 && downloadExecutor instanceof java.util.concurrent.ThreadPoolExecutor threadPool) {
                int currentSize = threadPool.getCorePoolSize();
                if (currentSize != newSize) {
                    logger.info("Updating thread pool size from {} to {}", currentSize, newSize);
                    if (newSize > currentSize) {
                        threadPool.setMaximumPoolSize(newSize);
                        threadPool.setCorePoolSize(newSize);
                    } else {
                        threadPool.setCorePoolSize(newSize);
                        threadPool.setMaximumPoolSize(newSize);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to adjust thread pool size", e);
        }
    }

    /**
     * Checks if the scheduler is currently running.
     *
     * @return True if the scheduler is running, false otherwise.
     */
    public boolean isSchedulerRunning() {
        return scheduledTask != null && !scheduledTask.isCancelled() && !scheduledTask.isDone();
    }

    /**
     * Updates a task's status and details based on the result of a download
     * attempt.
     *
     * @param task The task to update.
     * @param result The result of the download operation.
     */
    @Transactional
    protected void updateTaskFromResult(DownloadTask task, DownloadResult result) {
        task.updateStatusBasedOnSubTasks();
        downloadTaskRepository.save(task);
        if (task.getStatus() == TaskStatus.FAILED) {
            apiClientService.updateItemStatus(task.getVideoId(), task.getId(), TaskStatus.FAILED);
        } else if (task.getStatus() == TaskStatus.DOWNLOADED) {
            DownloadSubTask videoTask = task.getSubTask(SubTaskType.VIDEO);
            apiClientService.updateItem(task.getVideoId(), task.getId(),
                    Optional.ofNullable(videoTask).map(subTask -> subTask.getFileSize()).orElse(0L),
                    Optional.ofNullable(videoTask).map(subTask -> subTask.getFilePath()).orElse(""),
                    TaskStatus.DOWNLOADED);
        }
        logger.info("Finished processing task {} for video '{}' with status: {}", task.getId(), task.getTitle(),
                task.getStatus());
    }

    /**
     * Updates the status of a job based on the status of its tasks.
     *
     * @param jobId The unique identifier of the job to update.
     * @throws IllegalStateException If the job is not found.
     */
    @Transactional
    protected void updateJobStatus(String jobId) {
        DownloadJob job = downloadJobRepository.findByIdWithTasks(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found for status update: " + jobId));

        long totalTasks = job.getTasks().size();
        long completedTasks = job.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.DOWNLOADED).count();
        long failedTasks = job.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.FAILED).count();

        if (completedTasks + failedTasks == totalTasks) {
            if (failedTasks == 0) {
                job.setStatus(JobStatus.COMPLETED);
            } else if (completedTasks > 0) {
                job.setStatus(JobStatus.PARTIALLY_COMPLETED);
            } else {
                job.setStatus(JobStatus.FAILED);
            }
            logger.info("Job {} has finished with final status: {}", jobId, job.getStatus());
            downloadJobRepository.save(job);
        } else {
            job.setStatus(JobStatus.IN_PROGRESS);
            downloadJobRepository.save(job);
        }
    }

    /**
     * Executes the download process for a single task.
     *
     * @param task The task to execute.
     * @param config The configuration to use for the download.
     * @return A {@link DownloadResult} object containing the outcome of the
     * download.
     */
    private DownloadResult executeDownload(DownloadTask task, DownloaderConfig config) {
        DownloadResult result = new DownloadResult();
        result.setVideoId(task.getVideoId());

        // Sanitize title and create a predictable directory name.
        String sanitizedTitle = task.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
        String folderName = String.format("%s [%s]", sanitizedTitle, task.getVideoId()).trim();
        Path videoDirectory = Paths.get(defaultProperties.getDownloadFolder(), folderName);
        String videoUrl = "https://www.youtube.com/watch?v=" + task.getVideoId();

        logger.info("Output folder: {}", videoDirectory.toString());

        Path cookiePath = Paths.get(defaultProperties.getNetscapeCookieFolder(), config.getName() + "-cookie.txt");

        int maxRetries = Optional.ofNullable(config.getMaxDownloadRetries()).orElse(3);

        DownloadSubTask audioSubTask = task.getSubTask(SubTaskType.AUDIO);
        if (audioSubTask != null) {
            audioSubTask.setStatus(TaskStatus.DOWNLOADING);
            task.updateStatusBasedOnSubTasks();
            downloadTaskRepository.save(task);

            logger.info("Starting Audio extraction for video '{}'", task.getVideoId());
            boolean audioSuccess = false;
            boolean trimFilenames = false;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                if (attempt > 1) {
                    logger.info("Retrying Audio extraction for video '{}' (Attempt {}/{})", task.getVideoId(), attempt, maxRetries);
                }
                audioSuccess = executeYtDlpPhase(task, audioSubTask, config, videoDirectory, videoUrl, true, cookiePath, result, trimFilenames);
                if (audioSuccess) {
                    break;
                }
                if (isFileNameTooLongError(audioSubTask.getErrorMessage())) {
                    trimFilenames = true;
                    logger.warn("Audio extraction failed due to filename too long. Retrying with trimmed filenames...");
                    audioSubTask.setErrorMessage(null);
                }
                try {
                    if (attempt < maxRetries) {
                        long backoffMs = calculateRetryBackoffMs(config);
                        logger.info("Sleeping for {} ms before next audio extraction attempt...", backoffMs);
                        sleepForRetry(backoffMs);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!audioSuccess) {
                audioSubTask.setStatus(TaskStatus.FAILED);
                task.updateStatusBasedOnSubTasks();
                downloadTaskRepository.save(task);
                return result;
            }
            audioSubTask.setStatus(TaskStatus.DOWNLOADED);
            task.updateStatusBasedOnSubTasks();
            downloadTaskRepository.save(task);
        }

        DownloadSubTask videoSubTask = task.getSubTask(SubTaskType.VIDEO);
        if (videoSubTask != null) {
            videoSubTask.setStatus(TaskStatus.DOWNLOADING);
            task.updateStatusBasedOnSubTasks();
            downloadTaskRepository.save(task);

            logger.info("Starting Video download for video '{}'", task.getVideoId());
            boolean videoSuccess = false;
            boolean trimFilenames = false;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                if (attempt > 1) {
                    logger.info("Retrying Video download for video '{}' (Attempt {}/{})", task.getVideoId(), attempt, maxRetries);
                }
                videoSuccess = executeYtDlpPhase(task, videoSubTask, config, videoDirectory, videoUrl, false, cookiePath, result, trimFilenames);
                if (videoSuccess) {
                    break;
                }
                if (isFileNameTooLongError(videoSubTask.getErrorMessage())) {
                    trimFilenames = true;
                    logger.warn("Video download failed due to filename too long. Retrying with trimmed filenames...");
                    videoSubTask.setErrorMessage(null);
                }
                try {
                    if (attempt < maxRetries) {
                        long backoffMs = calculateRetryBackoffMs(config);
                        logger.info("Sleeping for {} ms before next video download attempt...", backoffMs);
                        sleepForRetry(backoffMs);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!videoSuccess) {
                videoSubTask.setStatus(TaskStatus.FAILED);
                task.updateStatusBasedOnSubTasks();
                downloadTaskRepository.save(task);
                return result;
            }
            videoSubTask.setStatus(TaskStatus.DOWNLOADED);
            task.updateStatusBasedOnSubTasks();
            downloadTaskRepository.save(task);

            if (StringUtils.hasText(videoSubTask.getFilePath())) {
                String downloadedFile = Paths.get(videoSubTask.getFilePath()).getFileName().toString();
                String baseFilename = getBaseFilename(downloadedFile);
                downloadThumbnail(task, videoDirectory, baseFilename, result);
                saveDescription(task, videoDirectory, baseFilename, result);
            } else {
                String fallbackBaseFilename = task.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
                downloadThumbnail(task, videoDirectory, fallbackBaseFilename, result);
                saveDescription(task, videoDirectory, fallbackBaseFilename, result);
            }
        }

        result.setSuccess(true);
        return result;
    }

    /**
     * Calculates a randomized backoff time (in milliseconds) for retries based
     * on the configuration.
     *
     * @param config The DownloaderConfig containing the sleep settings.
     * @return A randomized sleep duration in milliseconds.
     */
    private long calculateRetryBackoffMs(DownloaderConfig config) {
        long defaultBackoffMs = 2000L;
        if (config == null || config.getYtDlpConfig() == null) {
            return defaultBackoffMs;
        }

        YtDlpConfig ytDlpConfig = config.getYtDlpConfig();
        Integer sleepSec = ytDlpConfig.getSleepInterval();
        Integer maxSleepSec = ytDlpConfig.getMaxSleepInterval();

        long minMs = (sleepSec != null && sleepSec > 0) ? sleepSec * 1000L : defaultBackoffMs;

        if (maxSleepSec != null && maxSleepSec > 0) {
            long maxMs = maxSleepSec * 1000L;
            if (maxMs > minMs) {
                return ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
            } else if (maxMs == minMs) {
                return minMs;
            }
        }
        return minMs;
    }

    /**
     * Pauses the current thread for the specified duration. Extracted to a
     * separate method to avoid IDE/linter warnings about calling Thread.sleep()
     * directly inside a loop.
     *
     * @param ms The number of milliseconds to sleep.
     * @throws InterruptedException If the thread is interrupted while sleeping.
     */
    protected void sleepForRetry(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    private List<String> buildCommandForPhase(DownloaderConfig config, String videoUrl, boolean isAudioPhase, Path cookiePath, AtomicReference<Path> tempCookiePathRef, DownloadTask task, DownloadResult result, boolean trimFilenames) {
        List<String> command = new ArrayList<>();
        command.add("yt-dlp");
        command.add("--impersonate");
        command.add("chrome");
        command.add("--js-runtimes");
        command.add("deno");

        YtDlpConfig ytDlpConfig = config.getYtDlpConfig();

        if (Boolean.TRUE.equals(ytDlpConfig.getUseCookie())) {
            if (Files.exists(cookiePath)) {
                try {
                    Path tempCookiePath = Files.createTempFile("yt-dlp-cookie-", ".txt");
                    Files.copy(cookiePath, tempCookiePath, StandardCopyOption.REPLACE_EXISTING);
                    tempCookiePathRef.set(tempCookiePath);
                    command.add("--cookies");
                    command.add(tempCookiePath.toString());
                } catch (IOException e) {
                    logger.error("Failed to create temporary cookie file for video {}: {}", task.getVideoId(), e.getMessage(), e);
                    result.setErrorMessage("Failed to create temporary cookie file: " + e.getMessage());
                    return null;
                }
            } else {
                logger.warn("Cookie usage enabled for config '{}', but cookie file not found at: {}", config.getName(), cookiePath);
            }
        }

        if (isAudioPhase) {
            command.add("-f");
            command.add("ba");
            command.add("--extract-audio");

            if (StringUtils.hasText(ytDlpConfig.getAudioFormat())) {
                command.add("--audio-format");
                command.add(ytDlpConfig.getAudioFormat());
            } else {
                command.add("--audio-format");
                command.add("m4a");
            }

            if (ytDlpConfig.getAudioQuality() != null) {
                command.add("--audio-quality");
                command.add(String.valueOf(ytDlpConfig.getAudioQuality()));
            }
        } else {
            if (StringUtils.hasText(ytDlpConfig.getFormatFiltering())) {
                command.add("-f");
                command.add(ytDlpConfig.getFormatFiltering());
            }

            if (StringUtils.hasText(ytDlpConfig.getFormatSorting())) {
                command.add("--format-sort");
                command.add(ytDlpConfig.getFormatSorting());
            }

            if (StringUtils.hasText(ytDlpConfig.getRemuxVideo())) {
                command.add("--remux-video");
                command.add(ytDlpConfig.getRemuxVideo());
            }

            boolean wantsManual = Boolean.TRUE.equals(ytDlpConfig.getWriteSubs());
            boolean wantsAuto = Boolean.TRUE.equals(ytDlpConfig.getWriteAutoSubs());

            boolean appendSubLang = false;
            String langToUse = ytDlpConfig.getSubLang();

            if (wantsManual || wantsAuto) {
                SubtitleInfo subInfo = getSubtitleInfo(videoUrl, tempCookiePathRef.get());

                if (wantsManual && subInfo.hasManualSubs) {
                    command.add("--write-subs");
                    appendSubLang = true;
                } else if (wantsAuto && subInfo.hasAutoSubs) {
                    if (wantsManual) {
                        logger.info("Manual subtitles are enabled but not found for video {}. Falling back to auto-generated captions.", task.getVideoId());
                    }

                    command.add("--write-auto-subs");
                    appendSubLang = true;

                    // To protect the system and avoid triggering YouTube's translation API 429 error for auto-captions,
                    // completely ignore the user-configured language and enforce the use of the detected original audio language (-orig).
                    if (StringUtils.hasText(ytDlpConfig.getSubLang())) {
                        logger.info("Ignoring configured sub-lang '{}' and enforcing original language '{}' for auto-captions to prevent HTTP 429 errors.", ytDlpConfig.getSubLang(), subInfo.autoOriginalLanguage);
                    }
                    langToUse = subInfo.autoOriginalLanguage;
                }
            }

            if (appendSubLang && StringUtils.hasText(langToUse)) {
                command.add("--sub-lang");
                command.add(langToUse);
            }

            if (StringUtils.hasText(ytDlpConfig.getSubFormat())) {
                command.add("--sub-format");
                command.add(ytDlpConfig.getSubFormat());
            }

            if (Boolean.TRUE.equals(ytDlpConfig.getKeepVideo())) {
                command.add("-k");
            }
        }

        if (StringUtils.hasText(ytDlpConfig.getOutputTemplate())) {
            if (isAudioPhase) {
                String template = ytDlpConfig.getOutputTemplate();
                // If the template contains video-specific attributes, force a clean default template for audio
                if (template.contains("%(height)") || template.contains("%(fps)") || template.contains("%(resolution)")) {
                    command.add("-o");
                    command.add("%(title)s.%(ext)s");
                } else {
                    command.add("-o");
                    command.add(template);
                }
            } else {
                // Apply the configured template normally for the video phase
                command.add("-o");
                command.add(ytDlpConfig.getOutputTemplate());
            }
        } // If outputTemplate is empty, do not add -o, letting yt-dlp use its own default

        if (Boolean.TRUE.equals(ytDlpConfig.getNoProgress())) {
            command.add("--no-progress");
        }

        // Add overwrite options
        if (ytDlpConfig.getOverwrite() != null) {
            switch (ytDlpConfig.getOverwrite()) {
                case FORCE:
                    command.add("--force-overwrites");
                    break;
                case SKIP:
                    command.add("--no-overwrites");
                    break;
                case DEFAULT:
                default:
                    // This is the default behavior of yt-dlp (--no-force-overwrites), so no flag
                    // is needed.
                    break;
            }
        }

        // Add sleep parameters
        if (ytDlpConfig.getSleepInterval() != null && ytDlpConfig.getSleepInterval() > 0) {
            command.add("--sleep-interval");
            command.add(String.valueOf(ytDlpConfig.getSleepInterval()));
        }
        if (ytDlpConfig.getMaxSleepInterval() != null && ytDlpConfig.getMaxSleepInterval() > 0) {
            command.add("--max-sleep-interval");
            command.add(String.valueOf(ytDlpConfig.getMaxSleepInterval()));
        }
        if (ytDlpConfig.getSleepSubtitles() != null && ytDlpConfig.getSleepSubtitles() > 0) {
            command.add("--sleep-subtitles");
            command.add(String.valueOf(ytDlpConfig.getSleepSubtitles()));
        }

        if (!isAudioPhase) {
            command.add("--write-info-json");
        }

        if (trimFilenames) {
            command.add("--trim-filenames");
            command.add("100");
        }

        command.add(videoUrl);

        logger.info("Executing command: {}", String.join(" ", command));
        return command;
    }

    private boolean executeYtDlpPhase(DownloadTask task, DownloadSubTask subTask, DownloaderConfig config, Path videoDirectory, String videoUrl, boolean isAudioPhase, Path cookiePath, DownloadResult result, boolean trimFilenames) {
        AtomicReference<Path> tempCookiePathRef = new AtomicReference<>();
        List<String> command = buildCommandForPhase(config, videoUrl, isAudioPhase, cookiePath, tempCookiePathRef, task, result, trimFilenames);

        if (command == null) {
            subTask.setErrorMessage(truncateErrorMessage(result.getErrorMessage()));
            return false;
        }

        // Snapshot existing files in the output directory
        Set<Path> existingFiles = new HashSet<>();
        if (Files.exists(videoDirectory)) {
            try (var stream = Files.list(videoDirectory)) {
                stream.forEach(existingFiles::add);
            } catch (IOException e) {
                logger.warn("Failed to list existing files in directory: {}", videoDirectory);
            }
        }

        AtomicReference<String> finalFilename = new AtomicReference<>();
        ProgressTracker progressTracker = new ProgressTracker();

        try {
            long startTime = System.currentTimeMillis();
            Process process = startProcess(command, videoDirectory);
            StringBuilder processOutput = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                if (Boolean.TRUE.equals(config.getYtDlpConfig().getNoProgress())) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        processOutput.append(line).append("\n");
                        logger.debug("[yt-dlp] {}", line);
                        parseYtDlpOutput(line, task, subTask, finalFilename, progressTracker);
                    }
                } else {
                    StringBuilder lineBuilder = new StringBuilder();
                    char[] buffer = new char[2048];
                    int charsRead;
                    int lineStart = 0;
                    while ((charsRead = reader.read(buffer)) != -1) {
                        for (int i = 0; i < charsRead; ++i) {
                            char c = buffer[i];
                            if (c == '\n' || c == '\r') {
                                // Append the segment of the buffer that forms the line
                                lineBuilder.append(buffer, lineStart, i - lineStart);
                                if (lineBuilder.length() > 0) {
                                    String line = lineBuilder.toString();
                                    processOutput.append(line).append(c);
                                    logger.debug("[yt-dlp] {}", line);
                                    parseYtDlpOutput(line, task, subTask, finalFilename, progressTracker);
                                    lineBuilder.setLength(0);
                                }
                                lineStart = i + 1; // Next line starts after this character
                            }
                        }
                        // Append any remaining part of the buffer to the line builder
                        if (lineStart < charsRead) {
                            lineBuilder.append(buffer, lineStart, charsRead - lineStart);
                        }
                        lineStart = 0; // Reset for the next buffer read
                    }
                }
            }

            int exitCode = process.waitFor();
            long duration = System.currentTimeMillis() - startTime;
            logger.info("yt-dlp phase '{}' for {} exited with code {}. Duration: {} ms",
                    isAudioPhase ? "AUDIO" : "VIDEO", task.getVideoId(), exitCode, duration);

            String downloadedFile = finalFilename.get();
            boolean fileFound = false;

            // 1. Primary Strategy: Try the filename parsed from yt-dlp output logs
            if (StringUtils.hasText(downloadedFile)) {
                String lowerName = downloadedFile.toLowerCase();
                // Prevent metadata, subtitle, or thumbnail files from being mistakenly identified as the main media file.
                boolean isMetadataFile = lowerName.endsWith(".info.json") || lowerName.endsWith(".description")
                        || lowerName.endsWith(".vtt") || lowerName.endsWith(".srt") || lowerName.endsWith(".txt")
                        || lowerName.endsWith(".jpg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp");

                if (!isMetadataFile) {
                    Path path = videoDirectory.resolve(downloadedFile);
                    if (Files.exists(path)) {
                        subTask.setFilePath(path.toString());
                        subTask.setFileSize(Files.size(path));
                        fileFound = true;
                        logger.info("Strategy 1: Identified final target file via yt-dlp logs: {}", downloadedFile);
                    } else {
                        logger.warn("Strategy 1: yt-dlp logs indicated final file '{}', but it wasn't found on disk. Falling back to directory scan.", downloadedFile);
                    }
                } else {
                    logger.debug("Strategy 1: Parsed filename '{}' is a metadata/image file. Ignoring it for media target.", downloadedFile);
                }
            }

            // 2. Fallback Strategy: Directory Scan (only if primary strategy fails)
            if (!fileFound && (exitCode == 0 || exitCode == 1)) {
                Path targetPath = null;
                long maxSize = -1;

                if (Files.exists(videoDirectory)) {
                    try (var stream = Files.list(videoDirectory)) {
                        List<Path> newFiles = stream
                                .filter(Files::isRegularFile)
                                .filter(p -> !existingFiles.contains(p))
                                .collect(Collectors.toList());

                        List<String> targetExts = isAudioPhase
                                ? Arrays.asList(".m4a", ".mp3", ".opus", ".wav", ".flac", ".aac")
                                : Arrays.asList(".mp4", ".mkv", ".webm", ".avi", ".flv", ".mov");

                        for (Path file : newFiles) {
                            String lowerName = file.getFileName().toString().toLowerCase();
                            boolean isMedia = targetExts.stream().anyMatch(lowerName::endsWith) || lowerName.endsWith(".webm");

                            if (isMedia) {
                                long size = Files.size(file);
                                if (size > maxSize) {
                                    maxSize = size;
                                    targetPath = file;
                                }
                            }
                        }
                    } catch (IOException e) {
                        logger.error("Failed to scan directory for downloaded files: {}", videoDirectory, e);
                    }
                }

                if (targetPath != null) {
                    downloadedFile = targetPath.getFileName().toString();
                    subTask.setFilePath(targetPath.toString());
                    subTask.setFileSize(Files.size(targetPath));
                    fileFound = true;
                    logger.info("Fallback Strategy: Found target file via directory scan: {}", downloadedFile);
                }
            }

            if (exitCode == 0 || (exitCode == 1 && fileFound)) {
                if (!fileFound) {
                    String warningMsg = "Could not determine final video filename from yt-dlp output or fallback directory scan.";
                    subTask.setErrorMessage(truncateErrorMessage(warningMsg));
                    result.addWarning(warningMsg);
                    logger.warn(warningMsg);
                }
                return true;
            } else {
                String errorMessage = extractYtDlpError(processOutput.toString(), exitCode);
                subTask.setErrorMessage(truncateErrorMessage(errorMessage));
                result.setErrorMessage(errorMessage);
                logger.error("Failed to download video {} (Phase: {}). yt-dlp exit code: {}. Output:\n{}",
                        task.getVideoId(), isAudioPhase ? "AUDIO" : "VIDEO", exitCode, processOutput);
                return false;
            }
        } catch (IOException e) {
            String errorMsg = "I/O error during download: " + e.getMessage();
            subTask.setErrorMessage(truncateErrorMessage(errorMsg));
            result.setErrorMessage(errorMsg);
            logger.error("I/O error while trying to download video {}: {}", task.getVideoId(), e.getMessage(), e);
            return false;
        } catch (InterruptedException e) {
            String errorMsg = "Download process was interrupted.";
            subTask.setErrorMessage(truncateErrorMessage(errorMsg));
            result.setErrorMessage(errorMsg);
            logger.error("Download process for video {} was interrupted.", task.getVideoId(), e);
            Thread.currentThread().interrupt();
            return false;
        } finally {
            Path tempCookiePath = tempCookiePathRef.get();
            if (tempCookiePath != null) {
                try {
                    Files.deleteIfExists(tempCookiePath);
                } catch (IOException e) {
                    logger.warn("Failed to delete temporary cookie file: {}", tempCookiePath, e);
                }
            }
        }
    }

    /**
     * Starts an external process.
     *
     * @param command The command to execute.
     * @param directory The working directory for the process (can be null).
     * @return The started Process.
     * @throws IOException If an I/O error occurs.
     */
    protected Process startProcess(List<String> command, Path directory) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        if (directory != null) {
            Files.createDirectories(directory);
            processBuilder.directory(directory.toFile());
        }
        return processBuilder.start();
    }

    private boolean isFileNameTooLongError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        String lower = errorMessage.toLowerCase();
        return lower.contains("file name too long")
                || lower.contains("enametoolong")
                || lower.contains("errno 36");
    }

    /**
     * Truncates an error message to fit within the database column length
     * limits (255 characters).
     *
     * @param message The original error message.
     * @return The truncated message, or the original if it's within limits.
     */
    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        // Use 200 to be absolutely safe against multi-byte character expansions in some database setups
        return message.length() > 200 ? message.substring(0, 197) + "..." : message;
    }

    /**
     * Extracts error messages from the yt-dlp output.
     *
     * @param fullOutput The full output from the yt-dlp process.
     * @param exitCode The exit code of the process.
     * @return A string containing the extracted error messages or a fallback
     * message.
     */
    private String extractYtDlpError(String fullOutput, int exitCode) {
        List<String> errorLines = fullOutput.lines()
                .filter(line -> line.startsWith("ERROR:"))
                .collect(Collectors.toList());

        String errorMsg;
        if (!errorLines.isEmpty()) {
            // Return only the specific error lines from yt-dlp
            errorMsg = String.join("\n", errorLines);
        } else {
            // Fallback message if no specific "ERROR:" line is found
            errorMsg = String.format("yt-dlp process failed with exit code %d. Please check logs for full output.",
                    exitCode);
        }

        return truncateErrorMessage(errorMsg);
    }

    /**
     * Data structure to hold parsed subtitle information from yt-dlp.
     */
    private static class SubtitleInfo {

        boolean hasManualSubs = false;
        boolean hasAutoSubs = false;
        List<String> manualLanguages = new ArrayList<>();
        String autoOriginalLanguage = null;
    }

    /**
     * Checks what subtitles and automatic captions are available for a video.
     *
     * @param videoUrl The URL of the video to check.
     * @param cookiePath Temporary cookie file to bypass YouTube blocks.
     * @return A SubtitleInfo object containing the available subtitle
     * languages.
     */
    private SubtitleInfo getSubtitleInfo(String videoUrl, Path cookiePath) {
        SubtitleInfo info = new SubtitleInfo();
        logger.info("Checking for subtitles for video: {}", videoUrl);
        List<String> command = new ArrayList<>();
        command.add("yt-dlp");
        command.add("--impersonate");
        command.add("chrome");
        command.add("--js-runtimes");
        command.add("deno");
        if (cookiePath != null) {
            command.add("--cookies");
            command.add(cookiePath.toString());
        }
        command.add("--list-subs");
        command.add(videoUrl);

        try {
            Process process = startProcess(command, null);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean inAuto = false;
                boolean inManual = false;

                while ((line = reader.readLine()) != null) {
                    logger.debug("[yt-dlp --list-subs] {}", line);

                    if (line.startsWith("[info] Available automatic captions")) {
                        inAuto = true;
                        inManual = false;
                        continue;
                    } else if (line.startsWith("[info] Available subtitles")) {
                        inAuto = false;
                        inManual = true;
                        continue;
                    } else if (line.contains("has no subtitles") || line.contains("has no automatic captions")) {
                        if (line.contains("has no subtitles")) {
                            inManual = false;
                        }
                        if (line.contains("has no automatic captions")) {
                            inAuto = false;
                        }
                        continue;
                    }

                    if (line.startsWith("Language") || line.isBlank() || line.startsWith("[youtube]") || line.startsWith("[info]")) {
                        continue;
                    }

                    // Parse language codes
                    if (inAuto || inManual) {
                        String[] parts = line.trim().split("\\s+", 2);
                        String langCode = parts[0].trim();
                        if (inAuto) {
                            info.hasAutoSubs = true;
                            if (langCode.endsWith("-orig")) {
                                info.autoOriginalLanguage = langCode;
                            }
                        }
                        if (inManual) {
                            info.manualLanguages.add(langCode);
                            info.hasManualSubs = true;
                        }
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Subtitle check completed for video {}. Manual: {}, Auto: {}",
                        videoUrl, info.hasManualSubs, info.hasAutoSubs);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to check for subtitles for video {}: {}", videoUrl, e.getMessage());
            Thread.currentThread().interrupt();
        }
        return info;
    }

    /**
     * Parses a line of output from yt-dlp to update progress or capture the
     * filename.
     *
     * @param line The line of output to parse.
     * @param task The task associated with the download.
     * @param finalFilename A reference to store the final filename if found.
     * @param progressTracker The progress tracker to manage updates.
     */
    private void parseYtDlpOutput(String line, DownloadTask task, DownloadSubTask subTask, AtomicReference<String> finalFilename,
            ProgressTracker progressTracker
    ) {
        var progressMatcher = PROGRESS_PATTERN.matcher(line);

        boolean isTargetFile;
        String filename = finalFilename.get();
        if (filename != null && !filename.isEmpty()) {
            Path path = Paths.get(filename);
            String fileNameStr = path.getFileName().toString();
            if (subTask.getType() == SubTaskType.AUDIO) {
                isTargetFile = fileNameStr.endsWith(".m4a") || fileNameStr.endsWith(".webm") || fileNameStr.endsWith(".mp3") || fileNameStr.endsWith(".opus");
            } else {
                isTargetFile = fileNameStr.endsWith(".mp4") || fileNameStr.endsWith(".webm") || fileNameStr.endsWith(".mkv");
            }
        } else {
            isTargetFile = true;
        }

        // logger.debug("yt-dlp output: {}", line);
        if (progressMatcher.find()) {
            try {
                double progress = Double.parseDouble(progressMatcher.group(1));

                if (isTargetFile && progressTracker.shouldUpdate(progress)) {
                    subTask.setProgress(progress);
                    task.updateStatusBasedOnSubTasks();
                    downloadTaskRepository.save(task);
                    progressTracker.update(progress);
                    logger.trace("Updated progress for subtask {} to {}%", subTask.getId(), progress);
                }
            } catch (NumberFormatException e) {
                logger.warn("Could not parse progress from line: {}", line);
            }
            return;
        }

        // The rest of the parsing logic remains the same.
        var destinationMatcher = DESTINATION_PATTERN.matcher(line);
        if (destinationMatcher.find()) {
            finalFilename.set(destinationMatcher.group(1).trim());
            return;
        }

        var mergerMatcher = MERGER_DESTINATION_PATTERN.matcher(line);
        if (mergerMatcher.find()) {
            finalFilename.set(mergerMatcher.group(1).trim());
            return;
        }

        var alreadyDownloadedMatcher = ALREADY_DOWNLOADED_PATTERN.matcher(line);
        if (alreadyDownloadedMatcher.find()) {
            finalFilename.set(alreadyDownloadedMatcher.group(1).trim());
        }
    }

    /**
     * A helper class to track download progress and throttle database updates.
     * Updates are triggered based on time elapsed and progress percentage
     * change.
     */
    private static class ProgressTracker {

        private static final long UPDATE_INTERVAL_MS = 1000; // 1 second
        private static final double PROGRESS_INCREMENT_THRESHOLD = 5.0; // 5%

        private long lastUpdateTime;
        private double lastReportedProgress;

        ProgressTracker() {
            this.lastUpdateTime = 0;
            this.lastReportedProgress = -1;
        }

        /**
         * Determines if a database update should be performed.
         *
         * @param currentProgress The current download progress percentage.
         * @return {@code true} if the progress should be saved to the database.
         */
        boolean shouldUpdate(double currentProgress) {
            long now = Instant.now().toEpochMilli();
            boolean timeElapsed = (now - lastUpdateTime) > UPDATE_INTERVAL_MS;
            boolean progressChangedSignificantly = (currentProgress
                    - lastReportedProgress) >= PROGRESS_INCREMENT_THRESHOLD;
            return currentProgress == 100.0 || timeElapsed || progressChangedSignificantly;
        }

        void update(double newProgress) {
            this.lastReportedProgress = newProgress;
            this.lastUpdateTime = Instant.now().toEpochMilli();
        }
    }

    /**
     * Extracts the base filename (without extension) from a full filename.
     *
     * @param filename The full filename.
     * @return The base filename.
     */
    private String getBaseFilename(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }

    /**
     * Downloads the thumbnail for a video.
     *
     * @param task The task containing video details.
     * @param directory The directory to save the thumbnail in.
     * @param baseFilename The base filename to use for the thumbnail file.
     * @param result The result object to add warnings to if download fails.
     */
    private void downloadThumbnail(DownloadTask task, Path directory, String baseFilename, DownloadResult result) {
        if (!StringUtils.hasText(task.getThumbnailUrl())) {
            logger.warn("No thumbnail URL for video ID {}. Skipping thumbnail download.", task.getVideoId());
            return;
        }

        try {
            String extension = ".jpg"; // Default extension
            String urlString = task.getThumbnailUrl();
            int lastDot = urlString.lastIndexOf('.');
            if (lastDot > 0 && lastDot > urlString.lastIndexOf('/')) {
                extension = urlString.substring(lastDot);
            }

            Path thumbnailPath = directory.resolve(baseFilename + " (BQ)" + extension);
            logger.debug("Downloading thumbnail for {} to {}", task.getVideoId(), thumbnailPath);

            try (var in = URI.create(urlString).toURL().openStream()) {
                Files.copy(in, thumbnailPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Successfully downloaded thumbnail for video ID {}", task.getVideoId());
            }
        } catch (IOException e) {
            String warningMessage = String.format("Failed to download thumbnail: %s", e.getMessage());
            logger.error("Failed to download thumbnail for video ID {}: {}", task.getVideoId(), e.getMessage(), e);
            // We don't fail the whole download for this, so just log the error.
            result.addWarning(warningMessage);
        }
    }

    /**
     * Saves the video description to a text file.
     *
     * @param task The task containing video details.
     * @param directory The directory to save the description in.
     * @param baseFilename The base filename to use for the description file.
     * @param result The result object to add warnings to if saving fails.
     */
    private void saveDescription(DownloadTask task, Path directory, String baseFilename, DownloadResult result) {
        if (!StringUtils.hasText(task.getDescription())) {
            logger.info("No description for video ID {}. Skipping description file creation.", task.getVideoId());
            return;
        }

        Path descriptionPath = directory.resolve(baseFilename + " (Description).txt");
        logger.debug("Saving description for {} to {}", task.getVideoId(), descriptionPath);

        try {
            // Write the description content to the file using UTF-8 encoding
            Files.writeString(descriptionPath, task.getDescription(), StandardCharsets.UTF_8);
            logger.info("Successfully saved description for video ID {}", task.getVideoId());
        } catch (IOException e) {
            String warningMessage = String.format("Failed to save description.txt: %s", e.getMessage());
            logger.error("Failed to save description for video ID {}: {}", task.getVideoId(), e.getMessage(), e);
            // We don't fail the whole download for this, so just log the error.
            result.addWarning(warningMessage);
        }
    }

    /**
     * Executes the yt-dlp update command.
     *
     * @throws UpdateException If the update command fails.
     */
    private void updateYtDlp() {
        logger.info("Checking for yt-dlp updates...");
        List<String> command = Arrays.asList("yt-dlp", "-U");

        try {
            Process process = startProcess(command, null);
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[yt-dlp-update] {}", line);
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("yt-dlp update check completed successfully.");
            } else {
                String errorMessage = "yt-dlp update check failed with exit code " + exitCode + ". Output:\n" + output;
                logger.warn(errorMessage);
                throw new UpdateException(errorMessage);
            }
        } catch (IOException | InterruptedException e) {
            logger.error(UPDATE_COMMAND_FAILED, e);
            Thread.currentThread().interrupt();
            throw new UpdateException(UPDATE_COMMAND_FAILED, e);
        }
    }
}
