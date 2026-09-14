package io.miragon.blueprint.domain.leasing;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import org.junit.jupiter.api.Test;

class LeasingApplicationTest {

    @Test
    void exposesTheApplicantsAgeAndMonthlyNetIncome() {
        // given: an application built with a specific age and income
        LeasingApplication application = testLeasingApplication().age(40).monthlyNetIncome(4200.0).build();
        // then: both fields are exposed unchanged
        assertThat(application.age()).isEqualTo(40);
        assertThat(application.monthlyNetIncome()).isEqualTo(4200.0);
    }

    @Test
    void documentOrderAttachesTheOrderIdAndMovesToOrdered() {
        // given: a received application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.RECEIVED).build();
        // when: a bike order is attached
        LeasingApplication ordered = application.documentOrder(new OrderId("ORDER-1"));
        // then: the order id is set and the status is ORDERED
        assertThat(ordered).isEqualTo(
                testLeasingApplication().status(LeasingStatus.ORDERED).orderId(new OrderId("ORDER-1")).build());
    }

    @Test
    void selectAlternativeSwapsInTheNewlyChosenBike() {
        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-900")).build();
        // when: the customer accepts an alternative bike
        LeasingApplication updated = application.selectAlternative(new BikeId("BIKE-ALT"));
        // then: the chosen bike is recorded
        assertThat(updated.bikeId()).isEqualTo(new BikeId("BIKE-ALT"));
    }

    @Test
    void withContractRecordsTheIssuedContract() {
        // given: an application without a contract yet
        LeasingApplication application = testLeasingApplication().build();
        // when: the contract system issues a contract
        LeasingApplication updated = application.withContract(new ContractId("CONTRACT-1"));
        // then: the contract id is recorded
        assertThat(updated.contractId()).isEqualTo(new ContractId("CONTRACT-1"));
    }

    @Test
    void rejectChangesTheStatusToRejected() {
        // given: a received application
        LeasingApplication application = testLeasingApplication().build();
        // when: it is rejected
        LeasingApplication rejected = application.reject();
        // then: the status is REJECTED
        assertThat(rejected.status()).isEqualTo(LeasingStatus.REJECTED);
    }

    @Test
    void withdrawMovesTheApplicationToWithdrawn() {
        // given: a handed-over application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.HANDED_OVER).build();
        // when: the customer withdraws
        LeasingApplication withdrawn = application.withdraw();
        // then: the status is WITHDRAWN
        assertThat(withdrawn.status()).isEqualTo(LeasingStatus.WITHDRAWN);
    }

    @Test
    void reportHandoverMovesTheApplicationToHandedOver() {
        // given: an ordered application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.ORDERED).build();
        // when: the handover is reported
        LeasingApplication handedOver = application.reportHandover();
        // then: the status is HANDED_OVER
        assertThat(handedOver.status()).isEqualTo(LeasingStatus.HANDED_OVER);
    }

    @Test
    void activateMovesTheApplicationToActive() {
        // given: a handed-over application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.HANDED_OVER).build();
        // when: the leasing is activated
        LeasingApplication active = application.activate();
        // then: the status is ACTIVE
        assertThat(active.status()).isEqualTo(LeasingStatus.ACTIVE);
    }

    @Test
    void validateFailsWhenTheMonthlyNetIncomeIsZero() {
        // given: an application without income
        LeasingApplication application = testLeasingApplication().monthlyNetIncome(0.0).build();
        // when / then: validation reports the application as invalid
        assertThatThrownBy(application::validate).isInstanceOf(ApplicationInvalidException.class);
    }
}
