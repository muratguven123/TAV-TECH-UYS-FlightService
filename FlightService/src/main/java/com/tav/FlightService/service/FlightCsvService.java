package com.tav.FlightService.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.config.StompTopics;
import com.tav.FlightService.dto.BulkUploadResult;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.RowError;
import com.tav.FlightService.mapper.FlightMapper;
import com.tav.FlightService.repository.FlightRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
public class FlightCsvService {

    private static final Logger log = LoggerFactory.getLogger(FlightCsvService.class);
    private static final int PROGRESS_INTERVAL = 10;
    private static final String BULK_TOPIC_PREFIX = StompTopics.BULK_PREFIX;

    private static final int COL_FLIGHT_NUMBER       = 0;
    private static final int COL_AIRLINE_CODE        = 1;
    private static final int COL_AIRCRAFT_TAIL       = 2;
    private static final int COL_ORIGIN              = 3;
    private static final int COL_DESTINATION         = 4;
    private static final int COL_FLIGHT_TYPE         = 5;
    private static final int COL_SCHEDULED_DEPARTURE = 6;
    private static final int COL_SCHEDULED_ARRIVAL   = 7;
    private static final int EXPECTED_COLUMNS        = 8;

    private final FlightService flightService;
    private final Validator validator;
    private final SimpMessagingTemplate messagingTemplate;

    public FlightCsvService(FlightRepository flightRepository,
                            FlightMapper flightMapper,
                            FlightService flightService,
                            Validator validator,
                            SimpMessagingTemplate messagingTemplate) {
        this.flightService = flightService;
        this.validator = validator;
        this.messagingTemplate = messagingTemplate;
    }

    public BulkUploadResult upload(MultipartFile file) throws IOException {
        byte[] content = file.getBytes();
        return processUpload(content, countDataRows(content), null);
    }

    public String submitBulkUpload(MultipartFile file) throws IOException {
        String jobId = UUID.randomUUID().toString();
        byte[] content = file.getBytes();
        processAsync(jobId, content);
        return jobId;
    }

    @Async("bulkUploadExecutor")
    public void processAsync(String jobId, byte[] content) {
        try {
            int total = countDataRows(content);
            sendProgress(jobId, 0, total);

            BulkUploadResult result = processUpload(content, total, (processed, totalRows) -> {
                if (processed % PROGRESS_INTERVAL == 0 || processed == totalRows) {
                    sendProgress(jobId, processed, totalRows);
                }
            });

            sendCompleted(jobId, result);
            log.info("Async bulk upload tamamlandı jobId={}: toplam={}, başarılı={}, hatalı={}",
                    jobId, result.totalRows(), result.successCount(), result.failureCount());
        } catch (Exception ex) {
            log.error("Async bulk upload başarısız jobId={}: {}", jobId, ex.getMessage(), ex);
            sendFailed(jobId, ex.getMessage());
        }
    }

    private int countDataRows(byte[] content) throws IOException {
        int lineNumber = 0;
        int dataRows = 0;

        try (CSVReader csvReader = new CSVReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String[] cols;
            while ((cols = csvReader.readNext()) != null) {
                lineNumber++;
                if (lineNumber == 1 && cols.length > 0 && cols[0].trim().equalsIgnoreCase("flightNumber")) {
                    continue;
                }
                dataRows++;
            }
        } catch (CsvValidationException ex) {
            throw new IOException("CSV okuma hatası: " + ex.getMessage(), ex);
        }

        return dataRows;
    }

