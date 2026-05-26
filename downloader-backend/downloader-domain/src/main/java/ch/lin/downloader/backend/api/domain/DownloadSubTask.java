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

import static ch.lin.downloader.backend.api.domain.DownloadSubTask.TABLE_NAME;
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
 * Represents a specific sub-task within a larger {@link DownloadTask}.
 * <p>
 * Sub-tasks handle individual components of a download process, such as
 * downloading the video stream or extracting the audio. This allows for
 * fine-grained tracking of progress and status for each phase.
 */
@Entity
@Table(name = TABLE_NAME, indexes = {
    @Index(name = DownloadSubTask.ID_INDEX, columnList = UuidAuditableEntity.ID_COLUMN),
    @Index(name = DownloadSubTask.PARENT_TASK_ID_INDEX, columnList = DownloadSubTask.PARENT_TASK_ID_COLUMN)
})
@Getter
@EqualsAndHashCode(callSuper = true, exclude = "parentTask")
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DownloadSubTask extends UuidAuditableEntity {

    /**
     * The name of the download sub-task table in the database.
     */
    public static final String TABLE_NAME = "download_sub_task";
    /**
     * The name of the index for the ID column.
     */
    public static final String ID_INDEX = "download_sub_task_id_idx";
    /**
     * The name of the index for the parent task ID column.
     */
    public static final String PARENT_TASK_ID_INDEX = "idx_sub_task_parent_id";
    /**
     * The name of the parent task ID column in the database.
     */
    public static final String PARENT_TASK_ID_COLUMN = "parent_task_id";
    /**
     * The name of the foreign key constraint for the parent task relationship.
     */
    public static final String FK_SUB_TASK_PARENT = "fk_sub_task_parent";

    /**
     * The name of the type column in the database.
     */
    public static final String TYPE_COLUMN = "type";

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
     * The parent {@link DownloadTask} this sub-task belongs to. This forms the
     * many-to-one side of the aggregate root relationship.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = PARENT_TASK_ID_COLUMN, nullable = false, foreignKey = @ForeignKey(name = FK_SUB_TASK_PARENT))
    private DownloadTask parentTask;

    /**
     * The type of component this sub-task is responsible for downloading.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = DownloadSubTask.TYPE_COLUMN, nullable = false)
    private SubTaskType type;

    /**
     * The status of this individual download sub-task.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDING'")
    @Column(name = DownloadSubTask.STATUS_COLUMN, nullable = false)
    @Setter
    @lombok.Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    /**
     * The download progress percentage for this specific sub-task, from 0.0 to
     * 100.0.
     */
    @ColumnDefault("0.0")
    @Column(name = DownloadSubTask.PROGRESS_COLUMN)
    @Setter
    @lombok.Builder.Default
    private Double progress = 0.0;

    /**
     * The local file path of the downloaded component.
     */
    @Column(name = DownloadSubTask.FILE_PATH_COLUMN, length = 1024)
    @Setter
    private String filePath;

    /**
     * The size of the downloaded component file in bytes.
     */
    @Column(name = DownloadSubTask.FILE_SIZE_COLUMN)
    @Setter
    private Long fileSize;

    /**
     * Any error message if this specific sub-task failed.
     */
    @Lob
    @Column(name = DownloadSubTask.ERROR_MESSAGE_COLUMN)
    @Setter
    private String errorMessage;

    /**
     * Creates a new DownloadSubTask.
     *
     * @param parentTask The parent download task.
     * @param type The type of this sub-task.
     */
    public DownloadSubTask(DownloadTask parentTask, SubTaskType type) {
        this();
        this.parentTask = parentTask;
        this.type = type;
    }
}
