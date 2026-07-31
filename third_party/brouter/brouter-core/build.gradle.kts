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
    exclude("btools/router/RoutingEngineTest.class")
}

dependencies {
    api(project(":brouter-mapaccess"))
    api(project(":brouter-util"))
    api(project(":brouter-expressions"))
    api(project(":brouter-codec"))

    testImplementation(libs.junit)
}
