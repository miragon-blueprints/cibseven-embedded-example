package io.miragon.blueprint.adapter.outbound.dealer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import org.junit.jupiter.api.Test;

class BikeDealerAdapterTest {

    private final BikeDealerAdapter underTest = new BikeDealerAdapter();

    @Test
    void aBikeOutsideTheOutOfStockListIsAvailable() {
        // given: a regularly stocked bike / when-then: the dealer reports it as available
        assertThat(underTest.checkAvailability(new BikeId("BIKE-900"))).isTrue();
    }

    @Test
    void theOutOfStockDemoBikeIsUnavailable() {
        // given: the sentinel out-of-stock bike / when-then: the dealer reports it as unavailable
        assertThat(underTest.checkAvailability(new BikeId("BIKE-OOS"))).isFalse();
    }

    @Test
    void orderMintsAFreshUniqueOrderId() {
        // given: the same bike ordered twice
        OrderId first = underTest.order(new BikeId("BIKE-900"));
        OrderId second = underTest.order(new BikeId("BIKE-900"));
        // then: each order gets its own opaque reference, independent of the bike id
        assertThat(first.value()).startsWith("ORDER-");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theIncidentDemoBikeStaysAvailableSoTheFlowReachesTheOrderStep() {
        // given: the poison bike used by the incident demo / when-then: it is not on the out-of-stock
        // list, so the process reaches serviceTask_orderBike (where the dealer then fails) instead of
        // taking the out-of-stock branch
        assertThat(underTest.checkAvailability(new BikeId("BIKE-FAIL"))).isTrue();
    }

    @Test
    void orderingTheIncidentDemoBikeFailsAsASimulatedDealerOutage() {
        // given: the poison bike / when-then: the dealer call throws so the async job fails → incident
        assertThatThrownBy(() -> underTest.order(new BikeId("BIKE-FAIL")))
                .isInstanceOf(BikeDealerAdapter.DealerUnavailableException.class);
    }

    @Test
    void requestCancellationReportsTheOrderAsCancellable() {
        // given: a placed order / when-then: the demo dealer always allows cancellation
        assertThat(underTest.requestCancellation(new OrderId("ORDER-900"))).isTrue();
    }

    @Test
    void bookCancellationCostsRunsWithoutError() {
        // given: a placed order / when-then: booking the dealer's costs runs without error
        assertThatCode(() -> underTest.bookCancellationCosts(new OrderId("ORDER-900"))).doesNotThrowAnyException();
    }
}
