package io.miragon.blueprint.domain.leasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ContractIdTest {

    @Test
    void exposesTheWrappedContractId() {
        // given/when: a contract id is created from a non-blank value
        ContractId contractId = new ContractId("CONTRACT-1");
        // then: the raw value is exposed unchanged
        assertThat(contractId.value()).isEqualTo("CONTRACT-1");
    }

    @Test
    void rejectsABlankContractId() {
        // when/then: a blank value is refused
        assertThatThrownBy(() -> new ContractId("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
