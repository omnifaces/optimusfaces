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
import static org.mockito.Mockito.mock;
import static org.omnifaces.persistence.model.Identifiable.ID;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

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
    private static final int TOTAL_ROWS = 100;

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

    }

    private final List<Page> loadedPages = new ArrayList<>();

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

    private Page lastLoadedPage() {
        return loadedPages.get(loadedPages.size() - 1);
    }

    // DataScroller ---------------------------------------------------------------------------------------------------

    @Test
    void load_dataScroller_usesDefaultOrdering() {
        var ascendingModel = createModel(new LinkedHashMap<>(singletonMap(ID, true)));
        ascendingModel.load(0, PAGE_SIZE, emptyMap(), emptyMap());

        assertEquals(singletonMap(ID, true), lastLoadedPage().getOrdering());
    }

}
