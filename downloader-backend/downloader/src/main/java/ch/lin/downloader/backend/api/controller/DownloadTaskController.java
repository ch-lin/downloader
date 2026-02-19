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

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.downloader.backend.api.app.service.DownloaderService;
import ch.lin.downloader.backend.api.app.service.model.DownloadTaskDetails;

/**
 * REST controller for managing individual download tasks.
 * <p>
 * This controller provides endpoints to retrieve and delete individual download
 * tasks. It delegates the business logic to the {@link DownloaderService}.
 */
@RestController
@RequestMapping("/download/tasks")
public class DownloadTaskController {

    private final DownloaderService downloadService;

    /**
     * Constructs a {@code DownloadTaskController} with the necessary service.
     *
     * @param downloadService The service for managing download tasks.
     */
    public DownloadTaskController(DownloaderService downloadService) {
        this.downloadService = downloadService;
    }

    /**
     * Retrieves the details of a specific download task by its ID.
     *
     * @param taskId The UUID of the download task to retrieve.
     * @return A {@link ResponseEntity} with an HTTP 200 OK status and the
     * {@link DownloadTaskDetails} if found, or a 404 Not Found if the task does
     * not exist.
     * <p>
     * Example cURL request:
     *
     * <pre>
     * {@code curl -X GET http://localhost:8081/download/tasks/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx}
     * </pre>
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<DownloadTaskDetails> getTaskById(@PathVariable("taskId") @NonNull String taskId) {
        DownloadTaskDetails task = downloadService.getTaskById(taskId);
        return ResponseEntity.ok(task);
    }

    /**
     * Deletes a specific download task by its ID.
     *
     * @param taskId The UUID of the download task to delete.
     * @return A {@link ResponseEntity} with an HTTP 204 No Content status upon
     * successful deletion.
     * <p>
     * Example cURL request:
     *
     * <pre>
     * {@code curl -X DELETE http://localhost:8081/download/tasks/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx}
     * </pre>
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTaskById(@PathVariable("taskId") @NonNull String taskId) {
        downloadService.deleteTaskById(taskId);
        return ResponseEntity.noContent().build();
    }
}
