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
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.omnifaces.persistence.model.dto.Page.ID;
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

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
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
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.service.EntityRepository;
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

/**
 * <p>
 * Lazy-loading {@link PagedDataModel} implementation that delegates each page request to a {@link PagedDataModel.PartialResultListLoader}, typically backed by
 * an {@link EntityRepository}.
 * <p>
 * On each render PrimeFaces {@link DataTable} calls {@link #load}, which extracts the active sort, per-column filter values, global search term and selection
 * from PrimeFaces {@link DataTable} component, assembles a {@link Page} descriptor and forwards the call to the loader. The resulting {@link PartialResultList}
 * updates row-count and paginator state via {@link #count}.
 * <p>
 * When <code>updateQueryString</code> is set on the <code>&lt;op:dataTable&gt;</code>, every paging, sorting, filtering and selection change is reflected back
 * into the browser URL as query parameters via an Ajax <code>oncomplete</code> callback, making the table state bookmarkable.
 * <p>
 * When serialized, e.g. as part of a bean of a passivating scope, such as <code>@ViewScoped</code> or <code>@SessionScoped</code>, which is passivated or
 * replicated, the model serializes a copy of itself without the entities of the loaded page and of the selection; the model itself keeps them. After
 * deserialization, the page is loaded again on the next postback or render, and the selection is resolved again from its row keys, see {@link #getSelection()}.
 * The entities of the page and of the selection therefore do not need to be {@link Serializable}; the criteria and the ordering values are serialized as they
 * are.
 * <p>
 * Construct via {@link PagedDataModel#lazy(EntityRepository)}, {@link PagedDataModel#lazy(EntityRepository, PagedDataModel.PartialResultListLoader)},
 * {@link PagedDataModel#lazy(BaseEntityService)} or {@link PagedDataModel#lazy(PagedDataModel.PartialResultListLoader)}.
 *
 * @param <E> The entity type.
 * @see PagedDataModel
 * @see NonLazyPagedDataModel
 * @author Bauke Scholtz
 * @since 1.0
 */
public class LazyPagedDataModel<E> extends LazyDataModel<E> implements PagedDataModel<E>, Cloneable {

    // Constants ------------------------------------------------------------------------------------------------------

    private static final long serialVersionUID = 1L;
    private static final String GLOBAL_FILTER = "globalFilter";
    private static final Logger logger = Logger.getLogger(LazyPagedDataModel.class.getName());

    private static final String ERROR_ROW_DATA_NOT_LOADED = "Row key %s is not in the currently loaded data; set its row data loader via"
        + " PagedDataModel.Builder#rowData()";
    private static final String LOG_WARNING_SELECTION_PARTIALLY_RESTORED = "%d of %d selected rows could not be restored after deserialization, because they are"
        + " not in the loaded page; set their row data loader via PagedDataModel.Builder#rowData() to restore all selected rows";
    /** Maximum amount of rows of which the ordering values are cached for value based paging. */
    static final int MAX_CACHED_ORDERING_VALUES = 100;

    // Internal properties --------------------------------------------------------------------------------------------

    /** The loader used to fetch pages of data. */
    private final PartialResultListLoader<E> loader;
    /** The default ordering to be applied if no sorting is specified by the UI. */
    private final LinkedHashMap<String, Boolean> defaultOrdering;
    /** Predefined criteria that are always applied to every query. */
    private final Map<String, Object> predefinedCriteria;
    /** Supplier for dynamic criteria that may change during the view lifecycle. */
    private final DynamicCriteria<?> dynamicCriteria;
    /** How the rows are identified. */
    private final Rows<E> rows;

    /** Flag indicating whether the browser query string should be updated via Ajax. */
    protected boolean updateQueryString;
    /** Prefix for query parameters to avoid collisions when multiple tables are on one page. */
    protected String queryParameterPrefix;
    /** The currently active ordering. */
    protected LinkedHashMap<String, Boolean> ordering;
    /** The currently active column filters. */
    protected LinkedHashMap<String, Object> filters;
    /** The currently active global search term. */
    protected String globalFilter;

