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

import static ch.lin.downloader.backend.api.domain.DownloadJob.TABLE_NAME;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a download job entity, which acts as a container for one or more
 * individual {@link DownloadTask} instances.
 */
@Table(name = TABLE_NAME)
@Entity
@Getter
@Setter
public class DownloadJob {

    /**
     * The name of the download job table in the database.
     */
    public static final String TABLE_NAME = "download_job";

    /**
     * The unique identifier for the download job.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * The overall status of the job.
     */
    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private JobStatus status;

    /**
     * The name of the {@link DownloaderConfig} used for this job.
     */
    @Column(name = "config_name")
    private String configName;

    /**
     * The list of individual video download tasks associated with this job. The
     * relationship is managed by the {@code job} field in the
     * {@link DownloadTask} entity. All lifecycle operations are cascaded, and
     * orphaned tasks are removed.
     */
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DownloadTask> tasks = new ArrayList<>();

    /**
     * The timestamp when the job was created, automatically set on persistence.
     */
    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private OffsetDateTime createdAt;

    /**
     * The timestamp when the job was last updated, automatically set on
     * persistence or update.
     */
    @NotNull
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private OffsetDateTime updatedAt;

    /**
     * Helper method to add a task to the job and set the bidirectional
     * relationship.
     *
     * @param task The task to add.
     */
    public void addTask(DownloadTask task) {
        tasks.add(task);
        task.setJob(this);
    }

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
