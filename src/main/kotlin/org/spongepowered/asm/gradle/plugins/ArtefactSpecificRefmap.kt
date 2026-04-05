package org.spongepowered.asm.gradle.plugins

import java.io.File

class ArtefactSpecificRefmap(parent: File, refMap: String) : File(parent, refMap) {
    val refMap: File = File(refMap)
}
