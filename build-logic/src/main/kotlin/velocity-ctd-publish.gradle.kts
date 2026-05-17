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
            name = "velocityctd"
            val releasesUrl = "https://repo.velocityctd.com/releases"
            val snapshotsUrl = "https://repo.velocityctd.com/snapshots"
            setUrl(if (version.toString().endsWith("-SNAPSHOT")) snapshotsUrl else releasesUrl)
            credentials {
                username = providers.gradleProperty("velocityctdUser").orNull
                password = providers.gradleProperty("velocityctdPassword").orNull
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
