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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.omnifaces.optimusfaces.model.LazyPagedDataModel.MAX_CACHED_ORDERING_VALUES;
import static org.omnifaces.persistence.model.Identifiable.ID;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import jakarta.faces.component.UIData;

import org.junit.jupiter.api.Test;
import org.omnifaces.optimusfaces.model.PagedDataModel.PartialResultListLoader;
import org.omnifaces.persistence.model.Identifiable;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.utils.collection.PartialResultList;
import org.primefaces.component.datascroller.DataScroller;

/**
 * Covers the paging of {@link LazyPagedDataModel} through a {@link DataScroller}, with a loader returning the rows of a list of {@link #TOTAL_ROWS} entities
 * ordered by descending ID, so that the row at index <code>i</code> has ID <code>TOTAL_ROWS - i</code>.
 */
class LazyPagedDataModelTest {

    private static final int PAGE_SIZE = 10;
    private static final int TOTAL_ROWS = MAX_CACHED_ORDERING_VALUES * PAGE_SIZE;

    static final class TestEntity implements Identifiable<Long> {

        private Long id;

        TestEntity(long id) {
            this.id = id;
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

        public boolean isEven() {
            return id % 2 == 0;
        }

    }

    private final List<Page> loadedPages = new ArrayList<>();
    private final LazyPagedDataModel<TestEntity> model = createModel(new LinkedHashMap<>(singletonMap(ID, false)));

    private LazyPagedDataModel<TestEntity> createModel(LinkedHashMap<String, Boolean> defaultOrdering) {
        var scroller = mock(DataScroller.class);
        PartialResultListLoader<TestEntity> loader = (page, count) -> {
            loadedPages.add(page);
            var rows = new ArrayList<TestEntity>();

            for (var i = page.getOffset(); i < Math.min(page.getOffset() + page.getLimit(), TOTAL_ROWS); i++) {
                rows.add(new TestEntity(TOTAL_ROWS - i));
            }

            return new PartialResultList<>(rows, page.getOffset(), count ? TOTAL_ROWS : -1);
        };

        return new LazyPagedDataModel<>(loader, defaultOrdering, emptyMap(), null) {

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
        assertFalse(page.isReversed());
    }

    @Test
    void load_previousPage_isReversedValueBasedOnFirstRowOfCurrentPage() {
        load(5 * PAGE_SIZE);
        var page = load(4 * PAGE_SIZE);

        assertEquals(4 * PAGE_SIZE, page.getOffset());
        assertEquals(orderingValuesOfRow(5 * PAGE_SIZE), page.getLastOrderingValues());
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
    void load_pageNotNextToAnyVisitedPage_isOffsetBased() {
        load(0);
        var page = load(50 * PAGE_SIZE);

        assertEquals(50 * PAGE_SIZE, page.getOffset());
        assertTrue(page.getLastOrderingValues().isEmpty());
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
