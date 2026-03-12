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
package ch.lin.downloader.backend.api.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ch.lin.downloader.backend.api.app.service.ConfigsService;
import ch.lin.downloader.backend.api.app.service.model.AllConfigsData;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.dto.CreateConfigRequest;
import ch.lin.downloader.backend.api.dto.UpdateConfigRequest;
import ch.lin.downloader.backend.api.mapper.ConfigMapper;
import ch.lin.platform.api.ApiResponse;
import jakarta.validation.Valid;

/**
 * REST controller for managing yt-dlp configurations.
 * <p>
 * This controller provides a full set of CRUD (Create, Read, Update, Delete)
 * endpoints for {@link DownloaderConfig} entities. It allows clients to manage
 * the configurations used for processing download jobs, delegating the business
 * logic to the {@link ConfigsService}.
 */
@RestController
public class ConfigsController {

    private final ConfigsService configsService;
    private final ConfigMapper configMapper;

    /**
     * Constructs a {@code ConfigsController} with the necessary service.
     *
     * @param configsService The service for managing downloader configurations.
     * @param configMapper The mapper for converting DTOs to commands.
     */
    public ConfigsController(ConfigsService configsService, ConfigMapper configMapper) {
        this.configsService = configsService;
        this.configMapper = configMapper;
    }

    /**
     * Retrieves the names of all available yt-dlp configurations.
     *
     * @return A {@link ResponseEntity} with an HTTP 200 OK status, containing
     * an {@link AllConfigsData} object that lists the configuration names.
     * <p>
     * Example cURL request:
     *
     * <pre>
     * {@code
     * curl -X GET http://localhost:8081/configs
     * }
     * </pre>
     */
    @GetMapping("/configs")
    public ResponseEntity<AllConfigsData> getAllConfigs() {
        return ResponseEntity.ok(configsService.getAllConfigs());
    }

    /**
     * Creates a new yt-dlp configuration.
     *
     * @param request The request body containing the details of the new
     * configuration.
     * @return A {@link ResponseEntity} with an HTTP 201 Created status. The
     * 'Location' header will contain the URL to the new resource, and the body
     * will contain an {@link ApiResponse} wrapping the created
     * {@link DownloaderConfig}.
     * <p>
     * Example cURL request:
     *
     * <pre>
     * {@code
     * curl -X POST http://localhost:8081/configs \
     * -H "Content-Type: application/json" \
     * -d '{
     *   "name": "new-audio-config",
     *   "enabled": true,
     *   "formatFiltering": "bestaudio/best",
     *   "startAutomatically": true,
     *   "formatSorting": "abr"
     * }'
     * }
     * </pre>
     */
    @PostMapping("/configs")
    public ResponseEntity<ApiResponse<DownloaderConfig>> createConfig(@Valid @RequestBody CreateConfigRequest request) {
        DownloaderConfig createdConfig = configsService.createConfig(configMapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{name}")
                .buildAndExpand(createdConfig.getName())
                .toUri();
        return ResponseEntity.created(location).body(ApiResponse.success(createdConfig));
    }

    /**
     * Deletes all yt-dlp configurations.
     * <p>
     * This endpoint provides a way to reset all configurations. After deletion,
     * the system will automatically recreate the 'default' configuration on the
     * next relevant request.
     *
     * @return A {@link ResponseEntity} with an HTTP 204 No Content status upon
     * successful deletion.
     * <p>
     * Example cURL request:
     *
     * <pre>
     * {@code curl -X DELETE http://localhost:8081/configs}
     * </pre>
     */
    @DeleteMapping("/configs")
    public ResponseEntity<Void> deleteAllConfigs() {
        configsService.deleteAllConfigs();
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves a specific yt-dlp configuration by its name.
     *
     * @param name The name of the configuration to retrieve, passed as a path
     * variable.
     * @return A {@link ResponseEntity} containing the {@link DownloaderConfig}
     * if found, or a 404 Not Found status if no configuration with the given
     * name exists.
     * <p>
     * Example cURL request:
     *
     * <pre>
     * {@code
     * curl -X GET http://localhost:8081/configs/default
     * }
     * </pre>
     */
    @GetMapping("/configs/{name}")
    public ResponseEntity<DownloaderConfig> getConfig(@PathVariable("name") String name) {
        return ResponseEntity.ok(configsService.getConfig(name));
    }

    /**
     * Creates or updates a specific yt-dlp configuration.
     * <p>
     * If a configuration with the given name exists, it will be updated with
     * the provided fields. If it does not exist, a new configuration will be
     * created.
     *
     * @param name The name of the configuration to create or update.
     * @param request The request body containing the configuration fields to
     * set.
     * @return A {@link ResponseEntity} with an HTTP 200 OK status, containing
     * an {@link ApiResponse} that wraps the saved {@link DownloaderConfig}.
     * <p>
     * Example cURL request to create/update a config:
     *
     * <pre>
     * {@code
     * curl -X PATCH http://localhost:8081/configs/custom-audio \
     * -H "Content-Type: application/json" \
     * -d '{
     *   "enabled": true,
     *   "formatFiltering": "bestaudio/best",
     *   "startAutomatically": true,
     *   "formatSorting": "abr"
     * }'
     * }
     * </pre>
     */
    @PatchMapping("/configs/{name}")
    public ResponseEntity<ApiResponse<DownloaderConfig>> saveConfig(@PathVariable("name") String name,
            @RequestBody @Valid UpdateConfigRequest request) {
        DownloaderConfig savedConfig = configsService.saveConfig(name, configMapper.toCommand(request));
        return ResponseEntity.ok(ApiResponse.success(savedConfig));
    }

    /**
     * Deletes a specific yt-dlp configuration by its name.
     * <p>
     * The 'default' configuration is system-reserved and cannot be deleted.
     *
     * @param name The name of the configuration to delete.
     * @return A {@link ResponseEntity} with an HTTP 204 No Content status upon
     * successful deletion, or a 404 Not Found if the configuration does not
     * exist.
     * <p>
     * Example cURL request:
     *
     * <pre>
     * {@code curl -X DELETE http://localhost:8081/configs/custom-audio}
     * </pre>
     */
    @DeleteMapping("/configs/{name}")
    public ResponseEntity<Void> deleteConfig(@PathVariable("name") String name) {
        configsService.deleteConfig(name);
        return ResponseEntity.noContent().build();
    }
}
