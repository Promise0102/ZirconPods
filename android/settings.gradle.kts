pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google(); mavenCentral(); gradlePluginPortal()
    }
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "org.jetbrains.kotlin.android") useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
        if (requested.id.id == "org.jetbrains.kotlin.plugin.compose") useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google(); mavenCentral()
    }
}
rootProject.name = "ZirconPods"
include(":app")
