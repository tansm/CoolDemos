plugins {
    kotlin("jvm") version "2.1.10"
    id("me.champeau.jmh") version "0.7.2" // 添加JMH插件
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.bytebuddy:byte-buddy:1.14.17")
    implementation("net.bytebuddy:byte-buddy-agent:1.14.17") // For attaching to JVM and redefining classes if needed, though not strictly required for simple class generation

    testImplementation(kotlin("test"))
    testImplementation("org.openjdk.jol:jol-core:0.17")

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}