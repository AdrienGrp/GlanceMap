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
    exclude("btools/expressions/EncodeDecodeTest.class")
}

dependencies {
    api(project(":brouter-util"))
    api(project(":brouter-codec"))

    testImplementation(libs.junit)
}
