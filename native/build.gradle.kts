plugins {
    `java-library`
    id("velocity-ctd-publish")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.netty.handler)
    implementation(libs.checker.qual)
}
