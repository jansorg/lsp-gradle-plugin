package dev.j_a.ide.lsp_gradle_plugin

import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.logging.Logger

/**
 * Transformer, which updates the LSP library's plugin.xml includes with the relocated classes.
 *
 * Additionally, it adds snippets to the xml files to enable optional LSP and DAP features, which require a PSI langauge ID.
 */
internal class UpdatePluginXmlTransformer(val enabledPsiLanguages: Set<String>, val logger: Logger) : ResourceTransformer {
    // XML plugin.xml to snippet file for per-language feature snippets
    private val transformedDescriptorResources = mapOf(
        "META-INF/plugin-lsp-client.xml" to "META-INF/plugin-lsp-client-snippets.xml",
        "META-INF/plugin-dap-client.xml" to "META-INF/plugin-dap-client-snippets.xml",
    )

    // maps main XML resource path or snippet XML resource path to its transformed XML content
    private val transformedResources = mutableMapOf<String, String>()

    override fun canTransformResource(element: FileTreeElement): Boolean {
        return element.path.isMainXmlFile || element.path.isSnippetsXmlFile
    }

    override fun hasTransformedResource(): Boolean {
        return transformedResources.isNotEmpty()
    }

    override fun transform(context: TransformerContext) {
        val initialXml = context.inputStream.readAllBytes().toString(Charsets.UTF_8)
        val patchedXml = context.relocators.fold(initialXml) { xml, relocator ->
            relocator.applyToSourceContent(xml)
        }

        transformedResources.put(context.path, patchedXml)
    }

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        val expandedXmlSnippets = transformedResources
            .filter { it.key.isSnippetsXmlFile }
            .mapValues { expandXmlSnippet(it.value) }

        transformedResources
            .filter { it.key.isMainXmlFile }
            .mapValues {
                val snippetXml = transformedDescriptorResources[it.key]?.let { path -> expandedXmlSnippets[path] }
                if (logger.isDebugEnabled) {
                    logger.debug("LSP snippet XML: $snippetXml")
                }

                when {
                    snippetXml != null -> {
                        val patchedValue = it.value.replace("</idea-plugin>", "$snippetXml\n</idea-plugin>").trim()
                        if (logger.isDebugEnabled) {
                            logger.debug("LSP unpatched XML: ${it.value}")
                            logger.debug("LSP patched XML: $patchedValue")
                        }
                        patchedValue
                    }

                    else -> it.value
                }
            }.forEach { key, value ->
                os.putNextEntry(ZipEntry(key))
                os.write(value.toByteArray(Charsets.UTF_8))
                os.flush()
            }
    }

    private val String.isMainXmlFile: Boolean
        get() {
            return transformedDescriptorResources.containsKey(this)
        }

    private val String.isSnippetsXmlFile: Boolean
        get() {
            return transformedDescriptorResources.containsValue(this)
        }

    private fun expandXmlSnippet(xmlContent: String): String {
        return buildString {
            for (languageId in enabledPsiLanguages) {
                append(xmlContent.replace("\$LANGUAGE_ID\$", languageId).replaceIndent("    "))
                append("\n")
            }
        }
    }
}
