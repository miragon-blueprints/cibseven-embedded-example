package io.miragon.blueprint.adapter.outbound.db

import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.blueprint.domain.bike.OrderId

object LeasingApplicationEntityMapper {

    fun toDomain(entity: LeasingApplicationEntity): LeasingApplication =
        LeasingApplication(
            id = ApplicationId(entity.applicationId),
            customerName = CustomerName(entity.customerName),
            email = Email(entity.email),
            age = entity.age,
            monthlyNetIncome = entity.monthlyNetIncome,
            bikeId = BikeId(entity.bikeId),
            status = entity.status,
            orderId = entity.orderId?.let { OrderId(it) },
        )

    fun toEntity(domain: LeasingApplication): LeasingApplicationEntity =
        LeasingApplicationEntity(
            applicationId = domain.id.value,
            customerName = domain.customerName.value,
            email = domain.email.value,
            age = domain.age,
            monthlyNetIncome = domain.monthlyNetIncome,
            bikeId = domain.bikeId.value,
            status = domain.status,
            orderId = domain.orderId?.value,
        )
}
