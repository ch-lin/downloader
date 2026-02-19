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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.downloader.backend.api.app.repository.DownloadJobRepository;
import ch.lin.downloader.backend.api.app.repository.DownloadTaskRepository;
import ch.lin.downloader.backend.api.app.service.command.DownloadItem;
import ch.lin.downloader.backend.api.app.service.model.DownloadJobDetails;
import ch.lin.downloader.backend.api.app.service.model.DownloadTaskDetails;
import ch.lin.downloader.backend.api.app.service.model.DownloadTaskSummary;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.DownloadTask;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.JobStatus;
import ch.lin.downloader.backend.api.domain.TaskStatus;
import ch.lin.platform.exception.InvalidRequestException;

/**
 * Service for managing the creation and lifecycle of YouTube video download
 * jobs and their associated tasks.
 * <p>
 * This service acts as the primary interface for initiating download requests.
 * It is responsible for creating {@link DownloadJob} entities and their
 * corresponding {@link DownloadTask} entities, persisting them to the database,
 * and managing their overall lifecycle, including bulk deletion. The actual
 * execution of the download process for individual tasks is delegated to the
 * {@link ExecutorService}.
 */
@Service
public class DownloaderServiceImpl implements DownloaderService {

    private static final Logger logger = LoggerFactory.getLogger(DownloaderServiceImpl.class);

    private final DownloadJobRepository downloadJobRepository;
    private final DownloadTaskRepository downloadTaskRepository;
    private final ConfigsService configsService;
    private final ExecutorService executorService;
    private final AutoCleanupService autoCleanupService;

    /**
     * Constructs a new DownloaderServiceImpl with the necessary dependencies.
     *
     * @param downloadJobRepository Repository for DownloadJob entities.
     * @param downloadTaskRepository Repository for DownloadTask entities.
     * @param configsService Service for managing configurations.
     * @param executorService Service for executing download tasks.
     * @param autoCleanupService Service for auto-cleanup of completed jobs.
     */
    public DownloaderServiceImpl(DownloadJobRepository downloadJobRepository,
            DownloadTaskRepository downloadTaskRepository, ConfigsService configsService,
            ExecutorService executorService, AutoCleanupService autoCleanupService) {
        this.downloadJobRepository = downloadJobRepository;
        this.downloadTaskRepository = downloadTaskRepository;
        this.configsService = configsService;
        this.executorService = executorService;
        this.autoCleanupService = autoCleanupService;
    }

    /**
     * Creates a new download job and its associated tasks in the database.
     * <p>
     * This method initializes the job status to PENDING, creates tasks for each
     * item, and saves the job. It also triggers the executor service or
     * auto-cleanup service if configured in the selected configuration.
     *
     * @param items The list of items to be downloaded.
     * @param configName The name of the configuration to use for the download.
     * @return The created {@link DownloadJob} entity.
     * @throws InvalidRequestException if the provided configuration name is not
     * found.
     */
    @Override
    @Transactional
    public DownloadJob createDownloadJob(List<DownloadItem> items, String configName) {
        // Retrieve the DownloaderConfig to check the startAutomatically flag
        DownloaderConfig activeConfig = configsService.getResolvedConfig(configName);

        if (configName != null && !configName.isBlank() && !configName.equals(activeConfig.getName())) {
            throw new InvalidRequestException("Configuration with name '" + configName + "' not found.");
        }
        logger.info("Received request to create download job for {} items with config '{}'.", items.size(),
                activeConfig.getName());

        DownloadJob job = new DownloadJob();
        job.setStatus(JobStatus.PENDING);
        job.setConfigName(configName);

        for (DownloadItem item : items) {
            logger.debug("Creating task for videoId: {}", item.getVideoId());
            job.addTask(createTaskFromItem(item));
        }

        DownloadJob savedJob = downloadJobRepository.save(job);
        logger.info("Successfully created and saved download job with ID: {}", savedJob.getId());

        if (Boolean.TRUE.equals(activeConfig.getStartDownloadAutomatically())) {
            logger.info("DownloaderConfig '{}' has startDownloadAutomatically enabled. Starting scheduler.",
                    activeConfig.getName());
            executorService.start();
        }

        if (Boolean.TRUE.equals(activeConfig.getRemoveCompletedJobAutomatically())) {
            logger.info(
                    "DownloaderConfig '{}' has removeCompletedJobAutomatically enabled. Starting auto-cleanup scheduler.",
                    activeConfig.getName());
            autoCleanupService.start();
        }

        return savedJob;
    }

    /**
     * Creates a {@link DownloadTask} entity from a {@link DownloadItem} DTO.
     *
     * @param item The DTO containing the video details.
     * @return A new {@link DownloadTask} entity.
     */
    private DownloadTask createTaskFromItem(DownloadItem item) {
        DownloadTask task = new DownloadTask();
        task.setVideoId(item.getVideoId());
        task.setTitle(item.getTitle());
        task.setThumbnailUrl(item.getThumbnailUrl());
        task.setDescription(item.getDescription());
        task.setStatus(TaskStatus.PENDING);
        // Other fields like filePath, fileSize, errorMessage will be set later during
        // execution.
        return task;
    }

