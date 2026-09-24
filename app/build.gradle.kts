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
        versionCode = 56
        versionName = "0.44.0"
    }

    // EIN DEBUG-SCHLUESSEL IM REPO, nicht einer je Lauf. Jeder GitHub-Lauf
    // legte sich sonst einen eigenen an, und Android installiert eine anders
    // signierte Fassung nicht ueber die alte - nur nach dem Deinstallieren,
    // und das loescht den eigenen Speicher. Es ist ein Debug-Schluessel mit
    // dem ueblichen Passwort "android", kein Geheimnis: er sorgt nur dafuer,
    // dass jede Fassung dieselbe Unterschrift traegt.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Das Seitenmenue der Einstellungen - wischen, Schleier, Zurueck schliesst.
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
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
    // Die Karte fuer die Trainingsstrecke. OpenStreetMap statt Google Maps:
    // kein Schluessel, keine Play-Dienste, und dieselbe Datengrundlage, aus
    // der OsmAnd seine Karten baut.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Die taegliche Sicherung. Ein Wecker, den Android im Stromsparen
    // verschluckt, waere eine Sicherung, die es nur gibt, wenn man daran
    // denkt - und dann haette man sie auch von Hand angestossen.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Der Zettelleser ist reine Logik und laesst sich ohne Telefon pruefen.
    // org.json steckt zwar in android.jar, dort aber nur als Attrappe, die
    // null zurueckgibt - die echte Fassung muss sie im Test verdecken.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
