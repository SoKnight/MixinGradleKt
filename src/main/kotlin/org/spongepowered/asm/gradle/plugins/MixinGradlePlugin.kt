package org.spongepowered.asm.gradle.plugins

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.internal.VersionNumber

class MixinGradlePlugin : Plugin<Project> {

    companion object {
        @JvmField
        val VERSION: String = detectCurrentVersion() ?: "0.7"

        private fun detectCurrentVersion(): String? = try {
            val implVersion = MixinGradlePlugin::class.java.`package`?.implementationVersion
            val match = implVersion?.let { Regex("""^(\d+\.\d+(\.\d+))""").find(it) }
            match?.let { VersionNumber.parse(it.groupValues[1]).toString() }
        } catch (_: Throwable) {
            null
        }
    }

    override fun apply(project: Project) {
        checkEnvironment(project)
        project.extensions.create("mixin", MixinExtension::class.java, project)
    }

    private fun checkEnvironment(project: Project) {
        if (project.tasks.findByName("genSrgs") != null) {
            throw InvalidUserDataException("Found a 'genSrgs' task on $project, this version of MixinGradle does not support ForgeGradle 2.x.")
        }
        if (project.extensions.findByName("minecraft") == null && project.extensions.findByName("patcher") == null) {
            throw InvalidUserDataException("Could not find property 'minecraft', or 'patcher' on $project, ensure ForgeGradle is applied.")
        }
    }
}
