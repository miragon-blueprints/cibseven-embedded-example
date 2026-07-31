package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository
import io.miragon.blueprint.application.port.outbound.NotificationPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Service

@Service
class SendContractService(
    private val repository: LeasingApplicationRepository,
    private val notification: NotificationPort,
) : SendContractUseCase {

    override fun sendContract(id: ApplicationId) {
        val application = repository.findById(id) ?: error("Unknown application $id")
        notification.send("Please review and sign your leasing contract", application)
    }
}
