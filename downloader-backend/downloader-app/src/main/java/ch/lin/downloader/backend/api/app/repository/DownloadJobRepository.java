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
package ch.lin.downloader.backend.api.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.JobStatus;

/**
 * Spring Data JPA repository for {@link DownloadJob} entities.
 */
@Repository
public interface DownloadJobRepository extends JpaRepository<DownloadJob, String> {

    /**
     * Deletes all {@link DownloadJob} records from the database.
     * <p>
     * This is a data-changing query and should be used with caution, typically
     * for testing or reset purposes.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM DownloadJob d")
    void cleanTable();

    /**
     * Finds a {@link DownloadJob} by its ID, eagerly fetching its associated
     * {@link ch.lin.downloader.backend.api.domain.DownloadTask}
     * list.
     *
     * @param id The ID of the job to find.
     * @return An {@link Optional} containing the found job with its tasks, or
     * empty if not found.
     */
    @Query("SELECT j FROM DownloadJob j LEFT JOIN FETCH j.tasks WHERE j.id = :id")
    Optional<DownloadJob> findByIdWithTasks(@Param("id") String id);

    /**
     * Finds the parent {@link DownloadJob} associated with a given task ID.
     *
     * @param taskId The ID of the task.
     * @return An {@link Optional} containing the parent job, or empty if not
     * found.
     */
    @Query("SELECT j FROM DownloadJob j JOIN j.tasks t WHERE t.id = :taskId")
    Optional<DownloadJob> findByTaskId(@Param("taskId") String taskId);

    /**
     * Finds all {@link DownloadJob} entities with a specific status.
     *
     * @param status The status to filter by.
     * @return A {@link List} of jobs matching the given status.
     */
    List<DownloadJob> findAllByStatus(JobStatus status);
}
