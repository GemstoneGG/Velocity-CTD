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

        // Exclude all the collection types we don't intend to use
        exclude("it/unimi/dsi/fastutil/booleans/**")
        exclude("it/unimi/dsi/fastutil/bytes/**")
        exclude("it/unimi/dsi/fastutil/chars/**")
        exclude("it/unimi/dsi/fastutil/doubles/**")
        exclude("it/unimi/dsi/fastutil/floats/**")
        exclude("it/unimi/dsi/fastutil/longs/**")
        exclude("it/unimi/dsi/fastutil/shorts/**")

        // Exclude the fastutil IO utilities - we don't use them.
        exclude("it/unimi/dsi/fastutil/io/**")

        // Exclude most of the int types - Object2IntMap have a values() method that returns an
        // IntCollection, and we need Int2ObjectMap
        exclude("it/unimi/dsi/fastutil/ints/*Int2Boolean*")
        exclude("it/unimi/dsi/fastutil/ints/*Int2Byte*")
        exclude("it/unimi/dsi/fastutil/ints/*Int2Char*")
        exclude("it/unimi/dsi/fastutil/ints/*Int2Double*")
        exclude("it/unimi/dsi/fastutil/ints/*Int2Float*")
        exclude("it/unimi/dsi/fastutil/ints/*Int2Int*")
        exclude("it/unimi/dsi/fastutil/ints/*Int2Long*")
        exclude("it/unimi/dsi/fastutil/ints/*Int2Short*")
        exclude("it/unimi/dsi/fastutil/ints/*Int2Reference*")
        exclude("it/unimi/dsi/fastutil/ints/IntAVL*")
        exclude("it/unimi/dsi/fastutil/ints/IntArrayF*")
        exclude("it/unimi/dsi/fastutil/ints/IntArrayI*")
        exclude("it/unimi/dsi/fastutil/ints/IntArrayL*")
        exclude("it/unimi/dsi/fastutil/ints/IntArrayP*")
        exclude("it/unimi/dsi/fastutil/ints/IntArraySet*")
        exclude("it/unimi/dsi/fastutil/ints/*IntBi*")
        exclude("it/unimi/dsi/fastutil/ints/Int*Pair")
        exclude("it/unimi/dsi/fastutil/ints/IntLinked*")
        exclude("it/unimi/dsi/fastutil/ints/IntList*")
        exclude("it/unimi/dsi/fastutil/ints/IntHeap*")
        exclude("it/unimi/dsi/fastutil/ints/IntOpen*")
        exclude("it/unimi/dsi/fastutil/ints/IntRB*")
        exclude("it/unimi/dsi/fastutil/ints/IntSorted*")
        exclude("it/unimi/dsi/fastutil/ints/*Priority*")
        exclude("it/unimi/dsi/fastutil/ints/*BigList*")

        // Try to exclude everything BUT Object2Int{LinkedOpen,Open,CustomOpen}HashMap
        exclude("it/unimi/dsi/fastutil/objects/*ObjectArray*")
        exclude("it/unimi/dsi/fastutil/objects/*ObjectAVL*")
        exclude("it/unimi/dsi/fastutil/objects/*Object*Big*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2Boolean*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2Byte*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2Char*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2Double*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2Float*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2IntArray*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2IntAVL*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2IntRB*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2Long*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2Object*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2Reference*")
        exclude("it/unimi/dsi/fastutil/objects/*Object2Short*")
        exclude("it/unimi/dsi/fastutil/objects/*ObjectRB*")
        exclude("it/unimi/dsi/fastutil/objects/*Reference*")

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
    implementation(libs.adventure.facet)
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
