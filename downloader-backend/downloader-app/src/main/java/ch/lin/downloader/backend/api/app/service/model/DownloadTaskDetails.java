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
package ch.lin.downloader.backend.api.app.service.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

import ch.lin.downloader.backend.api.domain.TaskStatus;

/**
 * DTO containing detailed information about a specific download task.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadTaskDetails {

    /**
     * The unique identifier of the task.
     */
    private String id;

    /**
     * The ID of the job this task belongs to.
     */
    private String jobId;

    /**
     * The YouTube video ID.
     */
    private String videoId;

    /**
     * The title of the video.
     */
    private String title;

    /**
     * The URL of the video thumbnail.
     */
    private String thumbnailUrl;

    /**
     * The description of the video.
     */
    private String description;

    /**
     * The current status of the task (e.g., PENDING, DOWNLOADING, DOWNLOADED,
     * FAILED).
     */
    private TaskStatus status;

    /**
     * The download progress percentage (0.0 to 100.0).
     */
    private Double progress;

    /**
     * The absolute path to the downloaded file.
     */
    private String filePath;

    /**
     * The size of the downloaded file in bytes.
     */
    private Long fileSize;

    /**
     * The error message if the task failed.
     */
    private String errorMessage;

    /**
     * The timestamp when the task was created.
     */
    private OffsetDateTime createdAt;

    /**
     * The timestamp when the task was last updated.
     */
    private OffsetDateTime updatedAt;
}
