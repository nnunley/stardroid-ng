plugins {
    id("java-library")
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    api("com.google.protobuf:protobuf-javalite:4.29.2")
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
