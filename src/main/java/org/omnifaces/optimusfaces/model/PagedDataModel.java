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
package org.omnifaces.optimusfaces.model;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static javax.faces.component.UINamingContainer.getSeparatorChar;
import static org.omnifaces.util.Ajax.oncomplete;
import static org.omnifaces.util.Components.getCurrentComponent;
import static org.omnifaces.util.Faces.getContext;
import static org.omnifaces.util.Faces.isAjaxRequest;
import static org.omnifaces.util.FacesLocal.getRequestParameter;
import static org.omnifaces.utils.Lang.isEmpty;
import static org.primefaces.model.SortOrder.DESCENDING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.faces.context.FacesContext;

import org.omnifaces.component.ParamHolder;
import org.omnifaces.component.SimpleParam;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.dto.SortFilterPage;
import org.omnifaces.persistence.service.GenericEntityService;
import org.omnifaces.util.Servlets;
import org.omnifaces.utils.collection.PartialResultList;
import org.primefaces.component.api.UIColumn;
import org.primefaces.component.column.Column;
import org.primefaces.component.columntoggler.ColumnToggler;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.event.ToggleEvent;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;
import org.primefaces.model.Visibility;

/**
 * <p>
 * Paged data model for <code>&lt;op:dataTable&gt;</code> which utilizes {@link GenericEntityService} from
 * OmniPersistence project.
 *
 * <h3>Usage:</h3>
 * <p>
 * First create your generic entity service extending {@link GenericEntityService} from OmniPersistence project.
 * <pre>
 * {@code @Stateless}
 * {@code @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)}
 * public class YourGenericEntityService extends org.omnifaces.persistence.service.GenericEntityService {
 *
 *     {@code @PersistenceContext}
 *     private EntityManager entityManager;
 *
 *     {@code @PersistenceUnit}
 *     private EntityManagerFactory entityManagerFactory;
 *
 *     {@code @PostConstruct}
 *     public void init() {
 *         setEntityManager(entityManager);
 *         setEntityManagerFactory(entityManagerFactory);
 *     }
 *
 * }
 * </pre>
 * <p>
 * And make sure your entity extends {@link BaseEntity} from OmniPersistence project.
 * <pre>
 *
 * {@code @Entity}
 * public class YourEntity extends org.omnifaces.persistence.model.BaseEntity&lt;Long&gt; {
 *
 *     {@code @Id} {@code @GeneratedValue(strategy = IDENTITY)}
 *     private Long id;
 *     private Instant created;
 *     private String name;
 *     private String description;
 *
 *     // ...
 * }
 * </pre>
 * <p>
 * Then utilize any of the {@link GenericEntityService#getAllPagedAndSorted(Class, SortFilterPage)} methods in your
 * backing bean. Imagine that the returned results should be sorted descending on {@code created} column and only the
 * {@code name} and {@code description} fields are (text)searchable, then you could use it as below.
 * <pre>
 * {@code @Named}
 * {@code @ViewScoped}
 * public class YourBackingBean {
 *
 *     private org.omnifaces.optimusfaces.model.PagedDataModel&lt;YourEntity&gt; model;
 *
 *     {@code @Inject}
 *     private YourGenericEntityService yourGenericEntityService;
 *
 *     {@code @PostConstruct}
 *     public void init() {
 *         model = new PagedDataModel&lt;YourEntity&gt;("created", SortOrder.DESCENDING, "name", "description") {
 *             private static final long serialVersionUID = 1L;
 *
 *             {@code @Override}
 *             public List&lt;YourEntity&gt; load(SortFilterPage page, boolean countNeedsUpdate) {
 *                 return yourGenericEntityService.getAllPagedAndSorted(page, countNeedsUpdate);
 *             }
 *         };
 *     }
 *
 *     public PagedDataModel&lt;YourEntity&gt; getModel() {
 *         return model;
 *     }
 *
 * }
 * </pre>
 * <p>
 * Finally use <code>&lt;op:dataTable&gt;</code> to have a semi-dynamic lazy-loaded, searchable, sortable and filterable
 * <code>&lt;p:dataTable&gt;</code> without much hassle.
 * <pre>
 * &lt;... xmlns:op="http://omnifaces.org/optimusfaces"&gt;
 *
 * &lt;h:form&gt;
 *     &lt;op:dataTable id="yourEntitiesTable" value="#{bean.model}" searchable="true"&gt;
 *         &lt;op:column field="id" /&gt;
 *         &lt;op:column field="created" /&gt;
 *         &lt;op:column field="name" /&gt;
 *         &lt;op:column field="description" /&gt;
 *     &lt;/op:dataTable&gt;
 * &lt;/h:form&gt;
 * </pre>
 *
 * @author Bauke Scholtz
 */
public abstract class PagedDataModel<T> extends LazyDataModel<T> {

