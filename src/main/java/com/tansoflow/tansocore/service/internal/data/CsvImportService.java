/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tansoflow.tansocore.service.internal.data;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CsvImportService {

    List<CsvUploadInfo> listUploads(UUID accountId);

    CsvImportResult importCsv(UUID accountId, MultipartFile file);

    void deleteUpload(UUID accountId, UUID uploadId);

    @Data
    class CsvImportResult {
        private int totalRows;
        private int imported;
    }

    @Data
    class CsvUploadInfo {
        private UUID id;
        private String fileName;
        private int rowCount;
        private String headers;
        private Instant createdAt;
    }
}
