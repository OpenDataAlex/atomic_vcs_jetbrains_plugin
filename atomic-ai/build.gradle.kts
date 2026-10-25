plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.intellij.platform.module")
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":atomic-core"))
    implementation(project(":atomic-vcs"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    intellijPlatform {
        intellijIdeaCommunity(
            libs.versions.intellij.ide
                .get(),
        )
    }
}

tasks.test {
    useJUnitPlatform()
}
