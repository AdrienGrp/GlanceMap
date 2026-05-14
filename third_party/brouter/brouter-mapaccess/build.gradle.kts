plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<Test>().configureEach {
    exclude("btools/mapaccess/IntegrityCheckTest.class")
}

dependencies {
    api(project(":brouter-util"))
    api(project(":brouter-codec"))
    api(project(":brouter-expressions"))

    testImplementation(libs.junit)
}
