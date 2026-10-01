package io.miragon.blueprint.adapter.process.nativeimage

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/**
 * The engine's auto-deployment scans the classpath for process models at start-up
 * (`camunda.bpm.deployment-resource-pattern`). A native image only contains the resources it was told
 * about, so the model folders of this service are listed here.
 */
class ProcessModelRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        MODEL_PATTERNS.forEach { hints.resources().registerPattern(it) }
    }

    private companion object {
        private val MODEL_PATTERNS = listOf("bpmn/*.bpmn", "dmn/*.dmn", "forms/*.form")
    }
}
