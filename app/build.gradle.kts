import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ch.dysseus.kieselhelper"
    // 36 ist Bedingung von androidx.health.connect:connect-client:1.1.0,
    // nicht Geschmackssache.
    compileSdk = 36

    defaultConfig {
        applicationId = "ch.dysseus.kieselhelper"
        // Health Connect verlangt mindestens 26; 28 ist eine bequeme Untergrenze
        // und deckt jedes Telefon ab, das die Pebble-App ueberhaupt betreibt.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // Nicht verkleinern: die Health-Connect-Bibliothek arbeitet mit
            // Reflexion ueber die Datensatzklassen, und eine ungetestete App
            // durch ProGuard zu schicken hiesse, zwei Unbekannte zu stapeln.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
}

// Frueher stand das als kotlinOptions IM android-Block. Diese Form ist
// abgeloest; ab Gradle 10 faellt sie weg. Der Block gehoert jetzt nach
// aussen und nimmt einen JvmTarget statt einer Zeichenkette.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    // Fuer die Fensterraender: seit targetSdk 35 zeichnet Android von Kante zu
    // Kante, und ViewCompat liefert die Masse der Systemleisten einheitlich
    // ueber alle Fassungen.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
