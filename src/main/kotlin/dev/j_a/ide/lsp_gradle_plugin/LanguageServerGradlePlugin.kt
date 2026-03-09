package dev.j_a.ide.lsp_gradle_plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask
import javax.inject.Inject

/**
 * Gradle plugin which relocates the LSP classes to a different package.
 */
@Suppress("unused")
class LanguageServerGradlePlugin @Inject constructor(
    val dependencyFactory: DependencyFactory,
    val jvmPluginServices: JvmPluginServices,
) : Plugin<Project> {
    /**
     * We need to relocate classes in the JAR used by "runIde", "buildPlugin", etc.
     * Task "composedJar" provides the default JAR, we take it,
     * relocate the LSP classes and then configure tasks using it to take the relocated JAR instead.
     */
    override fun apply(project: Project) {
        val isIntelliJPlatformProject = project.plugins.hasPlugin("org.jetbrains.intellij.platform")
        val isIntelliJSubProject = project.plugins.hasPlugin("org.jetbrains.intellij.platform.module")
        // The IntelliJ Platform Gradle plugin must be applied to the project, we depend on several of its tasks
        if (!isIntelliJPlatformProject && !isIntelliJSubProject) {
            throw IllegalStateException("In project ${project.name}, you must apply org.jetbrains.intellij.platform[.module] to use the LSP Gradle plugin.")
        }

        val extension = createLspLibraryExtension(project, isIntelliJSubProject)

        fun createLspDependency(name: String): Provider<Dependency> {
            return extension.version.zip(extension.platform) { version, platform ->
                val suffix = if (version.endsWith("-SNAPSHOT")) "-SNAPSHOT" else ""
                dependencyFactory.create("dev.j-a.ide", name, "${version.removeSuffix(suffix)}.$platform$suffix")
            }
        }

        val lspLibraryConfiguration = createLspLibraryConfiguration(project, createLspDependency("lsp-client"), extension)
        val lspTestFrameworkConfiguration = createLspTestFrameworkConfiguration(project, createLspDependency("lsp-testframework-client"), extension)

        val isInstrumentingCode = project.extensions.findByType(IntelliJPlatformExtension::class.java)?.instrumentCode ?: project.provider { false }
        val pluginJarProvider = isInstrumentingCode.flatMap { enabled ->
            when {
                isIntelliJPlatformProject -> project.tasks.named(Tasks.COMPOSED_JAR, ComposedJarTask::class.java)
                else -> when (enabled) {
                    true -> project.tasks.named(Tasks.INSTRUMENTED_JAR, Jar::class.java)
                    false -> project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)
                }
            }
        }

        val relocatedLspLibraryTask = createRelocateLspLibraryTask(project, extension, pluginJarProvider, lspLibraryConfiguration)
        lspLibraryConfiguration.outgoing.artifact(relocatedLspLibraryTask)
    }

    private fun createLspLibraryConfiguration(
        project: Project,
        lspClientLibrary: Provider<Dependency>,
        extension: LanguageServerGradleExtension
    ): Configuration {
        return project.configurations.create(LSP_LIBRARY_CONFIGURATION) { config ->
            config.isTransitive = true
            config.isCanBeConsumed = true
            config.isCanBeResolved = true

            config.exclude(mapOf(ExcludeRule.GROUP_KEY to "org.jetbrains.kotlin", ExcludeRule.MODULE_KEY to "kotlin-stdlib"))
            config.exclude(mapOf(ExcludeRule.GROUP_KEY to "com.google.code.gson", ExcludeRule.MODULE_KEY to "gson"))

            config.defaultDependencies { dependencies ->
                dependencies.add(lspClientLibrary.get())
            }

            config.attributes {
                it.attribute(Attributes.kotlinJPlatformType, "jvm")
                it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling::class.java, Bundling.EXTERNAL))
                it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
                it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))

                it.attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM)
                )
                it.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, project.provider {
                    project.extensions.findByType(JavaPluginExtension::class.java)!!.targetCompatibility.majorVersion.toInt()
                })
            }
            project.afterEvaluate {
                if (extension.addLibraryDependency.get()) {
                    project.configurations.getByName(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(config)
                }
            }
        }
    }

    private fun createLspTestFrameworkConfiguration(
        project: Project,
        lspClientTestFramework: Provider<Dependency>,
        extension: LanguageServerGradleExtension
    ): Configuration {
        return project.configurations.create(LSP_LIBRARY_TEST_CONFIGURATION) { config ->
            config.isTransitive = true
            config.isCanBeConsumed = true
            config.isCanBeResolved = true

            config.defaultDependencies { dependencies ->
                dependencies.add(lspClientTestFramework.get())
            }

            project.afterEvaluate {
                if (extension.addTestFrameworkDependency.get()) {
                    project.configurations.getByName(JvmConstants.TEST_IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom(config)
                }
            }
        }
    }

    private fun createLspLibraryExtension(project: Project, isPluginModule: Boolean): LanguageServerGradleExtension {
        val extension = project.extensions.create("lspLibrary", LanguageServerGradleExtension::class.java)
        extension.addLibraryDependency.convention(true)
        extension.addTestFrameworkDependency.convention(true)
        extension.bundleLibrary.convention(false)
        extension.packagePrefix.convention(null)
        extension.archiveClassifier.convention(null)
        extension.enabledLanguageIds.convention(emptySet())
        extension.pluginModuleName.convention(null)
        return extension
    }

    private fun createRelocateLspLibraryTask(
        project: Project,
        extension: LanguageServerGradleExtension,
        pluginJarProvider: Provider<out Jar>,
        lspLibraryConfiguration: Configuration
    ): TaskProvider<RelocateLanguageServerPackageTask> = project.tasks.register(
        LSP_JAR_SHADOWED_TASK,
        RelocateLanguageServerPackageTask::class.java
    ) { task ->
        task.dependsOn(pluginJarProvider)
        task.dependsOn(lspLibraryConfiguration)

        val moduleName = extension.pluginModuleName.get()

        task.group = "LSP library"
        task.description = "Bundle and/or modify the LSP libraries"
        task.v2Descriptor.set(moduleName.isNotEmpty())
        task.packagePrefix.set(extension.packagePrefix)
        task.enabledLanguageIds.set(extension.enabledLanguageIds.getOrElse(emptySet()))
        task.pluginJar.set(pluginJarProvider.flatMap(Jar::getArchiveFile))
        if (moduleName.isNotEmpty()) {
            task.archiveBaseName.set(moduleName)
            task.archiveAppendix.set("")
            task.archiveVersion.set("")
            task.archiveClassifier.set("")
        } else {
            task.archiveClassifier.set(extension.archiveClassifier)
        }
        if (extension.bundleLibrary.get()) {
            task.lspLibrary.from(lspLibraryConfiguration)
        }
    }
}
