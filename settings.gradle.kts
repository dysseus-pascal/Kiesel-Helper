pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Fassungen aus der Vorgaenger-App uebernommen, nicht neu gewaehlt:
        // dieser Satz baut nachweislich. connect-client 1.1.0 verlangt AGP
        // 8.9.1 oder neuer - der Bau bricht sonst schon beim Pruefen der
        // AAR-Metadaten ab.
        id("com.android.application") version "8.13.0"
        id("com.android.library") version "8.13.0"
        id("org.jetbrains.kotlin.android") version "2.1.20"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kiesel-helper"
include(":app")
// OsmAnds AIDL-Schnittstelle, unveraendert uebernommen (GPLv3) - siehe
// osmand-api/build.gradle.kts.
include(":osmand-api")
