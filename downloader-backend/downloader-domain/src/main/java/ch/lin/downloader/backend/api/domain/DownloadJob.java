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

import static ch.lin.downloader.backend.api.domain.DownloadJob.TABLE_NAME;
import ch.lin.platform.domain.model.UuidAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents a download job entity, which acts as a container for one or more
 * individual {@link DownloadTask} instances.
 */
@Entity
@Table(name = TABLE_NAME, indexes = {
    @Index(name = DownloadJob.ID_INDEX, columnList = UuidAuditableEntity.ID_COLUMN)
})
@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DownloadJob extends UuidAuditableEntity {

    /**
     * The name of the download job table in the database.
     */
    public static final String TABLE_NAME = "download_job";

    /**
     * The name of the index for the ID column.
     */
    public static final String ID_INDEX = "download_job_id_index";

    /**
     * The name of the status column in the database.
     */
    public static final String STATUS_COLUMN = "status";

    /**
     * The name of the config name column in the database.
     */
    public static final String CONFIG_NAME_COLUMN = "config_name";

    /**
     * The overall status of the job.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDING'")
    @Column(name = DownloadJob.STATUS_COLUMN, nullable = false)
    @Setter
    @lombok.Builder.Default
    private JobStatus status = JobStatus.PENDING;

    /**
     * The name of the {@link DownloaderConfig} used for this job.
     */
    @Column(name = DownloadJob.CONFIG_NAME_COLUMN)
    @Setter
    private String configName;

    /**
     * The list of individual video download tasks associated with this job. The
     * relationship is managed by the {@code job} field in the
     * {@link DownloadTask} entity. All lifecycle operations are cascaded, and
     * orphaned tasks are removed.
     */
    @OneToMany(mappedBy = "job", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @lombok.Builder.Default
    private List<DownloadTask> tasks = new ArrayList<>();

    /**
     * Creates a new DownloadJob with the specified config name.
     *
     * @param configName The name of the configuration to use.
     */
    public DownloadJob(String configName) {
        this();
        this.configName = configName;
    }

    /**
     * Helper method to add a task to the job and set the bidirectional
     * relationship.
     *
     * @param task The task to add.
     */
    public void addTask(DownloadTask task) {
        if (task.getJob() != this) {
            throw new IllegalArgumentException("The task must be initialized with this job instance.");
        }
        tasks.add(task);
    }
}