    private BulkUploadResult processUpload(byte[] content, int totalRows, BiConsumer<Integer, Integer> progressCallback)
            throws IOException {
        List<RowError> errors = new ArrayList<>();
        int successCount = 0;
        int lineNumber = 0;
        int dataRowNumber = 0;

        try (CSVReader csvReader = new CSVReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {

            String[] cols;
            while ((cols = csvReader.readNext()) != null) {
                lineNumber++;

                if (lineNumber == 1 && cols[0].trim().equalsIgnoreCase("flightNumber")) {
                    continue;
                }

                dataRowNumber++;
                String rawLine = String.join(",", cols);

                if (cols.length < EXPECTED_COLUMNS) {
                    errors.add(new RowError(lineNumber, rawLine,
                        "Sütun sayısı yetersiz: beklenen " + EXPECTED_COLUMNS + ", bulunan " + cols.length));
                    notifyProgress(progressCallback, dataRowNumber, totalRows);
                    continue;
                }

                CreateFlightRequest req;
                try {
                    req = parseRow(cols);
                } catch (IllegalArgumentException | DateTimeParseException ex) {
                    errors.add(new RowError(lineNumber, rawLine, ex.getMessage()));
                    notifyProgress(progressCallback, dataRowNumber, totalRows);
                    continue;
                }

                Set<ConstraintViolation<CreateFlightRequest>> violations = validator.validate(req);
                if (!violations.isEmpty()) {
                    String msg = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining("; "));
                    errors.add(new RowError(lineNumber, rawLine, msg));
                    notifyProgress(progressCallback, dataRowNumber, totalRows);
                    continue;
                }

                try {
                    flightService.create(req);
                    successCount++;
                } catch (RuntimeException ex) {
                    errors.add(new RowError(lineNumber, rawLine, ex.getMessage()));
                }

                notifyProgress(progressCallback, dataRowNumber, totalRows);
            }

        } catch (CsvValidationException ex) {
            errors.add(new RowError(lineNumber, "", "CSV okuma hatası: " + ex.getMessage()));
        }

        return new BulkUploadResult(
            successCount + errors.size(),
            successCount,
            errors.size(),
            errors
        );
    }

    private void notifyProgress(BiConsumer<Integer, Integer> progressCallback, int processed, int total) {
        if (progressCallback != null) {
            progressCallback.accept(processed, total);
        }
    }

    private void sendProgress(String jobId, int processed, int total) {
        try {
            messagingTemplate.convertAndSend(
                    BULK_TOPIC_PREFIX + jobId,
                    Map.of("status", "PROGRESS", "processed", processed, "total", total));
        } catch (Exception ex) {
            log.warn("Bulk upload progress push failed jobId={}: {}", jobId, ex.getMessage());
        }
    }

    private void sendCompleted(String jobId, BulkUploadResult result) {
        try {
            List<String> errorMessages = result.errors().stream()
                    .map(e -> "Satır " + e.rowNumber() + ": " + e.error())
                    .toList();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", "COMPLETED");
            payload.put("totalRows", result.totalRows());
            payload.put("successCount", result.successCount());
            payload.put("failureCount", result.failureCount());
            payload.put("success", result.failureCount() == 0 && result.totalRows() > 0);
            payload.put("errors", errorMessages);

            messagingTemplate.convertAndSend(BULK_TOPIC_PREFIX + jobId, payload);
        } catch (Exception ex) {
            log.warn("Bulk upload summary push failed jobId={}: {}", jobId, ex.getMessage());
        }
    }

    private void sendFailed(String jobId, String message) {
        try {
            messagingTemplate.convertAndSend(
                    BULK_TOPIC_PREFIX + jobId,
                    Map.of("status", "FAILED", "message", message != null ? message : "Bilinmeyen hata"));
        } catch (Exception ex) {
            log.warn("Bulk upload failure push failed jobId={}: {}", jobId, ex.getMessage());
        }
    }

    private CreateFlightRequest parseRow(String[] cols) {
        String flightNumber  = cols[COL_FLIGHT_NUMBER].trim();
        String airlineCode   = cols[COL_AIRLINE_CODE].trim();
        String aircraftTail  = cols[COL_AIRCRAFT_TAIL].trim();
        String origin        = cols[COL_ORIGIN].trim();
        String destination   = cols[COL_DESTINATION].trim();
        String flightTypeRaw = cols[COL_FLIGHT_TYPE].trim();
        String departureRaw  = cols[COL_SCHEDULED_DEPARTURE].trim();
        String arrivalRaw    = cols[COL_SCHEDULED_ARRIVAL].trim();

        FlightType flightType;
        try {
            flightType = FlightType.valueOf(flightTypeRaw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                "Geçersiz flightType: '" + flightTypeRaw + "'. Geçerli değerler: PASSENGER, CARGO, POSITION"
            );
        }

        LocalDateTime departure;
        try {
            departure = LocalDateTime.parse(departureRaw);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                "scheduledDeparture parse hatası: '" + departureRaw + "' — ISO format bekleniyor (yyyy-MM-ddTHH:mm:ss)");
        }

        LocalDateTime arrival;
        try {
            arrival = LocalDateTime.parse(arrivalRaw);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                "scheduledArrival parse hatası: '" + arrivalRaw + "' — ISO format bekleniyor (yyyy-MM-ddTHH:mm:ss)");
        }

        return new CreateFlightRequest(
            flightNumber, airlineCode, aircraftTail,
            origin, destination, flightType,
            departure, arrival
        );
    }
}
