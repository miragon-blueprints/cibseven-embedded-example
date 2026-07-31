package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.application.port.outbound.ContractPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Service

@Service
class CancelContractService(
    private val contract: ContractPort,
) : CancelContractUseCase {

    override fun cancelContract(id: ApplicationId) = contract.revokeContract(id)
}
