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
package org.omnifaces.optimusfaces.el;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omnifaces.persistence.model.BaseEntity;

@ExtendWith(MockitoExtension.class)
class NestedBaseEntityELResolverTest {

    private final NestedBaseEntityELResolver resolver = new NestedBaseEntityELResolver();

    @Mock
    private ELContext context;

    @Mock
    private ELResolver elResolver;

    /**
     * Minimal concrete BaseEntity subclass for verifying the {@code base instanceof BaseEntity} guard.
     * No JPA annotations needed — we are testing EL resolution logic only.
     */
    static final class TestEntity extends BaseEntity<Long> {
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


    // ELResolver contract methods ------------------------------------------------------------------------------------

    @Test
    void getCommonPropertyType_alwaysReturnsNull() {
        assertNull(resolver.getCommonPropertyType(context, new Object()));
    }

    @Test
    void getType_alwaysReturnsNull() {
        assertNull(resolver.getType(context, new Object(), "property"));
    }

    @Test
    void isReadOnly_alwaysReturnsTrue() {
        assertTrue(resolver.isReadOnly(context, new Object(), "property"));
    }

    @Test
    void getFeatureDescriptors_alwaysReturnsNull() {
        assertNull(resolver.getFeatureDescriptors(context, new Object()));
    }

    @Test
    void setValue_isNoOp_doesNotThrow() {
        assertDoesNotThrow(() -> resolver.setValue(context, new Object(), "property", "value"));
    }


    // getValue: early-return guards ----------------------------------------------------------------------------------

    @Test
    void getValue_baseIsNotABaseEntity_returnsNullWithoutResolvingProperty() {
        var result = resolver.getValue(context, "not a BaseEntity", "some.property");

        assertNull(result);
        verify(context, never()).setPropertyResolved(anyBoolean());
    }

    @Test
    void getValue_propertyContainsNoDot_returnsNullWithoutResolvingProperty() {
        var result = resolver.getValue(context, new TestEntity(), "name");

        assertNull(result);
        verify(context, never()).setPropertyResolved(anyBoolean());
    }


    // getValue: nested property resolution ---------------------------------------------------------------------------

    @Test
    void getValue_twoLevelNestedProperty_resolvesFullChain() {
        when(context.getELResolver()).thenReturn(elResolver);

        var entity = new TestEntity();
        var address = new Object();
        when(elResolver.getValue(context, entity, "address")).thenReturn(address);
        when(elResolver.getValue(context, address, "street")).thenReturn("Baker Street");

        var result = resolver.getValue(context, entity, "address.street");

        assertEquals("Baker Street", result);
    }

    @Test
    void getValue_threeLevelNestedProperty_resolvesFullChain() {
        when(context.getELResolver()).thenReturn(elResolver);

        var entity = new TestEntity();
        var order = new Object();
        var customer = new Object();
        when(elResolver.getValue(context, entity, "order")).thenReturn(order);
        when(elResolver.getValue(context, order, "customer")).thenReturn(customer);
        when(elResolver.getValue(context, customer, "name")).thenReturn("Alice");

        var result = resolver.getValue(context, entity, "order.customer.name");

        assertEquals("Alice", result);
    }

    @Test
    void getValue_nestedProperty_setsPropertyResolvedToTrue() {
        when(context.getELResolver()).thenReturn(elResolver);

        var entity = new TestEntity();
        var intermediate = new Object();
        when(elResolver.getValue(context, entity, "a")).thenReturn(intermediate);
        when(elResolver.getValue(context, intermediate, "b")).thenReturn(new Object());

        resolver.getValue(context, entity, "a.b");

        verify(context).setPropertyResolved(true);
    }

    @Test
    void getValue_nestedPropertyOnNonBaseEntityIntermediateValue_delegatesViaELResolver() {
        // The intermediate resolved value does not need to be a BaseEntity;
        // only the root "base" is checked. Subsequent hops go through the regular ELResolver.
        when(context.getELResolver()).thenReturn(elResolver);

        var entity = new TestEntity();
        var plainObject = new Object();  // not a BaseEntity
        when(elResolver.getValue(context, entity, "wrapper")).thenReturn(plainObject);
        when(elResolver.getValue(context, plainObject, "value")).thenReturn("result");

        var result = resolver.getValue(context, entity, "wrapper.value");

        assertEquals("result", result);
    }


    // getValue: collection in the property chain ---------------------------------------------------------------------

    @Test
    void getValue_collectionInChain_getterMappedOverAllElements() {
        when(context.getELResolver()).thenReturn(elResolver);

        var entity = new TestEntity();
        var phone1 = new Object();
        var phone2 = new Object();
        when(elResolver.getValue(context, entity, "phones")).thenReturn(List.of(phone1, phone2));
        when(elResolver.getValue(context, phone1, "number")).thenReturn("555-0001");
        when(elResolver.getValue(context, phone2, "number")).thenReturn("555-0002");

        var result = resolver.getValue(context, entity, "phones.number");

        assertEquals(List.of("555-0001", "555-0002"), result);
    }

    @Test
    void getValue_collectionInChain_setsPropertyResolvedToTrue() {
        when(context.getELResolver()).thenReturn(elResolver);

        var entity = new TestEntity();
        var phone = new Object();
        when(elResolver.getValue(context, entity, "phones")).thenReturn(List.of(phone));
        when(elResolver.getValue(context, phone, "number")).thenReturn("555-0001");

        resolver.getValue(context, entity, "phones.number");

        verify(context).setPropertyResolved(true);
    }

    @Test
    void getValue_emptyCollectionInChain_returnsEmptyList() {
        when(context.getELResolver()).thenReturn(elResolver);

        var entity = new TestEntity();
        when(elResolver.getValue(context, entity, "phones")).thenReturn(List.of());

        var result = resolver.getValue(context, entity, "phones.number");

        assertEquals(List.of(), result);
    }

}
