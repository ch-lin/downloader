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
package ch.lin.downloader.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The main entry point for the Downloader service.
 * <p>
 * This class bootstraps the Spring Boot application. It is configured to:
 * <ul>
 * <li>Scan for components across the entire {@code ch.lin.downloader.backend}
 * package via {@link SpringBootApplication}.</li>
 * <li>Scan for JPA entities specifically within the
 * {@code ch.lin.downloader.backend.api.domain} package using
 * {@link EntityScan}.</li>
 * <li>Enable JPA repositories across the entire
 * {@code ch.lin.downloader.backend} package via
 * {@link EnableJpaRepositories}.</li>
 * <li>Enable support for scheduled tasks with {@link EnableScheduling}.</li>
 * </ul>
 * It also extends {@link SpringBootServletInitializer} to support deployment as
 * a traditional WAR file in a servlet container.
 */
@SpringBootApplication(scanBasePackages = "ch.lin.downloader.backend")
@EntityScan("ch.lin.downloader.backend.api.domain")
@EnableJpaRepositories(basePackages = "ch.lin.downloader.backend")
@EnableScheduling
public class DownloaderApplication extends SpringBootServletInitializer {

    /**
     * The main method which serves as the entry point to launch the Spring Boot
     * application.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(String[] args) {
        SpringApplication.run(DownloaderApplication.class, args);
    }
}
