package io.miragon.blueprint.process.mutation

import org.w3c.dom.Document
import org.w3c.dom.Element

private const val BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
private const val BPMN_DI = "http://www.omg.org/spec/BPMN/20100524/DI"
private const val CAMUNDA = "http://camunda.org/schema/1.0/bpmn"
private const val DMN = "https://www.omg.org/spec/DMN/20191111/MODEL/"

private val skippableActivities =
    setOf("serviceTask", "userTask", "businessRuleTask", "callActivity", "intermediateCatchEvent", "intermediateThrowEvent")

private val relationalComparison = Regex("""^\s*(<=|>=|<|>)\s*(\d+)\s*$""")

private val shiftedComparisonOperator = mapOf("<" to "<=", "<=" to "<", ">" to ">=", ">=" to ">")

private val codeAttributeByEventElement = mapOf("message" to "name", "error" to "errorCode", "escalation" to "escalationCode")

enum class ProcessModelMutationOperator(val modelExtension: String) {
    SKIP_ACTIVITY("bpmn") {
        override fun sites(model: Document) =
            skippableActivities
                .flatMap { model.bpmn(it) }
                .filter { it.getAttribute("isForCompensation") != "true" }
                .filter { model.incomingFlows(it.id).size == 1 && model.outgoingFlows(it.id).size == 1 }
                .map { it.id }

        override fun apply(model: Document, site: String) {
            val successor = model.outgoingFlows(site).single().getAttribute("targetRef")
            model.incomingFlows(site).single().setAttribute("targetRef", successor)
        }
    },
    NEGATE_CONDITION("bpmn") {
        override fun sites(model: Document) =
            model.bpmn("sequenceFlow").filter { it.conditionExpression() != null }.map { it.id }

        override fun apply(model: Document, site: String) {
            val condition = model.byId(site).conditionExpression()!!
            val expression = condition.textContent.trim().removePrefix("\${").removeSuffix("}")
            condition.textContent = "\${!($expression)}"
        }
    },
    FLIP_BOUNDARY_INTERRUPTION("bpmn") {
        override fun sites(model: Document) =
            model.bpmn("boundaryEvent").filter { model.outgoingFlows(it.id).isNotEmpty() }.map { it.id }

        override fun apply(model: Document, site: String) {
            val boundaryEvent = model.byId(site)
            val interrupting = boundaryEvent.getAttribute("cancelActivity") != "false"
            boundaryEvent.setAttribute("cancelActivity", (!interrupting).toString())
        }
    },
    STRETCH_TIMER("bpmn") {
        override fun sites(model: Document) = model.bpmn("timerEventDefinition").map { (it.parentNode as Element).id }

        override fun apply(model: Document, site: String) {
            model.byId(site).descendants(BPMN, "timeDuration").single().textContent = "P30D"
        }
    },
    REMOVE_COMPENSATION_HANDLER("bpmn") {
        override fun sites(model: Document) =
            model.bpmn("association")
                .filter { model.byId(it.getAttribute("targetRef")).getAttribute("isForCompensation") == "true" }
                .map { it.id }

        override fun apply(model: Document, site: String) {
            model.byId(site).detach()
            model.elements(BPMN_DI, "BPMNEdge").filter { it.getAttribute("bpmnElement") == site }.forEach { it.detach() }
        }
    },
    ROTATE_DELEGATE("bpmn") {
        override fun sites(model: Document) = model.delegateTasks().map { it.id }

        override fun apply(model: Document, site: String) {
            val delegateTasks = model.delegateTasks()
            val task = model.byId(site)
            val position = delegateTasks.indexOf(task)
            val otherDelegate =
                (1 until delegateTasks.size)
                    .map { delegateTasks[(position + it) % delegateTasks.size].delegateExpression() }
                    .first { it != task.delegateExpression() }
            task.setAttributeNS(CAMUNDA, "camunda:delegateExpression", otherDelegate)
        }
    },
    PARALLEL_TO_EXCLUSIVE_GATEWAY("bpmn") {
        override fun sites(model: Document) = model.bpmn("parallelGateway").map { it.id }

        override fun apply(model: Document, site: String) {
            val gateway = model.byId(site)
            model.renameNode(gateway, BPMN, "${gateway.prefix}:exclusiveGateway")
        }
    },
    REMOVE_ASYNC_CONTINUATION("bpmn") {
        override fun sites(model: Document) =
            model.allElements()
                .filter { it.getAttributeNS(CAMUNDA, "asyncBefore") == "true" || it.getAttributeNS(CAMUNDA, "asyncAfter") == "true" }
                .map { it.id }

        override fun apply(model: Document, site: String) {
            val element = model.byId(site)
            element.removeAttributeNS(CAMUNDA, "asyncBefore")
            element.removeAttributeNS(CAMUNDA, "asyncAfter")
        }
    },
    DROP_END_EVENT_DEFINITION("bpmn") {
        override fun sites(model: Document) = model.bpmn("endEvent").filter { it.eventDefinitions().isNotEmpty() }.map { it.id }

        override fun apply(model: Document, site: String) = model.byId(site).eventDefinitions().forEach { it.detach() }
    },
    RENAME_EVENT_CODE("bpmn") {
        override fun sites(model: Document) = codeAttributeByEventElement.keys.flatMap { model.bpmn(it) }.map { it.id }

        override fun apply(model: Document, site: String) {
            val element = model.byId(site)
            val attribute = codeAttributeByEventElement.getValue(element.localName)
            element.setAttribute(attribute, element.getAttribute(attribute) + "Mutated")
        }
    },
    REMOVE_CALL_ACTIVITY_INPUT("bpmn") {
        override fun sites(model: Document) =
            model.bpmn("callActivity").flatMap { callActivity ->
                callActivity.descendants(CAMUNDA, "in").indices.map { "${callActivity.id}#$it" }
            }

        override fun apply(model: Document, site: String) {
            val (callActivityId, index) = site.split("#")
            model.byId(callActivityId).descendants(CAMUNDA, "in")[index.toInt()].detach()
        }
    },
    SHIFT_COMPARISON_BOUNDARY("dmn") {
        override fun sites(model: Document) = model.comparisonEntries().map { it.id }

        override fun apply(model: Document, site: String) {
            val text = model.byId(site).dmnText()
            val (operator, value) = relationalComparison.find(text.textContent)!!.destructured
            text.textContent = "${shiftedComparisonOperator.getValue(operator)} $value"
        }
    },
    SHIFT_COMPARISON_VALUE("dmn") {
        override fun sites(model: Document) = model.comparisonEntries().map { it.id }

        override fun apply(model: Document, site: String) {
            val text = model.byId(site).dmnText()
            val (operator, value) = relationalComparison.find(text.textContent)!!.destructured
            val widenedValue = if (operator.startsWith("<")) value.toInt() - 1 else value.toInt() + 1
            text.textContent = "$operator $widenedValue"
        }
    },
    FLIP_BOOLEAN_OUTPUT("dmn") {
        override fun sites(model: Document) =
            model.elements(DMN, "outputEntry").filter { it.dmnText().textContent.trim() in setOf("true", "false") }.map { it.id }

        override fun apply(model: Document, site: String) {
            val text = model.byId(site).dmnText()
            text.textContent = (text.textContent.trim() != "true").toString()
        }
    },
    DELETE_RULE("dmn") {
        override fun sites(model: Document) = model.elements(DMN, "rule").map { it.id }

        override fun apply(model: Document, site: String) = model.byId(site).detach()
    },
    STRICT_HIT_POLICY("dmn") {
        override fun sites(model: Document) =
            model.elements(DMN, "decisionTable").filter { it.getAttribute("hitPolicy") == "FIRST" }.map { it.id }

        override fun apply(model: Document, site: String) = model.byId(site).setAttribute("hitPolicy", "UNIQUE")
    },
    ;

