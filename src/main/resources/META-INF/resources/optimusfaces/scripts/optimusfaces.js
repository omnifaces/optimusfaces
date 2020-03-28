/*
 * Copyright 2020 OmniFaces
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

	self.updateQueryStringParameter = function(url, name, value) {
		var parts = url.split(/#/, 2);
		var uri = parts[0];
		var hash = (parts.length > 1) ? ("#" + parts[1]) : "";
		var re = new RegExp("([?&])" + name + "=.*?(&|$)", "i");

		if (value) {
			var parameter = name + "=" + encodeURIComponent(value);

			if (uri.match(re)) {
				uri = uri.replace(re, "$1" + parameter + "$2");
			}
			else {
				uri += "&" + parameter;
			}
		}
		else {
			uri = uri.replace(re, "$2");
		}

		if (uri.indexOf("?") == -1) {
			uri = uri.replace(/&/, "?");
		}
		else if (uri.slice(-1) == "?") {
			uri = uri.slice(0, -1);
		}

		return uri + hash;
	}

	// Expose self to public ------------------------------------------------------------------------------------------

	return self;

})(window, document);