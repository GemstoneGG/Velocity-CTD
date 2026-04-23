plugins {
    `java-library`
}

dependencies {
    implementation(project(":velocity-api"))

    compileOnly("net.luckperms:api:5.5")
}

tasks {
}
