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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omnifaces.optimusfaces.model.PagedDataModel.DynamicCriteria;
import org.omnifaces.optimusfaces.model.PagedDataModel.PartialResultListLoader;
import org.omnifaces.optimusfaces.model.PagedDataModel.RowKeyGetter;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.service.EntityRepository;
import org.omnifaces.utils.collection.PartialResultList;
import org.omnifaces.utils.reflect.Getter;
import org.primefaces.component.column.Column;

@ExtendWith(MockitoExtension.class)
class PagedDataModelTest {

    /** Entity which does not extend {@link BaseEntity}. */
    static final class TestEntity implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String reference;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

    }

    static final class TestBaseEntity extends BaseEntity<Long> {

        private static final long serialVersionUID = 1L;

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

    /** Type without an <code>id</code> property. */
    public static final class ReferenceOnly {

        public String getReference() {
            return null;
        }

    }

    private static TestEntity entity(Long id, String reference) {
        var entity = new TestEntity();
        entity.setId(id);
        entity.setReference(reference);
        return entity;
    }

    /**
     * A mock of the interface itself so we can exercise its {@code default} method implementations without providing a full concrete subclass.
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

        var result = model.convertFilterOptionsIfNecessary(new SelectItem[] { si1, si2 });

        assertEquals(3, result.length);
        assertEquals("", result[0].getValue()); // empty marker prepended
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
        var result = model.convertFilterOptionsIfNecessary(new Object[] { "hello", 42 });

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

    // Row key and row data -------------------------------------------------------------------------------------------

    private static <E> LazyPagedDataModel<E> built(PagedDataModel.Builder<E> builder) {
        return (LazyPagedDataModel<E>) builder.build();
    }

    @Test
    void getRowKey_entityWithoutKnownIdAndNoRowKey_throwsIllegalStateExceptionNamingRowKey() {
        var model = built(PagedDataModel.nonLazy(new ArrayList<TestEntity>()));

        var exception = assertThrows(IllegalStateException.class, () -> model.getRowKey(entity(1L, "a")));
        assertTrue(exception.getMessage().contains("rowKey()"), exception.getMessage());
    }

    @Test
    void getRowKey_baseEntityViaEntityService_isId() {
        @SuppressWarnings("unchecked")
        BaseEntityService<Long, TestBaseEntity> service = mock(BaseEntityService.class);
        var entity = new TestBaseEntity();
        entity.setId(42L);

        assertEquals("42", built(PagedDataModel.lazy(service)).getRowKey(entity));
    }

    @Test
    void getRowKey_baseEntityInNonLazyModel_isId() {
        var entity = new TestBaseEntity();
        entity.setId(42L);

        assertEquals("42", built(PagedDataModel.nonLazy(new ArrayList<TestBaseEntity>())).getRowKey(entity));
    }

    @Test
    void getRowKey_baseEntityWithoutId_fallsBackToHashCode() {
        var entity = new TestBaseEntity();

        assertEquals(String.valueOf(entity.hashCode()), built(PagedDataModel.nonLazy(new ArrayList<TestBaseEntity>())).getRowKey(entity));
    }

    @Test
    void getRowKey_entityRepository_isIdOfRepository() {
        @SuppressWarnings("unchecked")
        EntityRepository<Long, TestEntity> repository = mock(EntityRepository.class);
        var entity = entity(null, "a");
        when(repository.getId(entity)).thenReturn(42L);

        assertEquals("42", built(PagedDataModel.lazy(repository)).getRowKey(entity));
    }

    @Test
    void getRowKey_rowKeyGetter_isUsed() {
        var model = built(PagedDataModel.nonLazy(new ArrayList<TestEntity>()).rowKey(TestEntity::getReference));

        assertEquals("a", model.getRowKey(entity(1L, "a")));
    }

    @Test
    void getRowKey_rowKeyIsNull_fallsBackToHashCode() {
        var model = built(PagedDataModel.nonLazy(new ArrayList<TestEntity>()).rowKey(TestEntity::getReference));
        var entity = entity(1L, null);

        assertEquals(String.valueOf(entity.hashCode()), model.getRowKey(entity));
    }

    @Test
    void getRowData_rowDataLoader_isUsed() {
        var entity = entity(1L, "a");
        var model = built(PagedDataModel.nonLazy(new ArrayList<TestEntity>()).rowKey(TestEntity::getReference).rowData(rowKey -> entity));

        assertSame(entity, model.getRowData("a"));
    }

    @Test
    void getRowData_nonLazyWithRowKeyGetterAndNoRowDataLoader_isFoundInAllData() {
        var entity = entity(1L, "a");
        var model = built(PagedDataModel.nonLazy(List.of(entity(2L, "b"), entity)).rowKey(TestEntity::getReference));

        assertSame(entity, model.getRowData("a"));
    }

    @Test
    void getRowData_lazyWithRowKeyGetterAndNoRowDataLoader_notLoaded_throwsIllegalStateExceptionNamingRowData() {
        var model = built(PagedDataModel.<TestEntity>lazy((page, estimate) -> null).rowKey(TestEntity::getReference));

        var exception = assertThrows(IllegalStateException.class, () -> model.getRowData("a"));
        assertTrue(exception.getMessage().contains("rowData()"), exception.getMessage());
    }

    @Test
    void processSelectionIfNecessary_rowDataLoader_isInvokedPerSelectedRowKey() {
        var model = built(PagedDataModel.<TestEntity>lazy((page, estimate) -> null).rowKey(TestEntity::getReference).rowData(rowKey -> entity(null, rowKey)));

        var selection = model.processSelectionIfNecessary(initialRequestWithSelection(model, "a", "b"), null);

        assertEquals(List.of("a", "b"), selection.stream().map(TestEntity::getReference).toList());
    }

    @Test
    void processSelectionIfNecessary_rowKeyGetterWithoutRowDataLoader_isNotRestored() {
        var model = built(PagedDataModel.<TestEntity>lazy((page, estimate) -> null).rowKey(TestEntity::getReference));

        assertTrue(model.processSelectionIfNecessary(initialRequestWithSelection(model, "a"), null).isEmpty());
    }

    @Test
    void processSelectionIfNecessary_rowKeyIsId_isLoadedViaIdCriteria() {
        var loadedPages = new ArrayList<Page>();
        var model = built(PagedDataModel.<TestBaseEntity>lazy((page, estimate) -> {
            loadedPages.add(page);
            return new PartialResultList<>(List.of(new TestBaseEntity()), 0, -1);
        }));

        var selection = model.processSelectionIfNecessary(initialRequestWithSelection(model, "1", "2"), null);

        assertEquals(1, selection.size());
        assertEquals(Map.of(Page.ID, List.of("1", "2")), loadedPages.get(0).getRequiredCriteria());
    }

    private static FacesContext initialRequestWithSelection(LazyPagedDataModel<?> model, String... rowKeys) {
        model.queryParameterPrefix = "";
        var context = mock(FacesContext.class, Answers.RETURNS_DEEP_STUBS);
        when(context.isPostback()).thenReturn(false);
        when(context.getExternalContext().getRequestParameterValuesMap()).thenReturn(Map.of(PagedDataModel.QUERY_PARAMETER_SELECTION, rowKeys));
        return context;
    }

    @Test
    void withoutUnknownId_typeWithoutIdProperty_removesIdFromOrdering() {
        var ordering = new LinkedHashMap<String, Boolean>();
        ordering.put("reference", true);
        ordering.put(Page.ID, false);

        assertEquals(Map.of("reference", true), NonLazyPagedDataModel.withoutUnknownId(ReferenceOnly.class, ordering));
        assertEquals(ordering, NonLazyPagedDataModel.withoutUnknownId(TestEntity.class, ordering));
    }

    @Test
    void model_lazyEntityRepository_survivesSerialization() throws Exception {
        var repository = new EntityRepository<Long, TestEntity>(() -> null, Long.class, TestEntity.class);

        assertInstanceOf(LazyPagedDataModel.class, serializeAndDeserialize(PagedDataModel.lazy(repository).build()));
    }

    @Test
    void rowKeyGetter_methodReference_survivesSerialization() throws Exception {
        RowKeyGetter<TestEntity> rowKey = TestEntity::getReference;

        assertEquals("a", serializeAndDeserialize(rowKey).apply(entity(1L, "a")));
    }

    // Builder: guard clauses -----------------------------------------------------------------------------------------

    @Test
    void builder_predefinedCriteriaCalled_secondCallThrowsIllegalStateException() {
        assertThrows(
            IllegalStateException.class, () -> PagedDataModel.lazy((page, estimate) -> null)
                .criteria(Map.of("deleted", false))
                .criteria(Map.of("active", true))
        );
    }

    @Test
    void builder_dynamicCriteriaCalled_secondCallThrowsIllegalStateException() {
        assertThrows(
            IllegalStateException.class, () -> PagedDataModel.lazy((page, estimate) -> null)
                .criteria(() -> Map.of())
                .criteria(() -> Map.of())
        );
    }

    @Test
    void builder_orderingCalled_secondCallThrowsIllegalStateException() {
        var ordering = new LinkedHashMap<String, Boolean>();
        ordering.put("name", true);

        assertThrows(
            IllegalStateException.class, () -> PagedDataModel.lazy((page, estimate) -> null)
                .ordering(ordering)
                .ordering(new LinkedHashMap<>())
        );
    }

    @Test
    void builder_orderByCalledBeforeOrdering_orderingThrowsIllegalStateException() {
        assertThrows(
            IllegalStateException.class, () -> PagedDataModel.lazy((page, estimate) -> null)
                .orderBy("name", true)
                .ordering(new LinkedHashMap<>())
        );
    }

    @Test
    void builder_orderingCalledBeforeOrderBy_orderByThrowsIllegalStateException() {
        var ordering = new LinkedHashMap<String, Boolean>();
        ordering.put("name", true);

        assertThrows(
            IllegalStateException.class, () -> PagedDataModel.lazy((page, estimate) -> null)
                .ordering(ordering)
                .orderBy("email", false)
        );
    }

    // Builder: no side-effects from chained calls that don't conflict ------------------------------------------------

    @Test
    void builder_orderByCanBeCalledMultipleTimes() {
        assertDoesNotThrow(
            () -> PagedDataModel.lazy((page, estimate) -> null)
                .orderBy("name", true)
                .orderBy("email", false)
                .build()
        );
    }

    @Test
    void builder_predefinedAndDynamicCriteriaCanBeCombined() {
        assertDoesNotThrow(
            () -> PagedDataModel.lazy((page, estimate) -> null)
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

    // Serialization --------------------------------------------------------------------------------------------------

    /** Mimics the view scoped bean holding the model, as that is what gets passivated or replicated. */
    static final class TestBean implements Serializable {

        private static final long serialVersionUID = 1L;

        private final PagedDataModel<TestEntity> model = PagedDataModel.<TestEntity>lazy((page, estimateTotalNumberOfResults) -> null)
            .rowKey(TestEntity::getReference)
            .criteria(Map.of("name", Like.contains("test")))
            .criteria(this::getCriteria)
            .build();

        Map<Getter<TestEntity>, Object> getCriteria() {
            return Map.of(TestEntity::getId, 1L);
        }

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

    @Test
    void model_heldByViewScopedBean_survivesSerializationWithoutItsEntities() throws Exception {
        var entity = entity(42L, "a");
        var bean = new TestBean();
        bean.model.setFilteredValue(List.of(entity));
        bean.model.setSelection(List.of(entity));

        var model = serializeAndDeserialize(bean).model;

        assertNull(model.getFilteredValue(), "Filtered value");
        assertNull(model.getSelection(), "Selection before it is resolved from its row keys");
    }

    @Test
    void nonLazyModel_customRowKeyWithoutRowData_resolvesSelectionFromAllDataAfterDeserialization() throws Exception {
        var selected = entity(2L, "b");
        var model = built(PagedDataModel.nonLazy(List.of(entity(1L, "a"), selected)).rowKey(TestEntity::getReference));
        model.setSelection(List.of(selected));

        var deserialized = serializeAndDeserialize(model);

        assertEquals(List.of("b"), deserialized.getSelection().stream().map(TestEntity::getReference).toList());
    }

    @Test
    void nonLazyModel_rowKeyIsId_resolvesSelectionFromAllDataAfterDeserialization() throws Exception {
        var selected = new TestBaseEntity();
        selected.setId(2L);
        var other = new TestBaseEntity();
        other.setId(1L);
        var model = built(PagedDataModel.nonLazy(List.of(other, selected)));
        model.setSelection(List.of(selected));

        var deserialized = serializeAndDeserialize(model);

        assertEquals(List.of(2L), deserialized.getSelection().stream().map(TestBaseEntity::getId).toList());
    }

    @Test
    void dynamicCriteria_methodReferenceToViewScopedBean_survivesSerialization() throws Exception {
        DynamicCriteria<TestEntity> dynamicCriteria = new TestBean()::getCriteria;

        var criteria = serializeAndDeserialize(dynamicCriteria).get();

        assertEquals(1, criteria.size());
        assertEquals("id", criteria.keySet().iterator().next().getPropertyName());
    }

    @Test
    void partialResultListLoader_methodReferenceToEntityService_survivesSerialization() throws Exception {
        PartialResultListLoader<TestEntity> loader = (page, estimateTotalNumberOfResults) -> null;

        assertNull(serializeAndDeserialize(loader).getPage(Page.ALL, false));
    }

}
