package io.miragon.blueprint.domain.leasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void exposesAValidEmailAddress() {
        // given/when: an email is created from a well-formed address
        Email email = new Email("john.doe@test.com");
        // then: the raw value is exposed unchanged
        assertThat(email.value()).isEqualTo("john.doe@test.com");
    }

    @Test
    void rejectsAMalformedAddress() {
        // when/then: a value that is not an email is refused
        assertThatThrownBy(() -> new Email("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnEmptyAddress() {
        // when/then: an empty value is refused
        assertThatThrownBy(() -> new Email(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
