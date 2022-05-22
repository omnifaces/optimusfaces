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
package org.omnifaces.optimusfaces.model;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Math.abs;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.faces.component.UIComponent.getCurrentComponent;
import static org.omnifaces.persistence.model.Identifiable.ID;
import static org.omnifaces.util.Ajax.oncomplete;
import static org.omnifaces.util.Components.getCurrentComponent;
import static org.omnifaces.util.Faces.getContext;
import static org.omnifaces.util.FacesLocal.getRequestParameter;
import static org.omnifaces.util.FacesLocal.getRequestParameterValues;
import static org.omnifaces.util.FacesLocal.isAjaxRequest;
import static org.omnifaces.utils.Lang.coalesce;
import static org.omnifaces.utils.Lang.isEmpty;
import static org.omnifaces.utils.stream.Collectors.toLinkedMap;
import static org.omnifaces.utils.stream.Collectors.toLinkedSet;
import static org.omnifaces.utils.stream.Streams.stream;
import static org.primefaces.model.SortOrder.ASCENDING;
import static org.primefaces.model.SortOrder.DESCENDING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.faces.component.UIComponent;
import javax.faces.component.UINamingContainer;
import javax.faces.context.FacesContext;

import org.omnifaces.component.ParamHolder;
import org.omnifaces.component.SimpleParam;
import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.model.Identifiable;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.util.Servlets;
import org.omnifaces.utils.Lang;
import org.omnifaces.utils.collection.PartialResultList;
import org.omnifaces.utils.reflect.Getter;
import org.primefaces.component.api.UIColumn;
import org.primefaces.component.api.UIData;
import org.primefaces.component.datascroller.DataScroller;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;

/**
 * Use {@link PagedDataModel#lazy(BaseEntityService)} or {@link PagedDataModel#lazy(PagedDataModel.PartialResultListLoader)} to build one.
 *
 * @see PagedDataModel
 * @author Bauke Scholtz
 */
public class LazyPagedDataModel<E extends Identifiable<?>> extends LazyDataModel<E> implements PagedDataModel<E> {

	// Constants ------------------------------------------------------------------------------------------------------

	private static final long serialVersionUID = 1L;
	private static final String GLOBAL_FILTER = "globalFilter";


	// Internal properties --------------------------------------------------------------------------------------------

	private final PartialResultListLoader<E> loader;
	private final LinkedHashMap<String, Boolean> defaultOrdering;
	private final Map<String, Object> predefinedCriteria;
	private final Supplier<Map<Getter<?>, Object>> dynamicCriteria;

	protected boolean updateQueryString;
	protected String queryParameterPrefix;
	protected LinkedHashMap<String, Boolean> ordering;
	protected LinkedHashMap<String, Object> filters;
	protected String globalFilter;

	private Page page;
	private PartialResultList<E> list;


	// op:dataTable properties ----------------------------------------------------------------------------------------

	private List<E> filteredValue;
	private List<E> selection;


	// Constructors ---------------------------------------------------------------------------------------------------

	LazyPagedDataModel(PartialResultListLoader<E> loader, LinkedHashMap<String, Boolean> defaultOrdering, Map<String, Object> predefinedCriteria, Supplier<Map<Getter<?>, Object>> dynamicCriteria) {
		this.loader = loader;
		this.defaultOrdering = defaultOrdering;
		this.predefinedCriteria = predefinedCriteria;
		this.dynamicCriteria = dynamicCriteria;
		filters = new LinkedHashMap<>();
		page = Page.ALL;
		setRowCount(-1);
	}


	// Actions --------------------------------------------------------------------------------------------------------

	@Override
	public List<E> load(int offset, int limit, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
		FacesContext context = getContext();
		UIData data = getDataComponent();

		if (data instanceof DataTable) {
			loadPage(context, (DataTable) data, sortBy, filterBy);
		}
		else if (data instanceof DataScroller) {
			loadPage(data, offset, limit, emptyMap(), emptyMap());
		}
		else {
			throw new UnsupportedOperationException("UIData component " + data + " is not yet supported.");
		}

		return list;
	}

