import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val javaVersion = 17
val pluginVersion = project.ext["pluginVersion"] as String
val pluginVersionSuffix = project.ext["pluginVersionSuffix"] as String

group = "dev.j-a.ide"
description = "Gradle plugin to help relocate the LSP library for JetBrains plugins"
version = "$pluginVersion$pluginVersionSuffix"

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.2.21"

    // https://plugins.gradle.org/plugin/com.gradle.plugin-publish
    id("com.gradle.plugin-publish") version "2.1.1"
    // https://github.com/GradleUp/shadow
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

publishing {
    repositories {
        mavenLocal()
    }
}

dependencies {
    // Gradle provides the Kotlin stdlib at runtime, it must not be bundled or exposed.
    // The automatic stdlib dependency is disabled in gradle.properties.
    compileOnly(kotlin("stdlib"))

    // Users must apply the IntelliJ Platform Gradle plugin themselves, we only compile against its API.
    compileOnly(rootProject.libs.intellij.platform)

    // For unknown reasons, class org.apache.tools.zip.ZipOutputStream is used in an interface of the Shadow plugin,
    // but it's unavailable unless we add this dependency (same as the dependency in the Shadow plugin itself).
    // At runtime, it's provided by the dependencies of the Shadow plugin.
    compileOnly(libs.apache.ant)

    // Bundled and relocated into our jar, see shadowJar below
    implementation(rootProject.libs.gradleup.shadow)
}

// The relocated Shadow plugin is bundled, but its own dependencies are not.
// They're declared as regular runtime dependencies of the published plugin instead.
configurations.shadow {
    withDependencies {
        val shadowPlugin = configurations.detachedConfiguration(project.dependencies.create(libs.gradleup.shadow.get()))
        shadowPlugin.incoming.resolutionResult.root.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .flatMap { it.selected.dependencies }
            .filterIsInstance<ResolvedDependencyResult>()
            .mapNotNull { it.selected.moduleVersion }
            .forEach { add(project.dependencies.create("${it.group}:${it.name}:${it.version}")) }
    }
}

gradlePlugin {
    website = "https://github.com/jansorg/lsp-gradle-plugin"
    vcsUrl = "https://github.com/jansorg/lsp-gradle-plugin.git"

    plugins {
        create("dev.j-a.ide.lsp") {
            id = "dev.j-a.ide.lsp"
            displayName = "LSP Gradle Plugin for the dev.j-a.ide namespace"
            description = "Gradle plugin to help relocate the LSP library for JetBrains plugins."
            implementationClass = "dev.j_a.ide.lsp_gradle_plugin.LanguageServerGradlePlugin"
            tags.addAll("lsp", "jetbrains")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

tasks.shadowJar {
    archiveClassifier = ""
    dependencies {
        include(dependency(libs.gradleup.shadow))
    }
    // don't expose the plugin IDs of the bundled Shadow plugin
    exclude("META-INF/gradle-plugins/com.gradleup.shadow.properties")
    exclude("META-INF/gradle-plugins/com.github.johnrengelman.shadow.properties")
    relocate("com.github.jengelman.gradle", "dev.j_a.ide.lsp_gradle_plugin.shadow")
}

java {
    toolchain {
        // using languageVersion with the SDK lookup break obfuscation with Zelix, e.g. when running Gradle on Java 17 and building for 221, which uses Java 11
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
    }
}

tasks {
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget("$javaVersion")
            // must be compatible with the Kotlin stdlib embedded in the oldest supported Gradle version (9.0 uses Kotlin 2.2)
            apiVersion = KotlinVersion.KOTLIN_2_2
        }
    }
}