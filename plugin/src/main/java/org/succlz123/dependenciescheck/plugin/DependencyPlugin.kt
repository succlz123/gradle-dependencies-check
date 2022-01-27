package org.succlz123.dependenciescheck.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import groovy.util.Node
import groovy.util.NodeList
import groovy.util.XmlParser
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.io.File

class DependencyPlugin : Plugin<Project> {

    companion object {
        const val EXTENSION_NAME = "dependenciesExtension"
        const val TASK_DEPENDENCIES = "checkGradleDependencies"

        val groupList = HashMap<String, HashMap<String, ArrayList<String>>>()
        val repetitiveDependencies = ArrayList<String>()

        val stringMap = HashMap<String, ArrayList<ResourceInfo>>()
    }

    private var showException = false

    override fun apply(project: Project) {
        project.extensions.create(EXTENSION_NAME, DependenciesPluginExtension::class.java, project)
        val task = project.task(mapOf("group" to "verification"), TASK_DEPENDENCIES)
        // https://github.com/Flowdalic/Smack/commit/bd1615c3056e6ce84bdf2a352eee0d7a6aac8485
        task.doLast {
            println("--- $TASK_DEPENDENCIES ${project.name} start ---")
            val extension =
                project.extensions.findByName(EXTENSION_NAME) as? DependenciesPluginExtension
            showException = extension?.showException ?: false
            for (configuration in project.configurations) {
                if (configuration.isCanBeResolved && needResolved(configuration)) {
                    for (dependency in configuration.incoming.resolutionResult.root.dependencies) {
                        if (dependency is ResolvedDependencyResult) {
                            checkDependencies(dependency, project)
                        } else {
                            // the plugin is only check for apply plugin module, so we need to apply all modules that we need to check
                            // println("Could not resolve ${dependency.requested.displayName}")
                        }
                    }
                }
            }

            project.afterEvaluate {
                val appVariants =
                    (project.extensions.findByName("android") as AppExtension).applicationVariants
                for (variant in appVariants) {
                    val variantName =
                        variant.name.substring(0, 1).toUpperCase() + variant.getName()
                            .substring(1);
                    // 上传依赖打包，打包依赖clean。
                    variant.assembleProvider.get()
                        .dependsOn(project.tasks.findByName("clean"));
                    uploadTask.dependsOn(variant.assembleProvider.get());
                }
            }

//                val xx=project.getTasksByName("android") as AndroidVariantTask
//                xx.variantName
//                variants.all { BaseVariant variant ->
//                    variant.outputs.each { BaseVariantOutput output ->
//
//                        //处理 processResources 这个 task
//                        //例如 :library:process${FlavorName}ReleaseResources
//                        output.processResources.doLast {
//                            //2.确定需要生成 R2.java 文件所在的目录和R.java 文件。
//                            //packageForR就是表示当前的包名
//                            //将 R.java 所在的包中的.替换为/,相当于 com.example.xxx —> com/example/xxx
//                            File rDir = new File(sourceOutputDir, packageForR.replaceAll('\\.',
//                                StringEscapeUtils.escapeJava(File.separator)))
//                            File R = new File(rDir, 'R.java')
//                            //3.生成 R2.java 文件
//                            FinalRClassBuilder.brewJava(R, sourceOutputDir, packageForR, 'R2')
//                        }
//                    }
//                }
        }
        println("--- $TASK_DEPENDENCIES end ---")
    }

    val deredundancy = project.tasks.create("aaaaResss") {
        it.doLast {
            filterValues(project, 0)
        }
    }
}

private fun needResolved(conf: Configuration): Boolean {
    return conf.name != "lintClassPath"
}

private fun checkDependencies(result: ResolvedDependencyResult, project: Project) {
    val groupAndId = result.selected.moduleVersion?.module?.toString()
    val version = result.selected.moduleVersion?.version
    val source = project.name + ":" + groupAndId + ":" + version
    if (!version.isNullOrEmpty() &&
        !version.equals("unspecified", true) &&
        !groupAndId.isNullOrEmpty()
    ) {
        doCheck(groupAndId, version, source)
    }
    for (dependency in result.selected.dependencies) {
        if (dependency is ResolvedDependencyResult) {
            checkDependencies(dependency, project)
        }
    }
}

private fun doCheck(groupAndId: String, version: String, source: String) {
    if (!dependenciesIsOk(groupAndId, version, source)) {
        if (repetitiveDependencies.contains(groupAndId)) {
            return
        }
        repetitiveDependencies.add(groupAndId)
        val errorMsg = "$TASK_DEPENDENCIES Failed: " + getErrorInfo(groupAndId)
        if (showException) {
            throw GradleException(errorMsg)
        } else {
            println(errorMsg)
        }
    }
}

// key = org.jetbrains.kotlin:kotlin-android-extensions-runtime
// value ③ 1.3.72 - app:org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.3.72,
//              ① app-test:org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.3.72
//              ② ...
//       1.3.62 - app-image:org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.3.62
//
private fun dependenciesIsOk(groupAndId: String, version: String, source: String): Boolean {
//        println("groupAndId = $groupAndId / version = $version / from = $source")
    var versionMap = groupList[groupAndId]
    if (versionMap != null) {
        // the same version dependencies
        val allSomeDependencies = versionMap[version]
        if (allSomeDependencies != null) {
            if (!allSomeDependencies.contains(source)) {
                allSomeDependencies.add(source) // ①
            }
        } else {
            val sourceList = ArrayList<String>() // ②
            sourceList.add(source)
            versionMap[version] = sourceList
        }
    } else {
        val sourceList = ArrayList<String>() // ③
        sourceList.add(source)
        versionMap = HashMap()
        versionMap[version] = sourceList
        groupList[groupAndId] = versionMap
    }
    // make sure all lib only have one dependency of the same version
    return versionMap.size <= 1
}

private fun getErrorInfo(groupAndId: String): String {
    val versionList = groupList[groupAndId] ?: return ""
    var result = "$groupAndId\n"
    for (version in versionList) {
        result = result + "\t version: " + version.key + "\n\t\tfound: "
        val sourceList = version.value
        for (s in sourceList) {
            result = "$result$s\n"
        }
    }
    return result
}

fun filterValues(project: Project, type: Int) {
    if (project.childProjects.size > 1) {
        for (childProject in project.childProjects) {
            filterValues(childProject.value, type)
        }
    } else {
        if (type == 0) {
            filterString("${project.projectDir}/src/main/res/values/strings.xml")
        } else if (type == 1) {
//                filterColor("${project.projectDir}/src/main/res/values/colors.xml")
        } else if (type == 2) {
//                filterDimen("${project.projectDir}/src/main/res/values/dimens.xml")
        }
    }
}

fun filterString(filePath: String) {
    val file = File(filePath)
    if (!file.exists()) {
        return
    }
    val list = XmlParser().parse(filePath)
    val nodes = list.get("string")
    if (nodes is NodeList) {
        nodes.forEach {
            if (it is Node) {
                val keyName = it.attribute("name") as String
                if ("app_name" != keyName) {
                    val valueStr = it.value() as String
//                        if (stringMap.containsKey(keyName)) {
//                            val info = stringMap[keyName]
//                            if (valueStr == info?.values) {
//                            }
//                        } else {
//                            stringMap[keyName] = (keyName, valueStr, filePath)
//                        }
                }
            }
        }
    }
}

class ResourceInfo(
    var name: String,
    var values: String,
    var path: String
) {
    override fun toString(): String {
        return ("name:$name--values:$values--path:$path")
    }
}
}

