package io.miragon.blueprint.domain.leasing;

/** Reference to the leasing contract issued by the (external) contract system. */
public record ContractId(String value) {

    public ContractId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ContractId must not be blank");
        }
    }
}
