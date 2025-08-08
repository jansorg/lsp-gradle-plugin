# LSP Library Gradle Plugin

This is a Gradle plugin to simplify the use of the LSP client library for JetBrains IDEs.

The LSP client at [j-a.dev/lsp-dap](https://www.j-a.dev/lsp-dap) is made for JetBrains IDEs.
But because the client is provided as a library, it must be bundled with a JetBrains plugin.
Due to technical limitations of JetBrains IDEs, the library must not share the same package name with other plugins on the Marketplace.
Therefore, the package of the LSP library must be relocated.

This Gradle plugin helps with the relocation.
It also simplifies the setup of additional features, which need a configured IDE language to be available.
Please refer to [j-a.dev/lsp-dap](https://www.j-a.dev/lsp-dap) for details.

## How To Apply To a Gradle Project

The plugin requires that the [IntelliJ Platform Gradle Plugin](https://github.com/JetBrains/intellij-platform-gradle-plugin)
is applied to your project.

```kotlin
plugins {
    // This must be present already. Version 2.7.0 is tested and is currently the required minimum version.
    id("org.jetbrains.intellij.platform") version "2.7.0"

    // Apply the LSP Gradle plugin to your project
    id("dev.j-a.ide.lsp") version "0.3.3"
}

project(":") {
    shadowLSP {
        // Parent package for the relocated classes of the LSP library
        packagePrefix = "com.example.my_plugin.lsp_support"

        // IDs of languages used by the LSP server.
        // This enables features, which are configured with a IntelliJ language ID.
        enabledLanguageIds = setOf()
    }

    dependencies {
        // 243 to you build against 2024.3
        implementation("dev.j-a.ide:lsp-client:0.3.0.243")
    }
}
```

## Publish Locally

To install a snapshot to your local Maven repository, run this script:

```bash
./publish-local.bash
```

To allow a Gradle project to load plugins from the local Maven repository, make sure to update your 
`settings.gradle.kts`:
```bash
pluginManagement {
    repositories {
        mavenLocal() // <-- add this
        gradlePluginPortal()
    }
}
```