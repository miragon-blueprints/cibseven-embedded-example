package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.Optional;

public interface GetLeasingApplicationQuery {

    Optional<Result> byId(ApplicationId id);

    /**
     * The application together with the model of its bike, resolved from the portfolio.
     * {@code bikeModel} may be {@code null} when the bike is not in the portfolio.
     */
    record Result(LeasingApplication application, String bikeModel) {
    }
}
