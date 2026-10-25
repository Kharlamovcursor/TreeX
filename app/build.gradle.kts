import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun gradleStringLiteral(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(file.reader())
    }
}

fun localProp(key: String): String = localProperties.getProperty(key)?.trim() ?: ""

android {
    namespace = "ru.hdlabs.treex"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.hdlabs.treex"
        minSdk = 28
        targetSdk = 35
        // 1.0.12 = 1*10000 + 0*100 + 12. Тег релиза: v1.0.12.
        versionCode = 10012
        versionName = "1.0.12"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_RELEASES_OWNER", "\"Kharlamovcursor\"")
        buildConfigField("String", "GITHUB_RELEASES_REPO", "\"TreeX\"")
        buildConfigField(
            "String",
            "TELEGRAM_FEEDBACK_URL",
            gradleStringLiteral(
                localProp("telegram.feedback.url").ifBlank { "https://t.me/Yakofffff" },
            ),
        )
        buildConfigField(
            "String",
            "TELEGRAM_FEEDBACK_BOT",
            gradleStringLiteral(localProp("telegram.feedback.bot")),
        )
        buildConfigField(
            "String",
            "MAX_FEEDBACK_URL",
            gradleStringLiteral(localProp("max.feedback.url")),
        )
        buildConfigField("String", "GITHUB_FAMILY_TOKEN", gradleStringLiteral(localProp("github.family.token")))
        buildConfigField("String", "GITHUB_FAMILY_OWNER", gradleStringLiteral(localProp("github.family.owner")))
        buildConfigField("String", "GITHUB_FAMILY_REPO", gradleStringLiteral(localProp("github.family.repo")))
        buildConfigField(
            "String",
            "GITHUB_FAMILY_BRANCH",
            gradleStringLiteral(localProp("github.family.branch").ifBlank { "main" }),
        )
        buildConfigField(
            "String",
            "GITHUB_FAMILY_PATH",
            gradleStringLiteral(localProp("github.family.path").ifBlank { "family/tree.json" }),
        )
    }

    signingConfigs {
        create("release") {
            val keystorePropsFile = rootProject.file("keystore.properties")
            if (keystorePropsFile.exists()) {
                val propsText = keystorePropsFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")
                val props = Properties().apply { load(propsText.reader()) }
                fun prop(name: String): String =
                    props.getProperty(name)?.trim()
                        ?: props.entries.firstOrNull { (it.key as String).removePrefix("\uFEFF").trim() == name }
                            ?.value?.toString()?.trim()
                        ?: error("Missing $name in keystore.properties")

                storeFile = rootProject.file(prop("storeFile"))
                storePassword = prop("storePassword")
                keyAlias = prop("keyAlias")
                keyPassword = prop("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (rootProject.file("keystore.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
