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
package ch.lin.downloader.backend.api.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import ch.lin.downloader.backend.api.domain.OverwriteOption;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * Holds the default configuration properties for the downloader application.
 * <p>
 * These properties are bound from the application's configuration files (e.g.,
 * {@code application.yml}) under the prefix {@code downloader}.
 */
@Component
@ConfigurationProperties(prefix = "downloader")
@Getter
@Setter
public class DownloaderDefaultProperties {

    public static final int DEFAULT_DURATION = 60;
    public static final int DEFAULT_THREAD_POOL_SIZE = 3;

    /**
     * The default directory where downloaded files will be saved.
     */
    private String downloadFolder;

    /**
     * The directory containing the Netscape format cookie file for
     * authentication.
     */
    private String netscapeCookieFolder;

    /**
     * The default duration in seconds for a download job before it times out.
     */
    private int duration = DEFAULT_DURATION;

    /**
     * Specifies whether to start downloads automatically upon job creation.
     * Defaults to {@code true}.
     */
    private boolean startDownloadAutomatically = true;

    /**
     * Specifies whether to remove completed jobs from the list automatically.
     * Defaults to {@code false}.
     */
    private boolean removeCompletedJobAutomatically = false;

    /**
     * The default client ID for access YouTube Hub's API
     */
    private String clientId;

    /**
     * The default client secret for access YouTube Hub's API
     */
    private String clientSecret;

    /**
     * The default size of the thread pool for concurrent downloads.
     */
    private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;

    /**
     * Nested configuration for yt-dlp specific options.
     */
    private Ytdlp ytdlp = new Ytdlp();

    /**
     * Holds default configuration properties specific to the yt-dlp
     * command-line tool.
     */
    @Data
    public static class Ytdlp {

        /**
         * The sorting order for video formats.
         */
        private String formatSorting;

        /**
         * The filter criteria for selecting video formats.
         */
        private String formatFiltering;

        /**
         * The target container format to remux the video into after download
         * (e.g., "mp4").
         */
        private String remuxVideo = "";

        /**
         * Whether to write the video description to a .description file.
         */
        private boolean writeDescription = true;

        /**
         * Whether to download subtitles for the video.
         */
        private boolean writeSubs = true;

        /**
         * The language(s) of the subtitles to download (e.g., "en", "ja.*").
         */
        private String subLang = "ja.*";

        /**
         * Whether to download automatically generated subtitles.
         */
        private boolean writeAutoSubs = true;

        /**
         * The format for the downloaded subtitles (e.g., "srt", "vtt").
         */
        private String subFormat = "srt";

        /**
         * The template for the output filename.
         */
        private String outputTemplate = "%(title)s.%(ext)s";

        /**
         * Whether to keep the original video file after post-processing (e.g.,
         * after extracting audio).
         */
        private boolean keepVideo = false;

        /**
         * Whether to extract the audio from the video file.
         */
        private boolean extractAudio = false;

        /**
         * The format for the extracted audio (e.g., "m4a", "mp3").
         */
        private String audioFormat = "m4a";

        /**
         * The quality of the extracted audio (0 is best, 9 is worst for VBR).
         */
        private int audioQuality = 0;

        /**
         * The policy for handling existing files (overwrite, do not overwrite,
         * etc.).
         */
        private OverwriteOption overwrite = OverwriteOption.DEFAULT;

        /**
         * Whether to disable the progress bar.
         */
        private boolean noProgress = false;

        /**
         * Whether to use the cookie file for authentication.
         */
        private boolean useCookie = false;
    }
}
