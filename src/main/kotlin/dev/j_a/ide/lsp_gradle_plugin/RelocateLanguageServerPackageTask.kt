package dev.j_a.ide.lsp_gradle_plugin

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Extends the ShadowJar task to relocate classes of the dev.j-a.ide LSP and DAP libraries to a different package.
 */
@CacheableTask
abstract class RelocateLanguageServerPackageTask() : ShadowJar() {
    @get:Inject
    protected abstract val archiveOperations: ArchiveOperations

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val composedPluginJar: RegularFileProperty

    @get:Input
    abstract val packagePrefix: Property<String>

    @TaskAction
    override fun copy() {
        val packagePrefix = packagePrefix.get()
        if (packagePrefix.isEmpty() || packagePrefix == LSP_PACKAGE_PREFIX) {
            throw IllegalArgumentException("packagePrefix must be set to a non-empty value different from $LSP_PACKAGE_PREFIX")
        }

        // Change the target package to be inside your own plugin's package
        // For v2 descriptors, it must be inside the package specified by the 'package' attribute of <idea-plugin>
        relocate(LSP_PACKAGE_PREFIX, packagePrefix)
        transform(UpdatePluginXmlTransformer())
        from(composedPluginJar.map { archiveOperations.zipTree(it) })

        println("Relocating LSP and DAP libraries in ${composedPluginJar.get()} into package $packagePrefix")
        super.copy()
    }

    private class UpdatePluginXmlTransformer : ResourceTransformer {
        private val transformedDescriptorResources = setOf(
            "META-INF/plugin-lsp-client.xml",
            "META-INF/plugin-dap-client.xml"
        )

        private val transformedResources = mutableMapOf<String, String>()

        override fun canTransformResource(element: FileTreeElement): Boolean {
            return element.path in transformedDescriptorResources
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
            transformedResources.forEach { key, value ->
                os.putNextEntry(ZipEntry(key))
                os.write(value.toByteArray(Charsets.UTF_8))
                os.flush()
            }
        }
    }
}