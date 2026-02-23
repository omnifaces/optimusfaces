/*
 * Copyright OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.optimusfaces.test.js;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OptimusFacesJsTest {

    private static String jsScript;

    @BeforeAll
    static void loadScript() throws Exception {
        jsScript = new String(OptimusFacesJsTest.class
            .getResourceAsStream("/META-INF/resources/optimusfaces/scripts/optimusfaces.js")
            .readAllBytes(), StandardCharsets.UTF_8);
    }

    static String updateQueryStringParameter(String url, String name, String value) {
        try {
            var call = "OptimusFaces.Util.updateQueryStringParameter(" + quote(url) + "," + quote(name) + "," + quote(value) + ")";
            var script = "var window = {}; var document = {};\n" + jsScript + "\nprocess.stdout.write(" + call + ");";
            var process = new ProcessBuilder("node").redirectErrorStream(true).start();

            try (var stdin = process.getOutputStream()) {
                stdin.write(script.getBytes(StandardCharsets.UTF_8));
            }

            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            if (process.waitFor() != 0) {
                throw new RuntimeException("node failed:\n" + output);
            }

            return output;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void addParamToPlainUrl() {
        assertEquals("http://example.com/page?foo=bar", updateQueryStringParameter("http://example.com/page", "foo", "bar"));
    }

    @Test
    void addParamToUrlWithExistingParams() {
        assertEquals("http://example.com/page?x=1&foo=bar", updateQueryStringParameter("http://example.com/page?x=1", "foo", "bar"));
    }

    @Test
    void replaceExistingParam() {
        assertEquals("http://example.com/page?foo=new", updateQueryStringParameter("http://example.com/page?foo=old", "foo", "new"));
    }

    @Test
    void removeParamWhenValueIsNull() {
        assertEquals("http://example.com/page?x=1", updateQueryStringParameter("http://example.com/page?x=1&foo=bar", "foo", null));
    }

    @Test
    void removeOnlyParamLeavesNoTrailingQuestionMark() {
        assertEquals("http://example.com/page", updateQueryStringParameter("http://example.com/page?foo=bar", "foo", null));
    }

    @Test
    void preservesHashFragment() {
        assertEquals("http://example.com/page?foo=bar#section", updateQueryStringParameter("http://example.com/page#section", "foo", "bar"));
    }

    @Test
    void spacesInValueEncodeAsPlusSign() {
        assertEquals("http://example.com/page?q=hello+world", updateQueryStringParameter("http://example.com/page", "q", "hello world"));
    }

    @Test
    void removeFirstOfMultipleParamsConvertsLeadingAmpersandToQuestionMark() {
        assertEquals("http://example.com/page?x=1", updateQueryStringParameter("http://example.com/page?foo=bar&x=1", "foo", null));
    }

    @Test
    void replaceParamAmongMultipleParams() {
        assertEquals("http://example.com/page?x=1&foo=new&y=2", updateQueryStringParameter("http://example.com/page?x=1&foo=old&y=2", "foo", "new"));
    }

    @Test
    void removeParamPreservesHashFragment() {
        assertEquals("http://example.com/page#section", updateQueryStringParameter("http://example.com/page?foo=bar#section", "foo", null));
    }

    @Test
    void replaceParamPreservesHashFragment() {
        assertEquals("http://example.com/page?foo=new#section", updateQueryStringParameter("http://example.com/page?foo=old#section", "foo", "new"));
    }

    @Test
    void emptyStringValueRemovesParam() {
        assertEquals("http://example.com/page", updateQueryStringParameter("http://example.com/page?foo=bar", "foo", ""));
    }

    @Test
    void removingAbsentParamIsNoOp() {
        assertEquals("http://example.com/page?x=1", updateQueryStringParameter("http://example.com/page?x=1", "foo", null));
    }

    @Test
    void paramNameSubstringDoesNotMatchLongerParamName() {
        assertEquals("http://example.com/page?foobar=1&foo=new", updateQueryStringParameter("http://example.com/page?foobar=1", "foo", "new"));
    }

    @Test
    void ampersandInValueIsPercentEncoded() {
        assertEquals("http://example.com/page?foo=a%26b", updateQueryStringParameter("http://example.com/page", "foo", "a&b"));
    }

    @Test
    void paramNamesAreCaseSensitive() {
        assertEquals("http://example.com/page?FOO=old&foo=new", updateQueryStringParameter("http://example.com/page?FOO=old", "foo", "new"));
    }
}
