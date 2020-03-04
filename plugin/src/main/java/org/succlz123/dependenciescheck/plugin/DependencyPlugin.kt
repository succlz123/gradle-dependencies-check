package org.succlz123.dependenciescheck.plugin

import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.specs.Spec
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class DependencyPlugin : Plugin<Project> {

    companion object {
        const val EXTENSION_NAME = "dependenciesExtension"
        const val TASK_DEPENDENCIES = "checkGradleDependencies"

        val groupList = HashMap<String, HashMap<String, ArrayList<String>>>()
        val repetitiveDependencies = ArrayList<String>()
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
            println("--- $TASK_DEPENDENCIES end ---")
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
}

