import com.diffplug.gradle.spotless.JavaExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin

apply<SpotlessPlugin>()

extensions.configure<SpotlessExtension> {
    java {
        target(project.fileTree("src") { include("**/com/velocitypowered/**/*.java") })
        if (project.name == "velocity-api") {
            licenseHeaderFile(file("HEADER.txt"))
            targetExclude("**/java/com/velocitypowered/api/util/Ordered.java")
        } else {
            licenseHeaderFile(rootProject.file("HEADER.txt"))
        }
        removeUnusedImports()
    }
    format("javaCtd", JavaExtension::class.java) {
        target(project.fileTree("src") { include("**/com/velocityctd/**/*.java") })
        if (project.name == "velocity-api") {
            licenseHeaderFile(file("HEADER-CTD.txt"))
        } else {
            licenseHeaderFile(rootProject.file("HEADER-CTD.txt"))
        }
        removeUnusedImports()
    }
}
