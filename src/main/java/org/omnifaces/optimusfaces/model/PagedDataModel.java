package org.omnifaces.optimusfaces.model;

import static org.omnifaces.util.Components.getCurrentComponent;
import static org.primefaces.model.SortOrder.DESCENDING;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.enterprise.inject.spi.CDI;

import org.omnifaces.persistence.model.dto.SortFilterPage;
import org.omnifaces.persistence.service.GenericEntityService;
import org.primefaces.component.column.Column;
import org.primefaces.component.columntoggler.ColumnToggler;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.event.ToggleEvent;
import org.primefaces.model.SortOrder;
import org.primefaces.model.Visibility;

public interface PagedDataModel<T> {

	/**
	 * Invoked on page load.
	 * TODO: Ugly. This must be improved.
	 */
	default boolean isVisible(String field, boolean defaultVisible) {
		getVisibleColumns().putIfAbsent(field, defaultVisible);
		return getVisibleColumns().get(field);
	}

	/**
	 * Invoked when "Columns" is adjusted.
	 */
	default void toggleColumn(ToggleEvent event) {
		getVisibleColumns().put(getDataTable(((ColumnToggler) event.getComponent()).getDatasource()).getColumns().get((Integer) event.getData()).getField(), event.getVisibility() == Visibility.VISIBLE);
	}

	/**
	 * Invoked when "Export Visible Columns" is chosen.
	 */
	default void prepareExportVisible(String tableId) {
		getDataTable(tableId).getColumns().forEach(c -> ((Column) c).setExportable(getVisibleColumns().get(c.getField())));
	}

	/**
	 * Invoked when "Export All Columns" is chosen.
	 */
	default void prepareExportAll(String tableId) {
		getDataTable(tableId).getColumns().forEach(c -> ((Column) c).setExportable(true));
	}

	// Internal properties --------------------------------------------------------------------------------------------

	Map<String, Boolean> getVisibleColumns();
	Map<String, Object> getFilters();

	// p:dataTable properties -----------------------------------------------------------------------------------------

	String getSortField();
	void setSortField(String sortField);

	String getSortOrder();
	void setSortOrder(String sortOrder);

	List<T> getFilteredValue();
	void setFilteredValue(List<T> filteredValue);

	List<T> getSelection();
	void setSelection(List<T> selection);

	// Helpers --------------------------------------------------------------------------------------------------------

	public static DataTable getDataTable(String tableId) {
		return (DataTable) getCurrentComponent().findComponent(tableId);
	}

	// Builder --------------------------------------------------------------------------------------------------------

	public static <T> PagedDataModelBuilder<T> forClass(Class<T> forClass) {
		return new PagedDataModelBuilder<>(forClass);
	}

	public static <T> PagedDataModelBuilder<T> forDataLoader(DataLoader<T> dataLoader) {
		return new PagedDataModelBuilder<>(dataLoader);
	}

	public static <T> PagedDataModelBuilder<T> forDataLoader(Function<SortFilterPage, List<T>> dataLoader) {
		return new PagedDataModelBuilder<>((page, count) -> dataLoader.apply(page));
	}

	public static <T> PagedDataModelBuilder<T> forAllData(List<T> allData) {
		return new PagedDataModelBuilder<>(allData);
	}

	public static interface DataLoader<T> {
		List<T> load(SortFilterPage page, boolean count);
	}

	public static class PagedDataModelBuilder<T> {

		private Class<T> forClass;
		private DataLoader<T> dataLoader;
		private List<T> allData;

		private String defaultSortField = "id";
		private SortOrder defaultSortOrder = DESCENDING;

		private PagedDataModelBuilder(Class<T> forClass) {
			this.forClass = forClass;
		}

		private PagedDataModelBuilder(DataLoader<T> dataLoader) {
			this.dataLoader = dataLoader;
		}

		private PagedDataModelBuilder(List<T> allData) {
			this.allData = allData;
		}

		public PagedDataModelBuilder<T> withDefaultSortField(String defaultSortField) {
			this.defaultSortField = defaultSortField;
			return this;
		}

		public PagedDataModelBuilder<T> withDefaultSortOrder(SortOrder defaultSortOrder) {
			this.defaultSortOrder = defaultSortOrder;
			return this;
		}

		public PagedDataModelBuilder<T> withDefaultSort(String defaultSortField, SortOrder defaultSortOrder) {
			return withDefaultSortField(defaultSortField).withDefaultSortOrder(defaultSortOrder);
		}

		public PagedDataModel<T> build() {
			if (allData != null) {
				return new NonLazyPagedDataModel<>(allData, defaultSortField, defaultSortOrder);
			}

			if (dataLoader == null) {
				if (forClass == null) {
					throw new IllegalStateException("You must provide non-null forClass or dataLoader or allData.");
				}

				GenericEntityService entityService = CDI.current().select(GenericEntityService.class).get();

				if (entityService == null) {
					throw new IllegalStateException("You must provide an implementation of GenericEntityService.");
				}

				dataLoader = (page, count) -> entityService.getAllPagedAndSortedByType(forClass, page, count);
			}

			return new LazyPagedDataModel<T>(defaultSortField, defaultSortOrder) {
				private static final long serialVersionUID = 1L;

				@Override
				public List<T> load(SortFilterPage page, boolean count) {
					return dataLoader.load(page, count);
				}
			};
		}

	}

}