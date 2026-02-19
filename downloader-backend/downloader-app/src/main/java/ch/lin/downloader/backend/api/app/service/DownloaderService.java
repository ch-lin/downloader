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

import org.springframework.lang.NonNull;

import ch.lin.downloader.backend.api.app.service.command.DownloadItem;
import ch.lin.downloader.backend.api.app.service.model.DownloadJobDetails;
import ch.lin.downloader.backend.api.app.service.model.DownloadTaskDetails;
import ch.lin.downloader.backend.api.domain.DownloadJob;

/**
 * Service interface for managing download jobs and tasks.
 * <p>
 * This service handles the lifecycle of download operations, including creating
 * jobs, retrieving status details, and deleting jobs or individual tasks.
 */
public interface DownloaderService {

    /**
     * Creates a new download job and its associated tasks in the database.
     *
     * @param items The list of items to be downloaded.
     * @param configName The name of the configuration to use for the download.
     * @return The created {@link DownloadJob} entity.
     * @throws ch.lin.platform.exception.InvalidRequestException if the provided
     * configuration name is not found.
     */
    DownloadJob createDownloadJob(List<DownloadItem> items, String configName);

    /**
     * Deletes all download jobs and their associated tasks from the database.
     */
    public void deleteAllJobs();

    /**
     * Retrieves the details of a specific download job.
     *
     * @param jobId The unique identifier of the job.
     * @return A {@link DownloadJobDetails} containing job information and its
     * tasks.
     */
    DownloadJobDetails getJobById(String jobId);

    /**
     * Deletes a specific download job and its associated tasks.
     *
     * @param jobId The unique identifier of the job to delete.
     */
    void deleteJobById(String jobId);

    /**
     * Retrieves the details of a specific download task.
     *
     * @param taskId The unique identifier of the task.
     * @return A {@link DownloadTaskDetails} containing task information.
     */
    DownloadTaskDetails getTaskById(@NonNull String taskId);

    /**
     * Deletes a specific download task.
     *
     * @param taskId The unique identifier of the task to delete.
     */
    void deleteTaskById(@NonNull String taskId);
}
