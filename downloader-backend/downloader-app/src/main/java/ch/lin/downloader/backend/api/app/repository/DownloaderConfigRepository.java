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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.downloader.backend.api.domain.DownloaderConfig;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DownloaderConfig} entities.
 */
@Repository
public interface DownloaderConfigRepository extends JpaRepository<DownloaderConfig, String> {

    /**
     * Deletes all {@link DownloaderConfig} records from the database.
     * <p>
     * This is a data-changing query and should be used with caution, typically
     * for testing or reset purposes.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM DownloaderConfig d")
    void cleanTable();

    /**
     * Finds a {@link DownloaderConfig} by its name, eagerly fetching the
     * associated
     * {@link ch.lin.downloader.backend.api.domain.YtDlpConfig}.
     *
     * @param name The name of the configuration.
     * @return An {@link Optional} containing the found configuration.
     */
    @EntityGraph(attributePaths = "ytDlpConfig")
    Optional<DownloaderConfig> findByName(String name);

    /**
     * Finds the first configuration that is marked as enabled.
     *
     * @return An {@link Optional} containing the first enabled config, or empty
     * if none are found.
     */
    @EntityGraph(attributePaths = "ytDlpConfig")
    Optional<DownloaderConfig> findFirstByEnabledTrue();

    /**
     * Finds all configurations that are marked as enabled.
     *
     * @return A {@link List} of all enabled configurations.
     */
    @EntityGraph(attributePaths = "ytDlpConfig")
    List<DownloaderConfig> findAllByEnabledTrue();
}
