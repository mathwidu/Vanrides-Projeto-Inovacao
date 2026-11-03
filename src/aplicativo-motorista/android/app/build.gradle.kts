plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

import java.net.URI
import java.security.cert.CertificateFactory
import java.util.Properties
import com.android.build.api.variant.BuildConfigField
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

// Match the exact validated Flutter target; an absent target selects the safe MVP source set.
val driverAppRoot = rootProject.projectDir.parentFile
val driverTargetFile = driverAppRoot.resolve(project.findProperty("target")?.toString() ?: "lib/main_driver_mvp.dart").canonicalFile
val driverGpsSpikeBuild = driverTargetFile == driverAppRoot.resolve("lib/main_driver_gps_spike.dart").canonicalFile

// Explicit compile-time configuration only. No Flutter argument or runtime link selects an origin.
val pairingApiOrigin = System.getenv("VANRIDES_PAIRING_API_ORIGIN")?.takeIf { it.isNotEmpty() } ?: ""
if (pairingApiOrigin.isNotEmpty()) {
    val origin = runCatching { URI(pairingApiOrigin) }.getOrNull()
    require(origin != null && origin.scheme == "https" && origin.host != null && origin.rawUserInfo == null &&
        origin.rawQuery == null && origin.rawFragment == null && origin.rawPath.isNullOrEmpty() &&
        origin.port in -1..65535 && origin.port != 0 && pairingApiOrigin == "https://${origin.rawAuthority}") {
        "VANRIDES_PAIRING_API_ORIGIN must be an explicit HTTPS origin without path or credentials."
    }
}
val pairingLabCa = System.getenv("VANRIDES_PAIRING_LAB_CA")?.takeIf { it.isNotEmpty() }
val pairingLinkHost = System.getenv("VANRIDES_PAIRING_LINK_HOST")?.takeIf { it.isNotEmpty() } ?: ""
require(pairingLinkHost.isEmpty() || pairingLinkHost.length <= 253 &&
    Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+").matches(pairingLinkHost) &&
    pairingLinkHost.split('.').all { it.length <= 63 } && pairingLinkHost.split('.').last().any { it in 'a'..'z' }) {
    "VANRIDES_PAIRING_LINK_HOST must be an explicit lowercase ASCII DNS hostname."
}

abstract class PreparePairingLabCa : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val caFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun prepare() {
        val bytes = caFile.get().asFile.readBytes()
        require(bytes.size in 128..16384 && bytes.first() == 0x30.toByte()) { "Lab CA must be a public DER certificate." }
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(bytes.inputStream()) as java.security.cert.X509Certificate
        require(certificate.basicConstraints >= 0 && certificate.encoded.contentEquals(bytes)) { "Lab CA must be one public DER CA certificate." }
        certificate.checkValidity()
        val output = outputDirectory.get().asFile
        output.mkdirs()
        output.resolve("wa060-lab-ca.der").writeBytes(certificate.encoded)
    }
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val envFile = rootProject.file("../scripts/.dev.env.local")
if (envFile.exists()) {
    envFile.inputStream().use { localProperties.load(it) }
}

val googleMapsApiKey =
    (project.findProperty("GOOGLE_MAPS_API_KEY") as String?)
        ?: localProperties.getProperty("VANRIDES_GOOGLE_PLACES_API_KEY")
        ?: localProperties.getProperty("GOOGLE_MAPS_API_KEY")
        ?: System.getenv("GOOGLE_MAPS_API_KEY")
        ?: ""

