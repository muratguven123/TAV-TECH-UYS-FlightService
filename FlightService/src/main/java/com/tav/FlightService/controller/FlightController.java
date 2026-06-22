package com.tav.FlightService.controller;

import com.tav.FlightService.dto.BulkUploadResult;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.dto.UpdateFlightRequest;
import com.tav.FlightService.service.FlightCsvService;
import com.tav.FlightService.service.FlightService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/flights")
public class FlightController {

    private final FlightService flightService;
    private final FlightCsvService flightCsvService;

    public FlightController(FlightService flightService, FlightCsvService flightCsvService) {
        this.flightService = flightService;
        this.flightCsvService = flightCsvService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OPERATION_OFFICER')")
    public FlightResponse createFlight(@Valid @RequestBody CreateFlightRequest request) {
        return flightService.create(request);
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OPERATION_OFFICER')")
    public BulkUploadResult bulkUpload(@RequestParam("file") MultipartFile file) throws IOException {
        return flightCsvService.upload(file);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OPERATION_OFFICER')")
    public FlightResponse updateFlight(@PathVariable Long id,
                                       @Valid @RequestBody UpdateFlightRequest request) {
        return flightService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OPERATION_OFFICER')")
    public ResponseEntity<Void> deleteFlight(@PathVariable Long id) {
        flightService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
