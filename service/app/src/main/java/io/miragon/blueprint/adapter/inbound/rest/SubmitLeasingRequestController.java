package io.miragon.blueprint.adapter.inbound.rest;

import io.miragon.blueprint.application.port.inbound.SubmitLeasingRequestUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bike-leasing")
public class SubmitLeasingRequestController {

    private static final Logger log = LoggerFactory.getLogger(SubmitLeasingRequestController.class);

    private final SubmitLeasingRequestUseCase useCase;

    public SubmitLeasingRequestController(SubmitLeasingRequestUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(operationId = "submitLeasingRequest")
    @PostMapping
    public ResponseEntity<LeasingApplicationCreatedDto> submit(@RequestBody LeasingRequestInput input) {
        log.debug("Received leasing request: {}", input);
        ApplicationId id = useCase.submit(toCommand(input));
        return ResponseEntity.ok(new LeasingApplicationCreatedDto(id.value().toString()));
    }

    private static SubmitLeasingRequestUseCase.Command toCommand(LeasingRequestInput input) {
        return new SubmitLeasingRequestUseCase.Command(
                new CustomerName(input.customerName()),
                new Email(input.email()),
                input.age(),
                input.monthlyNetIncome(),
                new BikeId(input.bikeId()),
                input.bikeModel());
    }

    public record LeasingRequestInput(
            String customerName,
            String email,
            int age,
            double monthlyNetIncome,
            String bikeId,
            String bikeModel) {
    }

    public record LeasingApplicationCreatedDto(String applicationId) {
    }
}
