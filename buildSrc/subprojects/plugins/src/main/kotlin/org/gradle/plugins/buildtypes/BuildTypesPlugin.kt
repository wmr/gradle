/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.buildtypes

import org.gradle.api.Plugin
import org.gradle.api.Project


class BuildTypesPlugin : Plugin<Project> {
    override fun apply(project: Project) = project.run {
        // can only be applied to the root project
        require(this === project.rootProject)
        val buildTypes = container(BuildType::class.java)
        extensions.add("buildTypes", buildTypes)

        buildTypes.all {
            val buildType = this@all

            active.value(false)

            subprojects {
                tasks.register(buildType.name).configure {
                    group = "Build Type"
                    description = "Run ${buildType.name} build type"

                    dependsOn(buildType.taskNames.map { taskNames ->
                        taskNames.filter {
                            it.startsWith(":") || tasks.findByName(it) != null
                        }
                    })

                    // This is a bit hacky.  We assume that if the build type is realized
                    // then it will be executed.
                    buildType.active.set(true)

                    // TODO: It would be better if we didn't need to change project properties or they were wired
                    // in more directly.  Changing the project properties is a side effect of configuring this task.
                    buildType.projectProperties.finalizeValue()
                    buildType.projectProperties.get().forEach { k, v -> setOrCreateProperty(k, v) }
                }
            }
        }

        // Only allow one build type to be used in a build
        gradle.taskGraph.whenReady {
            val activeBuildTypes = buildTypes.filter { it.active.get() }
            check(activeBuildTypes.size <= 1) { "You can only have one active build type at a time. Active: $activeBuildTypes" }
        }
    }

    private
    fun Project.setOrCreateProperty(propertyName: String, value: Any) {
        when {
            hasProperty(propertyName) -> setProperty(propertyName, value)
            else -> extensions.extraProperties[propertyName] = value
        }
    }
}
