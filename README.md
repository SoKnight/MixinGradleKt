![MixinGradle Logo](docs/logo.png?raw=true)

**MixinGradle** is a [Gradle](http://gradle.org/) plugin which simplifies the build-time complexity of working with the **[SpongePowered Mixin](https://github.com/SpongePowered/Mixin)** framework for Java. It currently only supports usage with **[ForgeGradle](https://github.com/MinecraftForge/ForgeGradle)**.

### Features

**MixinGradle** automates the following tasks:

* Locating (via **ForgeGradle**) and supplying input mapping files to the [Mixin](https://github.com/SpongePowered/Mixin) [Annotation Processor](https://github.com/SpongePowered/Mixin/wiki/Using-the-Mixin-Annotation-Processor)
* Providing processing options to the [Annotation Processor](https://github.com/SpongePowered/Mixin/wiki/Using-the-Mixin-Annotation-Processor)
* Contributing the generated [reference map (refmap)](https://github.com/SpongePowered/Mixin/wiki/Introduction-to-Mixins---Obfuscation-and-Mixins#511-the-mixin-reference-map-refmap) to the corresponding sourceSet compile task outputs
* Contributing the generated SRG files to appropriate **ForgeGradle** `reobf` tasks

### Using MixinGradle

To use **MixinGradle** you *must* be using **[ForgeGradle](https://github.com/MinecraftForge/ForgeGradle)**.
To configure the plugin for your build:

1. Add the MixinGradle plugin to your `plugins` block:

    ```kotlin
    plugins {
        id("org.spongepowered.mixin") version "0.7-SNAPSHOT"
    }
    ```

    Please ensure you are using the correct version of MixinGradle for your ForgeGradle version. Versions are not interchangeable.

    | ForgeGradle Version | Mixin Version | MixinGradle Version To Use |
    |---------------------|---------------|----------------------------|
    | 2.3                 | 0.8 and below | `0.6-SNAPSHOT`             |
    | 3.0+                | 0.8           | `0.7-SNAPSHOT`             |

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
        // Specify "notch" or "searge" here
        defaultObfuscationEnv("notch")
    }
    ```

### Building MixinGradle

**MixinGradle** can be built using [Gradle](http://gradle.org/). To perform a build simply execute:

```bash
./gradlew build
```

To add the compiled jar to your local maven repository, run:

```bash
./gradlew publishToMavenLocal
```
