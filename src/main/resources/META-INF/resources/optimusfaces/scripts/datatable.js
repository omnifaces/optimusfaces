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
 * Improve <p:dataTable> script.
 * 
 * 1. Make row with key=0 unselectable.
 * 2. Check if default sort needs to be descending (by presence of class="desc" on th).
 * 3. Initialize op:dataTable global search/filter events.
 * 
 * Original source here: https://github.com/primefaces/primefaces/blob/master/src/main/resources/META-INF/resources/primefaces/datatable/datatable.js
 */
if (PrimeFaces.widget.DataTable) {

	function makeRowWithKey0Unselectable(id) {
		$(document.getElementById(id)).find("tr[data-rk=0]").removeClass("ui-widget-content");
	}

	function checkIfDefaultSortNeedsToBeDescending($sortableColumns) {
		for(var i = 0; i < $sortableColumns.length; i++) {
			var $sortableColumn = $sortableColumns.eq(i);
			if ($sortableColumn.hasClass("desc") && !$sortableColumn.hasClass("ui-state-active")) {
				$sortableColumn.data("sortorder", 1);
			}
		}
	}

	PrimeFaces.widget.DataTable = PrimeFaces.widget.DataTable.extend({
		init: function(cfg) {
			makeRowWithKey0Unselectable(cfg.id);
			this._super(cfg);
			checkIfDefaultSortNeedsToBeDescending(this.sortableColumns);
		},
		sort: function(columnHeader, order, multi) {
			this._super(columnHeader, order, multi);
			checkIfDefaultSortNeedsToBeDescending(this.sortableColumns);
		},
		postUpdateData: function() {
			this._super();
	        this.jq.toggleClass("empty", this.isEmpty());
		}
	});

	/**
	 * Toggle global filter class in filterable columns on focus of global filter input.
	 */
	$(document).on("focus", ".ui-datatable-actions .ui-inputfield.filter", function() {
		$(this).closest("form").find("th.ui-filter-column").addClass("global");
	}).on("blur", ".ui-datatable-actions .ui-inputfield.filter", function() {
		$(this).closest("form").find("th.ui-filter-column").removeClass("global");
	});

	/**
	 * Global search actions.
	 */
	$(document).on("keypress", ".ui-datatable-actions .ui-inputfield.filter", function(event) {
		if (event.keyCode == 13) {
			$(this).next().click();
			return false;
		}
	}).on("search", ".ui-datatable-actions .ui-inputfield.filter", function() {
		$(this).next().click();
	}).on("click", ".ui-datatable-actions .ui-button.search", function() {
		var dataTableWidget = PF($(this).data("tablewidgetid"));
		var $globalFilter = dataTableWidget.jq.find("[id$=globalFilter]");
		var $globalFilterValue = $(this).prev().val().trim();

		if ($globalFilter.val() != $globalFilterValue) {
			$globalFilter.val($globalFilterValue);
			dataTableWidget.filter();
		}
	});

}