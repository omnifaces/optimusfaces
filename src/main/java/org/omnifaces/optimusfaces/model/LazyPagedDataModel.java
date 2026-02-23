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

import static jakarta.faces.component.UIComponent.getCurrentComponent;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIData;
import jakarta.faces.component.UINamingContainer;
import jakarta.faces.context.FacesContext;

import org.omnifaces.component.ParamHolder;
import org.omnifaces.component.SimpleParam;
import org.omnifaces.optimusfaces.component.ExtendedDataTable;
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
import org.primefaces.component.datascroller.DataScroller;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;

/**
 * <p>
 * Lazy-loading {@link PagedDataModel} implementation that delegates each page request to a
 * {@link PagedDataModel.PartialResultListLoader}, typically backed by a {@link BaseEntityService}.
 * <p>
 * On each render PrimeFaces {@link DataTable} calls {@link #load}, which extracts the active sort, per-column filter
 * values, global search term and selection from PrimeFaces {@link DataTable} component, assembles a {@link Page}
 * descriptor and forwards the call to the loader. The resulting {@link PartialResultList} updates row-count and
 * paginator state via {@link #count}.
 * <p>
 * When <code>updateQueryString</code> is set on the <code>&lt;op:dataTable&gt;</code>, every paging, sorting, filtering
 * and selection change is reflected back into the browser URL as query parameters via an Ajax <code>oncomplete</code>
 * callback, making the table state bookmarkable.
 * <p>
 * Construct via {@link PagedDataModel#lazy(BaseEntityService)} or
 * {@link PagedDataModel#lazy(PagedDataModel.PartialResultListLoader)}.
 *
 * @param <E> The entity type, which must extend {@link Identifiable}.
 * @see PagedDataModel
 * @see NonLazyPagedDataModel
 * @author Bauke Scholtz
 * @since 1.0
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

    /**
     * Called by PrimeFaces {@link DataTable} on every page render to supply data for the component.
     * <p>
     * Dispatches to {@link #loadPage} for PrimeFaces {@link DataTable} (which applies full sort, filter and
     * query-string processing) or to a simplified offset-based load for {@link DataScroller}.
     * @param offset Zero-based index of the first row to return.
     * @param limit Maximum number of rows to return.
     * @param sortBy Active sort metadata keyed by field name, supplied by PrimeFaces {@link DataTable}.
     * @param filterBy Active filter metadata keyed by field name, supplied by PrimeFaces {@link DataTable}.
     * @return The current page of entities; never <code>null</code>.
     */
    @Override
    public List<E> load(int offset, int limit, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
        var context = getContext();
        var data = getDataComponent();

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

    /**
     * Returns the estimated total number of matching rows as reported by the most recent {@link #load} call.
     * <p>
     * PrimeFaces {@link DataTable} calls this once after {@link #load} to update the paginator. The value originates from
     * {@link PartialResultList#getEstimatedTotalNumberOfResults()} and is <code>0</code> when no page has yet been
     * loaded.
     * @param filterBy Active filter metadata; not re-applied here, the count is already embedded in the
     * {@link PartialResultList} retrieved during {@link #load}.
     * @return Estimated total row count; <code>0</code> if {@link #load} has not yet been called.
     */
    @Override
    public int count(Map<String, FilterMeta> filterBy) {
        return list == null ? 0 : list.getEstimatedTotalNumberOfResults();
    }

    /**
     * Eagerly loads and caches the first page of data without waiting for PrimeFaces {@link DataTable}'s normal
     * {@link #load} invocation.
     * <p>
     * Called by {@link ExtendedDataTable#preDecode} when the model's wrapped data is still <code>null</code>
     * at the start of a postback, a situation that arises for request-scoped beans and stateless views that
     * do not retain model state between requests. Pre-loading guarantees that row keys, selection state and
     * pagination information are available when PrimeFaces {@link DataTable} processes the submitted form data during
     * decode.
     * @param context The current {@link FacesContext}.
     * @param table The {@link DataTable} that triggered the postback.
     */
    public void preloadPage(FacesContext context, DataTable table) {
        var sortBy = getInitialOrdering(context, table);
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

        var offset = table.getFirst();
        var limit = table.getRows();
        var requiredCriteria = processRequiredCriteria(processableColumns);
        var optionalCriteria = processOptionalCriteria(processableColumns);

        loadPage(table, offset, limit, requiredCriteria, optionalCriteria);
        updateQueryStringIfNecessary(context);
    }

    private static String parseQueryParameterPrefix(DataTable table) {
        return coalesce((String) table.getAttributes().get("queryParameterPrefix"), "");
    }

    private void loadPage(UIData data, int offset, int limit, Map<String, Object> requiredCriteria, Map<String, Object> optionalCriteria) {
        var pageOfSameCriteria = requiredCriteria.equals(page.getRequiredCriteria()) && optionalCriteria.equals(page.getOptionalCriteria());
        var nextOrPreviousPageOfSameCriteria = pageOfSameCriteria && !isEmpty(list) && abs(offset - page.getOffset()) == limit && ordering.equals(page.getOrdering());
        var previousPageOfSameCriteria = nextOrPreviousPageOfSameCriteria && offset < page.getOffset();
        var rowCountNeedsUpdate = getRowCount() <= 0 || !pageOfSameCriteria;
        var last = nextOrPreviousPageOfSameCriteria ? list.get(previousPageOfSameCriteria ? 0 : list.size() - 1) : null;

        page = new Page(offset, limit, last, previousPageOfSameCriteria, ordering, requiredCriteria, optionalCriteria);
        list = load(page, rowCountNeedsUpdate);
        var count = list.getEstimatedTotalNumberOfResults();

        if (count != -1 && count != getRowCount()) {
            if (list.isEmpty() && count > 0 && offset > count) { // Can happen when user has paginated too far and then changed criteria which returned fewer results.
                var offsetOfLastPage = offset - ((offset - count) / data.getRows() + 1) * data.getRows();
                data.setFirst(offsetOfLastPage);
                page = new Page(offsetOfLastPage, limit, ordering, requiredCriteria, optionalCriteria);
                list = load(page, false);
            }

            setRowCount(count);
        }
    }

    /**
     * Template method that forwards the page request to the underlying
     * {@link PagedDataModel.PartialResultListLoader}.
     * <p>
     * {@link NonLazyPagedDataModel} overrides this method to perform in-memory filtering and sorting instead
     * of delegating to the loader.
     * @param page Describes offset, limit, ordering and filter criteria.
     * @param estimateTotalNumberOfResults <code>true</code> when PrimeFaces {@link DataTable} needs the total row count.
     * @return The requested page of entities.
     */
    protected PartialResultList<E> load(Page page, boolean estimateTotalNumberOfResults) {
        return loader.getPage(page, estimateTotalNumberOfResults);
    }

    /**
     * Returns the current {@link UIData} component that is being rendered.
     * <p>
     * When PrimeFaces {@link DataTable} renders a paginator or filter widget, the current component is a child of the
     * table rather than the table itself; this method walks up to the enclosing {@link UIData}.
     * @return The enclosing {@link UIData} component; never <code>null</code>.
     */
    protected UIData getDataComponent() {
        var currentComponent = getCurrentComponent();

        if (currentComponent instanceof UIData) {
            return (UIData) currentComponent;
        }
        else {
            var id = currentComponent.getId().split("_", 2)[0];
            return (UIData) currentComponent.findComponent(id);
        }
    }

    /**
     * Returns <code>true</code> if the given column should be included when building filter criteria.
     * <p>
     * A column is considered processable when it has a <code>field</code> attribute and is either individually
     * filterable or the parent table has <code>searchable</code> enabled.
     * @param column The column to test.
     * @return <code>true</code> if the column participates in filter-criteria processing.
     */
    protected boolean isProcessableColumn(UIColumn column) {
        if (column.getField() == null) {
            return false;
        }

        if (column.isFilterable()) {
            return true;
        }

        var table = (DataTable) ((UIComponent) column).getParent();
        return Boolean.parseBoolean(String.valueOf(table.getAttributes().get("searchable")));
    }

    /**
     * Builds the active ordering map from PrimeFaces {@link DataTable} sort metadata combined with the configured
     * default ordering.
     * <p>
     * On the very first load this method also reads the <code>page</code> query parameter and advances the table's
     * first-row offset so that a bookmarked page is restored correctly.
     * @param context The current {@link FacesContext}.
     * @param table The surrounding {@link DataTable}.
     * @param sortBy Active sort metadata supplied by PrimeFaces {@link DataTable}.
     * @return An ordered map of field name to ascending flag representing the full effective ordering.
     */
    protected LinkedHashMap<String, Boolean> processPageAndOrdering(FacesContext context, DataTable table, Map<String, SortMeta> sortBy) {
        var ordering = new LinkedHashMap<String, Boolean>(2);

        var tableSortMeta = sortBy.isEmpty() ? new SortMeta() : sortBy.values().iterator().next();
        var tableSortField = tableSortMeta.getField();
        var tableSortOrder = tableSortMeta.getOrder();

        if (list == null) {
            var page = getTrimmedQueryParameter(context, queryParameterPrefix + QUERY_PARAMETER_PAGE);

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

    /**
     * Returns the initial {@link SortMeta} for pre-loading the model before PrimeFaces {@link DataTable} decode runs.
     * <p>
     * Reads the <code>order</code> query parameter to restore a bookmarked sort; falls back to the first entry
     * of the configured default ordering when no valid query parameter is found.
     * @param context The current {@link FacesContext}.
     * @param table The surrounding {@link DataTable}.
     * @return A {@link SortMeta} describing the field and direction to sort by initially.
     */
    protected SortMeta getInitialOrdering(FacesContext context, DataTable table) {
        var sort = getTrimmedQueryParameter(context, parseQueryParameterPrefix(table) + QUERY_PARAMETER_ORDER);

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

        var defaultOrder = defaultOrdering.entrySet().iterator().next();
        return SortMeta.builder().field(defaultOrder.getKey()).order(defaultOrder.getValue() ? ASCENDING : DESCENDING).build();
    }

    /**
     * Collects the active per-column filter values into an ordered map of field to filter value.
     * <p>
     * For each processable column the filter value is taken first from PrimeFaces {@link DataTable}'s
     * <code>filterBy</code> metadata; when that is empty the corresponding query string parameter is consulted so that
     * bookmarked filters are restored on the initial (non-postback) request.
     * @param context The current {@link FacesContext}.
     * @param table The surrounding {@link DataTable}.
     * @param processableColumns The columns selected by {@link #isProcessableColumn}.
     * @param filterBy Active filter metadata supplied by PrimeFaces {@link DataTable}.
     * @return An ordered map of field name to normalised filter value for all active column filters.
     */
    protected LinkedHashMap<String, Object> processFilters(FacesContext context, DataTable table, List<UIColumn> processableColumns, Map<String, FilterMeta> filterBy) {
        var mergedFilters = new LinkedHashMap<String, Object>();

        for (UIColumn column : processableColumns) {
            var field = column.getField();
            var value = getFilterValue(filterBy, field);

            if (isEmpty(value)) {
                value = getTrimmedQueryParameters(context, getFilterParameterName(context, table, field));
            }

            if (!isEmpty(value)) {
                mergedFilters.put(field, normalizeCriteriaValue(value));
            }
        }

        return mergedFilters;
    }

    /**
     * Restores row selection from the query string on the initial (non-postback) request.
     * <p>
     * On postbacks the current selection is left untouched. On initial requests the <code>selection</code>
     * query parameter is read and the corresponding entities are loaded by their IDs so that a bookmarked
     * selection is visible immediately.
     * @param context The current {@link FacesContext}.
     * @param currentSelection The in-memory selection list carried over from previous requests.
     * @return The restored or unchanged selection list; never <code>null</code>.
     */
    protected List<E> processSelectionIfNecessary(FacesContext context, List<E> currentSelection) {
        if (currentSelection != null || context.isPostback()) {
            return currentSelection;
        }

        var selection = getTrimmedQueryParameters(context, queryParameterPrefix + QUERY_PARAMETER_SELECTION);
        return selection.isEmpty() ? emptyList() : new ArrayList<>(load(new Page(0, selection.size(), null, singletonMap(ID, selection), null), false));
    }

    /**
     * Extracts and trims the global search term from PrimeFaces {@link DataTable} filter metadata or from the query
     * string.
     * <p>
     * The value in <code>filterBy</code> under the key <code>globalFilter</code> takes precedence; when absent or empty
     * the corresponding query string parameter is checked so that a bookmarked search term is applied on the initial
     * (non-postback) request.
     * @param context The current {@link FacesContext}.
     * @param table The surrounding {@link DataTable}.
     * @param filterBy Active filter metadata supplied by PrimeFaces {@link DataTable}.
     * @return The trimmed global search term, or <code>null</code> when no active global filter is present.
     */
    protected String processGlobalFilter(FacesContext context, DataTable table, Map<String, FilterMeta> filterBy) {
        var globalFilter = (String) getFilterValue(filterBy, GLOBAL_FILTER);

        if (globalFilter != null) {
            globalFilter = globalFilter.trim();
        }

        if (isEmpty(globalFilter)) {
            globalFilter = getTrimmedQueryParameter(context, getFilterParameterName(context, table, null));
        }

        return isEmpty(globalFilter) ? null : globalFilter;
    }

    private static Object getFilterValue(Map<String, FilterMeta> filterBy, String field) {
        var filterMeta = filterBy.get(field);
        return filterMeta == null ? null : filterMeta.getFilterValue();
    }

    private String getFilterParameterName(FacesContext context, DataTable table, String field) {
        String param;

        if (context.isPostback()) {
            var separatorChar = UINamingContainer.getSeparatorChar(context);
            param = table.getClientId(context) + separatorChar + (field == null ? "" : field + separatorChar) + "filter";
        }
        else {
            param = queryParameterPrefix + (field == null ? QUERY_PARAMETER_SEARCH : field);
        }

        return param;
    }

    /**
     * Builds the required (AND) criteria map used when querying the data store.
     * <p>
     * Layers criteria in the following priority: predefined criteria configured on the builder, dynamic criteria
     * supplied by the backing bean, and then the active per-column filter values, each applying
     * <code>startsWith</code>, <code>endsWith</code> or <code>contains</code> wrapping when the column's
     * <code>filterMatchMode</code> requests it.
     * @param processableColumns The columns selected by {@link #isProcessableColumn}.
     * @return A map of field path to criteria value representing all required filter conditions.
     */
    protected Map<String, Object> processRequiredCriteria(List<UIColumn> processableColumns) {
        Map<String, Object> requiredCriteria = new HashMap<>();

        if (predefinedCriteria != null) {
            requiredCriteria.putAll(predefinedCriteria);
        }

        if (dynamicCriteria != null) {
            ofNullable(dynamicCriteria.get()).orElse(emptyMap()).forEach((getter, value) -> processDynamicCriteria(getter.getPropertyName(), value, requiredCriteria));
        }

        for (UIColumn column : processableColumns) {
            var field = column.getField();
            var value = filters.get(field);

            if (!isEmpty(value)) {
                var filterMatchMode = column.getFilterMatchMode();

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
            var nestedCriteria = (Map<Getter<?>, Object>) value;
            nestedCriteria.forEach((getter, nestedValue) -> processDynamicCriteria(field + "." + getter.getPropertyName(), nestedValue, requiredCriteria));
        }
        else {
            requiredCriteria.put(field, value);
        }
    }

    /**
     * Builds the optional (OR) criteria map used when a global filter is active.
     * <p>
     * When {@link #globalFilter} is set, every processable column field receives a <code>contains</code> criterion so
     * that any row matching the search term in any of those fields is included in the results.
     * @param processableColumns The columns selected by {@link #isProcessableColumn}.
     * @return A map of field path to {@link org.omnifaces.persistence.criteria.Like#contains} criteria for the
     * global filter, or an empty map when no global filter is active.
     */
    protected Map<String, Object> processOptionalCriteria(List<UIColumn> processableColumns) {
        Map<String, Object> optionalCriteria = new HashMap<>();

        for (UIColumn column : processableColumns) {
            var field = column.getField();

            if (!isEmpty(globalFilter)) {
                optionalCriteria.put(field, Like.contains(globalFilter));
            }
        }

        return optionalCriteria;
    }

    /**
     * Pushes the current paging, sorting, filtering and selection state into the browser URL as query parameters via an
     * Ajax <code>oncomplete</code> callback, when <code>updateQueryString</code> is enabled.
     * <p>
     * Only fires during Ajax requests. Parameters for page, ordering, column filters and selection are only added when
     * they differ from their defaults; the resulting query string is passed to
     * <code>OptimusFaces.Util.updateQueryString</code> on the client.
     * @param context The current {@link FacesContext}.
     */
    protected void updateQueryStringIfNecessary(FacesContext context) {
        if (!updateQueryString || !isAjaxRequest(context)) {
            return;
        }

        List<ParamHolder<?>> params = new ArrayList<>();

        if (!isEmpty(globalFilter)) {
            params.add(new SimpleParam<>(queryParameterPrefix + QUERY_PARAMETER_SEARCH, globalFilter));
        }

        var currentPage = page.getOffset() / page.getLimit() + 1;

        if (currentPage > 1) {
            params.add(new SimpleParam<>(queryParameterPrefix + QUERY_PARAMETER_PAGE, currentPage));
        }

        if (!page.getOrdering().equals(defaultOrdering)) {
            var order = page.getOrdering().entrySet().iterator().next();
            params.add(new SimpleParam<>(queryParameterPrefix + QUERY_PARAMETER_ORDER, (order.getValue() ? "" : "-") + order.getKey()));
        }

        filters.entrySet().stream()
            .forEach(entry -> stream(normalizeCriteriaValue(stream(entry.getValue()).map(Criteria::unwrap)))
                .forEach(value -> params.add(new SimpleParam<>(queryParameterPrefix + entry.getKey(), value))));

        if (selection != null) {
            selection.stream().sorted().forEach(entity -> params.add(new SimpleParam<>(queryParameterPrefix + QUERY_PARAMETER_SELECTION, entity.getId())));
        }

        oncomplete("OptimusFaces.Util.updateQueryString('" + Servlets.toQueryString(params) + "')");
    }


    // PagedDataModel state -------------------------------------------------------------------------------------------

    @Override
    public Page getPage() {
        return page;
    }


    // Getters+setters for op:dataTable and op:column -----------------------------------------------------------------

    /**
     * Returns the entity's ID as a string row key for PrimeFaces {@link DataTable} selection tracking.
     * Falls back to the entity's identity hash code when the ID is <code>null</code>.
     * @param entity The entity whose row key is required.
     * @return String representation of the entity's ID, or its identity hash code.
     */
    @Override
    public String getRowKey(E entity) {
        return String.valueOf(entity.getId() != null ? entity.getId() : entity.hashCode());
    }

    /**
     * Loads and returns the single entity identified by the given row key.
     * <p>
     * Issues a one-row query filtered by the entity's ID so that PrimeFaces {@link DataTable} can restore selection or row
     * expansion state after a postback.
     * @param rowKey The string row key previously returned by {@link #getRowKey}.
     * @return The corresponding entity.
     */
    @Override
    public E getRowData(String rowKey) {
        return load(new Page(0, 1, null, singletonMap(ID, rowKey), null), false).get(0);
    }

    /**
     * Returns the active sort as a PrimeFaces {@link SortMeta}, or the initial ordering when no page has been
     * loaded yet.
     * @return The current primary sort descriptor; <code>null</code> when no ordering is defined.
     */
    @Override
    public SortMeta getOrdering() {
        var context = FacesContext.getCurrentInstance();
        var table = (DataTable) getCurrentComponent(context);

        return ordering == null ? getInitialOrdering(context, table) : ordering.entrySet().stream()
            .map(entry -> SortMeta.builder().field(entry.getKey()).order(entry.getValue() ? SortOrder.ASCENDING : SortOrder.DESCENDING).build())
            .findFirst().orElse(null); // TODO: optimize/cache this (and utilize the new multisort feature)
    }

    /**
     * Returns the active column filters as a map of field name to {@link FilterMeta}.
     * @return The current active filters; empty map when no filters are active.
     */
    @Override
    public Map<String, FilterMeta> getFilters() {
        return filters == null ? emptyMap() : filters.entrySet().stream()
            .map(entry -> FilterMeta.builder().field(entry.getKey()).filterValue(entry.getValue()).build())
            .collect(Collectors.toMap(FilterMeta::getField, identity())); // TODO: optimize/cache this
    }

    /**
     * Returns the {@link FilterMeta} for the given field, creating and registering an empty one when absent.
     * @param field The column field name.
     * @return The {@link FilterMeta} for the field; never <code>null</code>.
     */
    @Override
    public FilterMeta getFilter(String field) {
        var filterMeta = getFilters().get(field);

        if (filterMeta == null) {
            filterMeta = FilterMeta.builder().field(field).build();
            this.filters.put(field, filterMeta);
        }

        return filterMeta;
    }

    /**
     * Returns the filtered-value list used by PrimeFaces {@link DataTable} for client-side filtering support.
     * @return The filtered-value list; may be <code>null</code>.
     */
    @Override
    public List<E> getFilteredValue() {
        return filteredValue;
    }

    /**
     * Sets the filtered-value list used by PrimeFaces {@link DataTable} for client-side filtering support.
     * @param filteredValue The filtered list to store.
     */
    @Override
    public void setFilteredValue(List<E> filteredValue) {
        this.filteredValue = filteredValue;
    }

    /**
     * Returns the currently selected rows.
     * @return The selection list; may be <code>null</code> when selection has not been initialised.
     */
    @Override
    public List<E> getSelection() {
        return selection;
    }

    /**
     * Updates the currently selected rows and, when <code>updateQueryString</code> is enabled, reflects the
     * new selection in the browser URL immediately.
     * @param selection The new selection list.
     */
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
        var param = getRequestParameter(context, name);
        return param != null ? param.trim() : null;
    }

    private static List<String> getTrimmedQueryParameters(FacesContext context, String name) {
        var params = getRequestParameterValues(context, name);
        return params != null ? stream(params).filter(Lang::isNotBlank).collect(toList()) : emptyList();
    }

}