    /** The current page of data. */
    private Page page;
    /** The most recently loaded list of results; not serialized, it is loaded again on the next render or postback. */
    private transient PartialResultList<E> list;
    /** Whether a page has been loaded at least once; serialized, so that a deserialized model does not read the page number from the query string again. */
    private boolean loaded;
    /** Ordering values of the first and last row of each loaded page, keyed by row index; valid for {@link #page} criteria and ordering only. */
    private final TreeMap<Integer, Map<String, Object>> orderingValuesByRowIndex = new TreeMap<>();

    /** Cached {@link SortMeta} list derived from {@link #ordering}; recomputed whenever {@link #ordering} changes. */
    private List<SortMeta> orderingCache = emptyList();
    /** Cached view of {@link #filters} as {@link FilterMeta} map; cleared whenever {@link #filters} changes. */
    private Map<String, FilterMeta> filtersCache;

    // op:dataTable properties ----------------------------------------------------------------------------------------

    /** The current list of filtered entities; PrimeFaces only fills it on non-lazy tables. */
    private transient List<E> filteredValue;
    /** The current selection of entities; not serialized, it is resolved again from {@link #selectionRowKeys}. */
    private transient List<E> selection;
    /** The row keys of the selection which is yet to be resolved after deserialization, else <code>null</code>. */
    private List<String> selectionRowKeys;
    /**
     * Whether this model is deserialized; it is only set on the copy which is serialized instead of this model. It stays set, because the client keeps
     * submitting the row keys of selected rows on other pages until the table is rendered again.
     */
    private boolean deserialized;

    // Constructors ---------------------------------------------------------------------------------------------------

    /**
     * Internal constructor for the lazy paged data model.
     *
     * @param loader The strategy for loading partial result lists.
     * @param defaultOrdering The default sort order.
     * @param predefinedCriteria Fixed criteria to apply.
     * @param dynamicCriteria Dynamic criteria supplier.
     * @param rows How the rows are identified.
     */
    LazyPagedDataModel(
        PartialResultListLoader<E> loader, LinkedHashMap<String, Boolean> defaultOrdering, Map<String, Object> predefinedCriteria,
        DynamicCriteria<?> dynamicCriteria, Rows<E> rows
    )
    {
        this.loader = loader;
        this.defaultOrdering = defaultOrdering;
        this.predefinedCriteria = predefinedCriteria;
        this.dynamicCriteria = dynamicCriteria;
        this.rows = rows;
        filters = new LinkedHashMap<>();
        page = Page.ALL;
        setRowCount(-1);
    }

    // Actions --------------------------------------------------------------------------------------------------------

    /**
     * Called by PrimeFaces {@link DataTable} on every page render to supply data for the component.
     * <p>
     * Dispatches to {@link #loadPage} for PrimeFaces {@link DataTable} (which applies full sort, filter and query-string processing) or to a load with the
     * default ordering and no criteria for {@link DataScroller}.
     *
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
            ordering = defaultOrdering;
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
     * {@link PartialResultList#getEstimatedTotalNumberOfResults()} and is <code>0</code> when no page has yet been loaded.
     *
     * @param filterBy Active filter metadata; not re-applied here, the count is already embedded in the {@link PartialResultList} retrieved during
     * {@link #load}.
     * @return Estimated total row count; <code>0</code> if {@link #load} has not yet been called.
     */
    @Override
    public int count(Map<String, FilterMeta> filterBy) {
        return list == null ? 0 : list.getEstimatedTotalNumberOfResults();
    }

    /**
     * Eagerly loads and caches the first page of data without waiting for PrimeFaces {@link DataTable}'s normal {@link #load} invocation.
     * <p>
     * Called by {@link ExtendedDataTable#preDecode} when the model's wrapped data is still <code>null</code> at the start of a postback, a situation that
     * arises for request-scoped beans and stateless views that do not retain model state between requests, and for the model of a bean of a passivating scope,
     * such as <code>@ViewScoped</code> or <code>@SessionScoped</code>, after deserialization, which does not retain its loaded page. Pre-loading guarantees
     * that row keys, selection state and pagination information are available when PrimeFaces {@link DataTable} processes the submitted form data during
     * decode. A model which has been loaded before loads its last page again, with the same offset, ordering and criteria, so that the rows are the ones which
     * were rendered.
     *
     * @param context The current {@link FacesContext}.
     * @param table The {@link DataTable} that triggered the postback.
     */
    public void preloadPage(FacesContext context, DataTable table) {
        if (loaded) {
            list = load(page, false);
        }
        else {
            loadPage(context, table, toSortBy(getInitialOrdering(context, table)), emptyMap());
        }

        setWrappedData(list);
        setPageSize(table.getRows());
    }

