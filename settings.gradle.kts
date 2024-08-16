pluginManagement {
	repositories {
		maven { setUrl("https://maven.aliyun.com/repository/public") }
		google {
			content {
				includeGroupByRegex("com\\.android.*")
				includeGroupByRegex("com\\.google.*")
				includeGroupByRegex("androidx.*")
			}
		}
		mavenCentral()
		gradlePluginPortal()
	}
}
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		maven { setUrl("https://maven.aliyun.com/repository/public") }
		google()
		mavenCentral()
	}
}

rootProject.name = "UsbCameraLib"
include(":app")
include(":libusbcamera")
