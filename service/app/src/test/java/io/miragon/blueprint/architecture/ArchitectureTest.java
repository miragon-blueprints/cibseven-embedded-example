package io.miragon.blueprint.architecture;

import io.miragon.common.architecture.ServiceArchitectureTest;

/**
 * Runs the reusable ArchUnit suite against this service's base package.
 * The rules live in the {@code common-architecture-tests} module.
 */
class ArchitectureTest extends ServiceArchitectureTest {

    @Override
    protected String rootPackage() {
        return "io.miragon.blueprint";
    }
}