    abstract fun sites(model: Document): List<String>

    abstract fun apply(model: Document, site: String)
}

private val Element.id: String get() = getAttribute("id")

private fun Document.allElements(): List<Element> = elements("*", "*")

private fun Document.elements(namespace: String, localName: String): List<Element> =
    getElementsByTagNameNS(namespace, localName).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

private fun Document.bpmn(localName: String) = elements(BPMN, localName)

private fun Document.byId(id: String) = allElements().single { it.id == id }

private fun Document.incomingFlows(nodeId: String) = bpmn("sequenceFlow").filter { it.getAttribute("targetRef") == nodeId }

private fun Document.outgoingFlows(nodeId: String) = bpmn("sequenceFlow").filter { it.getAttribute("sourceRef") == nodeId }

private fun Document.delegateTasks() = bpmn("serviceTask").filter { it.delegateExpression().isNotEmpty() }

private fun Document.comparisonEntries() =
    elements(DMN, "inputEntry").filter { relationalComparison.matches(it.dmnText().textContent) }

private fun Element.detach() {
    parentNode.removeChild(this)
}

private fun Element.descendants(namespace: String, localName: String): List<Element> =
    getElementsByTagNameNS(namespace, localName).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

private fun Element.conditionExpression() = descendants(BPMN, "conditionExpression").firstOrNull()

private fun Element.delegateExpression() = getAttributeNS(CAMUNDA, "delegateExpression")

private fun Element.eventDefinitions() =
    (0 until childNodes.length).map { childNodes.item(it) }
        .filterIsInstance<Element>()
        .filter { it.localName.endsWith("EventDefinition") }

private fun Element.dmnText() = descendants(DMN, "text").single()
