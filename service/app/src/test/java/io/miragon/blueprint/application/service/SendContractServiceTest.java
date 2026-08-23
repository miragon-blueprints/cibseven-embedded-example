package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.ContractPort;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SendContractServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ContractPort contract = mock(ContractPort.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendContractService underTest =
            new SendContractService(repository, contract, notification);

    @Test
    void sendContractIssuesTheContractRecordsItsIdOnTheApplicationAndNotifiesTheCustomer() {

        // given: an application whose contract the contract system will issue
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(contract.issueContract(application.id())).thenReturn(new ContractId("CONTRACT-1"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the contract is sent
        underTest.sendContract(application.id());

        // then: the contract is issued, its id is stored on the application and the customer is asked to sign
        verify(repository).findById(application.id());
        verify(contract).issueContract(application.id());
        verify(repository).save(argThat(app -> new ContractId("CONTRACT-1").equals(app.contractId())));
        verify(notification).send(any(), eq(application));
        verifyNoMoreInteractions(repository, contract, notification);
    }
}
