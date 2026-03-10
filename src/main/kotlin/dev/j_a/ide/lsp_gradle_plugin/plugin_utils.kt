@file:Suppress("unused")

package dev.j_a.ide.lsp_gradle_plugin

import dev.j_a.ide.lsp_gradle_plugin.LanguageServerGradlePlugin.Companion.createLspDependency
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.tasks.TaskProvider

val Project.relocateLanguageServerLibraryTask: TaskProvider<RelocateLanguageServerPackageTask>
    get() {
        return tasks.named(LSP_JAR_SHADOWED_TASK, RelocateLanguageServerPackageTask::class.java)
    }

fun DependencyHandler.lspClientImplementation(libraryVersion: String, platformVersion: Int): ExternalModuleDependency {
    val dependency = createLspDependency(LSP_GRADLE_CLIENT_LIB_NAME, libraryVersion, platformVersion)
    return add(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME, dependency) as ExternalModuleDependency
}

fun DependencyHandler.lspClientAPI(libraryVersion: String, platformVersion: Int): ExternalModuleDependency {
    val dependency = createLspDependency(LSP_GRADLE_CLIENT_LIB_NAME, libraryVersion, platformVersion)
    return add(JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME, dependency) as ExternalModuleDependency
}

fun DependencyHandler.lspTestFramework(libraryVersion: String, platformVersion: Int): ExternalModuleDependency {
    val dependency = createLspDependency(LSP_GRADLE_CLIENT_TEST_LIB_NAME, libraryVersion, platformVersion)
    return add(JvmConstants.TEST_IMPLEMENTATION_CONFIGURATION_NAME, dependency) as ExternalModuleDependency
}