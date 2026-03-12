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
package ch.lin.downloader.backend.api.app.service.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a data transfer object (DTO) for requesting a video download. This
 * class encapsulates the necessary information to identify and describe a video
 * item to be downloaded.
 */
@Getter
@Setter
public class DownloadItem {

    /**
     * The unique identifier of the YouTube video (e.g., "dQw4w9WgXcQ"). This
     * field is mandatory.
     */
    @NotBlank(message = "Video id cannot be blank")
    private String videoId;

    /**
     * The title of the video. This field is mandatory.
     */
    @NotBlank(message = "Video title cannot be blank")
    private String title;

    /**
     * The URL of the video's thumbnail image. This field is optional.
     */
    private String thumbnailUrl;

    /**
     * A description of the video. This field is optional.
     */
    private String description;
}
