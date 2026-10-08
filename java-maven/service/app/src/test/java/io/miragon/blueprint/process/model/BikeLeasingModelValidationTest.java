package io.miragon.blueprint.process.model;

import io.miragon.bpmn.domain.shared.ProcessEngine;
import io.miragon.bpmn.domain.validation.ValidationRule;
import io.miragon.bpmn.testing.BpmnRules;
import io.miragon.bpmn.testing.BpmnValidator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Validates the BPMN models themselves (structure, not behaviour) with the {@code bpmn-to-code-testing}
 * rule engine: all built-in rules ({@link BpmnRules#all()}) plus the custom
 * {@link ServiceTaskDelegateExpressionRule}. Runs at build time from the classpath — no engine
 * required.
 */
class BikeLeasingModelValidationTest {

    @Test
    void theBpmnModelsSatisfyAllRulesAndOnlyUseDelegateExpressions() {
        List<ValidationRule> rules = new ArrayList<>(BpmnRules.all());
        rules.add(new ServiceTaskDelegateExpressionRule());

        BpmnValidator
                .fromClasspath("bpmn/")
                .engine(ProcessEngine.CAMUNDA_7)
                .withRules(rules)
                .validate()
                .assertNoViolations();
    }
}
