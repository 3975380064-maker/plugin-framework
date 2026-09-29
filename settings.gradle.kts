// 国内镜像默认关闭。
// 它们一旦排在前面，返回 5xx 时 Gradle 会把仓库判为失效并终止解析，
// 不会回退到 google()/mavenCentral()，导致海外 CI 或镜像抖动时无法构建。
// 需要时在用户级 ~/.gradle/gradle.properties 里加：useCnMirrors=true
pluginManagement {
    val useCnMirrors = providers.gradleProperty("useCnMirrors").getOrElse("false").toBoolean()
    repositories {
        if (useCnMirrors) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://repo.huaweicloud.com/repository/gradle-plugin/")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val useCnMirrors = providers.gradleProperty("useCnMirrors").getOrElse("false").toBoolean()
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useCnMirrors) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://repo.huaweicloud.com/repository/maven/")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "My Application"
include(":app")