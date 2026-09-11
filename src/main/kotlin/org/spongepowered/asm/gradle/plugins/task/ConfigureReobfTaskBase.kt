package org.spongepowered.asm.gradle.plugins.task

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Deferred configuration task")
abstract class ConfigureReobfTaskBase : DefaultTask() {

    @Internal
    lateinit var reobfTask: Task

    @Internal
    val mappingFiles: MutableSet<File> = mutableSetOf()

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        for (mappingFile in mappingFiles) {
            if (mappingFile.exists()) {
                logger.info("Contributing TSRG mappings ({}) to {}", mappingFile, reobfTask.path)
                addMappingFile(mappingFile)
            } else {
                logger.debug("TSRG file ({}) not found, skipping", mappingFile)
            }
        }
    }

    protected abstract fun addMappingFile(mappingFile: File)

}
