plugins {
    `kotlin-dsl`
    // kotlin("jvm") version "2.1.0"
}

gradlePlugin {
    plugins {
        register("lottiePreParser") {
            id = "org.telegram.lottie-meta"
            implementationClass = "org.telegram.lottie.LottieMetaPlugin"
        }
        register("testGenerator") {
            id = "test-generator"
            implementationClass = "com.example.TestGeneratorPlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}
/*
val checkEmojiKeyboard by tasks.registering(GenerateSchemeTask::class) {

}
*/
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
    incremental = false
}

dependencies {
    implementation(gradleApi())
    implementation("com.android.tools.build:gradle:9.2.1")

    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.github.javaparser:javaparser-core:3.28.2")
    implementation("com.squareup:kotlinpoet:2.3.0")
    implementation("com.google.code.gson:gson:2.11.0")
}
