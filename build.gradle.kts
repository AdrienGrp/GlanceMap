import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

buildscript {
    dependencies {
        // Keep Kotlin plugin pinned explicitly when AGP bundles an older Kotlin.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

subprojects {
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension> {
            ignoreFailures.set(false)
            filter {
                exclude("**/build/**")
                exclude("**/generated/**")
            }
        }
    }

    plugins.withId("io.gitlab.arturbosch.detekt") {
        val javaToolchains = extensions.getByType(JavaToolchainService::class.java)
        val java17Launcher = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            parallel = true
            ignoreFailures = false
            config.setFrom(rootProject.file("detekt.yml"))
            baseline = file("detekt-baseline.xml")
            basePath = rootProject.projectDir.absolutePath
        }

        tasks.withType<Detekt>().configureEach {
            jvmTarget = "17"
            jdkHome.set(java17Launcher.map { it.metadata.installationPath })
            reports {
                html.required.set(true)
                sarif.required.set(true)
                xml.required.set(false)
                md.required.set(false)
                txt.required.set(false)
            }
        }

        tasks.withType<DetektCreateBaselineTask>().configureEach {
            jvmTarget = "17"
            jdkHome.set(java17Launcher.map { it.metadata.installationPath })
        }
    }
}