	// Introduced in PrimeFaces 11.0.0, but we want to keep OptimusFaces backwards compatible with PrimeFaces 10.0.0,
	// hence the @Override annotation is dropped for now.
	public int count(Map<String, FilterMeta> filterBy) {
		return list == null ? 0 : list.getEstimatedTotalNumberOfResults();
	}

	public void preloadPage(FacesContext context, DataTable table) {
		SortMeta sortBy = getInitialOrdering(context, table);
		loadPage(context, table, singletonMap(sortBy.getField(), sortBy), emptyMap());
		setWrappedData(list);
		setRowCount(list.getEstimatedTotalNumberOfResults());
		setPageSize(table.getRows());
	}

	private void loadPage(FacesContext context, DataTable table, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
		List<UIColumn> processableColumns = table.getColumns().stream().filter(this::isProcessableColumn).collect(toList());

		updateQueryString = parseBoolean(String.valueOf(table.getAttributes().get("updateQueryString")));
		queryParameterPrefix = parseQueryParameterPrefix(table);
		ordering = processPageAndOrdering(context, table, sortBy);
		filters = processFilters(context, table, processableColumns, filterBy);
		globalFilter = processGlobalFilter(context, table, filterBy);
		selection = processSelectionIfNecessary(context, selection);

		int offset = table.getFirst();
		int limit = table.getRows();
		Map<String, Object> requiredCriteria = processRequiredCriteria(processableColumns);
		Map<String, Object> optionalCriteria = processOptionalCriteria(processableColumns);

		loadPage(table, offset, limit, requiredCriteria, optionalCriteria);
		updateQueryStringIfNecessary(context);
	}

	private static String parseQueryParameterPrefix(DataTable table) {
		return coalesce((String) table.getAttributes().get("queryParameterPrefix"), "");
	}

	private void loadPage(UIData data, int offset, int limit, Map<String, Object> requiredCriteria, Map<String, Object> optionalCriteria) {
		boolean pageOfSameCriteria = requiredCriteria.equals(page.getRequiredCriteria()) && optionalCriteria.equals(page.getOptionalCriteria());
		boolean nextOrPreviousPageOfSameCriteria = pageOfSameCriteria && !isEmpty(list) && abs(offset - page.getOffset()) == limit && ordering.equals(page.getOrdering());
		boolean previousPageOfSameCriteria = nextOrPreviousPageOfSameCriteria && offset < page.getOffset();
		boolean rowCountNeedsUpdate = getRowCount() <= 0 || !pageOfSameCriteria;
		E last = nextOrPreviousPageOfSameCriteria ? list.get(previousPageOfSameCriteria ? 0 : list.size() - 1) : null;

		page = new Page(offset, limit, last, previousPageOfSameCriteria, ordering, requiredCriteria, optionalCriteria);
		list = load(page, rowCountNeedsUpdate);
		int count = list.getEstimatedTotalNumberOfResults();

		if (count != -1 && count != getRowCount()) {
			if (list.isEmpty() && count > 0 && offset > count) { // Can happen when user has paginated too far and then changed criteria which returned fewer results.
				int offsetOfLastPage = offset - ((((offset - count) / data.getRows()) + 1) * data.getRows());
				data.setFirst(offsetOfLastPage);
				page = new Page(offsetOfLastPage, limit, ordering, requiredCriteria, optionalCriteria);
				list = load(page, false);
			}

			setRowCount(count);
		}
	}

	protected PartialResultList<E> load(Page page, boolean estimateTotalNumberOfResults) {
		return loader.getPage(page, estimateTotalNumberOfResults);
	}