	private static final long serialVersionUID = 1L;
	private static final String GLOBAL_FILTER = "globalFilter";

	private String sortField;
	private String sortOrder;
	private List<String> filterableFields;
	private String globalFilter;
	private Map<String, Object> filters;
	private Map<String, Object> remappedFilters;
	private boolean filterWithAND;
	private List<T> filteredValue;
	private List<T> selection;
	private Map<String, Boolean> visibleColumns;

	public PagedDataModel(String defaultSortField, SortOrder defaultSortOrder, String... filterableFields) { // TODO: filterableFields must exclusively be defined via <of:column filterable="true"> instead.
		sortField = defaultSortField;
		sortOrder = defaultSortOrder.name();
		this.filterableFields = filterableFields != null ? unmodifiableList(asList(filterableFields)) : emptyList(); // TODO: remove this.
		filters = new HashMap<>();
		selection = emptyList();
		visibleColumns = new HashMap<>();
		setRowCount(-1);
	}

	@Override
	public List<T> load(int first, int pageSize, String sortField, SortOrder sortOrder, Map<String, Object> tableFilters) {
		if (sortField != null) {
			setSortField(sortField);
			setSortOrder(sortOrder.name());
		}

		Map<String, Object> allFilters = mergeWithQueryParameters(tableFilters);
		Map<String, Object> remappedFilters = remapGlobalFilter(allFilters);
		boolean countNeedsUpdate = getRowCount() <= 0 || !remappedFilters.equals(this.remappedFilters);
		this.remappedFilters = remappedFilters;
		filters = allFilters;

		List<T> list = load(new SortFilterPage(first, pageSize, getSortField(), getSortOrder(), filterableFields, remappedFilters, filterWithAND), countNeedsUpdate);

		if (countNeedsUpdate && list instanceof PartialResultList) {
			setRowCount(((PartialResultList<T>) list).getEstimatedTotalNumberOfResults());
		}

		if (isAjaxRequest()) {
			updateQueryStringIfNecessary();
		}

		return list;
	}

	public abstract List<T> load(SortFilterPage page, boolean countNeedsUpdate);

	public void toggleColumn(ToggleEvent event) {
		DataTable table = getDataTable(((ColumnToggler) event.getComponent()).getDatasource());
		String field = table.getColumns().get((Integer) event.getData()).getField();
		visibleColumns.put(field, event.getVisibility() == Visibility.VISIBLE);
	}

	public boolean isVisible(String field, boolean defaultVisible) {
		visibleColumns.putIfAbsent(field, defaultVisible);
		return visibleColumns.get(field);
	}

	public void prepareExportVisible(String tableId) {
		getDataTable(tableId).getColumns().forEach(c -> ((Column) c).setExportable(visibleColumns.get(c.getField())));
	}

	public void prepareExportAll(String tableId) {
		getDataTable(tableId).getColumns().forEach(c -> ((Column) c).setExportable(true));
	}

	private static DataTable getDataTable(String id) {
		return (DataTable) getCurrentComponent().findComponent(id);
	}

	@Override
	public Object getRowKey(T entity) {
		return ((BaseEntity<?>) entity).getId();
	}

