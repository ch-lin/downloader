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

import static ch.lin.downloader.backend.api.domain.DownloadTask.TABLE_NAME;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single video download task within a larger {@link DownloadJob}.
 */
@Table(name = TABLE_NAME, indexes = {
    @Index(name = "idx_download_task_video_id", columnList = "video_id")
})
@Entity
@Getter
@Setter
public class DownloadTask {

    /**
     * The name of the download task table in the database.
     */
    public static final String TABLE_NAME = "download_task";

    /**
     * The unique identifier for the download task, serving as the primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * The parent {@link DownloadJob} this task belongs to. This forms the
     * many-to-one side of the relationship.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private DownloadJob job;

    /**
     * The YouTube video ID, indexed for faster lookups.
     */
    @NotNull
    @Column(name = "video_id", nullable = false)
    private String videoId;

    /**
     * The title of the video.
     */
    @NotNull
    @Column(nullable = false)
    private String title;

    /**
     * The URL of the video thumbnail.
     */
    @Column(name = "thumbnail_url", length = 512)
    private String thumbnailUrl;

    /**
     * The description of the video.
     */
    @Lob
    @Column(name = "description", columnDefinition = "MEDIUMTEXT")
    private String description;

    /**
     * The status of this individual download task.
     */
    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private TaskStatus status;

    /**
     * The download progress percentage, from 0.0 to 100.0.
     */
    @Column(name = "progress")
    private Double progress;

    /**
     * The local file path of the downloaded video.
     */
    @Column(name = "file_path", length = 1024)
    private String filePath;

    /**
     * The size of the downloaded file in bytes.
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Any error message if the download failed.
     */
    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    /**
     * The timestamp when the task was created, automatically set on
     * persistence.
     */
    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private OffsetDateTime createdAt;

    /**
     * The timestamp when the task was last updated, automatically set on
     * persistence or update.
     */
    @NotNull
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private OffsetDateTime updatedAt;

    /**
     * Sets the {@code createdAt} and {@code updatedAt} timestamps before the
     * entity is first persisted.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Updates the {@code updatedAt} timestamp before the entity is updated in
     * the database.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
