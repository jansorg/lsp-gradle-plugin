@file:JvmName("Constants")

package dev.j_a.ide.lsp_gradle_plugin

/** Gradle module group of the LSP and DAP libraries. */
const val LSP_GRADLE_MODULE_GROUP = "dev.j-a.ide"
const val LSP_GRADLE_CLIENT_LIB_NAME = "lsp-client"
const val LSP_GRADLE_CLIENT_TEST_LIB_NAME = "lsp-testframework-client"

const val LSP_LIBRARY_EXTENSION_NAME = "lspLibrary"

/** Package which contains all classes of the LSP and DAP libraries. */
const val LSP_PACKAGE_PREFIX = "dev.j_a.ide"
const val LSP4J_PACKAGE_PREFIX = "org.eclipse.lsp4j"
const val ANTLR4_PACKAGE_PREFIX = "org.antlr.v4"

const val LSP_LIBRARY_CONFIGURATION = "lspLibraryConfiguration"

const val LSP_LIBRARY_TEST_CONFIGURATION = "lspLibraryConfigurationTest"

/** Name of the task which relocates the LSP and DAP libraries to a different package. */
const val LSP_JAR_SHADOWED_TASK = "shadowedLspJar"
