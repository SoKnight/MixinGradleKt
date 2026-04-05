import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.gradleutils)
    alias(libs.plugins.kotlin.jvm)
}

group = "org.spongepowered"
version = "0.7-SNAPSHOT"

base {
    archivesName.set("mixingradle")
}

kotlin {
    coreLibrariesVersion = libs.versions.kotlin.get()

    compilerOptions {
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }

    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.gradle.org/gradle/libs-releases-local/")
}

dependencies {
    implementation("org.ow2.asm:asm-tree:9.2")
}

gradlePlugin {
    website.set("http://www.gradle.org/")
    vcsUrl.set("https://github.com/SpongePowered/MixinGradle")

    plugins {
        create("patcher") {
            id = "org.spongepowered.mixin"
            implementationClass = "org.spongepowered.asm.gradle.plugins.MixinGradlePlugin"
            displayName = "SpongePowered Mixin Gradle Plugin"
            description = "Gradle plugin for SpongePowered Mixin"
            tags.addAll("spongepowered", "sponge", "mixin")
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