// The driver test certificate is loaded only from an explicitly supplied local file.
val driverSigning = Properties()
val driverSigningFile = System.getenv("VANRIDES_DRIVER_SIGNING_FILE")?.let { file(it) }
if (driverSigningFile?.isFile == true) {
    driverSigningFile.reader(Charsets.UTF_8).use { driverSigning.load(it) }
}
val driverSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val driverSigningReady = driverSigningKeys.all { !driverSigning.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.example.vanrides_frontend_dev"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion
    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.example.vanrides_frontend_dev"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = googleMapsApiKey
        manifestPlaceholders["APP_LABEL"] = "vanrides_frontend_dev"
    }

    signingConfigs {
        if (driverSigningReady) {
            create("driverTest") {
                storeFile = file(driverSigning.getProperty("storeFile"))
                storePassword = driverSigning.getProperty("storePassword")
                keyAlias = driverSigning.getProperty("keyAlias")
                keyPassword = driverSigning.getProperty("keyPassword")
            }
        }
    }

    flavorDimensions += "experience"
    productFlavors {
        create("legacy") {
            dimension = "experience"
            isDefault = true
            // Preserve the old development build identity; never use it for the MVP.
            signingConfig = signingConfigs.getByName("debug")
        }
        create("driver") {
            dimension = "experience"
            applicationId = "dev.matheusduarte.vanrides.driver"
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            manifestPlaceholders["APP_LABEL"] = "VanRides Motorista"
            buildConfigField("boolean", "GPS_SPIKE_ENABLED", driverGpsSpikeBuild.toString())
            buildConfigField("String", "PAIRING_API_ORIGIN", "\"$pairingApiOrigin\"")
            buildConfigField("boolean", "PAIRING_LAB_ENABLED", "false")
            buildConfigField("String", "PAIRING_LINK_HOST", "\"$pairingLinkHost\"")
            manifestPlaceholders["PAIRING_LINK_HOST"] = pairingLinkHost.ifEmpty { "pairing-unconfigured.invalid" }
            manifestPlaceholders["PAIRING_HTTPS_LINKS_ENABLED"] = pairingLinkHost.isNotEmpty().toString()
            if (driverSigningReady) signingConfig = signingConfigs.getByName("driverTest")
        }
    }
    sourceSets {
        getByName("driver").java.srcDir(if (driverGpsSpikeBuild) "src/driverGpsSpike/kotlin" else "src/driverProduction/kotlin")
        if (driverGpsSpikeBuild) {
            getByName("driver").java.srcDir("src/driverGpsSpikeContract/kotlin")
        } else {
            // Preserve030 endpoint regressions solely in the unit-test artifact.
            maybeCreate("testDriver").java.srcDir("src/driverGpsSpikeContract/kotlin")
        }
    }
    buildTypes {
        // Let each flavor supply its certificate for standalone builds.
        getByName("release") { signingConfig = null }
        getByName("profile") { signingConfig = null }
    }
}

androidComponents {
    onVariants(selector().withFlavor("experience" to "driver").withBuildType("debug")) { variant ->
        if (pairingLabCa != null) {
            // Separate identity preserves the signed030 installation and its private tracking state.
            variant.applicationId.set("dev.matheusduarte.vanrides.driver.lab")
            val labFields = checkNotNull(variant.buildConfigFields) { "Driver laboratory requires BuildConfig." }
            labFields.put("PAIRING_API_ORIGIN", BuildConfigField("String", "\"https://localhost:18443\"", "Isolated local040 laboratory"))
            labFields.put("PAIRING_LAB_ENABLED", BuildConfigField("boolean", "true", "Explicit public CA opt-in; debug only"))
            val prepareCa = tasks.register<PreparePairingLabCa>("prepareDriverPairingLabCa") {
                caFile.set(file(pairingLabCa))
                outputDirectory.set(layout.buildDirectory.dir("generated/wa060Lab/assets"))
            }
            checkNotNull(variant.sources.assets).addGeneratedSourceDirectory(prepareCa, PreparePairingLabCa::outputDirectory)
        }
    }
}

flutter {
    source = "../.."
}

// Combined flavor/build-type configurations must be declared before dependencies.
configurations.maybeCreate("driverDebugImplementation")

dependencies {
    // Match the version already selected by geolocator_android 4.6.2.
    add("driverImplementation", "com.google.android.gms:play-services-location:21.2.0")
    add("testDriverImplementation", "junit:junit:4.13.2")
    add("testDriverImplementation", "org.json:json:20180813")
    // integration_test brings an older runner into debug runtime; AGP requires
    // the host and instrumentation APKs to resolve the same test infrastructure.
    add("driverDebugImplementation", "androidx.test:runner:1.7.0")
    add("driverDebugImplementation", "androidx.test:core:1.7.0")
    add("androidTestDriverImplementation", "androidx.test:runner:1.7.0")
    add("androidTestDriverImplementation", "androidx.test:core:1.7.0")
    add("androidTestDriverImplementation", "androidx.test.ext:junit:1.3.0")
}

// Fail closed before packaging a distributable driver build, including aggregate tasks.
val verifyDriverSigning by tasks.registering {
    doLast {
        check(driverSigningReady) {
            "Driver signing is missing. Set VANRIDES_DRIVER_SIGNING_FILE to a private properties file."
        }
    }
}
val verifyDriverEntrypoint by tasks.registering {
    doLast {
        val target = project.findProperty("target")?.toString() ?: flutter.target ?: "lib/main.dart"
        val appRoot = rootProject.projectDir.parentFile
        val dedicatedTargets = setOf("lib/main_driver_mvp.dart", "lib/main_driver_gps_spike.dart")
        check(dedicatedTargets.any { appRoot.resolve(target).canonicalFile == appRoot.resolve(it).canonicalFile }) {
            "The driver flavor requires a dedicated driver or GPS-spike entrypoint."
        }
    }
}
tasks.configureEach {
    if (name.startsWith("compileFlutterBuildDriver")) {
        dependsOn(verifyDriverEntrypoint)
    }
    if (name == "packageDriverRelease" || name == "packageDriverProfile" ||
        name == "signDriverReleaseBundle" || name == "signDriverProfileBundle") {
        dependsOn(verifyDriverSigning)
    }
}
