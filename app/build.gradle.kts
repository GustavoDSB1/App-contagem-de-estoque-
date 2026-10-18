plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.estoque.contagem"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.estoque.contagem"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Chave fixa: todas as versões do APK têm a mesma assinatura,
    // então o Android consegue atualizar o app sem desinstalar (e sem perder os dados).
    // O arquivo da chave NÃO fica no repositório (ele é público): no GitHub Actions ele vem do
    // Secret KEYSTORE_BASE64; no computador, coloque o arquivo em app/estoque.keystore.
    // Sem o arquivo, o build usa a chave de debug padrão (serve para testar, não para atualizar o app).
    val appKeystore = file("estoque.keystore")
    signingConfigs {
        if (appKeystore.exists()) {
            getByName("debug") {
                storeFile = appKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "estoque123"
                keyAlias = "estoque"
                keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "estoque123"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
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
}

base {
    archivesName.set("ContagemEstoque")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Implementação real do org.json nos testes (no Android ele já vem no sistema)
    testImplementation("org.json:json:20240303")
}
