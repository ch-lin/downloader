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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
 * OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *===========================================================================*/
package ch.lin.downloader.backend.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.downloader.backend.api.app.service.ExecutorServiceImpl;

import java.util.Map;

/**
 * REST controller for managing the download task scheduler. It provides
 * endpoints to start, stop, and check the status of the scheduler responsible
 * for executing download tasks.
 */
@RestController
@RequestMapping("/downloader")
public class DownloaderSchedulerController {

    private final ExecutorServiceImpl executorService;

    /**
     * Constructs a {@code DownloaderSchedulerController} with the necessary
     * executor service.
     *
     * @param executorService The service responsible for managing the download
     * task execution and scheduler.
     */
    public DownloaderSchedulerController(ExecutorServiceImpl executorService) {
        this.executorService = executorService;
    }

    /**
     * Starts the download task scheduler. This operation is idempotent; calling
     * it multiple times will not cause issues.
     *
     * @return A {@link ResponseEntity} with an HTTP 200 OK status and a
     * confirmation message.
     */
    @PostMapping("/start")
    public ResponseEntity<String> startScheduler() {
        executorService.start();
        return ResponseEntity.ok("Downloader scheduler started.");
    }

    /**
     * Stops the download task scheduler. This operation is idempotent; calling
     * it multiple times will not cause issues.
     *
     * @return A {@link ResponseEntity} with an HTTP 200 OK status and a
     * confirmation message.
     */
    @PostMapping("/stop")
    public ResponseEntity<String> stopScheduler() {
        executorService.stop();
        return ResponseEntity.ok("Downloader scheduler stopped.");
    }

    /**
     * Gets the current status of the download task scheduler.
     *
     * @return A {@link ResponseEntity} with an HTTP 200 OK status and a JSON
     * body containing a boolean field "isRunning" indicating the scheduler's
     * status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getSchedulerStatus() {
        boolean isRunning = executorService.isSchedulerRunning();
        return ResponseEntity.ok(Map.of("isRunning", isRunning));
    }
}
