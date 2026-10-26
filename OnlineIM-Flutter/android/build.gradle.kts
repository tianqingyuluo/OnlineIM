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

// 必须先登记 afterEvaluate，再 evaluationDependsOn(":app")。
// 后者会立刻把 :app 求值完，之后再对它调用 afterEvaluate 会抛
// "project is already evaluated"。已求值的工程改为当场抬 compileSdk。
subprojects {
    if (state.executed) {
        raiseCompileSdkNow()
    } else {
        afterEvaluate { raiseCompileSdkNow() }
    }
}
subprojects {
    project.evaluationDependsOn(":app")
}

fun Project.raiseCompileSdkNow() {
    val android = extensions.findByName("android") ?: return
    raiseCompileSdk(android, 36)
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
