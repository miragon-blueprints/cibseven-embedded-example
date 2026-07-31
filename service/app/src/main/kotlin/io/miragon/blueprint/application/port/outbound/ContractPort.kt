package io.miragon.blueprint.application.port.outbound

import io.miragon.blueprint.domain.leasing.ApplicationId

/**
 * Outbound port to the contract system: revokes a previously issued leasing contract as part of the
 * SAGA compensation. (Sending the contract to the customer is a customer notification, so it lives on
 * [NotificationPort]; this port is the back-office contract action.)
 */
interface ContractPort {
    fun revokeContract(id: ApplicationId)
}
