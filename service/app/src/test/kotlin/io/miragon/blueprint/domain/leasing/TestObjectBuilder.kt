package io.miragon.blueprint.domain.leasing

import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.OrderId
import java.util.UUID

/** Shared test builder — start from a valid, solvent application and override only what a test cares about. */
fun testLeasingApplication(
    id: ApplicationId = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
    customerName: CustomerName = CustomerName("John Doe"),
    email: Email = Email("john.doe@test.com"),
    age: Int = 35,
    monthlyNetIncome: Double = 3500.0,
    bikeId: BikeId = BikeId("BIKE-900"),
    status: LeasingStatus = LeasingStatus.RECEIVED,
    orderId: OrderId? = null,
    contractId: ContractId? = null,
) = LeasingApplication(
    id = id,
    customerName = customerName,
    email = email,
    age = age,
    monthlyNetIncome = monthlyNetIncome,
    bikeId = bikeId,
    status = status,
    orderId = orderId,
    contractId = contractId,
)
