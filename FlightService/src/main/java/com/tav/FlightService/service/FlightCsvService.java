package com.tav.FlightService.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.tav.FlightService.domain.Flight;
import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.dto.BulkUploadResult;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.RowError;
import com.tav.FlightService.mapper.FlightMapper;
import com.tav.FlightService.repository.FlightRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FlightCsvService {

    private static final Logger log = LoggerFactory.getLogger(FlightCsvService.class);

    private static final int COL_FLIGHT_NUMBER       = 0;
    private static final int COL_AIRLINE_CODE        = 1;
    private static final int COL_AIRCRAFT_TAIL       = 2;
    private static final int COL_ORIGIN              = 3;
    private static final int COL_DESTINATION         = 4;
    private static final int COL_FLIGHT_TYPE         = 5;
    private static final int COL_SCHEDULED_DEPARTURE = 6;
    private static final int COL_SCHEDULED_ARRIVAL   = 7;
    private static final int EXPECTED_COLUMNS        = 8;

    private final FlightRepository flightRepository;
    private final FlightMapper flightMapper;
    private final FlightService flightService;
    private final Validator validator;

    public FlightCsvService(FlightRepository flightRepository,
                            FlightMapper flightMapper,
                            FlightService flightService,
                            Validator validator) {
        this.flightRepository = flightRepository;
        this.flightMapper = flightMapper;
        this.flightService = flightService;
        this.validator = validator;
    }

    @Transactional
    public BulkUploadResult upload(MultipartFile file) throws IOException {
        List<RowError> errors = new ArrayList<>();
        List<Flight> validFlights = new ArrayList<>();
        // lineNumber: okunan her satır (header dahil) — RowError satır numarası için
        // dataRowNumber: yalnızca veri satırları — totalRows hesabı için
        int lineNumber = 0;
        int dataRowNumber = 0;

        try (CSVReader csvReader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String[] cols;
            while ((cols = csvReader.readNext()) != null) {
                lineNumber++;

                // Başlık satırını atla — dataRowNumber artırılmaz
                if (lineNumber == 1 && cols[0].trim().equalsIgnoreCase("flightNumber")) {
                    continue;
                }

                dataRowNumber++;
                String rawLine = String.join(",", cols);

                if (cols.length < EXPECTED_COLUMNS) {
                    errors.add(new RowError(lineNumber, rawLine,
                        "Sütun sayısı yetersiz: beklenen " + EXPECTED_COLUMNS + ", bulunan " + cols.length));
                    continue;
                }

                // Parse
                CreateFlightRequest req;
                try {
                    req = parseRow(cols);
                } catch (IllegalArgumentException | DateTimeParseException ex) {
                    errors.add(new RowError(lineNumber, rawLine, ex.getMessage()));
                    continue;
                }

                // Format validasyon (Bean Validation)
                Set<ConstraintViolation<CreateFlightRequest>> violations = validator.validate(req);
                if (!violations.isEmpty()) {
                    String msg = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining("; "));
                    errors.add(new RowError(lineNumber, rawLine, msg));
                    continue;
                }

                // İş kuralı ve referans validasyonu
                try {
                    flightService.validateBusinessRules(req);
                    flightService.validateReferences(req);
                } catch (RuntimeException ex) {
                    errors.add(new RowError(lineNumber, rawLine, ex.getMessage()));
                    continue;
                }

                // Duplicate kontrolü (pre-check: saveAll öncesi birincil savunma hattı)
                if (flightRepository.existsByFlightNumberAndScheduledDeparture(
                        req.flightNumber(), req.scheduledDeparture())) {
                    errors.add(new RowError(lineNumber, rawLine,
                        "Duplicate: " + req.flightNumber() + " / " + req.scheduledDeparture() + " zaten mevcut"));
                    continue;
                }

                validFlights.add(flightMapper.toEntity(req));
            }

        } catch (CsvValidationException ex) {
            errors.add(new RowError(lineNumber, "", "CSV okuma hatası: " + ex.getMessage()));
        }

        // Tek transaction'da toplu insert.
        // DataIntegrityViolationException burada BEKLENMİYOR: duplicate kontrolü yukarıda yapıldı.
        // Race condition durumunda exception propagate edilir ve @Transactional tam rollback yapar —
        // bu, @Transactional içinde catch edip sahte partial-success dönmekten daha doğru davranıştır.
        List<Flight> saved = flightRepository.saveAll(validFlights);

        log.info("Bulk upload tamamlandı: toplam={}, başarılı={}, hatalı={}",
            saved.size() + errors.size(), saved.size(), errors.size());

        // totalRows = yalnızca işlenen veri satırları (header satırı HARİÇ)
        // Garantisi: totalRows == successCount + failureCount
        return new BulkUploadResult(
            saved.size() + errors.size(),
            saved.size(),
            errors.size(),
            errors
        );
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
