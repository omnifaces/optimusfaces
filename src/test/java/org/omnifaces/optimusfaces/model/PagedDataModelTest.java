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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.faces.model.SelectItem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omnifaces.persistence.model.Identifiable;
import org.primefaces.component.column.Column;

@ExtendWith(MockitoExtension.class)
class PagedDataModelTest {

    /** Minimal concrete type satisfying the {@code E extends Identifiable<?>} bound. */
    static final class TestEntity implements Identifiable<Long> {
        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    /**
     * A mock of the interface itself so we can exercise its {@code default} method implementations
     * without providing a full concrete subclass.
     */
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private PagedDataModel<?> model;


    // computeColumnId ------------------------------------------------------------------------------------------------

    @Test
    void computeColumnId_simpleField_returnedUnchanged() {
        assertEquals("name", model.computeColumnId("name"));
    }

    @Test
    void computeColumnId_fieldWithSingleDot_dotReplacedByUnderscore() {
        assertEquals("address_street", model.computeColumnId("address.street"));
    }

    @Test
    void computeColumnId_fieldWithMultipleDots_allDotsReplacedByUnderscores() {
        assertEquals("a_b_c_d", model.computeColumnId("a.b.c.d"));
    }


    // convertFilterOptionsIfNecessary --------------------------------------------------------------------------------

    @Test
    void convertFilterOptionsIfNecessary_selectItemArray_itemsPassThroughWithoutRewrapping() {
        var si1 = new SelectItem("a", "Apple");
        var si2 = new SelectItem("b", "Banana");

        var result = model.convertFilterOptionsIfNecessary(new SelectItem[]{si1, si2});

        assertEquals(3, result.length);
        assertEquals("", result[0].getValue());  // empty marker prepended
        // Items must be the original SelectItem instances, not new SelectItem(originalSelectItem).
        assertSame(si1, result[1]);
        assertSame(si2, result[2]);
    }

    @Test
    void convertFilterOptionsIfNecessary_emptySelectItemArray_onlyEmptyMarkerReturned() {
        var result = model.convertFilterOptionsIfNecessary(new SelectItem[0]);

        assertEquals(1, result.length);
        assertEquals("", result[0].getValue());
    }

    @Test
    void convertFilterOptionsIfNecessary_objectArray_eachItemWrappedInSelectItem() {
        var result = model.convertFilterOptionsIfNecessary(new Object[]{"hello", 42});

        assertEquals(3, result.length);
        assertEquals("", result[0].getValue());
        assertEquals("hello", result[1].getValue());
        assertEquals(42, result[2].getValue());
    }

    @Test
    void convertFilterOptionsIfNecessary_collectionOfSelectItems_itemsPassThroughWithoutRewrapping() {
        var si = new SelectItem("x", "Xray");

        var result = model.convertFilterOptionsIfNecessary(List.of(si));

        assertEquals(2, result.length);
        assertEquals("", result[0].getValue());
        assertSame(si, result[1]);
    }

    @Test
    void convertFilterOptionsIfNecessary_collectionOfPlainObjects_eachItemWrappedInSelectItem() {
        var result = model.convertFilterOptionsIfNecessary(List.of("foo", "bar"));

        assertEquals(3, result.length);
        assertEquals("", result[0].getValue());
        assertEquals("foo", result[1].getValue());
        assertEquals("bar", result[2].getValue());
    }

    @Test
    void convertFilterOptionsIfNecessary_collectionMixed_selectItemsPreservedPlainObjectsWrapped() {
        var si = new SelectItem("existing", "Existing");

        var result = model.convertFilterOptionsIfNecessary(List.of(si, "plain"));

        assertEquals(3, result.length);
        assertSame(si, result[1]);
        assertEquals("plain", result[2].getValue());
    }

    @Test
    void convertFilterOptionsIfNecessary_map_selectItemsCreatedFromKeyValuePairs() {
        var input = new LinkedHashMap<String, String>();
        input.put("a", "Apple");
        input.put("b", "Banana");

        var result = model.convertFilterOptionsIfNecessary(input);

        assertEquals(3, result.length);
        assertEquals("", result[0].getValue());
        assertEquals("a", result[1].getValue());
        assertEquals("Apple", result[1].getLabel());
        assertEquals("b", result[2].getValue());
        assertEquals("Banana", result[2].getLabel());
    }

