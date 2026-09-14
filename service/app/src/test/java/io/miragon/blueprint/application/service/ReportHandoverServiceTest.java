package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportHandoverServiceTest {

    private final LeasingProcess process = mock(LeasingProcess.class);
    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ReportHandoverService underTest = new ReportHandoverService(process, repository);

    @Test
    void reportHandoverCorrelatesTheMessageAndPersistsTheHandedOverStatus() {

        // given: an ordered application
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        LeasingApplication application = testLeasingApplication().id(id).status(LeasingStatus.ORDERED).build();
        when(repository.findById(id)).thenReturn(Optional.of(application));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the handover is reported
        underTest.reportHandover(id);

        // then: the message is correlated and the application is persisted with HANDED_OVER
        verify(process).correlateHandoverReported(id);
        verify(repository).findById(id);
        verify(repository).save(argThat(app -> app.status() == LeasingStatus.HANDED_OVER));
        verifyNoMoreInteractions(process, repository);
    }

    @Test
    void reportHandoverDoesNotPersistWhenCorrelationFails() {

        // given: a correlation that throws
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174001"));
        doThrow(new RuntimeException("no token")).when(process).correlateHandoverReported(id);

        // when / then: the exception propagates without touching the repository
        try {
            underTest.reportHandover(id);
        } catch (RuntimeException ignored) {
        }

        verify(process).correlateHandoverReported(id);
        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
        verifyNoMoreInteractions(process, repository);
    }
}
