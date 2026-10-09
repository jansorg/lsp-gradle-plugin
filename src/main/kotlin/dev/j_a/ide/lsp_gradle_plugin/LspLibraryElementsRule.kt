package dev.j_a.ide.lsp_gradle_plugin

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * The Gradle module metadata of LSP library 0.6.35 requests the library elements `composed-jar`
 * for the dependencies between its modules, e.g. of `lsp-client` on `lsp-client-openapi`.
 * The published modules only provide `jar`, which this rule requests instead.
 */
@CacheableRule
abstract class LspLibraryElementsRule @Inject constructor(private val objects: ObjectFactory) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        if (context.details.id.group != LSP_GRADLE_MODULE_GROUP) {
            return
        }

        val jar = objects.named(LibraryElements::class.java, LibraryElements.JAR)
        context.details.allVariants { variant ->
            variant.withDependencies { dependencies ->
                dependencies.filter { it.group == LSP_GRADLE_MODULE_GROUP }.forEach { dependency ->
                    val attribute = dependency.attributes.keySet().firstOrNull { it.name == LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name }
                    if (attribute != null && dependency.attributes.getAttribute(attribute).toString() == COMPOSED_JAR) {
                        dependency.attributes { it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, jar) }
                    }
                }
            }
        }
    }

    private companion object {
        const val COMPOSED_JAR = "composed-jar"
    }
}
