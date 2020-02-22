/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.gradlebuild.test.integrationtests.splitIntoBuckets
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File


class BuildTypesPluginTest {
    @ParameterizedTest
    @CsvSource(value = [
        "100, 2",
        "100, 3",
        "100, 4",
        "100, 5",
        "100, 6",
        "100, 7",
        "100, 8",
        "100, 9",
        "100, 10"
    ])
    fun `can split files into buckets`(fileCount: Int, numberOfSplits: Int) {
        val files = (1..fileCount).map { File("$it") }
        val buckets = splitIntoBuckets(files, numberOfSplits)

        Assertions.assertEquals(numberOfSplits, buckets.size)
        Assertions.assertEquals(files, buckets.flatten())
    }
}
