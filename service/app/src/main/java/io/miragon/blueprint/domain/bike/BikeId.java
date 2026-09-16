package io.miragon.blueprint.domain.bike;

/** Identifies the concrete bike a leasing application is about — carried through to the order. */
public record BikeId(String value) {

    public BikeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BikeId must not be blank");
        }
    }
}
