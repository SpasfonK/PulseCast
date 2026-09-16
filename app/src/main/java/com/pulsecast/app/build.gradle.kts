plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.pulsecast.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pulsecast.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            // Minification désactivée en Phase 1 pour fiabiliser la première
            // compilation ; à activer explicitement (avec règles ProGuard
            // adaptées à Media3/Room) une fois l'app fonctionnelle de bout
            // en bout.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Core / Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // material-icons-core ne couvre qu'un sous-ensemble restreint d'icônes
    // (Pause et Palette, par exemple, n'en font pas partie alors que
    // PlayArrow ou Check si — une distinction non documentée qui a fait
    // échouer le build #10). material-icons-extended couvre tout le
    // catalogue Material au prix d'une bibliothèque plus lourde (~2000
    // icônes) ; comme isMinifyEnabled est encore à false, R8 ne retire pas
    // les icônes inutilisées pour l'instant — acceptable en développement,
    // à revisiter si la taille de l'APK release devient un sujet.
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Media3 / ExoPlayer (moteur audio, Phase 2) ---
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // --- Persistance Room (Phase 1) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Préférences de thème (Phase 3) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Chargement d'images + extraction de couleur (Phase 3/4) ---
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Pliables (Honor Magic V2 et autres foldables book-style) ---
    implementation("androidx.window:window:1.3.0")
}

ksp {
    // Schémas Room versionnés pour préparer les migrations futures
    // (Phase 2+ : toute évolution du schéma devra fournir une Migration
    // explicite plutôt qu'une fallbackToDestructiveMigration).
    arg("room.schemaLocation", "$projectDir/schemas")
}
