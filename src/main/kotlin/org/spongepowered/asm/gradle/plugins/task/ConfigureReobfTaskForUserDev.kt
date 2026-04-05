package org.spongepowered.asm.gradle.plugins.task

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Deferred configuration task")
open class ConfigureReobfTaskForUserDev : ConfigureReobfTaskBase() {

    override fun addMappingFile(mappingFile: File) {
        val extraMappings = reobfTask.javaClass.methods
            .find { it.name == "getExtraMappings" }
            ?.invoke(reobfTask)

        if (extraMappings is ConfigurableFileCollection) {
            extraMappings.from(mappingFile)
        } else {
            reobfTask.javaClass.methods
                .find { it.name == "extraMapping" && it.parameterCount == 1 }
                ?.invoke(reobfTask, mappingFile)
        }
    }

}
