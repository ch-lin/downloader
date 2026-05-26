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

import ch.lin.downloader.backend.api.domain.SubTaskType;
import ch.lin.downloader.backend.api.domain.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO containing detailed information about a specific download sub-task.
 * <p>
 * This provides fine-grained progress and status data for specific phases (like
 * AUDIO or VIDEO) of a download, intended for UI rendering.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadSubTaskDetails {

    /**
     * The unique identifier of the sub-task.
     */
    private String id;

    /**
     * The type of the sub-task (e.g., AUDIO, VIDEO).
     */
    private SubTaskType type;

    /**
     * The current status of the sub-task.
     */
    private TaskStatus status;

    /**
     * The download progress percentage for this sub-task (0.0 to 100.0).
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
     * The error message if the sub-task failed.
     */
    private String errorMessage;
}
