package dev.j_a.ide.lsp_gradle_plugin

import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Configuration settings for the LSP Gradle plugin.
 */
interface LanguageServerGradleExtension {
    @get:Input
    @get:Optional
    val enabled: Property<Boolean>

    @get:Input
    @get:Optional
    val shadowLspLibraries: Property<Boolean>

    /**
     * The parent package for the relocated classes.
     * The package must be inside your JetBrains plugin's top-level package.
     *
     * For example, if your plugin's classes are located under `com.example.myplugin`,
     * you could use `com.example.myplugin.lsp_support` as the package prefix.
     */
    @get:Input
    val packagePrefix: Property<String>

    /**
     * The archive classifier of the JAR file with the relocated LSP library classes.
     *
     * By default, `shadowed` is used.
     */
    @get:Input
    @get:Optional
    val archiveClassifier: Property<String>

    /**
     * Language IDs, for which the optional LSP features should be enabled.
     * The library's LSP and DAP XML files are patched with the enabled languages.
     *
     * If no language ID is specified, the optional LSP features are not enabled.
     */
    @get:Input
    @get:Optional
    val enabledLanguageIds: SetProperty<String>

    /**
     * Paths to the plugin.xml files or snippets, which may refer to dev.j-a.lsp classes and which have to be updated with the relocated LSP classes.
     */
    @get:Input
    @get:Optional
    val pluginXmlFiles: SetProperty<String>
}