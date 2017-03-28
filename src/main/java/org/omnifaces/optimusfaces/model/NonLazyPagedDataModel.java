/*
 * Copyright 2017 OmniFaces
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
package org.omnifaces.optimusfaces.model;

import static java.util.Collections.emptyList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.faces.model.ListDataModel;

import org.primefaces.model.SortOrder;

/**
 * <p>
 * Non lazy paged data model so &lt;op:dataTable&gt; can also be used on non-lazy data models.
 *
 * <h3>Usage:</h3>
 * <p>
 * Here's how you can utilize it:
 * <pre>
 * {@code @Named}
 * {@code @RequestScoped}
 * public class YourBackingBean {
 *
 *     private org.omnifaces.optimusfaces.model.PagedDataModel&lt;YourEntity&gt; model;
 *
 *     {@code @Inject}
 *     private YourEntityService yourEntityService;
 *
 *     {@code @PostConstruct}
 *     public void init() {
 *         model = PagedDataModel.forAllData(yourEntityService.getList());
 *     }
 *
 *     public PagedDataModel&lt;YourEntity&gt; getModel() {
 *         return model;
 *     }
 *
 * }
 * </pre>
 *
 * <pre>
 * &lt;h:form&gt;
 *     &lt;op:dataTable id="yourEntitiesTable" value="#{bean.model}"&gt;
 *         ...
 *     &lt;/op:dataTable&gt;
 * &lt;/h:form&gt;
 * </pre>
 *
 * @author Bauke Scholtz
 */
public final class NonLazyPagedDataModel<T> extends ListDataModel<T> implements PagedDataModel<T> {

	// Internal properties --------------------------------------------------------------------------------------------

	private Map<String, Object> filters;
	private Map<String, Boolean> visibleColumns;
	private Map<String, Object> remappedFilters; // TODO?
	private String globalFilter; // TODO?
	private boolean filterWithAND; // TODO?

	// p:dataTable properties -----------------------------------------------------------------------------------------

	private String sortField;
	private String sortOrder;
	private List<T> selection;
	private List<T> filteredValue;


	// Constructors ---------------------------------------------------------------------------------------------------

	/**
	 * Use PagedDataModel.forXxx() to build one.
	 */
	NonLazyPagedDataModel(List<T> allData, String defaultSortField, SortOrder defaultSortOrder) {
		super(allData);

		filters = new HashMap<>();
		visibleColumns = new HashMap<>();

		sortField = defaultSortField;
		sortOrder = defaultSortOrder.name();
		selection = emptyList();
	}

	// Getters+setters ------------------------------------------------------------------------------------------------

	@Override
	public Map<String, Boolean> getVisibleColumns() {
		return visibleColumns;
	}

	@Override
	public Map<String, Object> getFilters() {
		return filters;
	}

	@Override
	public String getSortField() {
		return sortField;
	}

	@Override
	public void setSortField(String sortField) {
		this.sortField = sortField;
	}

	@Override
	public String getSortOrder() {
		return sortOrder;
	}

	@Override
	public void setSortOrder(String sortOrder) {
		this.sortOrder = sortOrder;
	}

	@Override
	public List<T> getFilteredValue() {
		return filteredValue;
	}

	@Override
	public void setFilteredValue(List<T> filteredValue) {
		this.filteredValue = filteredValue;
	}

	@Override
	public List<T> getSelection() {
		return selection;
	}

	@Override
	public void setSelection(List<T> selection) {
		if (!Objects.equals(selection, this.selection)) {
			this.selection = selection;
			// updateQueryStringIfNecessary(); TODO?
		}
	}

}