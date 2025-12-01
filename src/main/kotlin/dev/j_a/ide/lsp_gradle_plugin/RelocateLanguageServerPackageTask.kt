package dev.j_a.ide.lsp_gradle_plugin

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*

/**
 * Extends the ShadowJar task to relocate classes of the dev.j-a.ide LSP and DAP libraries to a different package.
 */
@CacheableTask
abstract class RelocateLanguageServerPackageTask : ShadowJar() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val composedPluginJar: RegularFileProperty

    @get:Input
    abstract val relocateLibraryPackages: Property<Boolean>

    @get:Input
    abstract val packagePrefix: Property<String>

    @get:Input
    abstract val enabledLanguageIds: SetProperty<String>

    @TaskAction
    override fun copy() {
        val packagePrefix = packagePrefix.get()
        if (packagePrefix.isEmpty() || packagePrefix == LSP_PACKAGE_PREFIX) {
            throw IllegalArgumentException("packagePrefix must be set to a non-empty value different from $LSP_PACKAGE_PREFIX")
        }

        val enabledPsiLanguages = enabledLanguageIds.getOrElse(emptySet())

        // Change the target package to be inside your own plugin's package
        // For v2 descriptors, it must be inside the package specified by the 'package' attribute of <idea-plugin>
        if (relocateLibraryPackages.get()) {
            relocate(LSP_PACKAGE_PREFIX, packagePrefix)
        }
        transform(UpdatePluginXmlTransformer(enabledPsiLanguages, logger))
        from(composedPluginJar.map { archiveOperations.zipTree(it) })

        logger.info("Relocating LSP and DAP libraries in ${composedPluginJar.get()} into package $packagePrefix, PSI languages: $enabledPsiLanguages")
        super.copy()
    }
}