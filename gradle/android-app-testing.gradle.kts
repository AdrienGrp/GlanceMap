import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("testImplementation", libs.findLibrary("junit").get())
    add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    add("androidTestImplementation", libs.findLibrary("androidx-junit").get())
    add("androidTestImplementation", libs.findLibrary("androidx-espresso-core").get())
    add("androidTestImplementation", platform(libs.findLibrary("compose-bom").get()))
    add("androidTestImplementation", libs.findLibrary("ui-test-junit4").get())
    add("debugImplementation", libs.findLibrary("ui-test-manifest").get())
}
