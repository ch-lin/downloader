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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.downloader.backend.api.app.repository.DownloadJobRepository;
import ch.lin.downloader.backend.api.app.repository.DownloadTaskRepository;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.DownloadTask;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.JobStatus;
import ch.lin.downloader.backend.api.domain.TaskStatus;
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
     * Repository for accessing download task data.
     */
    private final DownloadTaskRepository downloadTaskRepository;

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
     * @param downloadTaskRepository Repository for download tasks.
     */
    public AutoCleanupServiceImpl(ConfigsService configsService, TaskScheduler taskScheduler,
            DownloadJobRepository downloadJobRepository, DownloadTaskRepository downloadTaskRepository) {
        this.configsService = configsService;
        this.taskScheduler = taskScheduler;
        this.downloadJobRepository = downloadJobRepository;
        this.downloadTaskRepository = downloadTaskRepository;
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

        // 1. Retrieve all FAILED and PARTIALLY_COMPLETED jobs
        List<DownloadJob> unresolvedJobs = downloadJobRepository.findAllByStatusInWithTasks(
                List.of(JobStatus.FAILED, JobStatus.PARTIALLY_COMPLETED));

        List<DownloadTask> tasksToDelete = new ArrayList<>();
        List<DownloadJob> jobsToDelete = new ArrayList<>();
        List<DownloadJob> jobsToSave = new ArrayList<>();

        if (!unresolvedJobs.isEmpty()) {
            // Collect all video IDs of failed tasks in these unresolved jobs
            Set<String> failedVideoIds = unresolvedJobs.stream()
                    .flatMap(job -> job.getTasks().stream())
                    .filter(task -> task.getStatus() == TaskStatus.FAILED)
                    .map(task -> task.getVideoId())
                    .collect(Collectors.toSet());

            if (!failedVideoIds.isEmpty()) {
                // Find which of these video IDs are successfully DOWNLOADED in any job
                Set<String> resolvedVideoIds = downloadTaskRepository.findActiveVideoIds(
                        failedVideoIds, List.of(TaskStatus.DOWNLOADED));

                if (!resolvedVideoIds.isEmpty()) {
                    for (DownloadJob job : unresolvedJobs) {
                        List<DownloadTask> resolvedTasks = job.getTasks().stream()
                                .filter(task -> task.getStatus() == TaskStatus.FAILED
                                        && resolvedVideoIds.contains(task.getVideoId()))
                                .collect(Collectors.toList());

                        if (!resolvedTasks.isEmpty()) {
                            logger.info("Job {} has {} resolved failed tasks to clean up.", job.getId(), resolvedTasks.size());
                            tasksToDelete.addAll(resolvedTasks);
                            for (DownloadTask task : resolvedTasks) {
                                job.getTasks().remove(task);
                            }

                            // Recalculate remaining status
                            long remainingFailedTasks = job.getTasks().stream()
                                    .filter(t -> t.getStatus() == TaskStatus.FAILED).count();
                            long remainingCompletedTasks = job.getTasks().stream()
                                    .filter(t -> t.getStatus() == TaskStatus.DOWNLOADED).count();

                            if (remainingFailedTasks == 0) {
                                logger.info("Job {} has no remaining failed tasks and will be deleted.", job.getId());
                                jobsToDelete.add(job);
                            } else {
                                JobStatus newStatus = remainingCompletedTasks > 0
                                        ? JobStatus.PARTIALLY_COMPLETED
                                        : JobStatus.FAILED;
                                if (job.getStatus() != newStatus) {
                                    logger.info("Updating Job {} status from {} to {}.", job.getId(), job.getStatus(), newStatus);
                                    job.setStatus(newStatus);
                                }
                                jobsToSave.add(job);
                            }
                        }
                    }
                }
            }
        }

        // Perform deletions/saves for the unresolved jobs
        if (!tasksToDelete.isEmpty()) {
            downloadTaskRepository.deleteAll(tasksToDelete);
            downloadTaskRepository.flush();
        }

        if (!jobsToDelete.isEmpty()) {
            downloadJobRepository.deleteAll(jobsToDelete);
        }

        if (!jobsToSave.isEmpty()) {
            downloadJobRepository.saveAll(jobsToSave);
        }

        // 2. Retrieve all COMPLETED jobs and delete them
        List<DownloadJob> completedJobs = downloadJobRepository.findAllByStatus(JobStatus.COMPLETED);
        if (!completedJobs.isEmpty()) {
            logger.info("Found {} completed jobs to remove.", completedJobs.size());
            downloadJobRepository.deleteAll(completedJobs);
            logger.info("Successfully removed {} completed jobs and their associated tasks.", completedJobs.size());
        }
    }
}