    @Test
    void convertFilterOptionsIfNecessary_unsupportedType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> model.convertFilterOptionsIfNecessary(42));
        assertThrows(IllegalArgumentException.class, () -> model.convertFilterOptionsIfNecessary("raw string"));
    }


    // Builder: build() returns the correct concrete type -------------------------------------------------------------

    @Test
    void builder_lazy_returnsLazyPagedDataModel() {
        var built = PagedDataModel.lazy((page, estimate) -> null).build();

        assertInstanceOf(LazyPagedDataModel.class, built);
    }

    @Test
    void builder_nonLazy_returnsNonLazyPagedDataModel() {
        PagedDataModel<TestEntity> built = PagedDataModel.nonLazy(new ArrayList<TestEntity>()).build();

        assertInstanceOf(NonLazyPagedDataModel.class, built);
    }


    // Builder: guard clauses -----------------------------------------------------------------------------------------

    @Test
    void builder_predefinedCriteriaCalled_secondCallThrowsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
            PagedDataModel.lazy((page, estimate) -> null)
                .criteria(Map.of("deleted", false))
                .criteria(Map.of("active", true))
        );
    }

    @Test
    void builder_dynamicCriteriaCalled_secondCallThrowsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
            PagedDataModel.lazy((page, estimate) -> null)
                .criteria(() -> Map.of())
                .criteria(() -> Map.of())
        );
    }

    @Test
    void builder_orderingCalled_secondCallThrowsIllegalStateException() {
        var ordering = new LinkedHashMap<String, Boolean>();
        ordering.put("name", true);

        assertThrows(IllegalStateException.class, () ->
            PagedDataModel.lazy((page, estimate) -> null)
                .ordering(ordering)
                .ordering(new LinkedHashMap<>())
        );
    }

    @Test
    void builder_orderByCalledBeforeOrdering_orderingThrowsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
            PagedDataModel.lazy((page, estimate) -> null)
                .orderBy("name", true)
                .ordering(new LinkedHashMap<>())
        );
    }

    @Test
    void builder_orderingCalledBeforeOrderBy_orderByThrowsIllegalStateException() {
        var ordering = new LinkedHashMap<String, Boolean>();
        ordering.put("name", true);

        assertThrows(IllegalStateException.class, () ->
            PagedDataModel.lazy((page, estimate) -> null)
                .ordering(ordering)
                .orderBy("email", false)
        );
    }

    // Builder: no side-effects from chained calls that don't conflict ------------------------------------------------

    @Test
    void builder_orderByCanBeCalledMultipleTimes() {
        assertDoesNotThrow(() ->
            PagedDataModel.lazy((page, estimate) -> null)
                .orderBy("name", true)
                .orderBy("email", false)
                .build()
        );
    }

    @Test
    void builder_predefinedAndDynamicCriteriaCanBeCombined() {
        assertDoesNotThrow(() ->
            PagedDataModel.lazy((page, estimate) -> null)
                .criteria(Map.of("deleted", false))
                .criteria(() -> Map.of())
                .build()
        );
    }


    // setExportable --------------------------------------------------------------------------------------------------

    @Test
    void setExportable_columnIsInitiallyExportable_columnIsToggled() {
        var column = mock(Column.class);
        var attrs = new HashMap<String, Object>();
        when(column.getAttributes()).thenReturn(attrs);
        when(column.isExportable()).thenReturn(true);

        PagedDataModel.setExportable(column, false);

        verify(column).setExportable(false);
    }

    @Test
    void setExportable_columnIsInitiallyNotExportable_columnIsNeverToggled() {
        var column = mock(Column.class);
        var attrs = new HashMap<String, Object>();
        when(column.getAttributes()).thenReturn(attrs);
        when(column.isExportable()).thenReturn(false);

        PagedDataModel.setExportable(column, true);

        verify(column, never()).setExportable(true);
    }

    @Test
    void setExportable_calledTwice_originalExportableStatePreservedForSecondCall() {
        var column = mock(Column.class);
        var attrs = new HashMap<String, Object>();
        when(column.getAttributes()).thenReturn(attrs);
        when(column.isExportable()).thenReturn(true);

        // First call: stores wasExportable=true in attrs, sets exportable to false.
        PagedDataModel.setExportable(column, false);
        // Second call: reads the stored wasExportable=true, so the column can be re-enabled.
        PagedDataModel.setExportable(column, true);

        verify(column).setExportable(false);
        verify(column).setExportable(true);
    }

}