	protected UIData getDataComponent() {
		UIComponent currentComponent = getCurrentComponent();

		if (currentComponent instanceof UIData) {
			return (UIData) currentComponent;
		}
		else {
			String id = currentComponent.getId().split("_", 2)[0];
			return (UIData) currentComponent.findComponent(id);
		}
	}

	protected boolean isProcessableColumn(UIColumn column) {
		if (column.getField() == null) {
			return false;
		}

		if (column.isFilterable()) {
			return true;
		}

		DataTable table = (DataTable) ((UIComponent) column).getParent();
		return Boolean.parseBoolean(String.valueOf(table.getAttributes().get("searchable")));
	}

	protected LinkedHashMap<String, Boolean> processPageAndOrdering(FacesContext context, DataTable table, Map<String, SortMeta> sortBy) {
		LinkedHashMap<String, Boolean> ordering = new LinkedHashMap<>(2);

		SortMeta tableSortMeta = sortBy.isEmpty() ? new SortMeta() : sortBy.values().iterator().next();
		String tableSortField = tableSortMeta.getField();
		SortOrder tableSortOrder = tableSortMeta.getOrder();

		if (list == null) {
			String page = getTrimmedQueryParameter(context, queryParameterPrefix + QUERY_PARAMETER_PAGE);

			if (!isEmpty(page)) {
				try {
					table.setFirst((Integer.valueOf(page) - 1) * table.getRows());
				}
				catch (NumberFormatException ignore) {
					//
				}
			}
		}

		if (!isEmpty(tableSortField)) {
			ordering.put(tableSortField, tableSortOrder == ASCENDING);
		}

		defaultOrdering.forEach((defaultSortField, defaultSortAscending) -> ordering.putIfAbsent(defaultSortField, defaultSortAscending));
		return ordering;
	}

	protected SortMeta getInitialOrdering(FacesContext context, DataTable table) {
		String sort = getTrimmedQueryParameter(context, parseQueryParameterPrefix(table) + QUERY_PARAMETER_ORDER);

		if (!isEmpty(sort)) {
			String field;
			SortOrder order;

			if (sort.startsWith("-")) {
				field = sort.substring(1);
				order = DESCENDING;
			}
			else {
				field = sort;
				order = ASCENDING;
			}

			if (!isEmpty(sort) && table.getColumns().stream().anyMatch(column -> field.equals(column.getField()))) {
				return SortMeta.builder().field(field).order(order).build();
			}
		}

		Entry<String, Boolean> defaultOrder = defaultOrdering.entrySet().iterator().next();
		return SortMeta.builder().field(defaultOrder.getKey()).order(defaultOrder.getValue() ? ASCENDING : DESCENDING).build();
	}

	protected LinkedHashMap<String, Object> processFilters(FacesContext context, DataTable table, List<UIColumn> processableColumns, Map<String, FilterMeta> filterBy) {
		LinkedHashMap<String, Object> mergedFilters = new LinkedHashMap<>();

		for (UIColumn column : processableColumns) {
			String field = column.getField();
			Object value = getFilterValue(filterBy, field);

			if (isEmpty(value)) {
				value = getTrimmedQueryParameters(context, getFilterParameterName(context, table, field));
			}

			if (!isEmpty(value)) {
				mergedFilters.put(field, normalizeCriteriaValue(value));
			}
		}

		return mergedFilters;
	}

	protected List<E> processSelectionIfNecessary(FacesContext context, List<E> currentSelection) {
		if (currentSelection != null || context.isPostback()) {
			return currentSelection;
		}

		List<String> selection = getTrimmedQueryParameters(context, queryParameterPrefix + QUERY_PARAMETER_SELECTION);
		return selection.isEmpty() ? emptyList() : new ArrayList<>(load(new Page(0, selection.size(), null, singletonMap(ID, selection), null), false));
	}

