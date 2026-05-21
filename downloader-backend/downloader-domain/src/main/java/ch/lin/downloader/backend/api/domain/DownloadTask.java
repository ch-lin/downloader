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

import org.hibernate.annotations.ColumnDefault;

import static ch.lin.downloader.backend.api.domain.DownloadTask.TABLE_NAME;
import ch.lin.platform.domain.model.UuidAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents a single video download task within a larger {@link DownloadJob}.
 */
@Entity
@Table(name = TABLE_NAME, indexes = {
    @Index(name = DownloadTask.ID_INDEX, columnList = UuidAuditableEntity.ID_COLUMN),
    @Index(name = DownloadTask.VIDEO_ID_INDEX, columnList = DownloadTask.VIDEO_ID_COLUMN)
})
@Getter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DownloadTask extends UuidAuditableEntity {

    /**
     * The name of the download task table in the database.
     */
    public static final String TABLE_NAME = "download_task";

    /**
     * The name of the index for the ID column.
     */
    public static final String ID_INDEX = "download_task_id_index";

    /**
     * The name of the index for the video ID column.
     */
    public static final String VIDEO_ID_INDEX = "idx_download_task_video_id";

    /**
     * The name of the job ID column in the database.
     */
    public static final String JOB_ID_COLUMN = "job_id";

    /**
     * The name of the foreign key constraint for the job relationship.
     */
    public static final String FK_DOWNLOAD_TASK_JOB = "fk_download_task_job";

    /**
     * The name of the video ID column in the database.
     */
    public static final String VIDEO_ID_COLUMN = "video_id";

    /**
     * The name of the title column in the database.
     */
    public static final String TITLE_COLUMN = "title";

    /**
     * The name of the thumbnail URL column in the database.
     */
    public static final String THUMBNAIL_URL_COLUMN = "thumbnail_url";

    /**
     * The name of the description column in the database.
     */
    public static final String DESCRIPTION_COLUMN = "description";

    /**
     * The name of the status column in the database.
     */
    public static final String STATUS_COLUMN = "status";

    /**
     * The name of the progress column in the database.
     */
    public static final String PROGRESS_COLUMN = "progress";

    /**
     * The name of the file path column in the database.
     */
    public static final String FILE_PATH_COLUMN = "file_path";

    /**
     * The name of the file size column in the database.
     */
    public static final String FILE_SIZE_COLUMN = "file_size";

    /**
     * The name of the error message column in the database.
     */
    public static final String ERROR_MESSAGE_COLUMN = "error_message";

    /**
     * The parent {@link DownloadJob} this task belongs to. This forms the
     * many-to-one side of the relationship.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = DownloadTask.JOB_ID_COLUMN, nullable = false, foreignKey = @ForeignKey(name = DownloadTask.FK_DOWNLOAD_TASK_JOB))
    private DownloadJob job;

    /**
     * The YouTube video ID, indexed for faster lookups.
     */
    @NotNull
    @Column(name = DownloadTask.VIDEO_ID_COLUMN, nullable = false)
    private String videoId;

    /**
     * The title of the video.
     */
    @NotNull
    @Column(name = DownloadTask.TITLE_COLUMN, nullable = false)
    @Setter
    private String title;

    /**
     * The URL of the video thumbnail.
     */
    @Column(name = DownloadTask.THUMBNAIL_URL_COLUMN, length = 512)
    @Setter
    private String thumbnailUrl;

    /**
     * The description of the video.
     */
    @Lob
    @Column(name = DownloadTask.DESCRIPTION_COLUMN, columnDefinition = "MEDIUMTEXT")
    @Setter
    private String description;

    /**
     * The status of this individual download task.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDING'")
    @Column(name = DownloadTask.STATUS_COLUMN, nullable = false)
    @Setter
    @lombok.Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    /**
     * The download progress percentage, from 0.0 to 100.0.
     */
    @ColumnDefault("0.0")
    @Column(name = DownloadTask.PROGRESS_COLUMN)
    @Setter
    @lombok.Builder.Default
    private Double progress = 0.0;

    /**
     * The local file path of the downloaded video.
     */
    @Column(name = DownloadTask.FILE_PATH_COLUMN, length = 1024)
    @Setter
    private String filePath;

    /**
     * The size of the downloaded file in bytes.
     */
    @Column(name = DownloadTask.FILE_SIZE_COLUMN)
    @Setter
    private Long fileSize;

    /**
     * Any error message if the download failed.
     */
    @Lob
    @Column(name = DownloadTask.ERROR_MESSAGE_COLUMN)
    @Setter
    private String errorMessage;

    /**
     * Creates a new DownloadTask.
     *
     * @param job The parent download job.
     * @param videoId The ID of the YouTube video to download.
     * @param title The title of the video.
     */
    public DownloadTask(DownloadJob job, String videoId, String title) {
        this();
        this.job = job;
        this.videoId = videoId;
        this.title = title;
    }
}
