plugins {
    java
    `maven-publish`
}

extensions.configure<JavaPluginExtension> {
    withSourcesJar()
}

extensions.configure<PublishingExtension> {
    repositories {
        maven {
            name = "gritter"
            val releasesUrl = "https://repo.gritter.nl/releases"
            val snapshotsUrl = "https://repo.gritter.nl/snapshots"
            setUrl(if (version.toString().endsWith("-SNAPSHOT")) snapshotsUrl else releasesUrl)
            credentials {
                username = providers.gradleProperty("gritterUser").orNull
                password = providers.gradleProperty("gritterPassword").orNull
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
