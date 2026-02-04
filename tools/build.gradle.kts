plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

application {
    mainClass.set("com.stardroid.awakening.tools.MainKt")
}

dependencies {
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
    implementation("com.google.code.gson:gson:2.10.1")
}
