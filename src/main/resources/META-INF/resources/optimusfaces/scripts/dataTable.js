/*
 * Copyright 2016 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
/**
 * Improve <p:dataTable> script.
 * 
 * 1. make row with key=0 unselectable.
 * 2. check if default sort needs to be descending (by presence of class="desc" on th).
 * 3. initialize op:dataTable global search/filter events.
 * 
 * Original source here: https://github.com/primefaces/primefaces/blob/master/src/main/resources/META-INF/resources/primefaces/datatable/datatable.js
 */
if (PrimeFaces.widget.DataTable) {
	PrimeFaces.widget.DataTable = PrimeFaces.widget.DataTable.extend({
		init: function(cfg) {
			$(document.getElementById(cfg.id)).find("tr[data-rk=0]").removeClass("ui-widget-content"); // Make row with rowKey=0 unselectable.
			this._super(cfg);
		},	
		bindSortEvents: function() {
			var d = this;
			this.cfg.tabindex = this.cfg.tabindex || "0";
			this.sortableColumns = this.thead.find("> tr > th.ui-sortable-column");
			this.sortableColumns.attr("tabindex", this.cfg.tabindex);
			if (this.cfg.multiSort) {
				this.sortMeta = []
			}
			for (var a = 0; a < this.sortableColumns.length; a++) {
				var c = this.sortableColumns.eq(a),
				e = c.children("span.ui-sortable-column-icon"),
				b = null;
				if (c.hasClass("ui-state-active")) {
					if (e.hasClass("ui-icon-triangle-1-n")) {
						b = this.SORT_ORDER.ASCENDING
					} else {
						b = this.SORT_ORDER.DESCENDING
					}
					if (d.cfg.multiSort) {
						d.addSortMeta({
							col: c.attr("id"),
							order: b
						})
					}
				} else {
					b = this.SORT_ORDER.UNSORTED
				}
				c.data("sortorder", b)
			}
			this.sortableColumns.on("mouseenter.dataTable", function() {
				var f = $(this);
				if (!f.hasClass("ui-state-active")) {
					f.addClass("ui-state-hover")
				}
			}).on("mouseleave.dataTable", function() {
				var f = $(this);
				if (!f.hasClass("ui-state-active")) {
					f.removeClass("ui-state-hover")
				}
			}).on("blur.dataTable", function() {
				$(this).removeClass("ui-state-focus")
			}).on("focus.dataTable", function() {
				$(this).addClass("ui-state-focus")
			}).on("keydown.dataTable", function(h) {
				var f = h.which,
				g = $.ui.keyCode;
				if ((f === g.ENTER || f === g.NUMPAD_ENTER) && $(h.target).is(":not(:input)")) {
					$(this).trigger("click.dataTable", (h.metaKey || h.ctrlKey));
					h.preventDefault()
				}
			}).on("click.dataTable", function(j, h) {
				if (!d.shouldSort(j, this)) {
					return
				}
				PrimeFaces.clearSelection();
				var i = $(this),
				f = i.data("sortorder"),
				g = (f === d.SORT_ORDER.UNSORTED) ? (i.hasClass("desc") ? d.SORT_ORDER.DESCENDING : d.SORT_ORDER.ASCENDING) : -1 * f, // #1
						k = j.metaKey || j.ctrlKey || h;
				if (d.cfg.multiSort) {
					if (k) {
						d.addSortMeta({
							col: i.attr("id"),
							order: g
						});
						d.sort(i, g, true)
					} else {
						d.sortMeta = [];
						d.addSortMeta({
							col: i.attr("id"),
							order: g
						});
						d.sort(i, g)
					}
				} else {
					d.sort(i, g)
				}
			})
		}
	});
	
	/**
	 * Highlight global filter columns on focus of global search.
	 */
	$(document).on("focus", "input.dataTableGlobalSearch", function() {
		$(this).closest("form").find("th.ui-filter-column:not(.filterable)").addClass("globalFilter");
	}).on("blur", "input.dataTableGlobalSearch", function() {
		$(this).closest("form").find("th.ui-filter-column:not(.filterable)").removeClass("globalFilter");
	});

	/**
	 * Global search actions.
	 */
	$(document).on("keypress", "input.dataTableGlobalSearch", function(event) {
		if (event.keyCode == 13) {
			$(this).next().click();
			return false;
		}
	}).on("search", "input.dataTableGlobalSearch", function() {
		$(this).next().click();
	}).on("click", "button.dataTableGlobalSearch", function() {
		var dataTableWidget = PF($(this).data("tablewidgetid"));
		var $globalFilter = dataTableWidget.jq.find("input[type=hidden][id$=globalFilter]");
		var $globalFilterValue = $(this).prev().val().trim();

		if ($globalFilter.val() != $globalFilterValue) {
			$globalFilter.val($globalFilterValue);
			dataTableWidget.filter();
		}

		return false;
	});
		
}