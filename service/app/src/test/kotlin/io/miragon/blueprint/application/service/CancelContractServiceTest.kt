package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.outbound.ContractPort
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CancelContractServiceTest {

    private val contract = mockk<ContractPort>()
    private val underTest = CancelContractService(contract = contract)

    @Test
    fun `cancelContract delegates the compensation to the contract system`() {
        // given: an application whose contract must be revoked
        val application = testLeasingApplication()
        every { contract.revokeContract(application.id) } just Runs
        // when: the contract is cancelled
        underTest.cancelContract(application.id)
        // then: the contract out-port revokes the contract
        verify { contract.revokeContract(application.id) }
        confirmVerified(contract)
    }
}
