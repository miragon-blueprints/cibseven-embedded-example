package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.inbound.SelectAlternativeUseCase;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SelectAlternativeServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final LeasingProcess process = mock(LeasingProcess.class);
    private final SelectAlternativeService underTest =
            new SelectAlternativeService(repository, bikePortfolio, process);

    @Test
    void anAcceptedAlternativeRegistersTheNewBikePointsTheApplicationAtItAndCompletesTheTask() {

        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikePortfolio.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: an alternative bike is selected
        underTest.selectAlternative(
                new SelectAlternativeUseCase.Command(application.id(), true, new BikeId("BIKE-ALT"), "Aero Road 700"));

        // then: the alternative is registered in the portfolio, the application points at it and the task is completed
        verify(repository).findById(application.id());
        verify(bikePortfolio).save(new Bike(new BikeId("BIKE-ALT"), "Aero Road 700"));
        verify(repository).save(argThat(app -> app.bikeId().equals(new BikeId("BIKE-ALT"))));
        verify(process).completeAlternativeClarification(application.id(), true, new BikeId("BIKE-ALT"));
        verifyNoMoreInteractions(repository, bikePortfolio, process);
    }

    @Test
    void noAlternativeCompletesTheUserTaskWithoutTouchingTheBike() {

        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));

        // when: no alternative is found
        underTest.selectAlternative(new SelectAlternativeUseCase.Command(application.id(), false, null, null));

        // then: neither the portfolio nor the application is touched, and the task is completed as declined
        verify(repository).findById(application.id());
        verify(process).completeAlternativeClarification(application.id(), false, null);
        verifyNoMoreInteractions(repository, bikePortfolio, process);
    }
}
