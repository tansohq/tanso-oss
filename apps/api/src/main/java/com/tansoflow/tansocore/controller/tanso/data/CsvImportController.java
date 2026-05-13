package com.tansoflow.tansocore.controller.tanso.data;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.data.CsvImportService;
import com.tansoflow.tansocore.service.internal.data.CsvImportService.CsvImportResult;
import com.tansoflow.tansocore.service.internal.data.CsvImportService.CsvUploadInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tanso/csv-import")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "CSV Import", description = "Import historic cost/usage data from CSV files for AI Insights")
public class CsvImportController {

    private final CsvImportService csvImportService;

    @GetMapping
    @Operation(summary = "List uploaded CSVs")
    public ResponseEntity<ApiResponse<List<CsvUploadInfo>>> listUploads(
            @AuthenticationPrincipal UserContext userContext) {
        UUID accountId = UUID.fromString(userContext.getAccountId());
        List<CsvUploadInfo> uploads = csvImportService.listUploads(accountId);
        return ResponseEntity.ok(ApiResponse.<List<CsvUploadInfo>>builder()
                .success(true).data(uploads).build());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import CSV for AI Insights")
    public ResponseEntity<ApiResponse<CsvImportResult>> importCsv(
            @AuthenticationPrincipal UserContext userContext,
            @RequestParam("file") MultipartFile file) {
        UUID accountId = UUID.fromString(userContext.getAccountId());
        CsvImportResult result = csvImportService.importCsv(accountId, file);
        return ResponseEntity.ok(ApiResponse.<CsvImportResult>builder()
                .success(true).data(result).build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an uploaded CSV")
    public ResponseEntity<ApiResponse<Void>> deleteUpload(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable UUID id) {
        UUID accountId = UUID.fromString(userContext.getAccountId());
        csvImportService.deleteUpload(accountId, id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }
}
