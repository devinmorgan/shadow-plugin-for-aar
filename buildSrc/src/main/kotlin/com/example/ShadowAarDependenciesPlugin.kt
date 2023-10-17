package com.example

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.get
import kotlin.io.path.createTempDirectory

private fun getGroupFromArtifact(artifact: String): String? {
    val parts = artifact.split(":")
    return if (parts.size >= 3) parts[0] else null
}

abstract class ShadowAarDependenciesPluginExtension {
    @get:Input
    abstract val targetAar: Property<String>

    @get:Input
    abstract val targetPackageName: Property<String>

    @get:Input
    abstract val destinationPackageName: Property<String>

    @get:Input
    abstract val subDependencies: ListProperty<String>
}

class ShadowAarDependenciesPlugin : Plugin<Project> {
    private val InternalAarConfigName = "internalARR"
    private val InternalJarConfigName = "internalJAR"

    private fun manuallyIncludeSubDependencies(project: Project, subDependencies: List<String>) {
        subDependencies.forEach {
            project.dependencies.add("implementation", it)
        }
    }

    private fun addTargetAarDependency(project: Project, targetAar: String) {
        project.dependencies.add(InternalAarConfigName, targetAar)
        project.dependencies.add(InternalJarConfigName, project.tasks.named("extractJARFromAARThatMayConflict").get().outputs.files)
        project.dependencies.add("implementation", project.tasks.named("internalizeJARs").get().outputs.files)
    }

    private fun addExtractJARFromAARThatMayConflictTask(project: Project, targetAar: String) {
        project.tasks.register("extractJARFromAARThatMayConflict") {
            val internalAARs = project.configurations["internalARR"].resolvedConfiguration.resolvedArtifacts

            // pick out the .aar artifacts so we can try to bundle them
            val targetAarGroup = getGroupFromArtifact(targetAar)
            val ddArtifacts = internalAARs.filter {
                it.moduleVersion.id.group == targetAarGroup && it.extension == "aar"
            }

            // TODO: is it possible to delete this temp directory at a
            //  later point?
            val tempDir = createTempDirectory()

            // use the configurations property to let the ShadowJarTask
            // know which artifacts we want it to operate on. In our case
            // we want it to operate on the targetAarGroup's .jar file.
            // These .jar artifacts called classes.jar inside .aar the file
            // so we need to  unzip the .aar files
            val jarArtifacts = ddArtifacts.map {
                // we need a unique name for each .jar since all .jars have
                // classes.jar as their name inside of an .aar
                val jarName = "${it.moduleVersion.id.name}.jar"

                // extract the contents of the .aar file (it's just a .zip file!)
                // and give it the unique name
                project.copy {
                    from(project.zipTree(it.file)) { rename("classes.jar", jarName) }
                    into(tempDir)
                }

                // go from path to the .jar file to a File object representing
                // the .jar file and, finally, to a FileCollection object that
                // wraps the File object. We do this because the ShadowJar
                // plugin is expecting a FileCollection object
                val jarPath = tempDir.resolve(jarName)
                project.files(jarPath)
            }

            // output the JARs so we can continue processing them
            outputs.files(jarArtifacts)
        }
    }

    private fun addInternalizeJARsTask(project: Project, targetPackageName: String, destinationPackageName: String) {
        project.tasks.register("internalizeJARs", ShadowJar::class.java) {
            configurations = mutableListOf(project.configurations["internalJAR"] as FileCollection)

            // the whole point is to move the aar artifacts to be under
            // the destinationPackageName's namespace to avoid collisions
            relocate(targetPackageName, destinationPackageName)
        }
    }

    private fun createCustomDependencyConfigurations(project: Project) {
        project.configurations.create(InternalAarConfigName)
        project.configurations.create(InternalJarConfigName)
    }

    override fun apply(project: Project) {
        // create an extension for easier plugin config
        val extension = project.extensions.create("shadowAarDependencies", ShadowAarDependenciesPluginExtension::class.java)

        project.afterEvaluate {
            val targetAar = extension.targetAar.get()
            val targetPackageName = extension.targetPackageName.get()
            val destinationPackageName = extension.destinationPackageName.get()
            val subDependencies = extension.subDependencies.get()

            // create custom dependency configurations. These configurations
            // will form a pipeline like so:
            // internalAAR --> internalJAR --> implementation
            createCustomDependencyConfigurations(project)

            // register the tasks first because they are
            // references when adding dependencies
            addExtractJARFromAARThatMayConflictTask(project, targetAar)
            addInternalizeJARsTask(project, targetPackageName, destinationPackageName)

            // add target aar dependency & it's sub-dependencies
            manuallyIncludeSubDependencies(project, subDependencies)
            addTargetAarDependency(project, targetAar)
        }
    }
}
