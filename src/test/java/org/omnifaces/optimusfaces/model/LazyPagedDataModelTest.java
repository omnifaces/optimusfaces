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

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.omnifaces.optimusfaces.model.LazyPagedDataModel.MAX_CACHED_ORDERING_VALUES;
import static org.omnifaces.persistence.model.dto.Page.ID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.LongStream;

import jakarta.faces.component.UIData;
import jakarta.faces.context.FacesContext;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.omnifaces.optimusfaces.model.PagedDataModel.PartialResultListLoader;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.utils.collection.PartialResultList;
import org.primefaces.component.datascroller.DataScroller;
import org.primefaces.component.datatable.DataTable;

/**
 * Covers the paging of {@link LazyPagedDataModel} through a {@link DataScroller}, with a loader returning the rows of a list of {@link #TOTAL_ROWS} entities
 * ordered by descending ID, so that the row at index <code>i</code> has ID <code>TOTAL_ROWS - i</code>.
 */
class LazyPagedDataModelTest {

    private static final int PAGE_SIZE = 10;
    private static final int TOTAL_ROWS = MAX_CACHED_ORDERING_VALUES * PAGE_SIZE;

    static final class TestEntity {

        private final Long id;

        TestEntity(long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }

        public boolean isEven() {
            return id % 2 == 0;
        }

    }

    private final List<Page> loadedPages = new ArrayList<>();
    private final LazyPagedDataModel<TestEntity> model = createModel(new LinkedHashMap<>(singletonMap(ID, false)));

    private LazyPagedDataModel<TestEntity> createModel(LinkedHashMap<String, Boolean> defaultOrdering) {
        return createModel(defaultOrdering, Rows.ofId(TestEntity::getId, null));
    }

    private LazyPagedDataModel<TestEntity> createModel(LinkedHashMap<String, Boolean> defaultOrdering, Rows<TestEntity> rowIdentification) {
        var scroller = mock(DataScroller.class);
        PartialResultListLoader<TestEntity> loader = (page, count) -> {
            loadedPages.add(page);
            var rows = new ArrayList<TestEntity>();

            for (var i = page.getOffset(); i < Math.min(page.getOffset() + page.getLimit(), TOTAL_ROWS); i++) {
                rows.add(new TestEntity(TOTAL_ROWS - i));
            }

            return new PartialResultList<>(rows, page.getOffset(), count ? TOTAL_ROWS : -1);
        };

        return new LazyPagedDataModel<>(loader, defaultOrdering, emptyMap(), null, rowIdentification) {

            private static final long serialVersionUID = 1L;

            @Override
            protected UIData getDataComponent() {
                return scroller;
            }

        };
    }

    private Page load(int offset) {
        return load(offset, PAGE_SIZE);
    }

    private Page load(int offset, int limit) {
        model.load(offset, limit, emptyMap(), emptyMap());
        return lastLoadedPage();
    }

    private List<Long> loadIds(int offset, int limit) {
        return model.load(offset, limit, emptyMap(), emptyMap()).stream().map(TestEntity::getId).toList();
    }

    private static List<Long> idsOfRows(int offset, int limit) {
        return LongStream.range(offset, Math.min(offset + limit, TOTAL_ROWS)).map(rowIndex -> TOTAL_ROWS - rowIndex).boxed().toList();
    }

    private Page lastLoadedPage() {
        return loadedPages.get(loadedPages.size() - 1);
    }

    private static Map<String, Object> orderingValuesOfRow(int rowIndex) {
        return singletonMap(ID, (long) (TOTAL_ROWS - rowIndex));
    }

    // Row data -------------------------------------------------------------------------------------------------------

    @Test
    void getRowData_rowKeyGetterWithoutRowDataLoader_isFoundInLoadedPage() {
        var referencedModel = createModel(new LinkedHashMap<>(singletonMap(ID, false)), Rows.of(entity -> "row" + entity.getId(), null));
        referencedModel.load(0, PAGE_SIZE, emptyMap(), emptyMap());

        assertEquals(TOTAL_ROWS - 1, referencedModel.getRowData("row" + (TOTAL_ROWS - 1)).getId());
    }

    // Serialization --------------------------------------------------------------------------------------------------

