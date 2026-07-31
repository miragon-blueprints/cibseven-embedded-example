package io.miragon.blueprint.adapter.outbound.contract

import io.miragon.blueprint.domain.leasing.testLeasingApplication
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

class LoggingContractAdapterTest {

    private val underTest = LoggingContractAdapter()

    @Test
    fun `revokeContract logs without error`() {
        // given: an application / when-then: revoking the contract runs without error
        assertThatCode { underTest.revokeContract(testLeasingApplication().id) }.doesNotThrowAnyException()
    }
}
