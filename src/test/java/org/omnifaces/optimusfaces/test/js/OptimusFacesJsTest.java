package org.omnifaces.optimusfaces.test.js;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStreamReader;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

class OptimusFacesJsTest {

    private static Scriptable scriptScope;

    @BeforeAll
    static void loadScript() throws Exception {
        try {
            var scriptContext = Context.enter();
            scriptContext.setInterpretedMode(true);
            scriptScope = scriptContext.initStandardObjects();
            scriptContext.evaluateString(scriptScope, "var window = {}; var document = {};", "<stub>", 1, null);

            try (var r = new InputStreamReader(OptimusFacesJsTest.class.getResourceAsStream("/META-INF/resources/optimusfaces/scripts/optimusfaces.js"))) {
                scriptContext.evaluateReader(scriptScope, r, "optimusfaces.js", 1, null);
            }
        }
        finally {
            Context.exit();
        }
    }

    static String updateQueryStringParameter(String url, String name, String value) {
        try {
            var scriptContext = Context.enter();
            var script = "OptimusFaces.Util.updateQueryStringParameter('" + url + "','" + name + "'," + (value == null ? "null" : ("'" + value + "'")) + ")";
            return (String) scriptContext.evaluateString(scriptScope, script, "<test>", 1, null);
        }
        finally {
            Context.exit();
        }
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
    void encodesSpecialCharactersInValue() {
        assertEquals("http://example.com/page?q=hello%20world", updateQueryStringParameter("http://example.com/page", "q", "hello world"));
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
    void caseInsensitiveParamNameMatching() {
        assertEquals("http://example.com/page?foo=new", updateQueryStringParameter("http://example.com/page?FOO=old", "foo", "new"));
    }
}
