package dev.j_a.ide.lsp_gradle_plugin

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Configuration settings for the LSP Gradle plugin.
 */
interface LanguageServerGradleExtension {
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
}