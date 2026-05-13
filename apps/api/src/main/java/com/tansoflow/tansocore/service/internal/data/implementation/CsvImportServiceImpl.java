package com.tansoflow.tansocore.service.internal.data.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AiCsvUpload;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AiCsvUploadRepository;
import com.tansoflow.tansocore.service.internal.data.CsvImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportServiceImpl implements CsvImportService {

    private static final int MAX_ROWS = 10_000;
    private static final int MAX_UPLOADS = 10;

    private final AiCsvUploadRepository aiCsvUploadRepository;
    private final AccountRepository accountRepository;

    @Override
    public List<CsvUploadInfo> listUploads(UUID accountId) {
        return aiCsvUploadRepository.findMetadataByAccountId(accountId)
                .stream()
                .map(row -> {
                    CsvUploadInfo info = new CsvUploadInfo();
                    info.setId((UUID) row[0]);
                    info.setFileName((String) row[1]);
                    info.setRowCount((Integer) row[2]);
                    info.setHeaders((String) row[3]);
                    info.setCreatedAt((Instant) row[4]);
                    return info;
                })
                .toList();
    }

    @Override
    @Transactional
    public CsvImportResult importCsv(UUID accountId, MultipartFile file) {
        log.info("CSV import started for account: {}, file: {}, size: {}",
                accountId, file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        long existing = aiCsvUploadRepository.countByAccount_Id(accountId);
        if (existing >= MAX_UPLOADS) {
            throw new IllegalArgumentException("Maximum of " + MAX_UPLOADS + " CSV files. Remove one before uploading.");
        }

        StringBuilder content = new StringBuilder();
        String headerLine;
        int rowCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("CSV file has no header row");
            }
            content.append(headerLine).append("\n");

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    content.append(line).append("\n");
                    rowCount++;
                }
                if (rowCount > MAX_ROWS) {
                    throw new IllegalArgumentException("CSV exceeds maximum of " + MAX_ROWS + " rows");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read CSV for account {}: {}", accountId, e.getMessage());
            throw new IllegalArgumentException("Failed to read CSV: " + e.getMessage());
        }

        if (rowCount == 0) {
            throw new IllegalArgumentException("CSV file has no data rows");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        AiCsvUpload upload = new AiCsvUpload();
        upload.setAccount(account);
        upload.setFileName(file.getOriginalFilename());
        upload.setHeaders(headerLine);
        upload.setCsvContent(content.toString());
        upload.setRowCount(rowCount);
        aiCsvUploadRepository.save(upload);

        log.info("CSV import stored for account {}: {} rows from {}", accountId, rowCount, file.getOriginalFilename());

        CsvImportResult result = new CsvImportResult();
        result.setTotalRows(rowCount);
        result.setImported(rowCount);
        return result;
    }

    @Override
    @Transactional
    public void deleteUpload(UUID accountId, UUID uploadId) {
        AiCsvUpload upload = aiCsvUploadRepository.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found"));
        if (!upload.getAccount().getId().equals(accountId)) {
            throw new ResourceNotFoundException("Upload not found");
        }
        aiCsvUploadRepository.delete(upload);
    }
}
