package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderBikeServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikeDealerPort bikeDealer = mock(BikeDealerPort.class);
    private final OrderBikeService underTest = new OrderBikeService(repository, bikeDealer);

    @Test
    void orderBikePlacesAnOrderWhenTheDealerHasTheBikeInStock() {

        // given: an application whose bike is available at the dealer
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-900")).build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikeDealer.checkAvailability(application.bikeId())).thenReturn(true);
        when(bikeDealer.order(application.bikeId())).thenReturn(new OrderId("ORDER-900"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the bike is ordered
        OrderId orderId = underTest.orderBike(application.id(), application.bikeId());

        // then: the order id is returned and the application moves to ORDERED
        assertThat(orderId).isEqualTo(new OrderId("ORDER-900"));
        verify(bikeDealer).checkAvailability(application.bikeId());
        verify(bikeDealer).order(application.bikeId());
        verify(repository).save(argThat(app ->
                app.status() == LeasingStatus.ORDERED && app.orderId().equals(new OrderId("ORDER-900"))));
        verifyNoMoreInteractions(bikeDealer);
    }

    @Test
    void orderBikeOrdersTheBikeTheProcessCarriesAndStoresItOnTheApplication() {

        // given: an application still pointing at the bike that was requested first
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-OOS")).build();
        BikeId alternative = new BikeId("BIKE-ALT");
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikeDealer.checkAvailability(alternative)).thenReturn(true);
        when(bikeDealer.order(alternative)).thenReturn(new OrderId("ORDER-ALT"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the process asks for the alternative bike
        OrderId orderId = underTest.orderBike(application.id(), alternative);

        // then: the alternative is checked, ordered and stored together with the order
        assertThat(orderId).isEqualTo(new OrderId("ORDER-ALT"));
        verify(bikeDealer).checkAvailability(alternative);
        verify(bikeDealer).order(alternative);
        verify(repository).save(argThat(app ->
                app.bikeId().equals(alternative) && new OrderId("ORDER-ALT").equals(app.orderId())));
        verifyNoMoreInteractions(bikeDealer);
    }

    @Test
    void orderBikeReportsAnOutOfStockBikeAsUnavailableAndPlacesNoOrder() {

        // given: an application whose bike is out of stock at the dealer
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-OOS")).build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikeDealer.checkAvailability(application.bikeId())).thenReturn(false);

        // when / then: ordering reports the bike as unavailable and places no order
        assertThatThrownBy(() -> underTest.orderBike(application.id(), application.bikeId()))
                .isInstanceOf(BikeUnavailableException.class)
                .hasMessage("Bike BIKE-OOS is not available at the dealer");
        verify(bikeDealer).checkAvailability(application.bikeId());
        verify(bikeDealer, never()).order(any());
        verify(repository, never()).save(argThat(app -> app.orderId() != null));
        verifyNoMoreInteractions(bikeDealer);
    }

    @Test
    void orderBikeKeepsAnUnavailableAlternativeOnTheApplication() {

        // given: an application whose alternative is out of stock as well
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-900")).build();
        BikeId alternative = new BikeId("BIKE-OOS");
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikeDealer.checkAvailability(alternative)).thenReturn(false);

        // when / then: the alternative is reported as unavailable, yet the application points at it
        assertThatThrownBy(() -> underTest.orderBike(application.id(), alternative))
                .isInstanceOf(BikeUnavailableException.class)
                .hasMessage("Bike BIKE-OOS is not available at the dealer");
        verify(repository).save(argThat(app -> app.bikeId().equals(alternative) && app.orderId() == null));
        verify(bikeDealer, never()).order(any());
    }
}
