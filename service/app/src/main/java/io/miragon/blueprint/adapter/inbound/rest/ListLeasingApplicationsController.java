package io.miragon.blueprint.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.miragon.blueprint.application.port.inbound.ListLeasingApplicationsQuery;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/bike-leasing?status=&page=&size=} — the customer-portal list. A separate controller
 * from the paging query keeps to the "one inbound port per controller" rule the architecture tests
 * enforce.
 */
@RestController
@RequestMapping("/api/bike-leasing")
public class ListLeasingApplicationsController {

    private final ListLeasingApplicationsQuery query;

    public ListLeasingApplicationsController(ListLeasingApplicationsQuery query) {
        this.query = query;
    }

    @Operation(operationId = "listLeasingApplications")
    @GetMapping
    public LeasingApplicationPageDto list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ListLeasingApplicationsQuery.Filter filter = new ListLeasingApplicationsQuery.Filter(
                status != null ? parseStatus(status) : null,
                page,
                size);
        return toDto(query.list(filter));
    }

    private LeasingStatus parseStatus(String raw) {
        try {
            return LeasingStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "unknown status '" + raw + "'; expected one of "
                            + Arrays.stream(LeasingStatus.values()).map(Enum::name).collect(Collectors.joining(", ")),
                    e);
        }
    }

    private static LeasingApplicationPageDto toDto(ListLeasingApplicationsQuery.Page page) {
        return new LeasingApplicationPageDto(
                page.items().stream().map(ListLeasingApplicationsController::toDto).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }

    private static LeasingApplicationSummaryDto toDto(ListLeasingApplicationsQuery.Item item) {
        return new LeasingApplicationSummaryDto(
                item.applicationId().value().toString(),
                item.customerName().value(),
                item.bikeId().value(),
                item.bikeModel(),
                item.status().name(),
                item.createdAt());
    }

    public record LeasingApplicationPageDto(
            List<LeasingApplicationSummaryDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record LeasingApplicationSummaryDto(
            String applicationId,
            String customerName,
            String bikeId,
            String bikeModel,
            String status,
            // ISO-8601 string — see the note in GetLeasingApplicationController.LeasingApplicationDto.
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            LocalDateTime createdAt) {
    }
}