    /**
     * Deletes all download jobs and their associated tasks from the database.
     * <p>
     * Tasks are deleted first to satisfy foreign key constraints.
     */
    @Override
    @Transactional
    public void deleteAllJobs() {
        // Must delete tasks first due to foreign key constraint from task to job
        downloadTaskRepository.cleanTable();
        downloadJobRepository.cleanTable();
    }

    /**
     * Retrieves the details of a specific download job.
     *
     * @param jobId The unique identifier of the job.
     * @return A {@link DownloadJobDetails} containing job information and its
     * tasks.
     * @throws RuntimeException If the job is not found.
     */
    @Override
    @Transactional(readOnly = true)
    public DownloadJobDetails getJobById(String jobId) {
        DownloadJob job = downloadJobRepository.findByIdWithTasks(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));

        DownloadJobDetails jobDetailsDto = new DownloadJobDetails();
        jobDetailsDto.setId(job.getId());
        jobDetailsDto.setStatus(job.getStatus());
        jobDetailsDto.setConfigName(job.getConfigName());

        List<DownloadTaskSummary> taskSummaries = job.getTasks().stream()
                .map(task -> new DownloadTaskSummary(task.getId(), task.getStatus(), task.getProgress()))
                .collect(Collectors.toList());

        jobDetailsDto.setTasks(taskSummaries);

        return jobDetailsDto;
    }

    /**
     * Retrieves the details of a specific download task.
     *
     * @param taskId The unique identifier of the task.
     * @return A {@link DownloadTaskDetails} containing task information.
     * @throws RuntimeException If the task is not found.
     */
    @Override
    @Transactional(readOnly = true)
    public DownloadTaskDetails getTaskById(@NonNull String taskId) {
        DownloadTask task = downloadTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task with id " + taskId + " not found."));

        DownloadTaskDetails dto = new DownloadTaskDetails();
        dto.setId(task.getId());
        dto.setJobId(task.getJob().getId());
        dto.setVideoId(task.getVideoId());
        dto.setTitle(task.getTitle());
        dto.setThumbnailUrl(task.getThumbnailUrl());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus());
        dto.setProgress(task.getProgress());
        dto.setFilePath(task.getFilePath());
        dto.setFileSize(task.getFileSize());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        return dto;
    }

    /**
     * Deletes a specific download task.
     * <p>
     * After deletion, the status of the parent job is updated based on the
     * remaining tasks.
     *
     * @param taskId The unique identifier of the task to delete.
     * @throws RuntimeException If the task is not found.
     */
    @Override
    @Transactional
    public void deleteTaskById(@NonNull String taskId) {
        DownloadTask task = downloadTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task with id " + taskId + " not found."));
        // Get the jobId before the task is deleted.
        String jobId = task.getJob().getId();

        downloadTaskRepository.delete(task);
        logger.info("Deleted task with ID: {}", taskId);

        // After deleting a task, the job status might need to be updated.
        updateJobStatus(jobId);
    }

    /**
     * Updates the status of a job based on the status of its tasks.
     * <p>
     * If all tasks are completed or failed, the job status is updated to
     * COMPLETED, PARTIALLY_COMPLETED, or FAILED.
     *
     * @param jobId The unique identifier of the job to update.
     * @throws IllegalStateException If the job is not found.
     */
    private void updateJobStatus(String jobId) {
        DownloadJob job = downloadJobRepository.findByIdWithTasks(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found for status update: " + jobId));

        if (job.getTasks().isEmpty()) {
            job.setStatus(JobStatus.COMPLETED); // Or FAILED, depending on desired logic for empty jobs
            logger.info("Job {} is now empty and marked as {}.", jobId, job.getStatus());
            downloadJobRepository.save(job);
            return;
        }

        long totalTasks = job.getTasks().size();
        long completedTasks = job.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.DOWNLOADED).count();
        long failedTasks = job.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.FAILED).count();

        if (completedTasks + failedTasks == totalTasks) {
            job.setStatus(failedTasks == 0 ? JobStatus.COMPLETED
                    : (completedTasks > 0 ? JobStatus.PARTIALLY_COMPLETED : JobStatus.FAILED));
            logger.info("Job {} has finished with final status: {}", jobId, job.getStatus());
            downloadJobRepository.save(job);
        }
    }

    /**
     * Deletes a specific download job and its associated tasks.
     *
     * @param jobId The unique identifier of the job to delete.
     * @throws RuntimeException If the job is not found.
     */
    @Override
    @Transactional
    public void deleteJobById(String jobId) {
        if (jobId != null) {
            DownloadJob job = downloadJobRepository.findById(jobId)
                    .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));
            downloadJobRepository.delete(Objects.requireNonNull(job));
        }
    }
}
