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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a single download operation. Contains details about
 * success status, file information, and any errors or warnings encountered.
 */
public class DownloadResult {

    private String videoId;
    private boolean success;
    private String filePath;
    private long fileSize;
    private String errorMessage;

    private List<String> warnings = new ArrayList<>();

    // Getters and setters for all fields
    /**
     * Gets the ID of the video that was processed.
     *
     * @return The video ID.
     */
    public String getVideoId() {
        return videoId;
    }

    /**
     * Sets the ID of the video that was processed.
     *
     * @param videoId The video ID.
     */
    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    /**
     * Checks if the download was successful.
     *
     * @return True if successful, false otherwise.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets the success status of the download.
     *
     * @param success True if successful, false otherwise.
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Gets the absolute path to the downloaded file.
     *
     * @return The file path, or null if download failed.
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Sets the absolute path to the downloaded file.
     *
     * @param filePath The file path.
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Gets the size of the downloaded file in bytes.
     *
     * @return The file size.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Sets the size of the downloaded file in bytes.
     *
     * @param fileSize The file size.
     */
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * Gets the error message if the download failed.
     *
     * @return The error message, or null if successful.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message if the download failed.
     *
     * @param errorMessage The error message.
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Gets the list of warning messages generated during the download.
     *
     * @return A list of warning strings.
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Sets the list of warning messages.
     *
     * @param warnings A list of warning strings.
     */
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    /**
     * Adds a single warning message to the list.
     *
     * @param warning The warning message to add.
     */
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
}