	protected String processGlobalFilter(FacesContext context, DataTable table, Map<String, FilterMeta> filterBy) {
		String globalFilter = getFilterValue(filterBy, GLOBAL_FILTER);

		if (globalFilter != null) {
			globalFilter = globalFilter.trim();
		}

		if (isEmpty(globalFilter)) {
			globalFilter = getTrimmedQueryParameter(context, getFilterParameterName(context, table, null));
		}

		return isEmpty(globalFilter) ? null : globalFilter;
	}

	private String getFilterValue(Map<String, FilterMeta> filterBy, String field) {
		FilterMeta filterMeta = filterBy.get(field);
		return (filterMeta == null) ? null : (String) filterMeta.getFilterValue();
	}

	private String getFilterParameterName(FacesContext context, DataTable table, String field) {
		String param;

		if (context.isPostback()) {
			char separatorChar = UINamingContainer.getSeparatorChar(context);
			param = table.getClientId(context) + separatorChar + (field == null ? "" : (field + separatorChar)) + "filter";
		}
		else {
			param = queryParameterPrefix + ((field == null) ? QUERY_PARAMETER_SEARCH : field);
		}

		return param;
	}

	protected Map<String, Object> processRequiredCriteria(List<UIColumn> processableColumns) {
		Map<String, Object> requiredCriteria = new HashMap<>();

		if (predefinedCriteria != null) {
			requiredCriteria.putAll(predefinedCriteria);
		}

		if (dynamicCriteria != null) {
			ofNullable(dynamicCriteria.get()).orElse(emptyMap()).forEach((getter, value) -> processDynamicCriteria(getter.getPropertyName(), value, requiredCriteria));
		}

		for (UIColumn column : processableColumns) {
			String field = column.getField();
			Object value = filters.get(field);

			if (!isEmpty(value)) {
				String filterMatchMode = column.getFilterMatchMode();

				if ("startsWith".equals(filterMatchMode)) {
					value = Like.startsWith(value.toString());
				}
				else if ("endsWith".equals(filterMatchMode)) {
					value = Like.endsWith(value.toString());
				}
				else if ("contains".equals(filterMatchMode)) {
					value = Like.contains(value.toString());
				}

				requiredCriteria.merge(field, value, LazyPagedDataModel::mergeCriteriaValue);
			}
		}

		return requiredCriteria;
	}

	@SuppressWarnings("unchecked")
	private static void processDynamicCriteria(String field, Object value, Map<String, Object> requiredCriteria) {
		if (value instanceof Map && !((Map<?, ?>) value).isEmpty() && ((Map<?, ?>) value).keySet().iterator().next() instanceof Getter) {
			Map<Getter<?>, Object> nestedCriteria = (Map<Getter<?>, Object>) value;
			nestedCriteria.forEach((getter, nestedValue) -> processDynamicCriteria(field + "." + getter.getPropertyName(), nestedValue, requiredCriteria));
		}
		else {
			requiredCriteria.put(field, value);
		}
	}

	protected Map<String, Object> processOptionalCriteria(List<UIColumn> processableColumns) {
		Map<String, Object> optionalCriteria = new HashMap<>();

		for (UIColumn column : processableColumns) {
			String field = column.getField();

			if (!isEmpty(globalFilter)) {
				optionalCriteria.put(field, Like.contains(globalFilter));
			}
		}

		return optionalCriteria;
	}

