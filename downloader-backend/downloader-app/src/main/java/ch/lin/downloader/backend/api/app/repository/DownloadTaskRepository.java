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
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.downloader.backend.api.domain.DownloadTask;
import ch.lin.downloader.backend.api.domain.TaskStatus;

/**
 * Spring Data JPA repository for {@link DownloadTask} entities.
 */
@Repository
public interface DownloadTaskRepository extends JpaRepository<DownloadTask, String> {

    /**
     * Deletes all {@link DownloadTask} records from the database.
     * <p>
     * This is a data-changing query and should be used with caution, typically
     * for testing or reset purposes.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM DownloadTask d")
    void cleanTable();

    /**
     * Finds all {@link DownloadTask} entities with a specific status.
     * <p>
     * Note: This method does not eagerly fetch the associated
     * {@link ch.lin.downloader.backend.api.domain.DownloadJob}.
     *
     * @param status The status to filter by.
     * @return A {@link List} of tasks matching the given status.
     */
    List<DownloadTask> findAllByStatus(TaskStatus status);

    /**
     * Finds all {@link DownloadTask} entities with a specific status, eagerly
     * fetching the associated
     * {@link ch.lin.downloader.backend.api.domain.DownloadJob} for
     * each task.
     *
     * @param status The status to filter by.
     * @return A {@link List} of tasks with their parent jobs, matching the
     * given status.
     */
    @Query("SELECT t FROM DownloadTask t JOIN FETCH t.job WHERE t.status = :status")
    List<DownloadTask> findAllByStatusWithJob(@Param("status") TaskStatus status);

    /**
     * Finds a {@link DownloadTask} by its ID, eagerly fetching the associated
     * {@link ch.lin.downloader.backend.api.domain.DownloadJob}.
     * <p>
     * This method overrides the default {@link JpaRepository#findById(Object)}
     * to ensure the parent job is loaded in the same query, preventing N+1
     * issues.
     *
     * @param taskId The non-null ID of the task to find.
     * @return A non-null {@link Optional} containing the found task, or empty
     * if not found.
     */
    @Override
    @Query("SELECT t FROM DownloadTask t JOIN FETCH t.job WHERE t.id = :taskId")
    @NonNull
    Optional<DownloadTask> findById(@Param("taskId") @NonNull String taskId);

}
