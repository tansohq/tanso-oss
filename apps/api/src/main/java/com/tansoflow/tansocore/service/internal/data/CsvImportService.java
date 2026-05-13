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
