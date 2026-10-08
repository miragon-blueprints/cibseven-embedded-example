package io.miragon.blueprint.adapter.outbound.db;

import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class BikePortfolioPersistenceAdapter implements BikePortfolioRepository {

    private final BikePortfolioJpaRepository repository;

    public BikePortfolioPersistenceAdapter(BikePortfolioJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Bike save(Bike bike) {
        return toDomain(repository.save(new BikeEntity(bike.bikeId().value(), bike.model())));
    }

    @Override
    public Optional<Bike> findByBikeId(BikeId bikeId) {
        return repository.findById(bikeId.value()).map(BikePortfolioPersistenceAdapter::toDomain);
    }

    @Override
    public List<Bike> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "bikeId")).stream()
                .map(BikePortfolioPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Bike> findAllByIds(List<BikeId> bikeIds) {
        if (bikeIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = bikeIds.stream().map(BikeId::value).toList();
        return repository.findAllById(ids).stream()
                .map(BikePortfolioPersistenceAdapter::toDomain)
                .toList();
    }

    private static Bike toDomain(BikeEntity entity) {
        return new Bike(new BikeId(entity.getBikeId()), entity.getModel());
    }
}
