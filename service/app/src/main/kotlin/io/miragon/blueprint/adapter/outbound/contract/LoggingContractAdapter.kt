package io.miragon.blueprint.adapter.outbound.contract

import io.miragon.blueprint.application.port.outbound.ContractPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Blueprint contract adapter — logs instead of calling a real contract system. Swap this for a real
 * integration without touching the application layer.
 */
@Component
class LoggingContractAdapter : ContractPort {

    private val log = KotlinLogging.logger {}

    override fun revokeContract(id: ApplicationId) {
        // A real system would revoke the issued contract here.
        log.info { "Revoking contract for application ${id.value}" }
    }
}