	protected void updateQueryStringIfNecessary(FacesContext context) {
		if (!updateQueryString || !isAjaxRequest(context)) {
			return;
		}

		List<ParamHolder> params = new ArrayList<>();

		if (!isEmpty(globalFilter)) {
			params.add(new SimpleParam(queryParameterPrefix + QUERY_PARAMETER_SEARCH, globalFilter));
		}

		int currentPage = (page.getOffset() / page.getLimit()) + 1;

		if (currentPage > 1) {
			params.add(new SimpleParam(queryParameterPrefix + QUERY_PARAMETER_PAGE, currentPage));
		}

		if (!page.getOrdering().equals(defaultOrdering)) {
			Entry<String, Boolean> order = page.getOrdering().entrySet().iterator().next();
			params.add(new SimpleParam(queryParameterPrefix + QUERY_PARAMETER_ORDER, (order.getValue() ? "" : "-") + order.getKey()));
		}

		filters.entrySet().stream()
			.forEach(entry -> stream(normalizeCriteriaValue(stream(entry.getValue()).map(Criteria::unwrap)))
				.forEach(value -> params.add(new SimpleParam(queryParameterPrefix + entry.getKey(), value))));

		if (selection != null) {
			selection.stream().sorted().forEach(entity -> params.add(new SimpleParam(queryParameterPrefix + QUERY_PARAMETER_SELECTION, entity.getId())));
		}

		oncomplete("OptimusFaces.Util.updateQueryString('" + Servlets.toQueryString(params) + "')");
	}


	// PagedDataModel state -------------------------------------------------------------------------------------------

	@Override
	public Page getPage() {
		return page;
	}


	// Getters+setters for op:dataTable and op:column -----------------------------------------------------------------

	@Override
	public String getRowKey(E entity) {
		return String.valueOf(entity.getId() != null ? entity.getId() : entity.hashCode());
	}

	@Override
	public E getRowData(String rowKey) {
		return load(new Page(0, 1, null, singletonMap(ID, rowKey), null), false).get(0);
	}

	@Override
	public SortMeta getOrdering() {
		FacesContext context = FacesContext.getCurrentInstance();
		DataTable table = (DataTable) getCurrentComponent(context);

		return ordering == null ? getInitialOrdering(context, table) : ordering.entrySet().stream()
			.map(entry -> SortMeta.builder().field(entry.getKey()).order(entry.getValue() ? SortOrder.ASCENDING : SortOrder.DESCENDING).build())
			.findFirst().orElse(null); // TODO: optimize/cache this (and utilize the new multisort feature)
	}

	@Override
	public Map<String, FilterMeta> getFilters() {
		return filters == null ? emptyMap() : filters.entrySet().stream()
			.map(entry -> FilterMeta.builder().field(entry.getKey()).filterValue(entry.getValue()).build())
			.collect(Collectors.toMap(FilterMeta::getField, identity())); // TODO: optimize/cache this
	}

	@Override
	public List<E> getFilteredValue() {
		return filteredValue;
	}

	@Override
	public void setFilteredValue(List<E> filteredValue) {
		this.filteredValue = filteredValue;
	}

	@Override
	public List<E> getSelection() {
		return selection;
	}

	@Override
	public void setSelection(List<E> selection) {
		if (!Objects.equals(selection, this.selection)) {
			this.selection = selection;
			updateQueryStringIfNecessary(getContext());
		}
	}


	// Helpers ---------------------------------------------------------------------------------------------------------

	private static Object normalizeCriteriaValue(Object value) {
		Set<Object> set = stream(value).collect(toLinkedSet());
		return set.size() == 1 ? set.iterator().next() : unmodifiableSet(set);
	}

	private static Object mergeCriteriaValue(Object oldValue, Object newValue) {
		Map<String, Object> newValues = stream(newValue).collect(toLinkedMap(Object::toString));
		newValues.keySet().removeAll(stream(oldValue).map(Object::toString).collect(toSet()));

		if (newValues.isEmpty()) {
			return oldValue;
		}
		else {
			return normalizeCriteriaValue(Stream.concat(stream(oldValue), stream(newValues.values())));
		}
	}

	private static String getTrimmedQueryParameter(FacesContext context, String name) {
		String param = getRequestParameter(context, name);
		return (param != null) ? param.trim() : null;
	}

	private static List<String> getTrimmedQueryParameters(FacesContext context, String name) {
		String[] params = getRequestParameterValues(context, name);
		return params != null ? stream(params).filter(Lang::isNotBlank).collect(toList()) : emptyList();
	}

}