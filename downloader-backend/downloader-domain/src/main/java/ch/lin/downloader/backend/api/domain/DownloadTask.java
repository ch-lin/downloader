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

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;

import static ch.lin.downloader.backend.api.domain.DownloadTask.TABLE_NAME;
import ch.lin.platform.domain.model.UuidAuditableEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
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
@EqualsAndHashCode(callSuper = true, exclude = "subTasks")
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
     * The list of sub-tasks for this download task (e.g., AUDIO, VIDEO).
     */
    @OneToMany(mappedBy = "parentTask", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @lombok.Builder.Default
    private List<DownloadSubTask> subTasks = new ArrayList<>();

    /**
     * Creates a new DownloadTask.
     *
     * @param job The parent download job.
     * @param videoId The ID of the YouTube video to download.
     * @param title The title of the video.
     */
    protected DownloadTask(DownloadJob job, String videoId, String title) {
        this();
        this.job = job;
        this.videoId = videoId;
        this.title = title;
    }

    /**
     * Creates a new DownloadTask and enforces the required sub-tasks.
     */
    public static DownloadTask create(DownloadJob job, String videoId, String title, boolean extractAudio) {
        DownloadTask task = new DownloadTask(job, videoId, title);

        task.addSubTask(new DownloadSubTask(task, SubTaskType.VIDEO));

        if (extractAudio) {
            task.addSubTask(new DownloadSubTask(task, SubTaskType.AUDIO));
        }

        return task;
    }

    /**
     * Adds a sub-task to this download task.
     *
     * @param subTask The sub-task to add.
     */
    public void addSubTask(DownloadSubTask subTask) {
        if (subTask.getParentTask() != this) {
            throw new IllegalArgumentException("SubTask must belong to this task.");
        }
        subTasks.add(subTask);
    }

    /**
     * Retrieves a sub-task by its type.
     *
     * @param type The type of the sub-task.
     * @return The sub-task if found, null otherwise.
     */
    public DownloadSubTask getSubTask(SubTaskType type) {
        return subTasks.stream()
                .filter(st -> st.getType() == type)
                .findFirst()
                .orElse(null);
    }

    /**
     * Dynamically computes and updates the overall status based on the statuses
     * of the sub-tasks.
     */
    public void updateStatusBasedOnSubTasks() {
        if (subTasks.isEmpty()) {
            this.status = TaskStatus.PENDING;
            return;
        }

        boolean allDownloaded = true;
        boolean anyStarted = false;
        boolean anyFailed = false;

        for (DownloadSubTask st : subTasks) {
            if (st.getStatus() == TaskStatus.FAILED) {
                anyFailed = true;
            }
            if (st.getStatus() != TaskStatus.PENDING) {
                anyStarted = true;
            }
            if (st.getStatus() != TaskStatus.DOWNLOADED) {
                allDownloaded = false;
            }
        }

        if (anyFailed) {
            this.status = TaskStatus.FAILED;
        } else if (allDownloaded) {
            this.status = TaskStatus.DOWNLOADED;
        } else if (anyStarted) {
            this.status = TaskStatus.DOWNLOADING;
        } else {
            this.status = TaskStatus.PENDING;
        }
    }
}
