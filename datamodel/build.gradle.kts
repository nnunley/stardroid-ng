plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    api("com.google.protobuf:protobuf-javalite:4.29.2")
    api(libs.flatbuffers.java)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.2"
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                    option("lite")
                }
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val flatcVersion = libs.versions.flatbuffers.get()
val flatcDir = layout.buildDirectory.dir("flatc")

val downloadFlatc by tasks.registering {
    description = "Download platform-specific flatc binary from GitHub releases"
    outputs.dir(flatcDir)

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        val asset = when {
            osName.contains("mac") && osArch == "aarch64" -> "Mac.flatc.binary.zip"
            osName.contains("mac") -> "MacIntel.flatc.binary.zip"
            osName.contains("linux") -> "Linux.flatc.binary.g++-13.zip"
            osName.contains("win") -> "Windows.flatc.binary.zip"
            else -> error("Unsupported OS: $osName ($osArch)")
        }
        val url = "https://github.com/google/flatbuffers/releases/download/v${flatcVersion}/${asset}"
        val zipFile = flatcDir.get().file("flatc.zip").asFile
        val outDir = flatcDir.get().asFile

        outDir.mkdirs()
        uri(url).toURL().openStream().use { input ->
            zipFile.outputStream().use { output -> input.copyTo(output) }
        }
        project.copy {
            from(project.zipTree(zipFile))
            into(outDir)
        }
        zipFile.delete()

        // Set executable permission (no-op on Windows)
        outDir.resolve("flatc").let { if (it.exists()) it.setExecutable(true) }
    }
}

val generateFlatBuffers by tasks.registering(Exec::class) {
    description = "Generate Kotlin code from FlatBuffers schema"
    dependsOn(downloadFlatc)
    val fbsFile = file("src/main/fbs/source.fbs")
    val outputDir = file("build/generated/flatbuffers/kotlin")
    inputs.file(fbsFile)
    outputs.dir(outputDir)
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val flatcBinary = flatcDir.get().file(if (isWindows) "flatc.exe" else "flatc").asFile.absolutePath
    commandLine(flatcBinary, "--kotlin", "-o", outputDir.absolutePath, fbsFile.absolutePath)
}

sourceSets {
    main {
        kotlin.srcDir("build/generated/flatbuffers/kotlin")
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateFlatBuffers)
}
