package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RejectApplicationServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final RejectApplicationService underTest = new RejectApplicationService(repository, notification);

    @Test
    void rejectNotifiesTheCustomerAndPersistsTheRejectedStatus() {

        // given: an application in the repository
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the application is rejected
        underTest.reject(application.id());

        // then: the application is loaded, the customer notified and the application saved as REJECTED
        verify(repository).findById(application.id());
        verify(notification).send(any(), eq(application));
        verify(repository).save(argThat(app -> app.status() == LeasingStatus.REJECTED));
        verifyNoMoreInteractions(repository, notification);
    }
}
