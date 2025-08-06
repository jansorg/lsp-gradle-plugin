package dev.j_a.ide.lsp_gradle_plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ResolvedDependency
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask
import org.jetbrains.intellij.platform.gradle.tasks.PrepareJarSearchableOptionsTask
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

/**
 * Gradle plugin which relocates the LSP classes to a different package.
 */
@Suppress("unused")
class LanguageServerGradlePlugin : Plugin<Project> {
    /**
     * We need to relocate classes in the JAR used by "runIde", "buildPlugin", etc.
     * Task "composedJar" provides the default JAR, we take it,
     * relocate the LSP classes and then configure tasks using it to take the relocated JAR instead.
     */
    override fun apply(project: Project) {
        // The IntelliJ Platform Gradle plugin must be applied to the project, we depend on several of its tasks
        if (!project.plugins.hasPlugin("org.jetbrains.intellij.platform")) {
            throw IllegalStateException("You must apply org.jetbrains.intellij.platform to use the LSP Gradle plugin")
        }

        // add exclusions for kotlin-stdlib and gson in all projects
        project.rootProject.allprojects { it.configureLspLibraryExclusions() }

        val extension = project.extensions.create("shadowLSP", LanguageServerGradleExtension::class.java)
        extension.archiveClassifier.convention("shadowed")

        val pluginComposedJarTaskProvider = project.tasks.named(Constants.Tasks.COMPOSED_JAR, ComposedJarTask::class.java)
        val prepareSandboxTaskProvider = project.tasks.named(Constants.Tasks.PREPARE_SANDBOX, PrepareSandboxTask::class.java)
        val prepareJarSearchableOptionsTaskProvider = project.tasks.named(
            Constants.Tasks.PREPARE_JAR_SEARCHABLE_OPTIONS,
            PrepareJarSearchableOptionsTask::class.java
        )

        val lspLibraryConfiguration = project.configurations.create("dev.j_a.libraries") { c ->
            c.isTransitive = false
            c.exclude(mapOf("group" to "org.jetbrains.kotlin"))
            c.exclude(mapOf("group" to "com.google.code.gson"))
        }

        // Collect all LSP libraries into configuration lspLibraryConfiguration.
        project.afterEvaluate {
            it.collectLspLibraryDependencies(lspLibraryConfiguration)
        }

        // Add all LSP library dependencies to the composed plugin JAR
        project.dependencies.add(
            Constants.Configurations.INTELLIJ_PLATFORM_PLUGIN_COMPOSED_MODULE,
            project.dependencies.create(lspLibraryConfiguration)
        )

        val relocateLibraryClassesTask = project.tasks.register(
            COMPOSED_JAR_SHADOWED_TASK,
            RelocateLanguageServerPackageTask::class.java
        ) { task ->
            task.group = "LSP library"
            task.dependsOn(pluginComposedJarTaskProvider)

            task.packagePrefix.set(extension.packagePrefix)
            task.archiveClassifier.set(extension.archiveClassifier)
            task.enabledLanguageIds.set(extension.enabledLanguageIds.getOrElse(emptySet()))
            task.composedPluginJar.set(pluginComposedJarTaskProvider.flatMap(ComposedJarTask::getArchiveFile))
        }

        // update task "prepareSandbox" to use the JAR with the relocated LSP classes
        prepareSandboxTaskProvider.configure { task ->
            task.dependsOn(relocateLibraryClassesTask)
            task.pluginJar.set(relocateLibraryClassesTask.flatMap { it.archiveFile })
        }

        // update task "prepareJarSearchableOptions" to use the JAR with the relocated LSP classes
        prepareJarSearchableOptionsTaskProvider.configure { task ->
            task.dependsOn(relocateLibraryClassesTask)
            task.composedJarFile.set(relocateLibraryClassesTask.flatMap { it.archiveFile })
        }
    }

    /**
     * Collects all production dependencies of the LSP library into [targetConfiguration].
     */
    private fun Project.collectLspLibraryDependencies(targetConfiguration: Configuration) {
        configurations.getByName("runtimeClasspath") { runtimeClasspath ->
            runtimeClasspath.resolvedConfiguration.firstLevelModuleDependencies
                .flatMap { it.allDependencies() }
                .filter { it.moduleGroup == LSP_GRADLE_MODULE_GROUP }
                .forEach {
                    targetConfiguration.dependencies.add(dependencies.create(it.name))
                }
        }
    }

    /**
     * The LSP libraries use kotlin-stdlib and gson in the same version as the IDE's major version.
     * These dependencies need to be excluded.
     */
    private fun Project.configureLspLibraryExclusions() {
        project.afterEvaluate {
            for (configuration in configurations) {
                for (dependency in configuration.dependencies) {
                    if (dependency is ModuleDependency && dependency.group == LSP_GRADLE_MODULE_GROUP) {
                        logger.info("Adding exclusions in project ${project.name} for kotlin-stdlib and gson to LSP library dependency $dependency")

                        dependency.exclude(mapOf(ExcludeRule.GROUP_KEY to "org.jetbrains.kotlin", ExcludeRule.MODULE_KEY to "kotlin-stdlib"))
                        dependency.exclude(mapOf(ExcludeRule.GROUP_KEY to "com.google.code.gson", ExcludeRule.MODULE_KEY to "gson"))
                    }
                }
            }
        }
    }
}

private fun ResolvedDependency.allDependencies(target: MutableSet<ResolvedDependency> = mutableSetOf()): Set<ResolvedDependency> {
    target += this
    children.forEach { it.allDependencies(target) }
    return target
}
