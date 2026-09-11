![MixinGradle Logo](docs/logo.png?raw=true)

**MixinGradleKt** is a Kotlin port of **[MixinGradle](https://github.com/SpongePowered/MixinGradle)**, the [Gradle](http://gradle.org/) plugin which simplifies the build-time complexity of working with the **[SpongePowered Mixin](https://github.com/SpongePowered/Mixin)** framework for Java. Like the original, it only supports usage with **[ForgeGradle](https://github.com/MinecraftForge/ForgeGradle)**.

### How it differs from MixinGradle

* Rewritten in Kotlin, for **Gradle 9**: tasks no longer reach the project while they run, which Gradle 10 will turn into an error
* Supports **ForgeGradle 7**, where the `reobf` extension is gone and the renamer plugin's `renameJar` task takes its place
* Eclipse support is dropped

### Features

**MixinGradleKt** automates the following tasks:

* Locating (via **ForgeGradle**) and supplying input mapping files to the [Mixin](https://github.com/SpongePowered/Mixin) [Annotation Processor](https://github.com/SpongePowered/Mixin/wiki/Using-the-Mixin-Annotation-Processor)
* Providing processing options to the [Annotation Processor](https://github.com/SpongePowered/Mixin/wiki/Using-the-Mixin-Annotation-Processor)
* Contributing the generated [reference map (refmap)](https://github.com/SpongePowered/Mixin/wiki/Introduction-to-Mixins---Obfuscation-and-Mixins#511-the-mixin-reference-map-refmap) to the corresponding sourceSet compile task outputs
* Contributing the generated TSRG files to the task that reobfuscates the jar: `renameJar` on ForgeGradle 7, the `reobf` tasks on earlier versions

### Compatibility

| Gradle | ForgeGradle | Mixin | Reobfuscation task         |
|--------|-------------|-------|----------------------------|
| 9+     | 7           | 0.8   | `renameJar` (renamer)      |
| 9+     | 3 – 6       | 0.8   | `reobf` (carried over)     |

ForgeGradle 2 is not supported; use MixinGradle `0.6-SNAPSHOT` for it.

### Using MixinGradleKt

MixinGradleKt is not published to a public repository, and it keeps the original plugin id and coordinates (`org.spongepowered.mixin`, `org.spongepowered:mixingradle:0.7-SNAPSHOT`). Make sure your build resolves this plugin rather than the one from SpongePowered's repository.

1. Bring the plugin into your build, either way works:

    * as an included build, e.g. a git submodule of this repository, in `settings.gradle.kts`:

        ```kotlin
        pluginManagement {
            includeBuild("path/to/MixinGradleKt")
        }
        ```

        ```kotlin
        plugins {
            id("org.spongepowered.mixin")
        }
        ```

    * or from your local Maven repository, after running `./gradlew publishToMavenLocal` here:

        ```kotlin
        pluginManagement {
            repositories {
                mavenLocal()
                gradlePluginPortal()
            }
        }
        ```

        ```kotlin
        plugins {
            id("org.spongepowered.mixin") version "0.7-SNAPSHOT"
        }
        ```

2. Create your `mixin` block, specify which sourceSets to process and provide refmap resource names for each one, the generated refmap will be added to the compiler task outputs automatically.

    ```kotlin
    mixin {
        add(sourceSets.main.get(), "main.refmap.json")
        add(sourceSets.named("another").get(), "another.refmap.json")
    }
    ```

3. Alternatively, you can simply specify the `refMap` extra property directly on your sourceSet:

    ```kotlin
    sourceSets {
        main {
            extra["refMap"] = "main.refmap.json"
        }

        create("another") {
            extra["refMap"] = "another.refmap.json"
        }
    }
    ```

4. You can define other mixin AP options in the `mixin` block, for example `disableTargetValidator` and `disableTargetExport`:

    ```kotlin
    mixin {
        disableTargetExport = true
        disableTargetValidator = true
    }
    ```

    You can also set the default obfuscation environment for generated refmaps, this is the obfuscation environment which will be contributed to the refmap's `mappings` node:

    ```kotlin
    mixin {
        // specify "notch" or "searge" here
        defaultObfuscationEnv = "notch"
    }
    ```

### Building MixinGradleKt

**MixinGradleKt** can be built using [Gradle](http://gradle.org/). To perform a build simply execute:

```bash
./gradlew build
```

To add the compiled jar to your local maven repository, run:

```bash
./gradlew publishToMavenLocal
```

### License

MixinGradleKt is licensed under the MIT License, see [LICENSE.txt](LICENSE.txt). It is based on MixinGradle by [SpongePowered](https://www.spongepowered.org) and its contributors.
