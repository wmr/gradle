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

package org.gradle.integtests.fixtures.executer;

import com.google.common.io.CharSource;
import org.gradle.api.UncheckedIOException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult.STACK_TRACE_ELEMENT;

public class OutputValidator {
    private int expectedGenericDeprecationWarnings;
    private final List<String> expectedDeprecationWarnings;
    private final boolean expectStackTraces;
    private final boolean checkDeprecations;

    public OutputValidator(
        int expectedGenericDeprecationWarnings,
        List<String> expectedDeprecationWarnings,
        boolean expectStackTraces,
        boolean checkDeprecations
    ) {
        this.expectedGenericDeprecationWarnings = expectedGenericDeprecationWarnings;
        this.expectedDeprecationWarnings = new ArrayList<>(expectedDeprecationWarnings);
        this.expectStackTraces = expectStackTraces;
        this.checkDeprecations = checkDeprecations;
    }

    public void assertExpectedDeprecationMessages() {
        if (!expectedDeprecationWarnings.isEmpty()) {
            throw new AssertionError(String.format("Expected the following deprecation warnings:%n%s",
                expectedDeprecationWarnings.stream()
                    .map(warning -> " - " + warning)
                    .collect(Collectors.joining("\n"))));
        }
        if (expectedGenericDeprecationWarnings > 0) {
            throw new AssertionError(String.format("Expected %d more deprecation warnings", expectedGenericDeprecationWarnings));
        }
    }

    public void validate(String output, String displayName) {
        List<String> lines;
        try {
            lines = CharSource.wrap(output).readLines();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int i = 0;
        boolean insideVariantDescriptionBlock = false;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (insideVariantDescriptionBlock && line.contains("]")) {
                insideVariantDescriptionBlock = false;
            } else if (!insideVariantDescriptionBlock && line.contains("variant \"")) {
                insideVariantDescriptionBlock = true;
            }
            if (line.matches(".*use(s)? or override(s)? a deprecated API\\.")) {
                // A javac warning, ignore
                i++;
            } else if (line.matches(".*w: .* is deprecated\\..*")) {
                // A kotlinc warning, ignore
                i++;
            } else if (isDeprecationMessageInHelpDescription(line)) {
                i++;
            } else if (expectedDeprecationWarnings.remove(line)) {
                // Deprecation warning is expected
                i++;
                i = skipStackTrace(lines, i);
            } else if (line.matches(".*\\s+deprecated.*")) {
                if (checkDeprecations && expectedGenericDeprecationWarnings <= 0) {
                    throw new AssertionError(String.format("%s line %d contains a deprecation warning: %s%n=====%n%s%n=====%n", displayName, i + 1, line, output));
                }
                expectedGenericDeprecationWarnings--;
                // skip over stack trace
                i++;
                i = skipStackTrace(lines, i);
            } else if (!expectStackTraces && !insideVariantDescriptionBlock && STACK_TRACE_ELEMENT.matcher(line).matches() && i < lines.size() - 1 && STACK_TRACE_ELEMENT.matcher(lines.get(i + 1)).matches()) {
                // 2 or more lines that look like stack trace elements
                throw new AssertionError(String.format("%s line %d contains an unexpected stack trace: %s%n=====%n%s%n=====%n", displayName, i + 1, line, output));
            } else {
                i++;
            }
        }
    }

    private int skipStackTrace(List<String> lines, int i) {
        while (i < lines.size() && STACK_TRACE_ELEMENT.matcher(lines.get(i)).matches()) {
            i++;
        }
        return i;
    }

    private boolean isDeprecationMessageInHelpDescription(String s) {
        return s.matches(".*\\[deprecated.*]");
    }
}
