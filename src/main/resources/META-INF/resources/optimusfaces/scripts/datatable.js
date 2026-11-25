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
 * Improve <p:dataTable> script.
 * 
 * 1. Make row with key=0 unselectable.
 * 2. Check if default sort needs to be descending (by presence of class="desc" on th).
 * 3. Initialize op:dataTable global search/filter events.
 * 
 * Original source here: https://github.com/primefaces/primefaces/blob/master/src/main/resources/META-INF/resources/primefaces/datatable/datatable.js
 */
if (PrimeFaces.widget.DataTable) {

    // The handlers bind before any table exists, so they match the ui- classes up to PrimeFaces 16 and the p- classes since PrimeFaces 17.
    var SELECTABLE_ROW_CLASSES = "ui-widget-content p-component-content";
    var SORTED_COLUMN_CLASSES = ["ui-state-active", "p-state-active"];
    var FILTER_COLUMN = "th.ui-filter-column, th.p-filter-column";
    var GLOBAL_FILTER_INPUT = ".ui-datatable-actions input.filter, .p-datatable-actions input.filter";
    var GLOBAL_SEARCH_BUTTON = ".ui-datatable-actions button.search, .p-datatable-actions button.search";

    function makeRowWithKey0Unselectable(id) {
        $(document.getElementById(id)).find("tr[data-rk=0]").removeClass(SELECTABLE_ROW_CLASSES);
    }

    function isSorted($column) {
        return SORTED_COLUMN_CLASSES.some(function(sortedColumnClass) {
            return $column.hasClass(sortedColumnClass);
        });
    }

    function checkIfDefaultSortNeedsToBeDescending($sortableColumns) {
        if (!$sortableColumns || !$sortableColumns.length) {
            return;
        }

        for (var i = 0; i < $sortableColumns.length; i++) {
            var $sortableColumn = $sortableColumns.eq(i);
            if ($sortableColumn.hasClass("desc") && !isSorted($sortableColumn)) {
                $sortableColumn.data("sortorder", 1);
            }
        }
    }

    PrimeFaces.widget.DataTable = class extends PrimeFaces.widget.DataTable {
        init(cfg) {
            makeRowWithKey0Unselectable(cfg.id);
            super.init(cfg);
            checkIfDefaultSortNeedsToBeDescending(this.sortableColumns);
        }
        sort(columnHeader, order, multi) {
            super.sort(columnHeader, order, multi);
            checkIfDefaultSortNeedsToBeDescending(this.sortableColumns);
        }
        postUpdateData() {
            super.postUpdateData();
            this.jq.toggleClass("empty", this.isEmpty());
        }
    };

    /**
     * Toggle global filter class in filterable columns on focus of global filter input.
     */
    $(document).on("focus", GLOBAL_FILTER_INPUT, function() {
        $(this).closest("form").find(FILTER_COLUMN).addClass("global");
    }).on("blur", GLOBAL_FILTER_INPUT, function() {
        $(this).closest("form").find(FILTER_COLUMN).removeClass("global");
    });

    /**
     * Global search actions.
     */
    $(document).on("keypress", GLOBAL_FILTER_INPUT, function(event) {
        if (event.keyCode == 13) {
            $(this).next().click();
            return false;
        }
    }).on("search", GLOBAL_FILTER_INPUT, function() {
        $(this).next().click();
    }).on("click", GLOBAL_SEARCH_BUTTON, function() {
        var dataTableWidget = PF($(this).data("tablewidgetid"));
        var $globalFilter = dataTableWidget.jq.find("[id$=globalFilter]");
        var $globalFilterValue = $(this).prev().val().trim();

        if ($globalFilter.val() != $globalFilterValue) {
            $globalFilter.val($globalFilterValue);
            dataTableWidget.filter();
        }
    });

}