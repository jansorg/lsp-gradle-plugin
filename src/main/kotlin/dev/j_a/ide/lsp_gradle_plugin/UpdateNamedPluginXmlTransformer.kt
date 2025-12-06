package dev.j_a.ide.lsp_gradle_plugin

import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.logging.Logger

/**
 * Transformer, which updates the LSP library package to the relocated package in XML files given by name.
 */
internal class UpdateNamedPluginXmlTransformer(val pluginXmlFiles: Set<String>, val logger: Logger) : ResourceTransformer {
    private val transformedResources = mutableMapOf<String, String>()

    override fun canTransformResource(element: FileTreeElement): Boolean {
        return element.path.isTransformedXmlFile
    }

    override fun hasTransformedResource(): Boolean {
        return transformedResources.isNotEmpty()
    }

    override fun transform(context: TransformerContext) {
        val initialXml = context.inputStream.readAllBytes().toString(Charsets.UTF_8)
        val patchedXml = context.relocators.fold(initialXml) { xml, relocator ->
            relocator.applyToSourceContent(xml)
        }
        transformedResources[context.path] = patchedXml
    }

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        transformedResources.forEach { (filePath, fileContent) ->
            os.putNextEntry(ZipEntry(filePath))
            os.write(fileContent.toByteArray(Charsets.UTF_8))
            os.closeEntry()
        }
    }

    private val String.isTransformedXmlFile: Boolean
        get() {
            return pluginXmlFiles.contains(this)
        }
}
