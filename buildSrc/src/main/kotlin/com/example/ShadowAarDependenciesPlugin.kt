package com.example

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.dependencies
import java.io.File

abstract class ShadowAarDependenciesPluginExtension {
    @get:Input
    abstract val relocations: ListProperty<List<String>>
}

class ShadowAarDependenciesPlugin : Plugin<Project> {

    // configuration
    val SHADOW_AAR_EXTENSION_NAME = "shadowAarDependencies"
    val SHADOW_ARR_CONFIGURATION_NAME = "aarImplementation"
    val UNZIP_AAR_DIR = "unzip-aars"
    val SHADOW_JAR_TASK_NAME = "relocateImplementationJARs"

    // constants
    val ANDROID_ARTIFACT_EXTENSION = "aar"
    val JAVA_ARTIFACT_EXTENSION = "jar"
    val JAR_IN_AAR_FILE_NAME = "classes.jar"

    private fun isAndroidArtifact(artifact: ResolvedArtifact): Boolean =
        artifact.extension == ANDROID_ARTIFACT_EXTENSION

    private fun isJavaArtifact(artifact: ResolvedArtifact): Boolean =
        artifact.extension == JAVA_ARTIFACT_EXTENSION


    private fun isFromGroups(artifact: ResolvedArtifact, groups: List<String?>): Boolean =
        groups.any { artifact.moduleVersion.id.group == it }

    private fun getAarsWithTopLevelDependencyGroups(
        artifacts: Set<ResolvedArtifact>,
        topLevelDeps: DependencySet
    ): List<ResolvedArtifact> {
        val topLevelDepGroups = topLevelDeps.map { it.group }
        return artifacts.filter {
            isAndroidArtifact(it) && isFromGroups(it, topLevelDepGroups)
        }
    }

    private fun getJarsWithTopLevelDependencyGroups(
        artifacts: Set<ResolvedArtifact>,
        topLevelDeps: DependencySet
    ): List<ResolvedArtifact> {
        val topLevelDepGroups = topLevelDeps.map { it.group }
        return artifacts.filter {
            isJavaArtifact(it) && isFromGroups(it, topLevelDepGroups)
        }
    }

    private fun extractJarfromAar(project: Project, targetDir: File, aar: ResolvedArtifact): File {
        val jarName = "${aar.moduleVersion.id.name}.$JAVA_ARTIFACT_EXTENSION"
        project.copy {
            from(project.zipTree(aar.file)) { rename(JAR_IN_AAR_FILE_NAME, jarName) }
            into(targetDir)
        }
        return targetDir.resolve(jarName)
    }

    private fun getFullyQualifiedDependencyCoordinates(artifact: ResolvedArtifact): String {
        val version = artifact.moduleVersion.id.version
        val group = artifact.moduleVersion.id.group
        val name = artifact.moduleVersion.id.name
        return "$group:$name:$version"
    }

    override fun apply(project: Project) {
        // create an extension for easier plugin config
        val extension = project.extensions.create(
            SHADOW_AAR_EXTENSION_NAME,
            ShadowAarDependenciesPluginExtension::class.java
        )

        // create a publically available dependencies configuration
        // to list project dependencies that are AAR that need to
        // be relocated to a new namespace
        val aarConfig = project.configurations.create(SHADOW_ARR_CONFIGURATION_NAME)

        // get a reference to the implementation configuration for later
        val implementationConfig = project.configurations.getByName("implementation")

        project.afterEvaluate {
            val unzipDir = project.mkdir(UNZIP_AAR_DIR)
            val relocations = extension.relocations.get()

            // resolve the aarImplementation configuration so that we get the artifacts
            // for all of the primary dependencies and transitive dependencies. We'll
            // then look for any AARs that are associated with the groups of the
            // primary dependencies of the aarImplementation configuration. Any matching
            // AARs we'll unzip and extract the classes.jar file. We'll then take those
            // extracted JARs and add them directly to the normal implementation
            // configuration. Finally, we'll apply the shadow plugin on the norm
            val allArtifacts = aarConfig.resolvedConfiguration.resolvedArtifacts
            val aarTopLevelDeps = aarConfig.dependencies
            val aarDepsForRelocation =
                getAarsWithTopLevelDependencyGroups(allArtifacts, aarTopLevelDeps)
            val jarDepsForRelocation =
                getJarsWithTopLevelDependencyGroups(allArtifacts, aarTopLevelDeps)
            val allJarsForRelocation =
                aarDepsForRelocation.map {
                    extractJarfromAar(project, unzipDir, it)
                } + jarDepsForRelocation.map { it.file }

            // since the ShadowJar plugin is a Task, we need to create an instance
            // of a ShadowJar task to perform the package namespace relocation
            val task = project.tasks.register(SHADOW_JAR_TASK_NAME, ShadowJar::class.java) {
                from(allJarsForRelocation)
                relocations.forEach {
                    val (namespaceFrom, namespaceTo) = it
                    relocate(namespaceFrom, namespaceTo)
                }
            }

            project.dependencies {
                val artifactsNotInNeedOfRelocation =
                    allArtifacts - aarDepsForRelocation.toSet() - jarDepsForRelocation.toSet()
                artifactsNotInNeedOfRelocation.forEach {
                    // any artifacts that were resolved from the `aarImplementation` that
                    // are not in need of being relocated with the ShadowJar plugin should
                    // be added directly to the standard `implementation` configuration.
                    // We want to add their coordinates and NOT the artifacts so
                    // that Gradle can accurately build the dependency graph. If we just
                    // add the artifacts, then Gradle will not be able to tell if these
                    // artifacts are duplicates, which would cause runtime and buildtime
                    // errors.
                    val depCoords = getFullyQualifiedDependencyCoordinates(it)
                    add(implementationConfig.name, depCoords)
                }

                // finally, add all of the Jars or Jars extract from Aars that have been
                // processed by the ShadowJar plugin to the implementation configuration
                // so they are exposed on the runtime classpath and the buildtime classpath
                val relocatedJars = task.get().outputs.files
                add(implementationConfig.name, relocatedJars)
            }


        }
    }
}
