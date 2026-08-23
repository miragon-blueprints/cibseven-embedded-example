package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads a page of leasing applications for the customer-portal list, optionally filtered by status.
 * The port owns its {@link Filter}/{@link Item}/{@link Page} types; Spring Data paging never crosses
 * into the application layer.
 */
public interface ListLeasingApplicationsQuery {

    Page list(Filter filter);

    /** {@code status} may be {@code null} to list applications of every status. */
    record Filter(LeasingStatus status, int page, int size) {
    }

    /** {@code bikeModel} may be {@code null} when the bike is not in the portfolio. */
    record Item(
            ApplicationId applicationId,
            CustomerName customerName,
            BikeId bikeId,
            String bikeModel,
            LeasingStatus status,
            LocalDateTime createdAt) {
    }

    record Page(
            List<Item> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
