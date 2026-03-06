package dev.j_a.ide.lsp_gradle_plugin

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*

/**
 * Extends the ShadowJar task to relocate classes of the dev.j-a.ide LSP and DAP libraries to a different package.
 */
@CacheableTask
abstract class RelocateLanguageServerPackageTask : ShadowJar() {
    @get:Input
    abstract val v2Descriptor: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val pluginJar: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val packagePrefix: Property<String>

    @get:Input
    abstract val enabledLanguageIds: SetProperty<String>

    @get:InputFiles
    @get:Classpath
    @get:Optional
    abstract val lspLibrary: ConfigurableFileCollection

    @TaskAction
    override fun copy() {
        // Change the target package to be inside your own plugin's package
        // For v2 descriptors, it must be inside the package specified by the 'package' attribute of <idea-plugin>
        val packagePrefix = packagePrefix.get()
        if (packagePrefix.isNotEmpty()) {
            if (packagePrefix == LSP_PACKAGE_PREFIX) {
                throw IllegalArgumentException("packagePrefix must be set to a non-empty value different from $LSP_PACKAGE_PREFIX")
            }
            relocate(LSP_PACKAGE_PREFIX, packagePrefix)
            relocate(ANTLR4_PACKAGE_PREFIX, "$packagePrefix.$ANTLR4_PACKAGE_PREFIX")
            // fixme: This break LSP4J own reflection, which is not updated to reference the relocated package
            //relocate(LSP4J_PACKAGE_PREFIX, "$packagePrefix.$LSP4J_PACKAGE_PREFIX")
        }

        val enabledPsiLanguages = enabledLanguageIds.getOrElse(emptySet())
        transform(UpdatePluginXmlTransformer(v2Descriptor.get(), enabledPsiLanguages, logger))

        from(pluginJar.map { jarFile -> archiveOperations.zipTree(jarFile) })
        if (!lspLibrary.isEmpty) {
            logger.info("Bundling LSP library into JAR")
            from(lspLibrary.map { jar -> archiveOperations.zipTree(jar) })
        }

        logger.info("Relocating LSP and DAP libraries in ${pluginJar.get()} into package $packagePrefix, PSI languages: $enabledPsiLanguages")
        super.copy()
    }
}