package io.miragon.common.architecture;

import org.junit.jupiter.api.Nested;

/**
 * A single, ready-to-use ArchUnit architecture suite for a service. A service wires up the full suite
 * with one class:
 *
 * <pre>{@code
 * class ArchitectureTest extends ServiceArchitectureTest {
 *     protected String rootPackage() { return "io.miragon.blueprint"; }
 * }
 * }</pre>
 *
 * <p>ArchUnit reads compiled <strong>bytecode</strong>, so it sees the fully resolved dependency
 * graph. It owns the dependency &amp; structure rules ({@link HexagonalArchitectureTest}), naming
 * conventions ({@link NamingConventionArchitectureTest}), freedom of cycles and the no-{@code println}
 * check ({@link BasicCodingGuidelinesTest}). The two source-structure guidelines Konsist used to own
 * (no wildcard imports, one top-level type per file) are enforced by Checkstyle in the Maven build.
 *
 * <p>This module is <strong>self-contained</strong>: it carries the ArchUnit dependency and its own
 * copies of the rules, so it can be dropped into a service as a single test dependency.
 */
public abstract class ServiceArchitectureTest {

    protected abstract String rootPackage();

    @Nested
    class Dependencies extends HexagonalArchitectureTest {
        @Override
        protected String rootPackage() {
            return ServiceArchitectureTest.this.rootPackage();
        }
    }

    @Nested
    class Naming extends NamingConventionArchitectureTest {
        @Override
        protected String rootPackage() {
            return ServiceArchitectureTest.this.rootPackage();
        }
    }

    @Nested
    class CodingGuidelines extends BasicCodingGuidelinesTest {
        @Override
        protected String rootPackage() {
            return ServiceArchitectureTest.this.rootPackage();
        }
    }
}
