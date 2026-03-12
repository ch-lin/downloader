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

import ch.lin.downloader.backend.api.app.service.AutoCleanupServiceImpl;

import java.util.Map;

/**
 * REST controller for managing the auto-cleanup task scheduler. It provides
 * endpoints to start, stop, and check the status of the scheduler responsible
 * for cleaning up downloaded files.
 */
@RestController
@RequestMapping("/cleanup")
public class AutoCleanupSchedulerController {

    private final AutoCleanupServiceImpl autoCleanupService;

    /**
     * Constructs an {@code AutoCleanupSchedulerController} with the necessary
     * auto-cleanup service.
     *
     * @param autoCleanupService The service responsible for managing the
     * auto-cleanup logic and scheduler.
     */
    public AutoCleanupSchedulerController(AutoCleanupServiceImpl autoCleanupService) {
        this.autoCleanupService = autoCleanupService;
    }

    /**
     * Starts the cleanup task scheduler. This operation is idempotent; calling
     * it multiple times will not cause issues.
     *
     * @return A {@link ResponseEntity} with an HTTP 200 OK status and a
     * confirmation message.
     */
    @PostMapping("/start")
    public ResponseEntity<String> startScheduler() {
        autoCleanupService.start();
        return ResponseEntity.ok("Cleanup scheduler started.");
    }

    /**
     * Stops the cleanup task scheduler. This operation is idempotent; calling
     * it multiple times will not cause issues.
     *
     * @return A {@link ResponseEntity} with an HTTP 200 OK status and a
     * confirmation message.
     */
    @PostMapping("/stop")
    public ResponseEntity<String> stopScheduler() {
        autoCleanupService.stop();
        return ResponseEntity.ok("Cleanup scheduler stopped.");
    }

    /**
     * Gets the current status of the cleanup task scheduler.
     *
     * @return A {@link ResponseEntity} with an HTTP 200 OK status and a JSON
     * body containing a boolean field "isRunning" indicating the scheduler's
     * status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getSchedulerStatus() {
        boolean isRunning = autoCleanupService.isSchedulerRunning();
        return ResponseEntity.ok(Map.of("isRunning", isRunning));
    }
}
