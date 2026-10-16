package io.miragon.blueprint.process.mutation

import org.w3c.dom.Document
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

fun main(args: Array<String>) {
    val resourcesDir = File(args[0])
    val mutantsDir = File(args[1]).apply { deleteRecursively(); mkdirs() }
    val modelFiles = resourcesDir.walkTopDown().filter { it.extension in setOf("bpmn", "dmn") }.sortedBy { it.path }.toList()
    val manifest = mutableListOf("id;operator;resource;site")
    var mutantNumber = 0
    for (modelFile in modelFiles) {
        val resource = modelFile.relativeTo(resourcesDir).path
        write(parse(modelFile), File(mutantsDir, "IDENTITY/$resource"))
        for (operator in ProcessModelMutationOperator.entries.filter { it.modelExtension == modelFile.extension }) {
            for (site in operator.sites(parse(modelFile))) {
                val mutantId = "M%03d".format(++mutantNumber)
                val mutatedModel = parse(modelFile).also { operator.apply(it, site) }
                write(mutatedModel, File(mutantsDir, "$mutantId/$resource"))
                manifest += "$mutantId;${operator.name};$resource;$site"
            }
        }
    }
    File(mutantsDir, "manifest.csv").writeText(manifest.joinToString("\n", postfix = "\n"))
    println("Generated $mutantNumber mutants into $mutantsDir")
}

private fun parse(file: File): Document =
    DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)

private fun write(model: Document, target: File) {
    target.parentFile.mkdirs()
    TransformerFactory.newInstance().newTransformer().transform(DOMSource(model), StreamResult(target))
}
