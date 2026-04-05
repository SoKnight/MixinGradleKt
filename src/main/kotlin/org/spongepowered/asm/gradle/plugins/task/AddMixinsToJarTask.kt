package org.spongepowered.asm.gradle.plugins.task

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.work.DisableCachingByDefault
import org.spongepowered.asm.gradle.plugins.ArtefactSpecificRefmap
import org.spongepowered.asm.gradle.plugins.MixinExtension
import java.io.File

@DisableCachingByDefault(because = "Mixin jar contribution task")
open class AddMixinsToJarTask : DefaultTask() {

    @Internal
    lateinit var extension: MixinExtension

    @Input
    lateinit var remappedJar: Jar

    @Input
    var reobfTasks: Set<Task> = mutableSetOf()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    val jarRefMaps: MutableSet<File> = mutableSetOf()

    @TaskAction
    fun run() {
        for (reobfTask in reobfTasks) {
            val jarTasks = reobfTask.dependsOn.filterIsInstance<Jar>().filter { it == remappedJar }.toMutableSet()
            tryResolveProducerTasks(reobfTask, jarTasks)

            for (jarTask in jarTasks) {
                contributeRefMaps(jarTask, reobfTask)
                contributeConfigs(jarTask, reobfTask)
            }
        }
    }

    private fun tryResolveProducerTasks(reobfTask: Task, jarTasks: MutableSet<Jar>) {
        try {
            val input = reobfTask.javaClass.methods
                .find { it.name == "getInput" }
                ?.invoke(reobfTask)
            if (input is org.gradle.api.internal.provider.ValueSupplier) {
                input.producer.visitProducerTasks { task ->
                    if (task == remappedJar && task is Jar) jarTasks.add(task)
                }
            }
        } catch (_: Throwable) {
            // ValueSupplier may not exist on all Gradle versions
        }
    }

    private fun contributeRefMaps(jarTask: Jar, reobfTask: Task) {
        for (refMap in jarRefMaps.filterIsInstance<ArtefactSpecificRefmap>()) {
            val archiveName = jarTask.archiveFileName.get()
            project.logger.info("Contributing refmap ({}) to {} in {}", refMap.refMap, archiveName, reobfTask.project)
            jarTask.refMaps.from(refMap)
            jarTask.from(refMap) { spec ->
                spec.into(refMap.refMap.parent ?: "")
            }
        }
    }

    private fun contributeConfigs(jarTask: Jar, reobfTask: Task) {
        if (extension.configNames.isEmpty()) return
        if (jarTask.manifest.attributes.containsKey("MixinConfigs")) return

        val csv = extension.configNames.joinToString(",")
        project.logger.info("Contributing configs ({}) to manifest of {} in {}", csv, jarTask.archiveFileName.get(), reobfTask.project)
        jarTask.manifest.attributes["MixinConfigs"] = csv
    }
}

internal val Jar.refMaps: ConfigurableFileCollection
    get() {
        val ext = (this as ExtensionAware).extensions.extraProperties
        if (!ext.has("refMaps")) {
            ext.set("refMaps", project.objects.fileCollection())
        }
        @Suppress("UNCHECKED_CAST")
        return ext.get("refMaps") as ConfigurableFileCollection
    }
