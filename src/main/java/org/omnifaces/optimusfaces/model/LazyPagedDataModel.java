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
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static javax.faces.component.UINamingContainer.getSeparatorChar;
import static org.omnifaces.util.Ajax.oncomplete;
import static org.omnifaces.util.Components.getCurrentComponent;
import static org.omnifaces.util.Faces.getContext;
import static org.omnifaces.util.Faces.isAjaxRequest;
import static org.omnifaces.util.FacesLocal.getRequestParameter;
import static org.omnifaces.utils.Lang.isEmpty;

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
import org.primefaces.component.datatable.DataTable;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;

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
public abstract class LazyPagedDataModel<T> extends LazyDataModel<T> implements PagedDataModel<T> {

	// Constants ------------------------------------------------------------------------------------------------------

	private static final long serialVersionUID = 1L;
	private static final String GLOBAL_FILTER = "globalFilter";

	// Internal properties --------------------------------------------------------------------------------------------

	private Map<String, Object> filters;
	private Map<String, Boolean> visibleColumns;
	private Map<String, Object> remappedFilters;
	private String globalFilter;
	private boolean filterWithAND;

	// p:dataTable properties -----------------------------------------------------------------------------------------

	private String sortField;
	private String sortOrder;
	private List<T> selection;
	private List<T> filteredValue;

	// Constructors ---------------------------------------------------------------------------------------------------

	/**
	 * Use PagedDataModel.forXxx() to build one.
	 */
	LazyPagedDataModel(String defaultSortField, SortOrder defaultSortOrder) {
		setRowCount(-1);

		filters = new HashMap<>();
		visibleColumns = new HashMap<>();

		sortField = defaultSortField;
		sortOrder = defaultSortOrder.name();
		selection = emptyList();
	}

	// Actions --------------------------------------------------------------------------------------------------------

	@Override
	public List<T> load(int first, int pageSize, String sortField, SortOrder sortOrder, Map<String, Object> tableFilters) {
		if (sortField != null) {
			setSortField(sortField);
			setSortOrder(sortOrder.name());
		}

		List<String> filterableFields = ((DataTable) getCurrentComponent()).getColumns().stream()
			.filter(UIColumn::isFilterable)
			.map(UIColumn::getField)
			.filter(Objects::nonNull)
			.collect(toList());
		Map<String, Object> allFilters = mergeWithQueryParameters(filterableFields, tableFilters);
		Map<String, Object> remappedFilters = remapGlobalFilter(filterableFields, allFilters);
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

	@Override
	public Object getRowKey(T entity) {
		return ((BaseEntity<?>) entity).getId();
	}

	@Override
	public T getRowData(String rowKey) {
		return load(new SortFilterPage(0, 1, getSortField(), getSortOrder(), emptyList(), singletonMap("id", rowKey), true), false).get(0);
	}

	// Helpers --------------------------------------------------------------------------------------------------------

	private static Map<String, Object> mergeWithQueryParameters(List<String> filterableFields, Map<String, Object> filters) {
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

	private Map<String, Object> remapGlobalFilter(List<String> filterableFields, Map<String, Object> filters) {
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

	private static String getQueryParameter(FacesContext context, String name) {
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
			updateQueryStringIfNecessary();
		}
	}

}