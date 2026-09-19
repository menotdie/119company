import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingProps = Properties().apply {
    val f = file(System.getProperty("user.home") + "/.android/locode-signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// UDP 디버그 로그 대상 — 커밋되지 않는 local.properties 에서 읽는다. 없으면 로거 비활성.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.locode.company119"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.locode.company119"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.1"
        buildConfigField("String", "UDP_LOG_HOSTS", "\"${localProps.getProperty("udpLog.hosts", "")}\"")
        buildConfigField("int", "UDP_LOG_PORT", localProps.getProperty("udpLog.port", "0"))
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (!signingProps.isEmpty) {
            create("locode") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("locode")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            // 서명 파일이 없으면 debug 키로 서명 (누구나 빌드 가능)
            signingConfig = signingConfigs.findByName("locode") ?: signingConfigs.getByName("debug")
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as? BaseVariantOutputImpl)
                ?.outputFileName = "119company_v${variant.versionName}.apk"
        }
        if (variant.name == "release") {
            val apk = variant.outputs.first().outputFile
            // 태그 규칙 v<versionName>-c<versionCode> — UpdateChecker.kt 가 -c<N> 을 읽는다
            val tag = "v${variant.versionName}-c${variant.versionCode}"
            val publish = tasks.register("publishGithubRelease") {
                onlyIf { apk.exists() }
                doLast {
                    val cmd = listOf("gh", "release", "create", tag, apk.absolutePath,
                        "--repo", "menotdie/119company", "--title", tag,
                        "--notes", "versionCode ${variant.versionCode}")
                    // gh 실패는 빌드 실패로 만들지 않고 경고만 남긴다
                    try {
                        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
                        val out = p.inputStream.bufferedReader().readText().trim()
                        if (p.waitFor() == 0) logger.lifecycle("[release] $tag 등록: $out")
                        else logger.warn("[release] 경고: gh release create 실패 ($tag): $out")
                    } catch (e: Exception) {
                        logger.warn("[release] 경고: gh 실행 불가 ($tag): $e")
                    }
                }
            }
            variant.assembleProvider.configure { finalizedBy(publish) }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
