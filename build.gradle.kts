plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.intellij.platform.module) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":atomic-core"))
    implementation(project(":atomic-vcs"))
    implementation(project(":atomic-ai"))

    kover(project(":atomic-core"))
    kover(project(":atomic-vcs"))
    kover(project(":atomic-ai"))

    intellijPlatform {
        intellijIdeaCommunity(libs.versions.intellij.ide.get())
    }
}

allprojects {
    group = "dev.atomic"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        maven("https://cache-redirector.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven")
    }
}

val ktlintVersion = libs.versions.ktlint.get()

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(ktlintVersion)
                .setEditorConfigPath(rootProject.file(".editorconfig"))
                .editorConfigOverride(mapOf("max_line_length" to "160"))
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
                .setEditorConfigPath(rootProject.file(".editorconfig"))
                .editorConfigOverride(mapOf("max_line_length" to "160"))
        }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        source.setFrom("src/main/kotlin", "src/test/kotlin")
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }

    // Static type checking: warnings from the Kotlin compiler fail the build in CI (-PwarningsAsErrors=true).
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false))
        }
    }
}

kover {
    reports {
        verify {
            rule("Minimum line coverage") {
                // Floor just below the current level (about 49%); raise it as coverage improves.
                minBound(45)
            }
        }
    }
}


intellijPlatform {
    pluginConfiguration {
        id = "com.bluefiredatasolutions.com.atomic-vcs-integration"
        name = "Atomic VCS Support"
        version = "1.0.0-SNAPSHOT"
        description = "Adds support for the Atomic version control system to JetBrains IDEs: file statuses, recording changes, " +
            "diffs, the change graph in the Log tab, AI-agent provenance and MCP integration. Requires the atomic CLI. " +
            "This is an independent community plugin and is not affiliated with or endorsed by atomic.dev."
        vendor {
            name = "Blue Fire Data Solutions"
            url = "https://github.com/OpenDataAlex/atomic_vcs_jetbrains_plugin"
        }
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }
}
