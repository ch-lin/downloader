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

import ch.lin.downloader.backend.api.domain.OverwriteOption;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Command object representing the fields of a YtDlpConfig.
 */
@Getter
@Setter
@NoArgsConstructor
public class YtDlpConfigCommand {

    /**
     * Video format selection string (e.g., "bestvideo+bestaudio/best").
     */
    private String formatFiltering;

    /**
     * Video format sorting string (e.g., "res,fps").
     */
    private String formatSorting;

    /**
     * Remux the video into another container if necessary (e.g., "mp4", "mkv").
     */
    private String remuxVideo;

    /**
     * Whether to write the video description to a .description file.
     */
    private Boolean writeDescription;

    /**
     * Whether to download subtitles.
     */
    private Boolean writeSubs;

    /**
     * Languages of the subtitles to download (e.g., "en,de").
     */
    private String subLang;

    /**
     * Whether to write automatically generated subtitles.
     */
    private Boolean writeAutoSubs;

    /**
     * Subtitle format (e.g., "srt", "ass/srt/best").
     */
    private String subFormat;

    /**
     * Output filename template (e.g., "%(title)s.%(ext)s").
     */
    private String outputTemplate;

    /**
     * Whether to keep the video file on disk after post-processing.
     */
    private Boolean keepVideo;

    /**
     * Whether to extract audio from the video.
     */
    private Boolean extractAudio;

    /**
     * Audio format to convert to (e.g., "mp3", "m4a").
     */
    private String audioFormat;

    /**
     * Audio quality (0-9, where 0 is best).
     */
    private Integer audioQuality;

    /**
     * Option for handling file overwrites.
     */
    private OverwriteOption overwrite;

    /**
     * Whether to disable the progress bar.
     */
    private Boolean noProgress;

    /**
     * Whether to use the cookie file for authentication.
     */
    private Boolean useCookie;

    /**
     * Cookie content to be used for authentication.
     */
    private String cookie;
}
