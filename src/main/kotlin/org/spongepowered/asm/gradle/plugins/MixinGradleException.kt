package org.spongepowered.asm.gradle.plugins

import org.gradle.api.GradleException

class MixinGradleException(message: String, cause: Throwable? = null) : GradleException(message, cause)
