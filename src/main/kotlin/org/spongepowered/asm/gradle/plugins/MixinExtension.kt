package org.spongepowered.asm.gradle.plugins

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.util.internal.VersionNumber
import org.spongepowered.asm.gradle.plugins.meta.Imports
import org.spongepowered.asm.gradle.plugins.struct.DynamicProperties
import org.spongepowered.asm.gradle.plugins.task.AddMixinsToJarTask
import org.spongepowered.asm.gradle.plugins.task.ConfigureReobfTaskForPatcher
import org.spongepowered.asm.gradle.plugins.task.ConfigureReobfTaskForUserDev
import org.spongepowered.asm.gradle.plugins.task.refMaps
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Suppress("unused")
open class MixinExtension(private val project: Project) {

    internal val projectType: String = detectProjectType(project)
    internal var reobfTasks: MutableSet<Task> = mutableSetOf()
    internal val configNames = mutableSetOf<String>()

    private var applyDefault = true
    private val registeredSourceSets = mutableSetOf<SourceSet>()
    private val refMaps = mutableMapOf<String, String>()
    private val tokens = mutableMapOf<String, String>()
    private val systemProperties = DynamicProperties("mixin")
    private val addMixinsToJarTasks = mutableSetOf<AddMixinsToJarTask>()
    private val extraMappings = mutableListOf<Any>()
    private val importConfigs = mutableSetOf<Any>()
    private val importLibs = mutableSetOf<Any>()
    private var mixinVersionForErrors: VersionNumber? = null

    var disableRefMapWarning = false
    var disableTargetValidator = false
    var disableTargetExport = false
    var disableOverwriteChecker = false
    var disableAnnotationProcessorCheck = false
    var overwriteErrorLevel: Any? = null
    var defaultObfuscationEnv: String? = "searge"
    var mappingTypes: MutableList<Any> = mutableListOf("tsrg")
    var reobfSrgFile: Any? = null
    var quiet = false
    var showMessageTypes = false
    var messages = mutableMapOf<String, String>()

    val mappings: File?
        get() = when {
            reobfSrgFile != null -> project.file(reobfSrgFile!!)
            projectType == "userdev" -> project.tasks.getByName("createMcpToSrg").outputs.files.singleFile
            projectType == "patcher" -> project.tasks.getByName("createMcp2Srg").outputs.files.singleFile
            else -> null
        }

    init {
        project.afterEvaluate {
            gatherReobfTasks()
            configureSourceSetsWithRefMap()
            propagateToUpstreamProjects()
            applyDefault()
            configureRuns()
        }
    }

    // -- Public API --

    fun add(set: String) =
        add(project.sourceSets.getByName(set))

    fun add(set: SourceSet) {
        if (!set.ext.has("refMap")) {
            throw InvalidUserDataException("No 'refMap' or 'ext.refMap' defined on $set. Call 'add(sourceSet, refMapName)' instead.")
        }
        manuallyAdd(set)
    }

    fun add(set: String, refMapName: Any) {
        val sourceSet = project.sourceSets.findByName(set)
            ?: throw InvalidUserDataException("No sourceSet '$set' was found")
        sourceSet.ext.set("refMap", refMapName)
        manuallyAdd(sourceSet)
    }

    fun add(set: SourceSet, refMapName: Any) {
        set.ext.set("refMap", refMapName.toString())
        manuallyAdd(set)
    }

    fun config(path: String) {
        configNames.add(path)
    }

    fun extraMappings(file: Any) {
        extraMappings.add(file)
    }

    fun token(name: Any) = token(name, "true")

    fun token(name: Any, value: Any) {
        tokens[name.toString().trim()] = value.toString().trim()
    }

    fun tokens(map: Map<String, *>): MixinExtension {
        for ((key, value) in map) {
            tokens[key.trim()] = value.toString().trim()
        }
        return this
    }

    fun getTokens(): Map<String, String> = tokens.toMap()

    fun importConfig(config: Any?) {
        config ?: throw InvalidUserDataException("Cannot import from null config")
        importConfigs.add(config)
    }

    fun importLibrary(lib: Any?) {
        lib ?: throw InvalidUserDataException("Cannot import null library")
        importLibs.add(lib)
    }

    // Directive methods (for Groovy-style DSL usage without `= true`)
    fun disableRefMapWarning() { disableRefMapWarning = true }
    fun disableTargetValidator() { disableTargetValidator = true }
    fun disableTargetExport() { disableTargetExport = true }
    fun disableOverwriteChecker() { disableOverwriteChecker = true }
    fun disableAnnotationProcessorCheck() { disableAnnotationProcessorCheck = true }
    fun quiet() { quiet = true }
    fun showMessageTypes() { showMessageTypes = true }
    fun overwriteErrorLevel(errorLevel: Any) { overwriteErrorLevel = errorLevel }

