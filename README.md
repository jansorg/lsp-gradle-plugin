# LSP Library Gradle Plugin

This is a Gradle plugin to simplify the use of the LSP client library for JetBrains IDEs.

## How To Apply To a Gradle Project

The plugin requires that the [IntelliJ Platform Gradle Plugin](https://github.com/JetBrains/intellij-platform-gradle-plugin)
is applied to your project.

```kotlin
plugins {
    // This must be present already. Version 2.3.0 is tested, earlier versions should work.
    id("org.jetbrains.intellij.platform") version "2.3.0"

    // Apply the LSP Gradle plugin to your project
    id("dev.j-a.ide.lsp") version "0.2.0-SNAPSHOT"
}

project(":") {
    shadowLSP {
        // Parent package for the relocated classes of the LSP library
        packagePrefix = "com.example.my_plugin.lsp_support"
    }
}
```