// OsmAnds AIDL-Schnittstelle, unveraendert uebernommen.
//
// HERKUNFT UND LIZENZ: die Dateien unter src/ stammen aus dem Modul
// `OsmAnd-api` von github.com/osmandapp/OsmAnd und stehen wie der uebrige
// OsmAnd-Code unter GPLv3. Die Lizenz liegt daneben als LICENSE-OsmAnd. Weil
// dieses Modul mit der App zu einem Werk verbunden wird, steht Kiesel-Helper
// als Ganzes unter GPLv3 - siehe LICENSE im Wurzelverzeichnis.
//
// WARUM ALLES UND NICHT NUR DAS GEBRAUCHTE: AIDL vergibt die
// Transaktionsnummern nach der Reihenfolge der Deklarationen. Wer aus
// IOsmAndAidlInterface die ungenutzten Methoden streicht, verschiebt alle
// folgenden - die App riefe dann stillschweigend die falsche Funktion auf.
// Entweder ganz oder gar nicht.
//
// GEAENDERT wurde nur diese Baudatei: das Original veroeffentlicht sich per
// ivy in ein Firmen-Repository und zieht seine Fassungen aus versions.gradle,
// beides gibt es hier nicht.
plugins {
    id("com.android.library")
}

android {
    namespace = "net.osmand.aidlapi"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Fremder Code, den wir nicht pflegen: seine Warnungen sind nicht unsere,
    // und ein abgebrochener Bau deswegen waere nur laestig.
    lint {
        abortOnError = false
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            aidl.srcDirs("src")
            java.srcDirs("src")
        }
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.6.0")
}