    // System property accessors (used in DSL: mixin { debug.verbose = true })
    val debug get() = systemProperties.child("debug")
    val checks get() = systemProperties.child("checks")
    val dumpTargetOnFailure get() = systemProperties.child("dumpTargetOnFailure")
    val ignoreConstraints get() = systemProperties.child("ignoreConstraints")
    val hotSwap get() = systemProperties.child("hotSwap")
    val env get() = systemProperties.child("env")
    val initialiserInjectionMode get() = systemProperties.child("initialiserInjectionMode")

    // -- Initialization --

    private fun gatherReobfTasks() {
        when (projectType) {
            "userdev" -> {
                val reobf = project.extensions.getByName("reobf") as Iterable<*>
                reobf.filterIsInstance<Task>().forEach { reobfTasks.add(it) }
            }
            "patcher" -> {
                val reobfJar = project.property("reobfJar")
                if (reobfJar is Task) reobfTasks.add(reobfJar)
            }
        }
    }

    private fun configureSourceSetsWithRefMap() {
        project.sourceSets.forEach { set ->
            if (set.ext.has("refMap")) {
                configure(set, projectType)
            }
        }
    }

    private fun propagateToUpstreamProjects() {
        project.configurations.getByName("implementation")
            .allDependencies.withType(ProjectDependency::class.java)
            .forEach { upstream ->
                val depProject = project.project(upstream.path)
                val mixinExt = depProject.extensions.findByName("mixin") as? MixinExtension ?: return@forEach
                val reobf = project.extensions.getByName("reobf") as Iterable<*>
                reobf.filterIsInstance<Task>().forEach { mixinExt.reobfTasks.add(it) }
            }
    }

    private fun manuallyAdd(set: SourceSet) {
        applyDefault = false
        val pType = projectType
        project.afterEvaluate { configure(set, pType) }
    }

    private fun applyDefault() {
        if (!applyDefault) return
        applyDefault = false
        project.logger.info("No sourceSets added for mixin processing, applying defaults")
        disableRefMapWarning = true
        project.sourceSets.forEach { set ->
            if (!set.ext.has("refMap")) {
                set.ext.set("refMap", "mixin.refmap.json")
            }
            configure(set, projectType)
        }
    }

    // -- SourceSet Configuration --

