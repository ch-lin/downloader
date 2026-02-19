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
package ch.lin.downloader.backend.api.domain;

import static ch.lin.downloader.backend.api.domain.YtDlpConfig.TABLE_NAME;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the yt-dlp specific configuration entity, stored in the database.
 * <p>
 * This class holds settings that are passed as command-line options to the
 * yt-dlp executable. It is designed to have a one-to-one relationship with a
 * {@link DownloaderConfig} entity.
 */
@Table(name = TABLE_NAME)
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"name", "formatFiltering", "formatSorting", "remuxVideo", "writeDescription", "writeSubs",
    "subLang", "writeAutoSubs", "subFormat", "outputTemplate", "overwrite", "keepVideo", "extractAudio", "audioFormat",
    "audioQuality", "noProgress", "useCookie"}, callSuper = false)
public class YtDlpConfig {

    /**
     * The name of the yt-dlp configuration table in the database.
     */
    public static final String TABLE_NAME = "ytdlp_config";

    /**
     * The name of the name column in the database.
     */
    public static final String NAME_COLUMN = "name";

    /**
     * The name of the format filtering column in the database.
     */
    public static final String FORMAT_FILTERING_COLUMN = "format_filtering";

    /**
     * The name of the format sorting column in the database.
     */
    public static final String FORMAT_SORTING_COLUMN = "format_sorting";

    /**
     * The name of the remux video column in the database.
     */
    public static final String REMUX_VIDEO_COLUMN = "remux_video";

    /**
     * The name of the write description column in the database.
     */
    public static final String WRITE_DESCRIPTION_COLUMN = "write_description";

    /**
     * The name of the write subtitles column in the database.
     */
    public static final String WRITE_SUBS_COLUMN = "write_subs";

    /**
     * The name of the subtitle language column in the database.
     */
    public static final String SUB_LANG_COLUMN = "sub_lang";

    /**
     * The name of the write auto subtitles column in the database.
     */
    public static final String WRITE_AUTO_SUBS_COLUMN = "write_auto_subs";

    /**
     * The name of the subtitle format column in the database.
     */
    public static final String SUB_FORMAT_COLUMN = "sub_format";

    /**
     * The name of the output template column in the database.
     */
    public static final String OUTPUT_TEMPLATE_COLUMN = "output_template";

    /**
     * The name of the overwrite column in the database.
     */
    public static final String OVERWRITE_COLUMN = "overwrite";

    /**
     * The name of the keep video column in the database.
     */
    public static final String KEEP_VIDEO_COLUMN = "keep_video";

    /**
     * The name of the extract audio column in the database.
     */
    public static final String EXTRACT_AUDIO_COLUMN = "extract_audio";

    /**
     * The name of the audio format column in the database.
     */
    public static final String AUDIO_FORMAT_COLUMN = "audio_format";

    /**
     * The name of the audio quality column in the database.
     */
    public static final String AUDIO_QUALITY_COLUMN = "audio_quality";

    /**
     * The name of the no progress column in the database.
     */
    public static final String NO_PROGRESS_COLUMN = "no_progress";

    /**
     * The name of the use cookie column in the database.
     */
    public static final String USE_COOKIE_COLUMN = "use_cookie";

    /**
     * The unique name of the configuration, serving as the primary key. This
     * name must match the name of the parent {@link DownloaderConfig}.
     */
    @Id
    @NotNull
    @Column(name = YtDlpConfig.NAME_COLUMN)
    private String name;

    /**
     * The filter criteria for selecting video formats.
     */
    @Column(name = YtDlpConfig.FORMAT_FILTERING_COLUMN)
    private String formatFiltering;

    /**
     * The sorting order for video formats.
     */
    @Column(name = YtDlpConfig.FORMAT_SORTING_COLUMN)
    private String formatSorting;

    /**
     * The target container format to remux the video into (e.g., "mp4").
     */
    @Column(name = YtDlpConfig.REMUX_VIDEO_COLUMN)
    private String remuxVideo;

    /**
     * Whether to write the video description to a .description file.
     */
    @Column(name = YtDlpConfig.WRITE_DESCRIPTION_COLUMN)
    private Boolean writeDescription;

    /**
     * Whether to download subtitles for the video.
     */
    @Column(name = YtDlpConfig.WRITE_SUBS_COLUMN)
    private Boolean writeSubs;

    /**
     * The language(s) of the subtitles to download (e.g., "en", "ja.*").
     */
    @Column(name = YtDlpConfig.SUB_LANG_COLUMN)
    private String subLang;

    /**
     * Whether to download automatically generated subtitles.
     */
    @Column(name = YtDlpConfig.WRITE_AUTO_SUBS_COLUMN)
    private Boolean writeAutoSubs;

    /**
     * The format for the downloaded subtitles (e.g., "srt", "vtt").
     */
    @Column(name = YtDlpConfig.SUB_FORMAT_COLUMN)
    private String subFormat;

    /**
     * The template for the output filename.
     */
    @Column(name = YtDlpConfig.OUTPUT_TEMPLATE_COLUMN)
    private String outputTemplate;

    /**
     * The policy for handling existing files (overwrite, do not overwrite,
     * etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = YtDlpConfig.OVERWRITE_COLUMN)
    private OverwriteOption overwrite;

    /**
     * Whether to keep the original video file after post-processing (e.g.,
     * after extracting audio).
     */
    @Column(name = YtDlpConfig.KEEP_VIDEO_COLUMN)
    private Boolean keepVideo;

    /**
     * Whether to extract the audio from the video file.
     */
    @Column(name = YtDlpConfig.EXTRACT_AUDIO_COLUMN)
    private Boolean extractAudio;

    /**
     * The format for the extracted audio (e.g., "m4a", "mp3").
     */
    @Column(name = YtDlpConfig.AUDIO_FORMAT_COLUMN)
    private String audioFormat;

    /**
     * The quality of the extracted audio (0 is best, 9 is worst for VBR).
     */
    @Column(name = YtDlpConfig.AUDIO_QUALITY_COLUMN)
    private Integer audioQuality;

    /**
     * Whether to disable the progress bar.
     */
    @Column(name = YtDlpConfig.NO_PROGRESS_COLUMN)
    private Boolean noProgress;

    /**
     * Whether to use the cookie file for authentication.
     */
    @Column(name = YtDlpConfig.USE_COOKIE_COLUMN)
    private Boolean useCookie;

    /**
     * A transient field to hold the content of the cookie file. This is used to
     * pass authentication data to the download process and is not persisted in
     * the database.
     */
    @Transient
    private String cookie;
}