    /**
     * Returns the rows by ID when the page has the criteria <code>id</code>, else the rows of the page, like {@link #createModel(LinkedHashMap, Rows)} does.
     */
    private static PartialResultList<TestEntity> loadByIdsOrOffset(Page page, boolean count) {
        if (page.getRequiredCriteria().get(ID) instanceof List<?> ids) {
            return new PartialResultList<>(ids.stream().map(id -> new TestEntity(Long.parseLong(id.toString()))).toList(), 0, -1);
        }

        var rows = new ArrayList<TestEntity>();

        for (var i = page.getOffset(); i < Math.min(page.getOffset() + page.getLimit(), TOTAL_ROWS); i++) {
            rows.add(new TestEntity(TOTAL_ROWS - i));
        }

        return new PartialResultList<>(rows, page.getOffset(), count ? TOTAL_ROWS : -1);
    }

    private static LazyPagedDataModel<TestEntity> loadedSerializableModel(int offset, Rows<TestEntity> rowIdentification) {
        var defaultOrdering = new LinkedHashMap<>(singletonMap(ID, false));
        var model = new LazyPagedDataModel<>(LazyPagedDataModelTest::loadByIdsOrOffset, defaultOrdering, emptyMap(), null, rowIdentification);
        model.ordering = defaultOrdering;
        model.loadPage(mock(UIData.class), offset, PAGE_SIZE, emptyMap(), emptyMap());
        model.setWrappedData(model.getLoadedData());
        return model;
    }

    private static LazyPagedDataModel<TestEntity> loadedSerializableModel() {
        return loadedSerializableModel(0, Rows.ofId(TestEntity::getId, null));
    }

