package org.spongepowered.asm.gradle.plugins.struct

class DynamicProperties(private val name: String) {

    private val properties = mutableMapOf<String, DynamicProperties>()

    var value: String? = null

    fun child(childName: String): DynamicProperties =
        properties.getOrPut(childName) { DynamicProperties("$name.$childName") }

    fun set(childName: String, childValue: Any?) {
        child(childName).value = childValue?.toString()
    }

    val args: Map<String, String>
        get() {
            val result = mutableMapOf<String, String>()
            value?.let { result[name] = it }

            for ((_, child) in properties)
                result.putAll(child.args)

            return result
        }

}
