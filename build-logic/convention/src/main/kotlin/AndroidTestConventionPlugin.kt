/*
 * Copyright 2022 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import com.android.build.api.dsl.TestExtension
import dev.maples.build.androidTestImplementation
import dev.maples.build.configureAndroid
import dev.maples.build.configureUnitTestDependencies
import dev.maples.build.debugImplementation
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.test")
            }

            configureAndroid(target, extensions.getByType<TestExtension>())
            configureUnitTestDependencies()

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                androidTestImplementation(libs, "test.androidx.ext.junit")
                androidTestImplementation(libs, "test.espresso.core")
                debugImplementation(libs, "compose.ui.tooling")
                debugImplementation(libs, "test.ui.testManifest")
            }
        }
    }
}
