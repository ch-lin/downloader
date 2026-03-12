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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.downloader.backend.api.app.repository.DownloadJobRepository;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.JobStatus;
import jakarta.annotation.PostConstruct;

/**
 * Implementation of {@link AutoCleanupService} that manages a scheduled task to
 * periodically remove completed download jobs.
 * <p>
 * The service can be started or stopped via its public methods and can be
 * configured to start automatically on application startup based on the active
 * {@link DownloaderConfig}.
 */
@Service
public class AutoCleanupServiceImpl implements AutoCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(AutoCleanupServiceImpl.class);

    /**
     * Service for accessing downloader configurations.
     */
    private final ConfigsService configsService;

    /**
     * Spring's scheduler for managing background tasks.
     */
    private final TaskScheduler taskScheduler;

    /**
     * Repository for accessing download job data.
     */
    private final DownloadJobRepository downloadJobRepository;

    /**
     * Holds the future of the scheduled cleanup task, allowing it to be
     * managed.
     */
    private ScheduledFuture<?> scheduledTask;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param configsService Service for retrieving configuration settings.
     * @param taskScheduler Spring's task scheduler.
     * @param downloadJobRepository Repository for download jobs.
     */
    public AutoCleanupServiceImpl(ConfigsService configsService, TaskScheduler taskScheduler,
            DownloadJobRepository downloadJobRepository) {
        this.configsService = configsService;
        this.taskScheduler = taskScheduler;
        this.downloadJobRepository = downloadJobRepository;
    }

    /**
     * Initializes the service after construction.
     * <p>
     * On application startup, this method checks the active configuration. If
     * {@code removeCompletedJobAutomatically} is enabled, it starts the cleanup
     * scheduler.
     */
    @PostConstruct
    public void init() {
        // On application startup, check the active configuration's
        // removeCompletedJobAutomatically flag.
        DownloaderConfig activeConfig = configsService.getResolvedConfig(null);
        if (Boolean.TRUE.equals(activeConfig.getRemoveCompletedJobAutomatically())) {
            start(); // Call the existing start method to schedule the task
        }
    }

    @Override
    public void start() {
        if (scheduledTask == null || scheduledTask.isDone()) {
            DownloaderConfig activeConfig = configsService.getResolvedConfig(null); // getResolvedConfig ensures duration is not null.
            Duration duration = Duration.ofSeconds(activeConfig.getDuration());
            scheduledTask = taskScheduler.scheduleWithFixedDelay(this::cleanupCompletedJobs, Objects.requireNonNull(duration));
            logger.info("Auto-cleanup scheduler started with a fixed delay of {} seconds.", duration);
        } else {
            logger.warn("Auto-cleanup scheduler is already running.");
        }
    }

    @Override
    public void stop() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            // false: don't interrupt the task if it's currently running.
            scheduledTask.cancel(false);
            logger.info("Auto-cleanup scheduler stopped.");
        } else {
            logger.warn("Auto-cleanup scheduler is not running.");
        }
    }

    /**
     * Checks if the cleanup scheduler is currently active and scheduled to run.
     *
     * @return {@code true} if the scheduler is running, {@code false}
     * otherwise.
     */
    public boolean isSchedulerRunning() {
        return scheduledTask != null && !scheduledTask.isCancelled() && !scheduledTask.isDone();
    }

    @Override
    @Transactional
    public void cleanupCompletedJobs() {
        logger.debug("Running cleanup for completed jobs.");
        List<DownloadJob> completedJobs = downloadJobRepository.findAllByStatus(JobStatus.COMPLETED);

        if (!completedJobs.isEmpty()) {
            logger.info("Found {} completed jobs to remove.", completedJobs.size());
            downloadJobRepository.deleteAll(completedJobs);
            logger.info("Successfully removed {} completed jobs and their associated tasks.", completedJobs.size());
        }
    }
}
