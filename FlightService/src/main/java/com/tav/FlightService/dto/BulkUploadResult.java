package com.tav.FlightService.dto;

import java.util.List;

public record BulkUploadResult(
    int totalRows,
    int successCount,
    int failureCount,
    List<RowError> errors
) {}
