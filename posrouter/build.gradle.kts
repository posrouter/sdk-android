import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

// JitPack builds set JITPACK=true; multi-module artifacts must be published under
// com.github.<org>.<repo> so consumers resolve com.github.posrouter.sdk-android:posrouter:<tag>.
// Every other build (local, GitHub Packages) keeps the com.posrouter coordinate.
group = if (System.getenv("JITPACK").toBoolean()) "com.github.posrouter.sdk-android" else "com.posrouter"
version = providers.gradleProperty("POSROUTER_VERSION").getOrElse("1.0.0")

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun publishProperty(key: String, envVar: String): String? =
    localProperties.getProperty(key)
        ?: findProperty(key) as String?
        ?: System.getenv(envVar)

android {
    namespace = "com.posrouter"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("io.nats:jnats:2.20.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = project.group.toString()
                artifactId = "posrouter"
                version = project.version.toString()

                from(components["release"])

                pom {
                    name.set("POSRouter Android SDK")
                    description.set("Lensing Protocol SDK for Android POS integrations")
                    url.set("https://github.com/posrouter/sdk-android")
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/posrouter/sdk-android")
                credentials {
                    username = publishProperty("gpr.user", "GITHUB_ACTOR")
                    password = publishProperty("gpr.key", "GITHUB_TOKEN")
                }
            }
        }
    }
}