    private void loadPage(FacesContext context, DataTable table, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
        List<UIColumn> processableColumns = table.getColumns().stream().filter(this::isProcessableColumn).collect(toList());

        updateQueryString = parseBoolean(String.valueOf(table.getAttributes().get("updateQueryString")));
        queryParameterPrefix = parseQueryParameterPrefix(table);
        ordering = processPageAndOrdering(context, table, sortBy);
        orderingCache = toSortedColumns(ordering, table);
        filters = processFilters(context, table, processableColumns, filterBy);
        filtersCache = null;
        globalFilter = processGlobalFilter(context, table, filterBy);
        selection = processSelectionIfNecessary(context, getSelection());

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

    void loadPage(UIData data, int offset, int limit, Map<String, Object> requiredCriteria, Map<String, Object> optionalCriteria) {
        var pageOfSameCriteria = requiredCriteria.equals(page.getRequiredCriteria()) && optionalCriteria.equals(page.getOptionalCriteria());
        var rowCountNeedsUpdate = getRowCount() <= 0 || !pageOfSameCriteria;

        if (!pageOfSameCriteria || !isSameOrdering(ordering, page.getOrdering())) {
            orderingValuesByRowIndex.clear();
        }

        page = buildPage(offset, limit, requiredCriteria, optionalCriteria);
        list = load(page, rowCountNeedsUpdate);
        loaded = true;
        var count = list.getEstimatedTotalNumberOfResults();

        if (count != -1 && count != getRowCount()) {
            if (list.isEmpty() && count > 0 && offset > count) { // Can happen when user has paginated too far and then changed criteria which returned fewer
                                                                 // results.
                var offsetOfLastPage = offset - ((offset - count) / data.getRows() + 1) * data.getRows();
                data.setFirst(offsetOfLastPage);
                page = buildPage(offsetOfLastPage, limit, requiredCriteria, optionalCriteria);
                list = load(page, false);
            }

            setRowCount(count);
        }

        cacheOrderingValues();
    }

    private static boolean isSameOrdering(Map<String, Boolean> ordering, Map<String, Boolean> otherOrdering) {
        return List.copyOf(ordering.entrySet()).equals(List.copyOf(otherOrdering.entrySet()));
    }

    private LinkedHashMap<String, Boolean> withDefaultOrdering(Map<String, Boolean> ordering) {
        var orderingWithDefault = new LinkedHashMap<>(ordering);
        defaultOrdering.forEach(orderingWithDefault::putIfAbsent);
        return orderingWithDefault;
    }

    /**
     * Returns the shortest leading part of the given ordering which yields the given ordering again after {@link #withDefaultOrdering(Map)}. This is the part
     * which is sorted by the user, and is empty when the given ordering is the default ordering.
     */
    LinkedHashMap<String, Boolean> withoutDefaultOrdering(Map<String, Boolean> ordering) {
        var leadingOrdering = new LinkedHashMap<String, Boolean>();

        for (var entry : ordering.entrySet()) {
            if (isSameOrdering(withDefaultOrdering(leadingOrdering), ordering)) {
                break;
            }

            leadingOrdering.put(entry.getKey(), entry.getValue());
        }

        return leadingOrdering;
    }

    private Map<String, Boolean> orElseFirstDefaultOrdering(Map<String, Boolean> ordering) {
        if (!ordering.isEmpty() || defaultOrdering.isEmpty()) {
            return ordering;
        }

        var firstDefaultOrdering = defaultOrdering.entrySet().iterator().next();
        return singletonMap(firstDefaultOrdering.getKey(), firstDefaultOrdering.getValue());
    }

    /**
     * Returns the {@link SortMeta} list of the columns of the given table which are sorted by the given ordering: the part sorted by the user, or else the
     * first entry of the default ordering.
     */
    List<SortMeta> toSortedColumns(Map<String, Boolean> ordering, DataTable table) {
        return toSortMetas(orElseFirstDefaultOrdering(withoutDefaultOrdering(ordering)), getColumnFields(table));
    }

    private static Map<String, SortMeta> toSortBy(List<SortMeta> sortMetas) {
        var sortBy = new LinkedHashMap<String, SortMeta>();

        for (var sortMeta : sortMetas) {
            sortBy.put(sortMeta.getField(), sortMeta);
        }

        return sortBy;
    }

    private static Set<String> getColumnFields(DataTable table) {
        return table.getColumns().stream().map(UIColumn::getField).filter(Objects::nonNull).collect(toSet());
    }

    /**
     * Converts the given ordering to {@link SortMeta} instances with ascending priority. Fields which are not a column of the table are skipped, because
     * PrimeFaces {@link DataTable} rejects a {@link SortMeta} without a matching column.
     */
    private static List<SortMeta> toSortMetas(Map<String, Boolean> ordering, Set<String> columnFields) {
        var sortMetas = new ArrayList<SortMeta>(ordering.size());

        for (var entry : ordering.entrySet()) {
            if (columnFields.contains(entry.getKey())) {
                sortMetas.add(SortMeta.builder().field(entry.getKey()).order(entry.getValue() ? ASCENDING : DESCENDING).priority(sortMetas.size()).build());
            }
        }

        return sortMetas;
    }

    private Page buildPage(int offset, int limit, Map<String, Object> requiredCriteria, Map<String, Object> optionalCriteria) {
        var builder = orderedPage().allMatch(requiredCriteria).anyMatch(optionalCriteria);
        var boundaryBefore = orderingValuesByRowIndex.floorEntry(offset - 1);
        var boundaryAfter = limit <= MAX_VALUE - offset ? orderingValuesByRowIndex.ceilingEntry(offset + limit) : null;
        var residualOffsetBefore = boundaryBefore != null ? offset - 1 - boundaryBefore.getKey() : MAX_VALUE;
        var residualOffsetAfter = boundaryAfter != null ? boundaryAfter.getKey() - offset - limit : MAX_VALUE;

        if (boundaryBefore != null && residualOffsetBefore <= residualOffsetAfter) {
            builder.range(offset, limit, boundaryBefore.getValue(), residualOffsetBefore, false);
        }
        else if (residualOffsetAfter < offset) {
            builder.range(offset, limit, boundaryAfter.getValue(), residualOffsetAfter, true);
        }
        else {
            builder.range(offset, limit);
        }

        return builder.build();
    }

    private void cacheOrderingValues() {
        if (list.isEmpty()) {
            return;
        }

        var offset = page.getOffset();
        cacheOrderingValuesOfRow(offset, list.get(0));
        cacheOrderingValuesOfRow(offset + list.size() - 1, list.get(list.size() - 1));

        while (orderingValuesByRowIndex.size() > MAX_CACHED_ORDERING_VALUES) {
            var first = orderingValuesByRowIndex.firstKey();
            var last = orderingValuesByRowIndex.lastKey();
            orderingValuesByRowIndex.remove(offset - first > last - offset ? first : last);
        }
    }

    private void cacheOrderingValuesOfRow(int rowIndex, E row) {
        try {
            orderingValuesByRowIndex.put(rowIndex, orderedPage().range(row, 1, false).build().getLastOrderingValues());
        }
        catch (IllegalStateException ignore) {
            //
        }
    }

    private Page.Builder orderedPage() {
        var builder = Page.with();
        ordering.forEach(builder::orderBy);
        return builder;
    }

    /**
     * Template method that forwards the page request to the underlying {@link PagedDataModel.PartialResultListLoader}.
     * <p>
     * {@link NonLazyPagedDataModel} overrides this method to perform in-memory filtering and sorting instead of delegating to the loader.
     *
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
     * When PrimeFaces {@link DataTable} renders a paginator or filter widget, the current component is a child of the table rather than the table itself; this
     * method walks up to the enclosing {@link UIData}.
     *
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
     * A column is considered processable when it has a <code>field</code> attribute and is either individually filterable or the parent table has
     * <code>searchable</code> enabled.
     *
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
     * Builds the active ordering map from PrimeFaces {@link DataTable} sort metadata combined with the configured default ordering.
     * <p>
     * On the very first load this method also reads the <code>page</code> query parameter and advances the table's first-row offset so that a bookmarked page
     * is restored correctly.
     *
     * @param context The current {@link FacesContext}.
     * @param table The surrounding {@link DataTable}.
     * @param sortBy Active sort metadata supplied by PrimeFaces {@link DataTable}.
     * @return An ordered map of field name to ascending flag representing the full effective ordering.
     */
    protected LinkedHashMap<String, Boolean> processPageAndOrdering(FacesContext context, DataTable table, Map<String, SortMeta> sortBy) {
        if (!loaded) {
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

        var tableOrdering = new LinkedHashMap<String, Boolean>(sortBy.size());

        for (var sortMeta : sortBy.values()) {
            if (!isEmpty(sortMeta.getField())) {
                tableOrdering.putIfAbsent(sortMeta.getField(), sortMeta.getOrder() == ASCENDING);
            }
        }

        return withDefaultOrdering(tableOrdering);
    }

    /**
     * Returns the initial {@link SortMeta} list for pre-loading the model before PrimeFaces {@link DataTable} decode runs.
     * <p>
     * Reads the <code>order</code> query parameters to restore a bookmarked sort, in their order, skipping fields which are not a column of the table. Only the
     * first valid one is used when the table is not {@link DataTable#isMultiSort()}. Falls back to the first entry of the configured default ordering when no
     * valid query parameter is found.
     *
     * @param context The current {@link FacesContext}.
     * @param table The surrounding {@link DataTable}.
     * @return The {@link SortMeta} list describing the fields and directions to sort by initially, in order of priority.
     */
    protected List<SortMeta> getInitialOrdering(FacesContext context, DataTable table) {
        var columnFields = getColumnFields(table);
        var initialOrdering = new LinkedHashMap<String, Boolean>();

        for (var order : getTrimmedQueryParameters(context, parseQueryParameterPrefix(table) + QUERY_PARAMETER_ORDER)) {
            var descending = order.startsWith("-");
            var field = descending ? order.substring(1) : order;

            if (columnFields.contains(field)) {
                initialOrdering.putIfAbsent(field, !descending);

                if (!table.isMultiSort()) {
                    break;
                }
            }
        }

        return toSortMetas(orElseFirstDefaultOrdering(initialOrdering), columnFields);
    }

    /**
     * Collects the active per-column filter values into an ordered map of field to filter value.
     * <p>
     * For each processable column the filter value is taken first from PrimeFaces {@link DataTable}'s <code>filterBy</code> metadata; when that is empty the
     * corresponding query string parameter is consulted so that bookmarked filters are restored on the initial (non-postback) request.
     *
     * @param context The current {@link FacesContext}.
     * @param table The surrounding {@link DataTable}.
     * @param processableColumns The columns selected by {@link #isProcessableColumn}.
     * @param filterBy Active filter metadata supplied by PrimeFaces {@link DataTable}.
     * @return An ordered map of field name to normalized filter value for all active column filters.
     */
    protected LinkedHashMap<String, Object> processFilters(
        FacesContext context, DataTable table, List<UIColumn> processableColumns, Map<String, FilterMeta> filterBy
    )
    {
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
     * The current selection is left untouched when there is one, and on postbacks. On initial requests the <code>selection</code> query parameter is read so
     * that a bookmarked selection is visible immediately, its row keys are resolved like in {@link #getSelection()}.
     *
     * @param context The current {@link FacesContext}.
     * @param currentSelection The in-memory selection list carried over from previous requests.
     * @return The restored or unchanged selection list; <code>null</code> on a postback without any selection.
     */
    protected List<E> processSelectionIfNecessary(FacesContext context, List<E> currentSelection) {
        if (currentSelection != null || context.isPostback()) {
            return currentSelection;
        }

        return coalesce(resolveSelection(getTrimmedQueryParameters(context, queryParameterPrefix + QUERY_PARAMETER_SELECTION)), new ArrayList<>());
    }

    /**
     * Returns the entities of the given row keys: each via {@link PagedDataModel.Builder#rowData(PagedDataModel.RowDataLoader)} when specified, else all at
     * once via the criteria <code>id</code> when the row key is the ID, else from the currently loaded data, see {@link #getLoadedData()}. A row key which is
     * not found is skipped.
     *
     * @param rowKeys The row keys.
     * @return The entities of the given row keys, or <code>null</code> when they can only be found in the loaded data and nothing is loaded yet.
     */
    private List<E> resolveSelection(List<String> rowKeys) {
        if (rowKeys.isEmpty()) {
            return new ArrayList<>();
        }
        else if (rows.data() != null) {
            return rowKeys.stream().map(rows.data()).filter(Objects::nonNull).collect(toList());
        }
        else if (rows.keyIsId() && !isLoadedDataComplete()) {
            return new ArrayList<>(load(Page.with().range(0, rowKeys.size()).allMatch(singletonMap(ID, rowKeys)).build(), false));
        }
        else if (getLoadedData() != null) {
            return rowKeys.stream().map(this::findLoadedRowData).flatMap(Optional::stream).collect(toList());
        }
        else {
            return null;
        }
    }

    /**
     * Extracts and trims the global search term from PrimeFaces {@link DataTable} filter metadata or from the query string.
     * <p>
     * The value in <code>filterBy</code> under the key <code>globalFilter</code> takes precedence; when absent or empty the corresponding query string
     * parameter is checked so that a bookmarked search term is applied on the initial (non-postback) request.
     *
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
     * Layers criteria in the following priority: predefined criteria configured on the builder, dynamic criteria supplied by the backing bean, and then the
     * active per-column filter values, each applying <code>startsWith</code>, <code>endsWith</code> or <code>contains</code> wrapping when the column's
     * <code>filterMatchMode</code> requests it.
     *
     * @param processableColumns The columns selected by {@link #isProcessableColumn}.
     * @return A map of field path to criteria value representing all required filter conditions.
     */
    protected Map<String, Object> processRequiredCriteria(List<UIColumn> processableColumns) {
        Map<String, Object> requiredCriteria = new HashMap<>();

        if (predefinedCriteria != null) {
            requiredCriteria.putAll(predefinedCriteria);
        }

        if (dynamicCriteria != null) {
            ofNullable(dynamicCriteria.get()).orElse(emptyMap())
                .forEach((getter, value) -> processDynamicCriteria(getter.getPropertyName(), value, requiredCriteria));
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
     * When {@link #globalFilter} is set, every processable column field receives a <code>contains</code> criterion so that any row matching the search term in
     * any of those fields is included in the results.
     *
     * @param processableColumns The columns selected by {@link #isProcessableColumn}.
     * @return A map of field path to {@link org.omnifaces.persistence.criteria.Like#contains} criteria for the global filter, or an empty map when no global
     * filter is active.
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
     * Pushes the current paging, sorting, filtering and selection state into the browser URL as query parameters via an Ajax <code>oncomplete</code> callback,
     * when <code>updateQueryString</code> is enabled.
     * <p>
     * Only fires during Ajax requests. Parameters for page, ordering, column filters and selection are only added when they differ from their defaults; the
     * resulting query string is passed to <code>OptimusFaces.Util.updateQueryString</code> on the client. The selection is sorted by its string row keys, and
     * only added when it can be restored, see {@link #processSelectionIfNecessary(FacesContext, List)}.
     *
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

        withoutDefaultOrdering(page.getOrdering())
            .forEach((field, ascending) -> params.add(new SimpleParam<>(queryParameterPrefix + QUERY_PARAMETER_ORDER, (ascending ? "" : "-") + field)));

        filters.entrySet().stream()
            .forEach(
                entry -> stream(normalizeCriteriaValue(stream(entry.getValue()).map(Criteria::unwrap)))
                    .forEach(value -> params.add(new SimpleParam<>(queryParameterPrefix + entry.getKey(), value)))
            );

        if (getSelection() != null && isSelectionRestorable()) {
            getSelection().stream().map(this::getRowKey).sorted()
                .forEach(rowKey -> params.add(new SimpleParam<>(queryParameterPrefix + QUERY_PARAMETER_SELECTION, rowKey)));
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
     * Returns the row key of the entity as a string, for PrimeFaces {@link DataTable} selection tracking: by default its ID, else as specified via
     * {@link PagedDataModel.Builder#rowKey(PagedDataModel.RowKeyGetter)}. Falls back to the entity's hash code when the row key is <code>null</code>, e.g. when
     * the entity is not persisted yet.
     *
     * @param entity The entity whose row key is required.
     * @return String representation of the entity's row key, or its hash code.
     * @throws IllegalStateException When the row key is the ID and the model cannot determine the ID of the entity.
     */
    @Override
    public String getRowKey(E entity) {
        var rowKey = rows.key().apply(entity);
        return String.valueOf(rowKey != null ? rowKey : entity.hashCode());
    }

    /**
     * Returns the single entity identified by the given row key, so that PrimeFaces {@link DataTable} can restore selection or row expansion state after a
     * postback: as specified via {@link PagedDataModel.Builder#rowData(PagedDataModel.RowDataLoader)}, else via a one-row query on the ID when the row key is
     * the ID, else from the currently loaded data.
     *
     * @param rowKey The string row key previously returned by {@link #getRowKey}.
     * @return The corresponding entity, or <code>null</code> when the row key is not the ID and the entity is not in the currently loaded data of a
     * deserialized model, whose selection may hold rows which were on another page.
     * @throws IllegalStateException When the row key is not the ID and the entity is not in the currently loaded data of a model which is not deserialized.
     */
    @Override
    public E getRowData(String rowKey) {
        if (rows.data() != null) {
            return rows.data().apply(rowKey);
        }
        else if (rows.keyIsId()) {
            return load(Page.with().range(0, 1).allMatch(singletonMap(ID, rowKey)).build(), false).get(0);
        }
        else {
            var rowData = findLoadedRowData(rowKey);

            if (rowData.isEmpty() && !deserialized) {
                throw new IllegalStateException(format(ERROR_ROW_DATA_NOT_LOADED, rowKey));
            }

            return rowData.orElse(null);
        }
    }

    /**
     * Returns the entity identified by the given row key in the currently loaded data.
     *
     * @param rowKey The string row key previously returned by {@link #getRowKey}.
     * @return The corresponding entity, if any.
     * @since 2.0
     */
    protected Optional<E> findLoadedRowData(String rowKey) {
        var loadedData = getLoadedData();
        return loadedData == null
            ? Optional.empty()
            : loadedData.stream().filter(entity -> entity != null && getRowKey(entity).equals(rowKey)).findFirst();
    }

    /**
     * Returns the currently loaded data, wherein {@link #findLoadedRowData(String)} looks up the entity of a row key.
     *
     * @return The currently loaded data, or <code>null</code> when nothing is loaded yet.
     * @since 2.0
     */
    protected List<E> getLoadedData() {
        return list;
    }

    /**
     * Returns whether {@link #getLoadedData()} holds all data, so that an entity of a row key can always be looked up in it.
     *
     * @return Whether the loaded data holds all data.
     */
    boolean isLoadedDataComplete() {
        return false;
    }

    private boolean isSelectionRestorable() {
        return rows.keyIsId() || rows.data() != null;
    }

    /**
     * Returns the active sort as PrimeFaces {@link SortMeta} list, or the initial ordering when no page has been loaded yet.
     *
     * @return The current sort descriptors, in order of priority; empty when no ordering is defined.
     */
    @Override
    public List<SortMeta> getOrdering() {
        if (ordering == null) {
            var context = FacesContext.getCurrentInstance();
            var table = (DataTable) getCurrentComponent(context);
            return getInitialOrdering(context, table);
        }

        return orderingCache;
    }

    /**
     * Returns the active column filters as a map of field name to {@link FilterMeta}.
     *
     * @return The current active filters; empty map when no filters are active.
     */
    @Override
    public Map<String, FilterMeta> getFilters() {
        if (filters == null) {
            return emptyMap();
        }

        if (filtersCache == null) {
            filtersCache = filters.entrySet().stream()
                .map(entry -> FilterMeta.builder().field(entry.getKey()).filterValue(entry.getValue()).build())
                .collect(Collectors.toMap(FilterMeta::getField, identity()));
        }

        return filtersCache;
    }

    /**
     * Returns the {@link FilterMeta} for the given field, creating and registering an empty one when absent.
     *
     * @param field The column field name.
     * @return The {@link FilterMeta} for the field; never <code>null</code>.
     */
    @Override
    public FilterMeta getFilter(String field) {
        var filterMeta = getFilters().get(field);

        if (filterMeta == null) {
            filterMeta = FilterMeta.builder().field(field).build();
            this.filters.put(field, filterMeta);
            this.filtersCache = null;
        }

        return filterMeta;
    }

    /**
     * Returns the filtered-value list used by PrimeFaces {@link DataTable} for client-side filtering support.
     *
     * @return The filtered-value list; may be <code>null</code>.
     */
    @Override
    public List<E> getFilteredValue() {
        return filteredValue;
    }

    /**
     * Sets the filtered-value list used by PrimeFaces {@link DataTable} for client-side filtering support.
     *
     * @param filteredValue The filtered list to store.
     */
    @Override
    public void setFilteredValue(List<E> filteredValue) {
        this.filteredValue = filteredValue;
    }

    /**
     * Returns the currently selected rows.
     * <p>
     * After deserialization, the selection is resolved once from its row keys: each via {@link PagedDataModel.Builder#rowData(PagedDataModel.RowDataLoader)}
     * when specified, else via the criteria <code>id</code> when the row key is the ID, else from the loaded page, which restores only the selected rows on
     * that page.
     *
     * @return The selection list; may be <code>null</code> when selection has not been initialized.
     */
    @Override
    public List<E> getSelection() {
        if (selection == null && selectionRowKeys != null) {
            selection = resolveSelection(selectionRowKeys);

            if (selection != null) {
                if (selection.size() < selectionRowKeys.size() && rows.data() == null && !rows.keyIsId()) {
                    var total = selectionRowKeys.size();
                    var restored = selection.size();
                    logger.log(WARNING, () -> format(LOG_WARNING_SELECTION_PARTIALLY_RESTORED, total - restored, total));
                }

                selectionRowKeys = null;
            }
        }

        return selection;
    }

    /**
     * Updates the currently selected rows and, when <code>updateQueryString</code> is enabled, reflects the new selection in the browser URL immediately.
     *
     * @param selection The new selection list.
     */
    @Override
    public void setSelection(List<E> selection) {
        if (!Objects.equals(selection, this.selection)) {
            this.selection = selection;
            selectionRowKeys = null;
            updateQueryStringIfNecessary(getContext());
        }
    }

    // Serialization --------------------------------------------------------------------------------------------------

    /**
     * Returns the copy of this model which is serialized instead: without the wrapped data, so that the entities of the current page are not serialized, and
     * with the row keys of the selection instead of its entities. This model itself keeps both. After deserialization, the page is loaded again on the next
     * postback by {@link ExtendedDataTable}, or on the next render, because the wrapped data is then absent.
     *
     * @return The copy of this model which is serialized instead.
     */
    @Serial
    @SuppressWarnings("unchecked")
    Object writeReplace() {
        try {
            var copy = (LazyPagedDataModel<E>) clone();
            copy.setWrappedData(null);
            copy.deserialized = true;

            if (selection != null) {
                copy.selectionRowKeys = selection.stream().map(this::getRowKey).collect(toList());
            }

            return copy;
        }
        catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
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
        return params != null ? stream(params).filter(Lang::isNotBlank).map(String::trim).collect(toList()) : emptyList();
    }

}
