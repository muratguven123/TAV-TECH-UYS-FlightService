package com.tav.FlightService.controller;

import com.tav.FlightService.dto.BulkUploadJobResponse;
import com.tav.FlightService.dto.BulkUploadResult;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.dto.UpdateFlightRequest;
import com.tav.FlightService.service.FlightCsvService;
import com.tav.FlightService.service.FlightService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/flights")
public class FlightController {

    private final FlightService flightService;
    private final FlightCsvService flightCsvService;

    public FlightController(FlightService flightService, FlightCsvService flightCsvService) {
        this.flightService = flightService;
        this.flightCsvService = flightCsvService;
    }

    // ------------------------------------------------------------------ READ

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATION_OFFICER', 'BI_SPECIALIST', 'ADMIN')")
    public List<FlightResponse> listFlights(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate flightDate) {
        if (flightDate != null) {
            return flightService.findByFlightDate(flightDate);
        }
        return flightService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATION_OFFICER', 'BI_SPECIALIST', 'ADMIN')")
    public FlightResponse getFlight(@PathVariable Long id) {
        return flightService.findById(id);
    }

    @GetMapping("/sorted")
    @PreAuthorize("hasAnyRole('OPERATION_OFFICER', 'BI_SPECIALIST', 'ADMIN')")
    public List<FlightResponse> getSortedFlights(
            @RequestParam(defaultValue = "50") @Max(200) int limit) {
        return flightService.getFlightsSortedByDeparture(limit);
    }

    // ----------------------------------------------------------------- WRITE

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OPERATION_OFFICER', 'ADMIN')")
    public FlightResponse createFlight(@Valid @RequestBody CreateFlightRequest request) {
        return flightService.create(request);
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OPERATION_OFFICER', 'ADMIN')")
    public ResponseEntity<BulkUploadJobResponse> bulkUpload(@RequestParam("file") MultipartFile file)
            throws IOException {
        String jobId = flightCsvService.submitBulkUpload(file);
        return ResponseEntity.accepted().body(new BulkUploadJobResponse(jobId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATION_OFFICER', 'ADMIN')")
    public FlightResponse updateFlight(@PathVariable Long id,
                                       @Valid @RequestBody UpdateFlightRequest request) {
        return flightService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATION_OFFICER', 'ADMIN')")
    public ResponseEntity<Void> deleteFlight(@PathVariable Long id) {
        flightService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
