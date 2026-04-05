package org.spongepowered.asm.gradle.plugins.meta

import java.io.File

object Imports {

    private val imports = mutableMapOf<File, Import>()

    operator fun get(file: File): Import =
        imports.getOrPut(file) { Import(file).read() }

}
