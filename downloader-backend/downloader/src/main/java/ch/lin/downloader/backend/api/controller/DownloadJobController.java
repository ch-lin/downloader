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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.downloader.backend.api.app.service.DownloaderService;
import ch.lin.downloader.backend.api.app.service.model.DownloadJobDetails;

/**
 * REST controller for managing download jobs and tasks.
 * <p>
 * This controller provides endpoints to retrieve and delete individual download
 * jobs and their associated tasks. It delegates the business logic to the
 * {@link DownloaderService}.
 */
@RestController
@RequestMapping("/download/jobs")
public class DownloadJobController {

    private final DownloaderService downloadService;

    /**
     * Constructs a {@code DownloadJobController} with the necessary service.
     *
     * @param downloadService The service for managing download jobs.
     */
    public DownloadJobController(DownloaderService downloadService) {
        this.downloadService = downloadService;
    }

    /**
     * Retrieves the details of a specific download job by its ID.
     *
     * @param id The UUID of the download job to retrieve.
     * @return A {@link ResponseEntity} with an HTTP 200 OK status and the
     * {@link DownloadJobDetails} if found, or a 404 Not Found if the job does
     * not exist.
     * <p>
     * Example cURL request:
     *
     * <pre>
     * {@code curl -X GET http://localhost:8081/download/jobs/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx}
     * </pre>
     */
    @GetMapping("/{id}")
    public ResponseEntity<DownloadJobDetails> getJobById(@PathVariable("id") String id) {
        DownloadJobDetails job = downloadService.getJobById(id);
        return ResponseEntity.ok(job);
    }

    /**
     * Deletes a specific download job and its associated tasks by its ID.
     *
     * @param id The UUID of the download job to delete.
     * @return A {@link ResponseEntity} with an HTTP 204 No Content status upon
     * successful deletion.
     * <p>
     * Example cURL request:
     *
     * <pre>
     * {@code curl -X DELETE http://localhost:8081/download/jobs/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx}
     * </pre>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJobById(@PathVariable("id") String id) {
        downloadService.deleteJobById(id);
        return ResponseEntity.noContent().build();
    }
}
