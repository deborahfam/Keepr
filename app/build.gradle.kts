plugins { id("com.android.application") }
android {
    namespace = "com.keepr.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.keepr.app"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = providers.gradleProperty("keeprVersionCode").orElse("7").get().toInt()
        versionName = providers.gradleProperty("keeprVersionName").orElse("0.8.0").get()
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("androidx.work:work-runtime:2.10.1")
    implementation("org.jsoup:jsoup:1.24.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// Machine-readable inventory used for advisory checks and shipped license notices.
tasks.register("releaseInventory") {
    doLast {
        val files = (configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts +
            configurations.getByName("coreLibraryDesugaring").resolvedConfiguration.resolvedArtifacts)
        val output = layout.buildDirectory.file("release-inventory.tsv").get().asFile
        output.parentFile.mkdirs()
        output.writeText(files.sortedBy { it.moduleVersion.id.toString() }.joinToString("\n") {
            "${it.moduleVersion.id}\t${it.file.absolutePath}"
        } + "\n")
    }
}
