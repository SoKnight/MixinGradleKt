package org.spongepowered.asm.gradle.plugins.task

import org.gradle.api.provider.ListProperty
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Deferred configuration task")
open class ConfigureReobfTaskForPatcher : ConfigureReobfTaskBase() {

    @Suppress("UNCHECKED_CAST")
    override fun addMappingFile(mappingFile: File) {
        val add = listOf("--srg-in", mappingFile.absolutePath)
        val args = reobfTask.javaClass.methods
            .find { it.name == "getArgs" }
            ?.invoke(reobfTask)

        when (args) {
            is ListProperty<*> -> (args as ListProperty<String>).addAll(add)
            is MutableList<*> -> (args as MutableList<String>).addAll(add)
        }
    }

}
