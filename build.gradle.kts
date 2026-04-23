plugins {
    `java-library`
    id("velocity-checkstyle") apply false
    id("velocity-spotless") apply false
}

subprojects {
    apply<JavaLibraryPlugin>()

    apply(plugin = "velocity-checkstyle")
    apply(plugin = "velocity-spotless")

    plugins.withId("checkstyle") {
        extensions.configure<CheckstyleExtension> {
            configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        testImplementation(rootProject.libs.junit)
    }

    testing.suites.named<JvmTestSuite>("test") {
        useJUnitJupiter()
        targets.all {
            testTask.configure {
                reports.junitXml.required = true
            }
        }
    }
}

project(":velocity-proxy") {
    plugins.withId("checkstyle") {
        extensions.configure<CheckstyleExtension> {
            configFile = rootProject.file("config/checkstyle/checkstyle-lenient-comments.xml")
        }
    }
}
