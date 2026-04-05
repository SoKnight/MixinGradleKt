package org.spongepowered.asm.gradle.plugins.meta

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import java.io.File
import java.io.PrintWriter
import java.util.zip.ZipInputStream

class Import(val file: File) {

    val targets = mutableListOf<String>()
    private var generated = false

    fun read(): Import {
        if (generated) return this
        if (file.isFile) readFile()
        this.generated = true
        return this
    }

    private fun readFile() {
        targets.clear()

        ZipInputStream(file.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".class")) {
                    val mixin = MixinScannerVisitor()
                    ClassReader(zin).accept(mixin, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

                    for (target in mixin.targets) {
                        targets.add("${mixin.name}\t$target")
                    }
                }

                entry = zin.nextEntry
            }
        }
    }

    fun appendTo(writer: PrintWriter): Import {
        read()
        targets.forEach(writer::println)
        return this
    }

    private class MixinScannerVisitor : ClassVisitor(Opcodes.ASM9) {

        var mixin: AnnotationNode? = null
        var name: String = ""

        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            this.name = name
        }

        override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
            if ("Lorg/spongepowered/asm/mixin/Mixin;" == desc)
                return AnnotationNode(desc).also { mixin = it }

            return super.visitAnnotation(desc, visible)
        }

        val targets: List<String>
            get() {
                val node = mixin ?: return emptyList()
                val result = mutableListOf<String>()

                val publicTargets = getAnnotationValue<List<Type>>(node, "value")
                publicTargets?.forEach { type ->
                    result.add(type.className.replace(".", "/"))
                }

                val privateTargets = getAnnotationValue<List<String>>(node, "targets")
                privateTargets?.forEach { type ->
                    result.add(type.replace(".", "/"))
                }

                return result
            }

        @Suppress("UNCHECKED_CAST")
        private fun <T> getAnnotationValue(node: AnnotationNode, key: String): T? {
            val values = node.values ?: return null
            var getNext = false

            for (value in values) {
                if (getNext) return value as T
                if (value == key) getNext = true
            }

            return null
        }

    }

}
