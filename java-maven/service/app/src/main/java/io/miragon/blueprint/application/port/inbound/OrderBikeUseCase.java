package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;

public interface OrderBikeUseCase {

    Result orderBike(ApplicationId id);

    /** {@code orderId} is {@code null} when the requested bike was out of stock and no order was placed. */
    record Result(OrderId orderId, boolean bikeAvailable) {
    }
}
