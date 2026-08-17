import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

plugins {
    application
    id("velocity-ctd-publish")
    id("velocity-init-manifest")
    alias(libs.plugins.shadow)
}

application {
    mainClass.set("com.velocitypowered.proxy.Velocity")
    applicationDefaultJvmArgs += listOf("-Dvelocity.packet-decode-logging=true")
}

val relocations = mapOf(
    "org.bstats" to "com.velocitypowered.proxy.bstats",
)

val relocatedLibraries: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}
// Keep the relocated libraries on the compile/runtime classpath so the proxy compiles against them
// and the fat shadowJar continues to bundle them.
configurations.named("implementation") { extendsFrom(relocatedLibraries) }

// Permission integration modules embedded as jar-in-jar resources. Each is shipped at
// `<permissionIntegrationsResourceDir>/<module-dir-name>.jar` and listed in `integrations.index`,
// which `PermissionResolverAdapterFactory` reads at runtime to discover and load them. Adding an
// integration only requires appending its project path here.
val permissionIntegrations = listOf(
    ":velocity-permission-integration-luckperms",
)
val permissionIntegrationsResourceDir = "META-INF/velocityctd/permission-integration"

// Generates the integrations index listing each embedded integration jar resource (one per line).
val generatePermissionIntegrationsIndex by tasks.registering {
    val resourceDir = permissionIntegrationsResourceDir
    val entries = permissionIntegrations.map { project(it).projectDir.name }
    inputs.property("entries", entries)

    val outputDir = layout.buildDirectory.dir("generated/permission-integrations")
    outputs.dir(outputDir)

    doLast {
        val indexFile = outputDir.get().asFile.resolve("$resourceDir/integrations.index")
        indexFile.parentFile.mkdirs()
        indexFile.writeText(entries.joinToString("\n", postfix = "\n") { "$resourceDir/$it.jar" })
    }
}

tasks {
    jar {
        manifest {
            attributes["Implementation-Title"] = "Velocity-CTD"
            attributes["Implementation-Vendor"] = "Velocity(-CTD) Contributors"
            attributes["Multi-Release"] = "true"
            attributes["Enable-Native-Access"] = "ALL-UNNAMED"
            attributes["Enable-Final-Field-Mutation"] = "ALL-UNNAMED"
        }
    }

    processResources {
        // Embed each permission integration module as a jar-in-jar at
        // `<permissionIntegrationsResourceDir>/<module-dir-name>.jar`, alongside the generated index.
        permissionIntegrations.forEach { path ->
            val integrationProject = project(path)
            val integrationJar = integrationProject.tasks.named<Jar>("jar")
            from(integrationJar.flatMap { it.archiveFile }) {
                into(permissionIntegrationsResourceDir)
                rename { "${integrationProject.projectDir.name}.jar" }
            }
        }
        from(generatePermissionIntegrationsIndex)
    }

    shadowJar {
        filesMatching("META-INF/org/apache/logging/log4j/core/config/plugins/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }

        transform(Log4j2PluginsCacheFileTransformer::class.java)

        // Exclude Checker Framework annotations
        exclude("org/checkerframework/checker/**")

        relocations.forEach { (from, to) -> relocate(from, to) }

        // Include Configurate 3
        val configurateBuildTask = project(":deprecated-configurate3").tasks.named("shadowJar")
        dependsOn(configurateBuildTask)
        from(zipTree(configurateBuildTask.map { it.outputs.files.singleFile }))
    }

    // A minimal shaded jar containing the proxy classes plus relocated copies of `relocatedLibraries`
    // (and nothing else). The bootstrap embeds this as the proxy jar, while resolving every other
    // dependency from Maven.
    register<ShadowJar>("proxyRelocatedJar") {
        archiveClassifier.set("relocated")
        from(sourceSets["main"].output)
        configurations = listOf(relocatedLibraries)
        relocations.forEach { (from, to) -> relocate(from, to) }
    }

    runShadow {
        workingDir = file("run").also(File::mkdirs)
        standardInput = System.`in`
        jvmArgs("-Dvelocity.packet-decode-logging=true")
    }
    named<JavaExec>("run") {
        workingDir = file("run").also(File::mkdirs)
        standardInput = System.`in` // Doesn't work?
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-Alog4j.graalvm.groupId=${project.group}",
                "-Alog4j.graalvm.artifactId=${project.name}"
            )
        )
    }
}

dependencies {
    implementation(project(":velocity-api"))
    implementation(project(":velocity-native"))
    implementation(project(":velocity-permission-integration-spi"))

    implementation(libs.bundles.log4j)
    implementation(libs.kyori.ansi)
    implementation(libs.netty.codec)
    implementation(libs.netty.codec.haproxy)
    implementation(libs.netty.codec.http)
    implementation(libs.netty.handler)
    implementation(libs.netty.transport.native.epoll)
    implementation(variantOf(libs.netty.transport.native.epoll) { classifier("linux-x86_64") })
    implementation(variantOf(libs.netty.transport.native.epoll) { classifier("linux-aarch_64") })
    implementation(libs.netty.transport.native.iouring)
    implementation(variantOf(libs.netty.transport.native.iouring) { classifier("linux-x86_64") })
    implementation(variantOf(libs.netty.transport.native.iouring) { classifier("linux-aarch_64") })
    implementation(libs.netty.transport.native.kqueue)
    implementation(variantOf(libs.netty.transport.native.kqueue) { classifier("osx-x86_64") })
    implementation(variantOf(libs.netty.transport.native.kqueue) { classifier("osx-aarch_64") })

    implementation(libs.lettuce.core)
    implementation(libs.httpclient5)
    implementation(libs.jopt)
    implementation(libs.terminalconsoleappender)
    implementation(libs.jline.terminal)
    implementation(libs.jline.reader)
    runtimeOnly(libs.jline.terminal.jni)
    runtimeOnly(libs.jline.terminal.ffm)
    runtimeOnly(libs.disruptor)
    implementation(libs.fastutil)
    implementation(platform(libs.adventure.bom))
    implementation(libs.adventure.text.serializer.json.legacy.impl)
    implementation(libs.completablefutures)
    implementation(libs.component)
    implementation(libs.nightconfig)
    relocatedLibraries(libs.bstats)
    implementation(libs.lmbda)
    implementation(libs.asm)
    implementation(libs.bundles.flare)
    implementation(libs.uuid.creator)
    compileOnly(libs.spotbugs.annotations)
    compileOnly(libs.auto.service.annotations)
    testImplementation(libs.mockito)

    annotationProcessor(libs.auto.service)
    annotationProcessor(libs.log4j.core)
}
