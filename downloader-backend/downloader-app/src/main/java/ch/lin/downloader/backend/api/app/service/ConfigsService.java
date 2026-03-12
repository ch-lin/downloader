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
package ch.lin.downloader.backend.api.app.service;

import ch.lin.downloader.backend.api.app.service.command.CreateConfigCommand;
import ch.lin.downloader.backend.api.app.service.command.UpdateConfigCommand;
import ch.lin.downloader.backend.api.app.service.model.AllConfigsData;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;

/**
 * Defines the contract for managing downloader configurations.
 * <p>
 * This service provides methods for creating, reading, updating, and deleting
 * {@link DownloaderConfig} entities. It also handles the logic for resolving
 * the active configuration for downloads.
 */
public interface ConfigsService {

    /**
     * Retrieves a summary of all available configurations, including which one
     * is currently enabled.
     *
     * @return An {@link AllConfigsData} containing the name of the enabled
     * config and a list of all config names.
     * @throws ch.lin.platform.exception.ConfigCreationException if the default
     * configuration needs to be created but fails.
     */
    AllConfigsData getAllConfigs();

    /**
     * Creates a new downloader configuration.
     *
     * @param request The request DTO containing the new configuration details.
     * @return The newly created and persisted {@link DownloaderConfig}.
     * @throws ch.lin.platform.exception.InvalidRequestException if the
     * configuration name is 'default' or already exists.
     */
    DownloaderConfig createConfig(CreateConfigCommand request);

    /**
     * Deletes all downloader configurations and their associated cookie files.
     */
    void deleteAllConfigs();

    /**
     * Deletes a specific downloader configuration by its name.
     *
     * @param name The name of the configuration to delete.
     * @throws ch.lin.platform.exception.InvalidRequestException if attempting
     * to delete the reserved 'default' configuration.
     * @throws ch.lin.platform.exception.ConfigNotFoundException if no
     * configuration with the given name exists.
     * @throws ch.lin.platform.exception.ConfigCreationException if the default
     * configuration needs to be created but fails.
     */
    void deleteConfig(String name);

    /**
     * Retrieves a specific downloader configuration by its name.
     *
     * @param name The name of the configuration to retrieve.
     * @return The found {@link DownloaderConfig}.
     * @throws ch.lin.platform.exception.ConfigNotFoundException if no
     * configuration with the given name exists.
     * @throws ch.lin.platform.exception.ConfigCreationException if the
     * requested config is 'default' and it needs to be created but fails.
     */
    DownloaderConfig getConfig(String name);

    /**
     * Updates an existing downloader configuration or creates it if it doesn't
     * exist.
     *
     * @param name The name of the configuration to save.
     * @param request The request DTO containing the fields to update.
     * @return The saved {@link DownloaderConfig}.
     * @throws ch.lin.platform.exception.ConfigCreationException if the default
     * configuration needs to be enabled but fails.
     */
    DownloaderConfig saveConfig(String name, UpdateConfigCommand request);

    /**
     * Retrieves the active download configuration, falling back to defaults if
     * necessary.
     *
     * @param configName The name of a specific configuration to use (can be
     * null).
     * @return The fully resolved {@link DownloaderConfig} to use for a
     * download.
     * @throws ch.lin.platform.exception.ConfigCreationException if a default
     * configuration needs to be created but fails.
     */
    DownloaderConfig getResolvedConfig(String configName);
}
