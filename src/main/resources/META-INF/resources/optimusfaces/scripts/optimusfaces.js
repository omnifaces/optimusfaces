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
/**
 * The OptimusFaces namespace.
 * 
 * @author Bauke Scholtz
 */
var OptimusFaces = OptimusFaces || {};

/**
 * Utility scripts.
 */
OptimusFaces.Util = (function(window, document) {

    // Private static fields ------------------------------------------------------------------------------------------

    var self = {};

    // Public static functions ----------------------------------------------------------------------------------------

    self.historyPushQueryString = function(queryString) {
        if (window.history && window.history.pushState) {
            var url = window.location.href.split(/\?/, 2)[0] + (queryString ? "?" : "") + queryString;
            window.history.pushState(null, document.title, url);
        }
    }

    self.historyPushQueryStringParameter = function(name, value) {
        if (window.history && window.history.pushState) {
            var url = self.updateQueryStringParameter(window.location.href, name, value);
            window.history.pushState(null, document.title, url);
        }
    }

    self.historyReplaceQueryString = function(queryString) {
        if (window.history && window.history.replaceState) {
            var url = window.location.href.split(/\?/, 2)[0] + (queryString ? "?" : "") + queryString;
            window.history.replaceState(null, document.title, url);
        }
    }

    self.historyReplaceQueryStringParameter = function(name, value) {
        if (window.history && window.history.replaceState) {
            var url = self.updateQueryStringParameter(window.location.href, name, value);
            window.history.replaceState(null, document.title, url);
        }
    }

    self.updateQueryString = function(queryString) {
        self.historyReplaceQueryString(queryString);
        for (var i = 0; i < document.forms.length; i++) {
            var form = document.forms[i];
            if (form["jakarta.faces.ViewState"]) {
                form.action = form.action.split(/\?/, 2)[0] + (queryString ? "?" : "") + queryString;
            }
        }
    }

    self.updateQueryStringParameter = function(url, name, value) {
        var parts = url.split(/#/, 2);
        var uri = parts[0];
        var hash = parts.length > 1 ? "#" + parts[1] : "";
        var questionMarkIndex = uri.indexOf("?");
        var base = questionMarkIndex === -1 ? uri : uri.slice(0, questionMarkIndex);
        var params = new URLSearchParams(questionMarkIndex === -1 ? "" : uri.slice(questionMarkIndex + 1));

        if (value) {
            params.set(name, value);
        }
        else {
            params.delete(name);
        }

        var queryString = params.toString();
        return base + (queryString ? "?" + queryString : "") + hash;
    }

    // Expose self to public ------------------------------------------------------------------------------------------

    return self;

})(window, document);