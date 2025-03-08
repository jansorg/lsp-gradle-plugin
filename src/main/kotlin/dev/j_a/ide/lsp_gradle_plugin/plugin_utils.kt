@file:JvmName("LspGradlePluginUtil")

package dev.j_a.ide.lsp_gradle_plugin

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

@Suppress("unused")
val Project.relocateLanguageServerLibraryTask: TaskProvider<RelocateLanguageServerPackageTask>
    get() {
        return tasks.named(COMPOSED_JAR_SHADOWED_TASK, RelocateLanguageServerPackageTask::class.java)
    }