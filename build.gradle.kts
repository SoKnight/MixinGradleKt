plugins {
    `java-gradle-plugin`
    `maven-publish`
    groovy // TODO: remove after full Kotlin migration
    alias(libs.plugins.gradleutils)
    alias(libs.plugins.kotlin.jvm)
}

group = "org.spongepowered"
version = "0.7-SNAPSHOT"

base {
    archivesName.set("mixingradle")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven("https://repo.gradle.org/gradle/libs-releases-local/")
}

dependencies {
    implementation("com.google.guava:guava:21.0")
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
