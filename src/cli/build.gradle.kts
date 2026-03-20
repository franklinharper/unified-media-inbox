plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.test)
}

application {
    mainClass = "com.franklinharper.social.media.client.cli.MainKt"
}
