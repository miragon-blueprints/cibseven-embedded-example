package io.miragon.blueprint.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.miragon.blueprint.application.port.inbound.GetLeasingApplicationQuery;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bike-leasing")
public class GetLeasingApplicationController {

    private final GetLeasingApplicationQuery query;

    public GetLeasingApplicationController(GetLeasingApplicationQuery query) {
        this.query = query;
    }

    @Operation(operationId = "getLeasingApplication")
    @GetMapping("/{applicationId}")
    public ResponseEntity<LeasingApplicationDto> byId(@PathVariable String applicationId) {
        return query.byId(ApplicationId.of(applicationId))
                .map(result -> ResponseEntity.ok(toDto(result)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record LeasingApplicationDto(
            String applicationId,
            String customerName,
            String email,
            int age,
            double monthlyNetIncome,
            String bikeId,
            String bikeModel,
            String status,
            String orderId,
            String contractId,
            // Force ISO-8601 string form: Jackson 3 (SB4) defaults to a numeric array, but the CIB seven
            // webapp serves /api with its own Jackson mapper that ignores our global date-time config, so
            // the format is pinned at the field to keep the payload in sync with the springdoc contract.
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            LocalDateTime createdAt) {
    }

    private static LeasingApplicationDto toDto(GetLeasingApplicationQuery.Result result) {
        LeasingApplication application = result.application();
        return new LeasingApplicationDto(
                application.id().value().toString(),
                application.customerName().value(),
                application.email().value(),
                application.age(),
                application.monthlyNetIncome(),
                application.bikeId().value(),
                // resolved from the bike portfolio, not carried on the application
                result.bikeModel(),
                application.status().name(),
                application.orderId() != null ? application.orderId().value() : null,
                application.contractId() != null ? application.contractId().value() : null,
                application.createdAt());
    }
}
