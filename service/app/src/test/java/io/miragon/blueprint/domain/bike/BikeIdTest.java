package io.miragon.blueprint.domain.bike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BikeIdTest {

    @Test
    void exposesTheWrappedBikeId() {
        // given/when: a bike id is created from a non-blank value
        BikeId bikeId = new BikeId("BIKE-900");
        // then: the raw value is exposed unchanged
        assertThat(bikeId.value()).isEqualTo("BIKE-900");
    }

    @Test
    void rejectsABlankBikeId() {
        // when/then: a blank value is refused
        assertThatThrownBy(() -> new BikeId("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
