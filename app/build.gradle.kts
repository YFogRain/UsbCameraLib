plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.parcelize)
	alias(libs.plugins.kapt)
}

android {
	namespace = "com.rain.uvc.demo"
	compileSdk = libs.versions.compileSdk.get().toInt()
	
	defaultConfig {
		applicationId = "com.rain.uvc.demo"
		minSdk = libs.versions.minSdk.get().toInt()
		targetSdk = libs.versions.targetSdk.get().toInt()
		versionCode = 1
		versionName = "1.0.1"
		
	}
	
	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
	kotlinOptions {
		jvmTarget = "1.8"
	}
	buildFeatures {
		dataBinding = true
	}
}

dependencies {
	implementation(libs.core.ktx)
	implementation(libs.appcompat)
	implementation(libs.material)
	implementation(libs.activity.ktx)
	implementation(project(":libusbcamera"))
}