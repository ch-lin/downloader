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

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class DownloadTaskJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void testCreationAndUpdateTimestampsAreManagedByHibernate() throws InterruptedException {
        // 1. Prepare associated Job (satisfy Foreign Key constraint)
        DownloadJob job = new DownloadJob("default-config");
        DownloadJob savedJob = entityManager.persistAndFlush(job);

        // 2. Prepare Task
        DownloadTask task = new DownloadTask(savedJob, "vid-12345", "Test Video Title");

        // 3. Test @CreationTimestamp: persist entity to database
        DownloadTask savedTask = entityManager.persistAndFlush(task);
        entityManager.clear(); // Clear Hibernate level-1 cache to ensure it's fetched from the DB next time

        assertThat(savedTask.getCreatedAt()).isNotNull();
        assertThat(savedTask.getUpdatedAt()).isNotNull();

        OffsetDateTime initialCreatedAt = savedTask.getCreatedAt();
        OffsetDateTime initialUpdatedAt = savedTask.getUpdatedAt();

        // 4. Wait a bit to ensure the timestamp difference is noticeable after update
        Thread.sleep(50);

        // 5. Test @UpdateTimestamp: modify field to trigger an update
        DownloadTask fetchedTask = entityManager.find(DownloadTask.class, savedTask.getId());
        fetchedTask.setProgress(50.0); // Simulate progress update
        entityManager.persistAndFlush(fetchedTask);
        entityManager.clear(); // Clear cache again

        // 6. Refetch data and verify
        DownloadTask updatedTask = entityManager.find(DownloadTask.class, savedTask.getId());

        // Verify: creation time remains unchanged, update time must be later
        assertThat(updatedTask.getCreatedAt()).isEqualTo(initialCreatedAt);
        assertThat(updatedTask.getUpdatedAt()).isAfter(initialUpdatedAt);
    }
}
