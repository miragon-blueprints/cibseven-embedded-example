package io.miragon.blueprint.domain.bike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderIdTest {

    @Test
    void exposesTheWrappedOrderId() {
        // given/when: an order id is created from a non-blank value
        OrderId orderId = new OrderId("ORDER-1");
        // then: the raw value is exposed unchanged
        assertThat(orderId.value()).isEqualTo("ORDER-1");
    }

    @Test
    void rejectsABlankOrderId() {
        // when/then: a blank value is refused
        assertThatThrownBy(() -> new OrderId("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
