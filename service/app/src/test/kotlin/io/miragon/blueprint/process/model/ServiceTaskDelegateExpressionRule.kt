package io.miragon.blueprint.process.model

import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Custom bpmn-to-code validation rule: every *implemented* service task must use a Camunda delegate
 * expression (`#{...}`) — i.e. no external tasks, `camunda:class` or plain `${...}` expressions.
 *
 * This keeps all service-task logic behind Spring-managed JavaDelegates, matching this blueprint's
 * inbound-adapter design. Service tasks with no implementation at all are left to the built-in
 * `MISSING_SERVICE_TASK_IMPLEMENTATION` rule.
 */
class ServiceTaskDelegateExpressionRule : SingleModelValidationRule {

    override val id: String = "SERVICE_TASK_MUST_USE_DELEGATE_EXPRESSION"

    override val severity: Severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> =
        context.model.serviceTasks
            .filter { it.hasImplementation() && !usesDelegateExpression(it) }
            .map { task ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = task.id,
                    processId = context.model.processId,
                    message = "Service task '${task.id}' must use a delegate expression (#{...})",
                )
            }

    private fun usesDelegateExpression(task: ServiceTaskDefinition): Boolean {
        val kind = task.engineSpecificProperties[ServiceTaskDefinition.IMPL_KIND_KEY] as? String
        return kind == DELEGATE_EXPRESSION_KIND
    }

    private companion object {
        const val DELEGATE_EXPRESSION_KIND = "DELEGATE_EXPRESSION"
    }
}
