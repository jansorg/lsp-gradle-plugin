import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val javaVersion = 11

group = "dev.j-a.ide"
description = "Gradle plugin to help relocate the LSP library for JetBrains plugins"
version = project.ext["pluginVersion"] as String

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.1.0"

    id("com.gradle.plugin-publish") version "1.3.1"
    id("com.gradleup.shadow") version "9.0.0-beta10"
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
    api(rootProject.libs.intellij.platform)
    // For unknown reasons, class org.apache.tools.zip.ZipOutputStream is used in an interface of the Shadow plugin,
    // but it's unavailable unless we add this dependency (same as the dependency in the Shadow plugin itself).
    api(libs.apache.ant)

    implementation(rootProject.libs.gradleup.shadow)
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
        }
    }
}

tasks.shadowJar {
    archiveClassifier = ""
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
        }
    }
}