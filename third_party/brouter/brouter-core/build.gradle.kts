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

dependencies {
    api(project(":brouter-mapaccess"))
    api(project(":brouter-util"))
    api(project(":brouter-expressions"))
    api(project(":brouter-codec"))
}
