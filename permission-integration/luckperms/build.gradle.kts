plugins {
    `java-library`
}

dependencies {
    implementation(project(":velocity-permission-integration-spi"))
    implementation(project(":velocity-api"))

    compileOnly("net.luckperms:api:5.5")
    compileOnly(libs.log4j.api)
}

tasks {
}