	@Override
	public T getRowData(String rowKey) {
		return load(new SortFilterPage(0, 1, getSortField(), getSortOrder(), emptyList(), singletonMap("id", rowKey), true), false).get(0);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<T> getWrappedData() {
		return (List<T>) super.getWrappedData();
	}

	private Map<String, Object> mergeWithQueryParameters(Map<String, Object> filters) {
		if (filterableFields.isEmpty()) {
			filterableFields = ((DataTable) getCurrentComponent()).getColumns().stream()
				.filter(UIColumn::isFilterable)
				.map(UIColumn::getField)
				.filter(Objects::nonNull)
				.collect(toList());
		}

		FacesContext context = getContext();
		Map<String, Object> mergedFilters = new HashMap<>(filters);

		for (String filterableField : filterableFields) {
			if (!mergedFilters.containsKey(filterableField)) {
				String value = getQueryParameter(context, filterableField);

				if (value != null) {
					mergedFilters.put(filterableField, value);
				}
			}
		}

		return mergedFilters;
	}

	private Map<String, Object> remapGlobalFilter(Map<String, Object> filters) {
		globalFilter = (String) filters.get(GLOBAL_FILTER);

		if (globalFilter != null) {
			globalFilter = globalFilter.trim();

			if (globalFilter.isEmpty()) {
				filters.remove(GLOBAL_FILTER);
			}
			else {
				filters.put(GLOBAL_FILTER, globalFilter);
			}
		}

		if (!filters.containsKey(GLOBAL_FILTER)) {
			FacesContext context = getContext();
			String q = getQueryParameter(context, "q");

			if (q == null) {
				q = getQueryParameter(context, getCurrentComponent().getClientId(context) + getSeparatorChar(context) + GLOBAL_FILTER);
			}

			if (q != null) {
				filters.put(GLOBAL_FILTER, q);
			}
		}

		filterWithAND = !filters.containsKey(GLOBAL_FILTER);
		globalFilter = (String) filters.remove(GLOBAL_FILTER);

		Map<String, Object> remappedFilters = new HashMap<>(filters);
		remappedFilters.values().remove(null);

		for (String filterableField : filterableFields) {
			Object filterableFieldValue = filters.get(filterableField);

			if (!isEmpty(filterableFieldValue)) {
				remappedFilters.put(filterableField, filterableFieldValue);
			}
			else if (globalFilter != null) {
				remappedFilters.put(filterableField, globalFilter);
			}
		}

		return remappedFilters;
	}

	private String getQueryParameter(FacesContext context, String name) {
		String param = getRequestParameter(context, name);

		if (param != null) {
			param = param.trim();

			if (!param.isEmpty()) {
				return param;
			}
		}

		return null;
	}

	private void updateQueryStringIfNecessary() {
		if (!isAjaxRequest()) {
			return;
		}

		List<ParamHolder> params = new ArrayList<>();

		if (!isEmpty(globalFilter)) {
			params.add(new SimpleParam("q", globalFilter));
		}

		filters.forEach((key, value) -> {
			params.add(new SimpleParam(key, value));
		});

		selection.stream().filter(item -> item instanceof BaseEntity<?>).forEach(item -> {
			params.add(new SimpleParam("selected", ((BaseEntity<?>) item).getId()));
		});

		oncomplete("OptimusFaces.Util.historyPushQueryString('" + Servlets.toQueryString(params) + "')");
	}

	public String getSortField() {
		return sortField;
	}

	public void setSortField(String sortField) {
		this.sortField = sortField;
	}

	public String getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(String sortOrder) {
		this.sortOrder = sortOrder;
	}

	public List<String> getFilterableFields() {
		return filterableFields;
	}

	public Map<String, Object> getFilters() {
		return filters;
	}

	public boolean isFilterWithAND() {
		return filterWithAND;
	}

	public List<T> getFilteredValue() {
		return filteredValue;
	}

	public void setFilteredValue(List<T> filteredValue) {
		this.filteredValue = filteredValue;
	}

	public List<T> getSelection() {
		return selection;
	}

	public void setSelection(List<T> selection) {
		if (!Objects.equals(selection, this.selection)) {
			this.selection = selection;
			updateQueryStringIfNecessary();
		}
	}

	public static <T> PagedDataModelBuilder<T> with() {
		return new PagedDataModelBuilder<>();
	}

	public static <T> PagedDataModelBuilder<T> pagedDataModelFor(Class<T> clazz) {
		PagedDataModelBuilder<T> builder = new PagedDataModelBuilder<>();
		return builder.forClass(clazz);
	}

	public static interface DataLoader<T> {
		List<T> load(SortFilterPage page, boolean countNeedsUpdate);
	}

	public static class PagedDataModelBuilder<T> {
		private String defaultSortField;
		private SortOrder defaultSortOrder = DESCENDING;
		private String[] filterableFields;
		private DataLoader<T> dataLoader;

		private Class<T> forClass;
		private GenericEntityService entityService;

		public PagedDataModelBuilder<T> defaultSortField(String defaultSortField) {
			this.defaultSortField = defaultSortField;
			return this;
		}

		public PagedDataModelBuilder<T> defaultSortOrder(SortOrder defaultSortOrder) {
			this.defaultSortOrder = defaultSortOrder;
			return this;
		}

		public PagedDataModelBuilder<T> filterableFields(String... filterableFields) {
			this.filterableFields = filterableFields;
			return this;
		}

		public PagedDataModelBuilder<T> dataLoader(DataLoader<T> dataLoader) {
			this.dataLoader = dataLoader;
			return this;
		}

		public PagedDataModelBuilder<T> forClass(Class<T> forClass) {
			this.forClass = forClass;
			return this;
		}

		public PagedDataModelBuilder<T> entityService(GenericEntityService entityService) {
			this.entityService = entityService;
			return this;
		}

		public PagedDataModel<T> buildWithService(GenericEntityService entityService) {
			return entityService(entityService).build();
		}

		public PagedDataModel<T> build() {

			if (dataLoader == null) {

				if (forClass != null && entityService != null) {
					dataLoader = (page, count) -> entityService.getAllPagedAndSortedByType(forClass, page, count);
				} else {
					throw new IllegalStateException("Must provide a non null dataLoader, or a forClass and entityService");
				}
			}

			return new PagedDataModel<T>(defaultSortField, defaultSortOrder, filterableFields) {
				private static final long serialVersionUID = 1L;
				@Override
				public List<T> load(SortFilterPage page, boolean countNeedsUpdate) {
					return dataLoader.load(page, countNeedsUpdate);
				}
			};

		}

	}

}