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
package ch.lin.downloader.backend.api.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.downloader.backend.api.app.service.DownloaderService;
import ch.lin.downloader.backend.api.app.service.model.TaskIdentifier;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.dto.DownloadRequest;
import ch.lin.platform.api.ApiResponse;
import ch.lin.platform.exception.InvalidRequestException;

/**
 * REST controller for creating and managing download jobs.
 * <p>
 * This controller provides endpoints to create new download jobs from a list of
 * video items and to delete all existing jobs and tasks. It delegates the
 * business logic to the {@link DownloaderService}.
 */
@RestController
@RequestMapping("/download")
public class DownloaderController {

    private final DownloaderService downloadService;

    /**
     * Constructs a {@code DownloaderController} with the necessary service.
     *
     * @param downloadService The service for managing download jobs.
     */
    public DownloaderController(DownloaderService downloadService) {
        this.downloadService = downloadService;
    }

    /**
     * Creates a new job to download one or more YouTube videos asynchronously.
     * <p>
     * This endpoint accepts a list of video items, creates a download job, and
     * immediately returns an HTTP 202 Accepted status. The response body
     * contains the identifiers for the tasks created, which can be used for
     * tracking.
     *
     * @param request The request body containing the list of video items to
     * download.
     * @return A {@link ResponseEntity} with an HTTP 202 Accepted status and a
     * body containing an {@link ApiResponse} that wraps a list of
     * {@link TaskIdentifier} objects.
     * <p>
     * Example cURL request:
     *
     * <pre>
     * {@code
     * curl -X POST http://localhost:8081/download \
     * -H "Content-Type: application/json" \
     * -d '{
     *   "items": [
     *     { "videoId": "dQw4w9WgXcQ", "title": "Rick Astley - Never Gonna Give You Up" },
     *     { "videoId": "C0DPdy98e4c", "title": "What is Backpressure" }
     *   ]
     * }'
     * }
     * </pre>
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<TaskIdentifier>>> createDownloadJob(
            @RequestBody(required = true) DownloadRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            // return ResponseEntity.badRequest().build();
            throw new InvalidRequestException("Request must contain a non-empty list of items to download.");
        }
        DownloadJob createdJob = downloadService.createDownloadJob(request.getItems(), request.getConfigName());

        List<TaskIdentifier> taskIdentifiers = createdJob.getTasks().stream()
                .map(task -> new TaskIdentifier(task.getVideoId(), task.getId()))
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(taskIdentifiers));
    }

    /**
     * Deletes all download jobs and their associated tasks.
     * <p>
     * This endpoint provides a way to clear all historical and pending download
     * data.
     *
     * @return A {@link ResponseEntity} with an HTTP 204 No Content status upon
     * successful deletion.
     * <p>
     * Example cURL request:
     *
     * <pre>{@code curl -X DELETE http://localhost:8081/download}</pre>
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAllJobs() {
        downloadService.deleteAllJobs();
        return ResponseEntity.noContent().build();
    }
}
