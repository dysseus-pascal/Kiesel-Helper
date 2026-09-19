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
        versionCode = 23
        versionName = "0.20.0"
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
        getByName("test") {
            java.srcDirs("src/test/kotlin")
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
    // OsmAnds AIDL-Schnittstelle, unveraendert uebernommen. Sie steht unter
    // GPLv3, und weil sie mit dieser App zu einem Werk verbunden wird, steht
    // Kiesel-Helper als Ganzes unter GPLv3 - siehe LICENSE.
    implementation(project(":osmand-api"))

    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    // Fuer die Fensterraender: seit targetSdk 35 zeichnet Android von Kante zu
    // Kante, und ViewCompat liefert die Masse der Systemleisten einheitlich
    // ueber alle Fassungen.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // Ziehen zum Auffrischen. Die einzige Stelle, an der eine Bibliothek
    // billiger ist als die eigene Fassung: die Geste hat Schwellen,
    // Abbruchbedingungen und ein Zusammenspiel mit dem Roller, das man
    // nicht nachbaut, sondern nachbaut und dann falsch hat.
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Der Zettelleser ist reine Logik und laesst sich ohne Telefon pruefen.
    // org.json steckt zwar in android.jar, dort aber nur als Attrappe, die
    // null zurueckgibt - die echte Fassung muss sie im Test verdecken.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
