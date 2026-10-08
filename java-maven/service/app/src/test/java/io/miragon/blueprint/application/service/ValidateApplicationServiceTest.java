package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValidateApplicationServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ValidateApplicationService underTest = new ValidateApplicationService(repository);

    @Test
    void validateLoadsAWellFormedApplicationWithoutError() {

        // given: a valid, solvent application in the repository
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));

        // when: the application is validated
        underTest.validate(application.id());

        // then: the application was loaded and accepted
        verify(repository).findById(application.id());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void validateRejectsAnApplicationWithoutIncome() {

        // given: an application with zero monthly net income
        LeasingApplication application = testLeasingApplication().monthlyNetIncome(0.0).build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));

        // when / then: validation surfaces the application as invalid
        assertThatThrownBy(() -> underTest.validate(application.id()))
                .isInstanceOf(ApplicationInvalidException.class);
    }
}
