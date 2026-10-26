allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

// 插件模块常把 compileSdk 写死在 31–35。flutter_plugin_android_lifecycle
// 要求依赖它的库至少按 36 编译，否则 :file_picker 等子项目的
// checkReleaseAarMetadata 会失败。这里在各插件自身配置之后再抬上去。
subprojects {
    afterEvaluate {
        val android = extensions.findByName("android") ?: return@afterEvaluate
        raiseCompileSdk(android, 36)
    }
}

fun raiseCompileSdk(android: Any, minimum: Int) {
    val methods = android.javaClass.methods
    val current = methods.firstOrNull { it.name == "getCompileSdk" && it.parameterCount == 0 }
        ?.invoke(android) as? Int
    if (current != null && current >= minimum) {
        return
    }
    val setter = methods.firstOrNull { method ->
        method.name == "setCompileSdk" &&
            method.parameterCount == 1 &&
            (method.parameterTypes[0] == Int::class.javaPrimitiveType || method.parameterTypes[0] == Integer::class.java)
    }
    if (setter != null) {
        setter.invoke(android, minimum)
        return
    }
    methods.firstOrNull { method ->
        method.name == "compileSdkVersion" &&
            method.parameterCount == 1 &&
            (method.parameterTypes[0] == Int::class.javaPrimitiveType || method.parameterTypes[0] == Integer::class.java)
    }?.invoke(android, minimum)
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