    @SuppressWarnings("unchecked")
    private static <T> T serializeAndDeserialize(T object) throws IOException, ClassNotFoundException {
        var bytes = new ByteArrayOutputStream();

        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }

        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }

    private static List<Long> idsOf(List<TestEntity> entities) {
        return entities.stream().map(TestEntity::getId).toList();
    }

    private static DataTable tableWithPageSize() {
        var table = mock(DataTable.class);
        when(table.getRows()).thenReturn(PAGE_SIZE);
        return table;
    }

    @Test
    void serialization_entityNotSerializable_serializesCopyWithoutEntitiesAndKeepsThemInModel() throws Exception {
        var model = loadedSerializableModel();
        var selected = new ArrayList<>(model.getLoadedData().subList(0, 2));
        model.setSelection(selected);

        var deserialized = serializeAndDeserialize(model);

        assertNull(deserialized.getWrappedData(), "Wrapped data of the copy");
        assertNull(deserialized.getLoadedData(), "Loaded data of the copy");
        assertEquals(idsOfRows(0, PAGE_SIZE), idsOf(model.getLoadedData()), "Loaded data of the model");
        assertEquals(model.getLoadedData(), model.getWrappedData(), "Wrapped data of the model");
        assertEquals(selected, model.getSelection(), "Selection of the model");
    }

    @Test
    void serialization_rowKeyIsId_resolvesSelectionOnFirstAccessViaIdCriteria() throws Exception {
        var model = loadedSerializableModel();
        var selected = new ArrayList<>(model.getLoadedData().subList(0, 2));
        model.setSelection(selected);

        var deserialized = serializeAndDeserialize(model);

        assertEquals(idsOf(selected), idsOf(deserialized.getSelection()), "Selection resolved from row keys");
        assertSame(deserialized.getSelection(), deserialized.getSelection(), "Selection is memoized");
    }

    @Test
    void serialization_preloadPage_loadsSerializedPageAgainAndKeepsRowCount() throws Exception {
        var deserialized = serializeAndDeserialize(loadedSerializableModel(3 * PAGE_SIZE, Rows.ofId(TestEntity::getId, null)));

        deserialized.preloadPage(mock(FacesContext.class), tableWithPageSize());

        assertEquals(idsOfRows(3 * PAGE_SIZE, PAGE_SIZE), idsOf(deserialized.getLoadedData()), "Loaded data");
        assertEquals(deserialized.getLoadedData(), deserialized.getWrappedData(), "Wrapped data");
        assertEquals(TOTAL_ROWS, deserialized.getRowCount(), "Row count");
    }

    @Test
    void serialization_customRowKeyWithoutRowData_resolvesSelectedRowsOfReloadedPage() throws Exception {
        var model = loadedSerializableModel(0, Rows.of(entity -> "row" + entity.getId(), null));
        var selected = new ArrayList<>(model.getLoadedData().subList(0, 2));
        model.setSelection(selected);
        var deserialized = serializeAndDeserialize(model);

        assertNull(deserialized.getSelection(), "Selection before the page is loaded again");
        deserialized.preloadPage(mock(FacesContext.class), tableWithPageSize());

        assertEquals(idsOf(selected), idsOf(deserialized.getSelection()), "Selection resolved from reloaded page");
    }

    @Test
    void serialization_customRowKeyWithoutRowData_dropsSelectedRowsOfOtherPagesWithWarning() throws Exception {
        var model = loadedSerializableModel(0, Rows.of(entity -> "row" + entity.getId(), null));
        var onLoadedPage = model.getLoadedData().get(0);
        var onOtherPage = new TestEntity(1);
        model.setSelection(new ArrayList<>(List.of(onLoadedPage, onOtherPage)));
        var deserialized = serializeAndDeserialize(model);
        deserialized.preloadPage(mock(FacesContext.class), tableWithPageSize());
        var warnings = new ArrayList<LogRecord>();
        var handler = new Handler() {

            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
                // NOOP.
            }

            @Override
            public void close() {
                // NOOP.
            }

        };
        var logger = Logger.getLogger(LazyPagedDataModel.class.getName());
        logger.addHandler(handler);

        try {
            assertEquals(List.of(onLoadedPage.getId()), idsOf(deserialized.getSelection()), "Selection restored from loaded page");
            deserialized.getSelection();
        }
        finally {
            logger.removeHandler(handler);
        }

        assertEquals(1, warnings.size(), "Warnings");
        assertTrue(warnings.get(0).getMessage().contains("rowData()"), warnings.get(0).getMessage());
        assertNull(deserialized.getRowData("row" + onOtherPage.getId()), "Row data of selected row on other page");
    }

    @Test
    void serialization_loadedModel_doesNotReadPageNumberFromQueryStringAgain() throws Exception {
        var deserialized = serializeAndDeserialize(loadedSerializableModel());
        deserialized.queryParameterPrefix = "";
        var table = mock(DataTable.class);

        deserialized.processPageAndOrdering(requestWithPageNumber("5"), table, emptyMap());

        verify(table, never()).setFirst(anyInt());
    }

    @Test
    void processPageAndOrdering_modelNotLoadedYet_readsPageNumberFromQueryString() {
        var defaultOrdering = new LinkedHashMap<>(singletonMap(ID, false));
        var notLoaded = new LazyPagedDataModel<>(
            LazyPagedDataModelTest::loadByIdsOrOffset, defaultOrdering, emptyMap(), null, Rows.ofId(TestEntity::getId, null)
        );
        notLoaded.queryParameterPrefix = "";
        var table = tableWithPageSize();

        notLoaded.processPageAndOrdering(requestWithPageNumber("5"), table, emptyMap());

        verify(table).setFirst(4 * PAGE_SIZE);
    }

    private static FacesContext requestWithPageNumber(String pageNumber) {
        var context = mock(FacesContext.class, Answers.RETURNS_DEEP_STUBS);
        when(context.getExternalContext().getRequestParameterMap()).thenReturn(Map.of(PagedDataModel.QUERY_PARAMETER_PAGE, pageNumber));
        return context;
    }

    // DataScroller ---------------------------------------------------------------------------------------------------

    @Test
    void load_dataScroller_usesDefaultOrdering() {
        var ascendingModel = createModel(new LinkedHashMap<>(singletonMap(ID, true)));
        ascendingModel.load(0, PAGE_SIZE, emptyMap(), emptyMap());

        assertEquals(singletonMap(ID, true), lastLoadedPage().getOrdering());
    }

    // Value based paging ---------------------------------------------------------------------------------------------

    @Test
    void load_firstPage_isOffsetBased() {
        var page = load(0);

        assertTrue(page.getLastOrderingValues().isEmpty());
    }

    @Test
    void load_nextPage_isValueBasedOnLastRowOfCurrentPage() {
        load(0);
        var page = load(PAGE_SIZE);

        assertEquals(PAGE_SIZE, page.getOffset());
        assertEquals(orderingValuesOfRow(PAGE_SIZE - 1), page.getLastOrderingValues());
        assertEquals(0, page.getResidualOffset());
        assertFalse(page.isReversed());
    }

    @Test
    void load_previousPage_isReversedValueBasedOnFirstRowOfCurrentPage() {
        load(5 * PAGE_SIZE);
        var page = load(4 * PAGE_SIZE);

        assertEquals(4 * PAGE_SIZE, page.getOffset());
        assertEquals(orderingValuesOfRow(5 * PAGE_SIZE), page.getLastOrderingValues());
        assertEquals(0, page.getResidualOffset());
        assertTrue(page.isReversed());
    }

    @Test
    void load_pageNextToEarlierVisitedPage_isValueBasedOnBoundaryRowOfThatPage() {
        load(0);
        load(50 * PAGE_SIZE);
        var page = load(PAGE_SIZE);

        assertEquals(orderingValuesOfRow(PAGE_SIZE - 1), page.getLastOrderingValues());
        assertFalse(page.isReversed());
    }

    @Test
    void load_pageAfterVisitedPage_isValueBasedWithResidualOffsetFromLastRowOfThatPage() {
        load(0);
        var page = load(50 * PAGE_SIZE);

        assertEquals(50 * PAGE_SIZE, page.getOffset());
        assertEquals(orderingValuesOfRow(PAGE_SIZE - 1), page.getLastOrderingValues());
        assertEquals(49 * PAGE_SIZE, page.getResidualOffset());
        assertFalse(page.isReversed());
    }

    @Test
    void load_pageBeforeVisitedPage_isReversedValueBasedWithResidualOffsetFromFirstRowOfThatPage() {
        load(50 * PAGE_SIZE);
        var page = load(40 * PAGE_SIZE);

        assertEquals(40 * PAGE_SIZE, page.getOffset());
        assertEquals(orderingValuesOfRow(50 * PAGE_SIZE), page.getLastOrderingValues());
        assertEquals(9 * PAGE_SIZE, page.getResidualOffset());
        assertTrue(page.isReversed());
    }

    @Test
    void load_pageBeforeVisitedPage_residualOffsetNotLessThanOffset_isOffsetBased() {
        load(50 * PAGE_SIZE);
        var page = load(PAGE_SIZE);

        assertEquals(PAGE_SIZE, page.getOffset());
        assertTrue(page.getLastOrderingValues().isEmpty());
    }

    @Test
    void load_pageBeforeVisitedPage_residualOffsetEqualToOffset_isOffsetBased() {
        load(20 * PAGE_SIZE);
        var page = load(9 * PAGE_SIZE, 2 * PAGE_SIZE);

        assertEquals(9 * PAGE_SIZE, page.getOffset());
        assertTrue(page.getLastOrderingValues().isEmpty());
    }

    @Test
    void load_pageBeforeVisitedPage_limitChanged_isReversedValueBasedWithResidualOffsetFromFirstRowOfThatPage() {
        load(50 * PAGE_SIZE);
        var page = load(40 * PAGE_SIZE, 2 * PAGE_SIZE);

        assertEquals(orderingValuesOfRow(50 * PAGE_SIZE), page.getLastOrderingValues());
        assertEquals(8 * PAGE_SIZE, page.getResidualOffset());
        assertTrue(page.isReversed());
    }

    @Test
    void load_pageHalfwayBetweenVisitedPages_prefersForward() {
        load(0);
        load(20 * PAGE_SIZE);
        var page = load(10 * PAGE_SIZE);

        assertEquals(orderingValuesOfRow(PAGE_SIZE - 1), page.getLastOrderingValues());
        assertEquals(9 * PAGE_SIZE, page.getResidualOffset());
        assertFalse(page.isReversed());
    }

    @Test
    void load_limitOverflowingRowIndex_isOffsetBased() {
        load(50 * PAGE_SIZE);
        var page = load(1, Integer.MAX_VALUE);

        assertEquals(1, page.getOffset());
        assertTrue(page.getLastOrderingValues().isEmpty());
    }

    @Test
    void load_pageBetweenVisitedPages_isValueBasedOnNearestRow() {
        load(0);
        load(50 * PAGE_SIZE);
        var page = load(45 * PAGE_SIZE);

        assertEquals(orderingValuesOfRow(50 * PAGE_SIZE), page.getLastOrderingValues());
        assertEquals(4 * PAGE_SIZE, page.getResidualOffset());
        assertTrue(page.isReversed());
    }

    @Test
    void load_pageWithBothNeighborsVisited_prefersForward() {
        load(0);
        load(2 * PAGE_SIZE);
        var page = load(PAGE_SIZE);

        assertEquals(orderingValuesOfRow(PAGE_SIZE - 1), page.getLastOrderingValues());
        assertFalse(page.isReversed());
    }

    @Test
    void loadPage_orderingChanged_isOffsetBased() {
        load(0);
        model.ordering = new LinkedHashMap<>(singletonMap(ID, true));
        model.loadPage(mock(UIData.class), PAGE_SIZE, PAGE_SIZE, emptyMap(), emptyMap());

        assertTrue(lastLoadedPage().getLastOrderingValues().isEmpty());
    }

    @Test
    void loadPage_orderingReordered_isOffsetBased() {
        var ordering = new LinkedHashMap<String, Boolean>();
        ordering.put("even", true);
        ordering.put(ID, false);
        model.ordering = ordering;
        model.loadPage(mock(UIData.class), 0, PAGE_SIZE, emptyMap(), emptyMap());
        var reordered = new LinkedHashMap<String, Boolean>();
        reordered.put(ID, false);
        reordered.put("even", true);
        model.ordering = reordered;
        model.loadPage(mock(UIData.class), PAGE_SIZE, PAGE_SIZE, emptyMap(), emptyMap());

        assertTrue(lastLoadedPage().getLastOrderingValues().isEmpty());
    }

    @Test
    void loadPage_criteriaChanged_isOffsetBased() {
        load(0);
        model.loadPage(mock(UIData.class), PAGE_SIZE, PAGE_SIZE, singletonMap(ID, 42L), emptyMap());

        assertTrue(lastLoadedPage().getLastOrderingValues().isEmpty());
    }

    @Test
    void load_rowWithoutPropertyForOrderingField_isOffsetBased() {
        var modelOrderedByMissingProperty = createModel(new LinkedHashMap<>(singletonMap("name", true)));
        modelOrderedByMissingProperty.load(0, PAGE_SIZE, emptyMap(), emptyMap());
        modelOrderedByMissingProperty.load(PAGE_SIZE, PAGE_SIZE, emptyMap(), emptyMap());

        assertTrue(lastLoadedPage().getLastOrderingValues().isEmpty());
    }

    @Test
    void load_limitChanged_isValueBasedOnLastRowOfCurrentPage() {
        load(0);
        var page = load(PAGE_SIZE, 2 * PAGE_SIZE + 5);

        assertEquals(orderingValuesOfRow(PAGE_SIZE - 1), page.getLastOrderingValues());
    }

    @Test
    void load_valueBasedPages_loaderReadingOnlyOffsetAndLimit_returnsSameRows() {
        var lastPageOffset = TOTAL_ROWS - PAGE_SIZE / 2;

        assertEquals(idsOfRows(0, PAGE_SIZE), loadIds(0, PAGE_SIZE));
        assertEquals(idsOfRows(lastPageOffset, PAGE_SIZE), loadIds(lastPageOffset, PAGE_SIZE));
        assertEquals(idsOfRows(PAGE_SIZE, PAGE_SIZE), loadIds(PAGE_SIZE, PAGE_SIZE));
        assertFalse(lastLoadedPage().getLastOrderingValues().isEmpty());
        assertEquals(idsOfRows(lastPageOffset - PAGE_SIZE, PAGE_SIZE), loadIds(lastPageOffset - PAGE_SIZE, PAGE_SIZE));
        assertTrue(lastLoadedPage().isReversed());
    }

    @Test
    void load_moreRowsCachedThanMaximum_evictsRowsFarthestFromCurrentOffset() {
        var lastOffset = (MAX_CACHED_ORDERING_VALUES - 1) * PAGE_SIZE;

        for (var offset = 0; offset <= lastOffset; offset += PAGE_SIZE) {
            load(offset);
        }

        assertTrue(load(PAGE_SIZE).getLastOrderingValues().isEmpty());
        assertEquals(orderingValuesOfRow(lastOffset - 1), load(lastOffset).getLastOrderingValues());
    }

}