    private fun configure(set: SourceSet, projectType: String) {
        if (!registeredSourceSets.add(set)) {
            project.logger.info("Not adding {} to mixin processor, sourceSet already added", set)
            return
        }

        project.logger.info("Adding {} to mixin processor", set)

        val compileTask = project.tasks.getByName(set.compileJavaTaskName) as? JavaCompile
            ?: throw InvalidUserDataException("Cannot add non-java $set to mixin processor")

        applyDefault = false

        val refMapFile = project.file("${compileTask.temporaryDir}/${compileTask.name}-refmap.json")
        val tsrgFile = project.file("${compileTask.temporaryDir}/${compileTask.name}-mappings.tsrg")
        val refMapName = set.ext.get("refMap").toString()

        compileTask.ext.set("outTsrgFile", tsrgFile)
        compileTask.ext.set("refMapFile", refMapFile)
        compileTask.ext.set("refMap", refMapName)
        set.ext.set("refMapFile", refMapFile)

        when (this.projectType) {
            "userdev" -> compileTask.dependsOn("createMcpToSrg")
            "patcher" -> compileTask.dependsOn("createMcp2Srg")
        }

        compileTask.doFirst {
            val currentRefMap = compileTask.ext.get("refMap") as String
            val existing = refMaps[currentRefMap]
            if (!disableRefMapWarning && existing != null) {
                project.logger.warn(
                    "Potential refmap conflict. Duplicate refmap name {} specified for sourceSet {}, already defined for sourceSet {}",
                    currentRefMap, set.name, existing
                )
            } else {
                refMaps[currentRefMap] = set.name
            }
            refMapFile.delete()
            tsrgFile.delete()
            checkTokens()
            applyCompilerArgs(compileTask)
        }

        val taskSpecificRefMap = ArtefactSpecificRefmap(refMapFile.parentFile, refMapName)

        compileTask.doLast {
            taskSpecificRefMap.delete()
            if (refMapFile.exists()) {
                taskSpecificRefMap.parentFile.mkdirs()
                Files.copy(refMapFile.toPath(), taskSpecificRefMap.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        configureJarTasks(compileTask, taskSpecificRefMap, projectType)
        configureReobfTasks(tsrgFile)
    }

    private fun configureJarTasks(compileTask: JavaCompile, taskSpecificRefMap: ArtefactSpecificRefmap, projectType: String) {
        project.tasks.withType(Jar::class.java).configureEach { jarTask ->
            val taskName = "addMixinsTo${jarTask.name.replaceFirstChar { it.uppercase() }}"
            val addTask = project.tasks.maybeCreate(taskName, AddMixinsToJarTask::class.java).apply {
                doFirst { checkForAnnotationProcessors() }
                extension = this@MixinExtension
                dependsOn(compileTask)
                remappedJar = jarTask
                reobfTasks = this@MixinExtension.reobfTasks
                jarRefMaps.add(taskSpecificRefMap)
            }
            jarTask.dependsOn(addTask)
            addMixinsToJarTasks.add(addTask)

            if (projectType == "patcher" && jarTask.name == "universalJar") {
                project.logger.info("Contributing refmap ({}) to {} in {}", taskSpecificRefMap, jarTask.archiveFileName.get(), project)
                jarTask.refMaps.from(taskSpecificRefMap)
                jarTask.from(taskSpecificRefMap)
            }
        }
    }

    private fun configureReobfTasks(tsrgFile: File) {
        val taskType = when (projectType) {
            "patcher" -> ConfigureReobfTaskForPatcher::class.java
            "userdev" -> ConfigureReobfTaskForUserDev::class.java
            else -> return
        }

        for (reobfTask in reobfTasks) {
            val taskName = "configureReobfTaskFor${reobfTask.name.replaceFirstChar { it.uppercase() }}"
            val configTask = project.tasks.maybeCreate(taskName, taskType).apply {
                this.reobfTask = reobfTask
                mappingFiles.add(tsrgFile)
            }
            reobfTask.dependsOn(configTask)
        }
    }

    // -- Run Configuration --

    private fun configureRuns() {
        if (projectType != "userdev") return
        val minecraft = project.extensions.findByName("minecraft") ?: return

        val runs = minecraft.invokeMethod("getRuns") as? Iterable<*> ?: return
        for (runConfig in runs) {
            runConfig ?: continue
            configureSingleRun(runConfig)
        }
    }

    private fun configureSingleRun(runConfig: Any) {
        if (project.tasks.findByName("createSrgToMcp") != null) {
            val srgToMcpFile = project.tasks.getByName("createSrgToMcp").outputs.files.singleFile.path
            runConfig.invokeMethod("property", "net.minecraftforge.gradle.GradleStart.srg.srg-mcp", srgToMcpFile)

            val properties = runConfig.invokeMethod("getProperties") as? Map<*, *> ?: emptyMap<String, Any>()
            if (!properties.containsKey("mixin.env.remapRefMap")) {
                runConfig.invokeMethod("property", "mixin.env.remapRefMap", "true")
                runConfig.invokeMethod("property", "mixin.env.refMapRemappingFile", srgToMcpFile)
            }
        }

        for (configName in configNames) {
            runConfig.invokeMethod("args", "--mixin.config", configName)
        }
        for ((key, value) in systemProperties.args) {
            runConfig.invokeMethod("property", key, value)
        }
    }

    // -- Annotation Processor Checks --

    internal fun checkForAnnotationProcessors() {
        if (disableAnnotationProcessorCheck) return

        val missingAPs = findMissingAnnotationProcessors()
        if (missingAPs.isEmpty()) return

        val missingAPNames = missingAPs.map { it.annotationProcessorConfigurationName }
        val addAPName = if (missingAPNames.size > 1) "<configurationName>" else missingAPNames[0]
        val eachOfThese = if (missingAPNames.size > 1) " where <configurationName> is each of $missingAPNames." else ""
        val mixinVersion = mixinVersionForErrors ?: "0.1.2-SNAPSHOT"

        throw MixinGradleException(
            "Gradle ${project.gradle.gradleVersion} was detected but the mixin dependency was missing from one or more " +
            "Annotation Processor configurations: $missingAPNames. To enable the Mixin AP please include the mixin " +
            "processor artefact in each Annotation Processor configuration. For example if you are using mixin dependency " +
            "'org.spongepowered:mixin:$mixinVersion' you should specify: dependencies { $addAPName " +
            "'org.spongepowered:mixin:$mixinVersion:processor' }$eachOfThese. If you believe you are seeing this message " +
            "in error, you can disable this check via disableAnnotationProcessorCheck() in your mixin { } block."
        )
    }

    private fun findMissingAnnotationProcessors(): Set<SourceSet> {
        val missing = mutableSetOf<SourceSet>()
        for (sourceSet in registeredSourceSets) {
            val mixinDep = findMixinDependency(sourceSet.implementationConfigurationName) ?: continue
            val mainVersion = getDependencyVersion(mixinDep)
            if (mainVersion != null && (mixinVersionForErrors == null || mainVersion > mixinVersionForErrors)) {
                mixinVersionForErrors = mainVersion
            }
            val apDep = findMixinDependency(sourceSet.annotationProcessorConfigurationName)
            if (apDep != null) {
                val apVersion = getDependencyVersion(apDep)
                if (mainVersion != null && apVersion != null && mainVersion > apVersion) {
                    project.logger.warn(
                        "Mixin AP version ({}) in configuration '{}' is older than compile version ({})",
                        apVersion, sourceSet.annotationProcessorConfigurationName, mainVersion
                    )
                }
            } else {
                missing.add(sourceSet)
            }
        }
        return missing
    }

    private fun findMixinDependency(configurationName: String): Any? {
        val config = project.configurations.getByName(configurationName)
        return if (config.isCanBeResolved) {
            config.resolvedConfiguration.resolvedArtifacts.find { ":mixin:" in it.id.toString() }
        } else {
            config.allDependencies.find { it.group?.contains("spongepowered") == true && "mixin" in it.name }
        }
    }

    private fun getDependencyVersion(dependency: Any): VersionNumber? = when (dependency) {
        is ResolvedArtifact -> VersionNumber.parse(dependency.moduleVersion.id.version)
        is Dependency -> dependency.version?.let { VersionNumber.parse(it) }
        else -> null
    }

    // -- Compiler Args --

    private fun applyCompilerArgs(compileTask: JavaCompile) {
        val mappingsFile = mappings ?: return
        val args = compileTask.options.compilerArgs

        args += "-AreobfTsrgFile=${mappingsFile.canonicalPath}"
        args += "-AoutTsrgFile=${(compileTask.ext.get("outTsrgFile") as File).canonicalPath}"
        args += "-AoutRefMapFile=${(compileTask.ext.get("refMapFile") as File).canonicalPath}"
        args += "-AmappingTypes=tsrg"
        args += "-ApluginVersion=${MixinGradlePlugin.VERSION}"

        if (disableTargetValidator) args += "-AdisableTargetValidator=true"
        if (disableTargetExport) args += "-AdisableTargetExport=true"
        if (disableOverwriteChecker) args += "-AdisableOverwriteChecker=true"
        overwriteErrorLevel?.let { args += "-AoverwriteErrorLevel=${it.toString().trim()}" }
        defaultObfuscationEnv?.let { args += "-AdefaultObfuscationEnv=$it" }
        if (mappingTypes.isNotEmpty()) args += "-AmappingTypes=${mappingTypes.joinToString(",")}"
        if (tokens.isNotEmpty()) args += "-Atokens=${tokens.entries.joinToString(";") { "${it.key}=${it.value}" }}"
        if (extraMappings.isNotEmpty()) args += "-AreobfTsrgFiles=${extraMappings.joinToString(";") { project.file(it).toString() }}"

        generateImportsFile(compileTask)?.let { args += "-AdependencyTargetsFile=${it.canonicalPath}" }

        if (quiet) args += "-Aquiet=true"
        if (showMessageTypes) args += "-AshowMessageTypes=true"

        for ((property, level) in messages) {
            if (property.matches(Regex("^[A-Z][A-Z_]+$")) && level.matches(Regex("^(note|warning|error|disabled)$"))) {
                args += "-AMSG_$property=$level"
            }
        }
    }

    private fun checkTokens() {
        for ((key, value) in tokens) {
            if (';' in value) {
                throw InvalidUserDataException("Invalid token value '$value' for token '$key'")
            }
        }
    }

    private fun generateImportsFile(compileTask: JavaCompile): File? {
        val importsFile = File(compileTask.temporaryDir, "mixin.imports.json")
        importsFile.delete()

        val libs = mutableSetOf<File>()
        for (cfg in importConfigs) {
            val config = cfg as? Configuration ?: project.configurations.findByName(cfg.toString())
            config?.files?.let { libs.addAll(it) }
        }

        for (lib in importLibs) {
            libs.add(project.file(lib))
        }

        if (libs.isEmpty()) return null

        importsFile.outputStream().use { stream ->
            val writer = PrintWriter(stream)
            for (lib in libs) {
                Imports[lib].appendTo(writer)
            }
            writer.flush()
        }

        return importsFile
    }

    companion object {
        private fun detectProjectType(project: Project): String = when {
            project.extensions.findByName("minecraft") != null -> "userdev"
            project.extensions.findByName("patcher") != null -> "patcher"
            else -> throw InvalidUserDataException(
                "Could not find property 'minecraft', or 'patcher' on $project, ensure ForgeGradle is applied."
            )
        }
    }
}

// -- Extension property helpers --

private val Project.sourceSets: SourceSetContainer
    get() = extensions.getByType(SourceSetContainer::class.java)

private val SourceSet.ext
    get() = (this as ExtensionAware).extensions.extraProperties

private val Task.ext
    get() = (this as ExtensionAware).extensions.extraProperties

private fun Any.invokeMethod(name: String, vararg args: Any?): Any? = try {
    javaClass.methods
        .find { it.name == name && it.parameterCount == args.size }
        ?.invoke(this, *args)
} catch (_: Exception) {
    null
}
