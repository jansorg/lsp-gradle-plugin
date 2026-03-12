package dev.j_a.ide.lsp_gradle_plugin

import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.logging.Logger
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Transformer, which updates the LSP library package to the relocated package in XML files given by name.
 */
internal class UpdatePluginXmlTransformer(
    val pluginId: String,
    val isV2Descriptor: Boolean,
    val enabledPsiLanguages: Set<String>,
    val logger: Logger
) : ResourceTransformer {
    // XML plugin.xml to snippet file for per-language feature snippets
    private val xmlMainToSnippetPath = mapOf(
        "META-INF/plugin-lsp-client.xml" to "META-INF/plugin-lsp-client-snippets.xml",
        "META-INF/plugin-dap-client.xml" to "META-INF/plugin-dap-client-snippets.xml",
    )

    // Transformed plugin.xml files
    private val filePathToTransformedContent = mutableMapOf<String, String>()

    override fun canTransformResource(element: FileTreeElement): Boolean {
        // LSP entry plugin.xml or LSP snippet XML file
        if (element.path.isLspEntryXmlFile || element.path.isLspSnippetXmlFile) {
            return true
        }

        // Plugin XML files
        val path = element.relativePath
        if (path.isFile && path.lastName.endsWith(".xml")) {
            return try {
                element.open().use { input ->
                    val textData = String(input.readNBytes("<idea-plugin ".length), StandardCharsets.UTF_8)
                    textData.startsWith("<idea-plugin>") || textData.startsWith("<idea-plugin ")
                }
            } catch (_: IOException) {
                false
            }
        }

        return false
    }

    override fun hasTransformedResource(): Boolean {
        return filePathToTransformedContent.isNotEmpty()
    }

    override fun transform(context: TransformerContext) {
        logger.info("Transforming plugin.xml file ${context.path}...")

        val initialXml = context.inputStream.readAllBytes().toString(Charsets.UTF_8)
        filePathToTransformedContent[context.path] = context.relocators.fold(initialXml) { xml, relocator ->
            relocator.applyToSourceContent(xml)
        }
    }

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        // Append the snippets to the transformed content of the main XML files
        xmlMainToSnippetPath.forEach { (mainFilePath, snippetFilePath) ->
            val mainFileContent = filePathToTransformedContent[mainFilePath]
            val snippetFileContent = filePathToTransformedContent[snippetFilePath]
            if (mainFileContent != null && snippetFileContent != null) {
                val expandedSnippetXML = expandXmlSnippet(snippetFileContent)
                val updatedMainFileContent = mainFileContent.replace("</idea-plugin>", "$expandedSnippetXML\n</idea-plugin>").trim()
                filePathToTransformedContent[mainFilePath] = updatedMainFileContent
            }
        }

        // Transform plugin.xml files of the plugin
        filePathToTransformedContent
            .filter { (filePath, _) -> !filePath.isLspEntryXmlFile && !filePath.isLspSnippetXmlFile }
            .forEach { (filePath, transformedContent) ->
                logger.info("Saving transformed $filePath...")
                val finalContent = when {
                    // in v2 descriptors, inline <xi:include> elements
                    isV2Descriptor -> inlineXmlIncludes(filePath, transformedContent)
                    else -> transformedContent
                }

                os.putNextEntry(ZipEntry(filePath))
                os.write(finalContent.trim().toByteArray(Charsets.UTF_8))
                os.closeEntry()
            }
    }

    private fun inlineXmlIncludes(filePath: String, xml: String): String {
        val xmlReferencePattern = Regex("<xi:include href=\"(.+?)\"[^>]*>")

        var updated = xml
        xmlReferencePattern.findAll(xml).toList().asReversed().forEach { match ->
            val referencedFilePath = match.groups[1]!!.value
            logger.debug("Inlining xi:include to $referencedFilePath...")

            val filePathReference = referencedFilePath.removePrefix("/")
            val xmlSnippetContent = filePathToTransformedContent[filePathReference]
            if (xmlSnippetContent.isNullOrEmpty()) {
                logger.warn("xi:include reference $referencedFilePath not found in file $filePath")
            } else {
                updated = updated.substring(0, match.range.first) + xmlSnippetContent.trimPluginXmlTag() + updated.substring(match.range.last + 1)
            }
        }
        return updated.replace(" xmlns:xi=\"http://www.w3.org/2001/XInclude\"", "")
    }

    private fun String.trimPluginXmlTag(): String {
        return replace(Regex("<idea-plugin[^>]*>"), "").replace(Regex("</idea-plugin>"), "")
    }

    private val String.isLspEntryXmlFile: Boolean
        get() {
            return xmlMainToSnippetPath.containsKey(this)
        }

    private val String.isLspSnippetXmlFile: Boolean
        get() {
            return xmlMainToSnippetPath.containsValue(this)
        }

    private fun expandXmlSnippet(xmlContent: String): String {
        return buildString {
            for (languageId in enabledPsiLanguages) {
                val updatedContent = xmlContent
                    .replace("\$PLUGIN_ID$", pluginId)
                    .replace("\$LANGUAGE_ID$", languageId)
                    .replaceIndent("    ")
                append(updatedContent)
                append("\n")
            }
        }
    }
}
