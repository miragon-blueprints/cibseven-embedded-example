package io.miragon.blueprint.domain.leasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CustomerNameTest {

    @Test
    void exposesTheWrappedName() {
        // given/when: a customer name is created from a non-blank value
        CustomerName name = new CustomerName("John Doe");
        // then: the raw value is exposed unchanged
        assertThat(name.value()).isEqualTo("John Doe");
    }

    @Test
    void rejectsAnEmptyName() {
        // when/then: an empty value is refused
        assertThatThrownBy(() -> new CustomerName(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankName() {
        // when/then: a whitespace-only value is refused
        assertThatThrownBy(() -> new CustomerName("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
