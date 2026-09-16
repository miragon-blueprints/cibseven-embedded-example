package io.miragon.common.architecture;

import static com.tngtech.archunit.lang.conditions.ArchConditions.haveSimpleNameEndingWith;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Naming conventions per hexagonal layer — a simple bytecode name check, so ArchUnit owns it.
 *
 * <p>Each rule lists the class-name suffixes allowed in a given package with a short rationale via
 * {@link AllowedSuffix}, so the file doubles as living documentation. Packages a given service does
 * not have simply match no classes ({@code allowEmptyShould(true)}), so the same rule set fits every
 * service. The generated {@code adapter.process} package is intentionally left unchecked.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class NamingConventionArchitectureTest {

    protected abstract String rootPackage();

    private JavaClasses productionClasses;

    private JavaClasses productionClasses() {
        if (productionClasses == null) {
            productionClasses = new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages(rootPackage());
        }
        return productionClasses;
    }

    @Test
    void inboundPortsAreUseCasesOrQueries() {
        checkNaming("..application.port.inbound", List.of(
                new AllowedSuffix("UseCase", "inbound port for a state-changing operation"),
                new AllowedSuffix("Query", "inbound port for a read-only operation")));
    }

    @Test
    void outboundPortsArePortsOrRepositories() {
        checkNaming("..application.port.outbound", List.of(
                new AllowedSuffix("Port", "generic outbound port delegating to infrastructure"),
                new AllowedSuffix("Repository", "outbound port for persistence access"),
                new AllowedSuffix("Process", "outbound port driving the process engine")));
    }

    @Test
    void applicationServicesAreServices() {
        checkNaming("..application.service", List.of(
                new AllowedSuffix("Service", "orchestrates a use case and owns the transaction boundary"),
                new AllowedSuffix("Configuration", "Spring configuration for the application layer")));
    }

    @Test
    void restAdaptersFollowNamingConventions() {
        checkNaming("..adapter.inbound.rest", List.of(
                new AllowedSuffix("Controller", "Spring MVC REST controller"),
                new AllowedSuffix("Dto", "REST response type outside the domain model"),
                new AllowedSuffix("Input", "REST request type"),
                new AllowedSuffix("Mapper", "translates between REST DTOs and domain types"),
                new AllowedSuffix("Configuration", "Spring web configuration for the REST adapter (CORS, OpenAPI metadata)")));
    }

    @Test
    void cibsevenAdaptersFollowNamingConventions() {
        checkNaming("..adapter.inbound.cibseven", List.of(
                new AllowedSuffix("Delegate", "JavaDelegate invoked by a BPMN service task"),
                new AllowedSuffix("Worker", "external-task worker subscribed to a BPMN topic"),
                new AllowedSuffix("Listener", "ExecutionListener/TaskListener invoked by a BPMN element")));
    }

    @Test
    void outboundAdaptersFollowNamingConventions() {
        checkNaming("..adapter.outbound", List.of(
                new AllowedSuffix("PersistenceAdapter", "primary outbound adapter implementing out-ports"),
                new AllowedSuffix("Adapter", "outbound adapter adapting to infrastructure"),
                new AllowedSuffix("Mapper", "translates between infrastructure types and domain objects"),
                new AllowedSuffix("Entity", "JPA entity mapped to a database table"),
                new AllowedSuffix("Repository", "Spring Data repository backing a persistence adapter")));
    }

    private void checkNaming(String packageRoot, List<AllowedSuffix> allowedSuffixes) {
        if (allowedSuffixes.isEmpty()) {
            throw new IllegalArgumentException("allowedSuffixes must not be empty");
        }
        ArchCondition<JavaClass> nameCondition = allowedSuffixes.stream()
                .map(suffix -> haveSimpleNameEndingWith(suffix.suffix()))
                .reduce(ArchCondition::or)
                .orElseThrow();

        ArchRuleDefinition.classes()
                .that().resideInAPackage(packageRoot + "..")
                .and().areTopLevelClasses()
                .and().areNotAnonymousClasses()
                .and().haveSimpleNameNotEndingWith("Kt")
                .and().haveSimpleNameNotEndingWith("_")
                .and().haveSimpleNameNotContaining("$")
                .should(nameCondition)
                .allowEmptyShould(true)
                .check(productionClasses());
    }

    /**
     * An allowed class-name suffix together with a short rationale.
     * The {@code reason} documents <em>why</em> a suffix is allowed; it is not part of ArchUnit's
     * failure message.
     */
    public record AllowedSuffix(String suffix, String reason) {
    }
}
