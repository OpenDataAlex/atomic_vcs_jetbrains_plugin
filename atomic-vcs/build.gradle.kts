plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform.module")
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":atomic-core"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    intellijPlatform {
        intellijIdeaCommunity(
            libs.versions.intellij.ide
                .get(),
        )
        bundledModule("intellij.platform.vcs.impl")
        bundledModule("intellij.platform.vcs.log.impl")
    }
}

tasks.test {
    useJUnitPlatform()
}
