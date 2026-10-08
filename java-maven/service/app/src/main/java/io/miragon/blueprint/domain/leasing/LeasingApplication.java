package io.miragon.blueprint.domain.leasing;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import java.time.LocalDateTime;

/**
 * Aggregate root of the bike-leasing domain. All state transitions return a copy, so an instance is
 * never mutated in place — the calling service persists the returned copy.
 *
 * <p>{@code orderId} and {@code contractId} may be {@code null} until the process produces them.
 */
public record LeasingApplication(
        ApplicationId id,
        CustomerName customerName,
        Email email,
        int age,
        double monthlyNetIncome,
        BikeId bikeId,
        LeasingStatus status,
        LocalDateTime createdAt,
        OrderId orderId,
        ContractId contractId) {

    public LeasingApplication validate() {
        if (monthlyNetIncome <= 0.0) {
            throw new ApplicationInvalidException(id, "monthly net income must be greater than zero");
        }
        return this;
    }

    public LeasingApplication withContract(ContractId contractId) {
        return new LeasingApplication(
                id, customerName, email, age, monthlyNetIncome, bikeId, status, createdAt, orderId, contractId);
    }

    public LeasingApplication documentOrder(OrderId orderId) {
        return new LeasingApplication(
                id, customerName, email, age, monthlyNetIncome, bikeId, LeasingStatus.ORDERED, createdAt, orderId,
                contractId);
    }

    public LeasingApplication selectAlternative(BikeId bikeId) {
        return new LeasingApplication(
                id, customerName, email, age, monthlyNetIncome, bikeId, status, createdAt, orderId, contractId);
    }

    public LeasingApplication reportHandover() {
        return new LeasingApplication(
                id, customerName, email, age, monthlyNetIncome, bikeId, LeasingStatus.HANDED_OVER, createdAt, orderId,
                contractId);
    }

    public LeasingApplication activate() {
        return new LeasingApplication(
                id, customerName, email, age, monthlyNetIncome, bikeId, LeasingStatus.ACTIVE, createdAt, orderId,
                contractId);
    }

    public LeasingApplication withdraw() {
        return new LeasingApplication(
                id, customerName, email, age, monthlyNetIncome, bikeId, LeasingStatus.WITHDRAWN, createdAt, orderId,
                contractId);
    }

    public LeasingApplication reject() {
        return new LeasingApplication(
                id, customerName, email, age, monthlyNetIncome, bikeId, LeasingStatus.REJECTED, createdAt, orderId,
                contractId);
    }

    public LeasingApplication cancel() {
        return new LeasingApplication(
                id, customerName, email, age, monthlyNetIncome, bikeId, LeasingStatus.CANCELLED, createdAt, orderId,
                contractId);
    }

    public static LeasingApplication receive(
            ApplicationId id,
            CustomerName customerName,
            Email email,
            int age,
            double monthlyNetIncome,
            BikeId bikeId,
            LocalDateTime createdAt) {
        return new LeasingApplication(
                id, customerName, email, age, monthlyNetIncome, bikeId, LeasingStatus.RECEIVED, createdAt, null, null);
    }
}
