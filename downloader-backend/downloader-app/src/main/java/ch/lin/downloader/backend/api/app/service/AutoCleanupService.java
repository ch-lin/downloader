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

/**
 * Defines the contract for a service that automatically cleans up completed
 * download jobs.
 * <p>
 * Implementations are responsible for managing a scheduled task that
 * periodically removes jobs based on the application's configuration.
 */
public interface AutoCleanupService {

    /**
     * Scans for and removes completed download jobs and their associated tasks
     * from the database.
     * <p>
     * This method is typically invoked by a scheduler. The cleanup logic should
     * respect the auto-cleanup settings defined in the
     * {@link ch.lin.downloader.backend.api.domain.DownloaderConfig}.
     */
    void cleanupCompletedJobs();

    /**
     * Starts the cleanup scheduler.
     *
     * <p>
     * If the scheduler is already running, this method will do nothing.
     *
     * @throws ch.lin.platform.exception.ConfigNotFoundException if a required
     * configuration property, such as duration, is missing.
     */
    void start();

    /**
     * Stops the cleanup scheduler.
     */
    void stop();
}
