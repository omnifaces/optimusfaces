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

import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.criteria.Not;
import org.omnifaces.persistence.query.Page;
import org.omnifaces.util.Components;
import org.primefaces.component.datatable.DataTable;

class NonLazyPagedDataModelTest {

    /** Bean with a collection and a collection nested in a collection. */
    public static final class Parent {

        private final String name;
        private final List<String> tags;
        private final Set<String> flags;
        private final List<Parent> children;
        private final Set<Parent> members;

        Parent(String name, List<String> tags, Set<String> flags, List<Parent> children) {
            this(name, tags, flags, children, Set.of());
        }

        Parent(String name, List<String> tags, Set<String> flags, List<Parent> children, Set<Parent> members) {
            this.name = name;
            this.tags = tags;
            this.flags = flags;
            this.children = children;
            this.members = members;
        }

        public String getName() {
            return name;
        }

        public List<String> getTags() {
            return tags;
        }

        public Set<String> getFlags() {
            return flags;
        }

        public List<Parent> getChildren() {
            return children;
        }

        public Set<Parent> getMembers() {
            return members;
        }

    }

    private static final List<Parent> PARENTS = List.of(
        new Parent("a", List.of(), Set.of(), List.of()),
        new Parent("b", List.of("x"), Set.of("f"), List.of(child("b1", List.of()))),
        new Parent("c", List.of(), Set.of(), List.of(child("c1", List.of("y")), child("c2", List.of()))),
        new Parent("d", null, null, List.of(child("d1", null))),
        new Parent("e", Arrays.asList((String) null), Set.of(), List.of())
    );

    /**
     * A <code>null</code> criteria on a collection matches the rows without elements, and <code>Not(null)</code> the rows with elements, the same as the
     * database does for a lazy model.
     */
    @Test
    void load_nullCriteriaOnCollection_matchesRowsWithoutElements() {
        assertEquals(List.of("a", "c", "d"), namesMatching(Collections.singletonMap("tags", null)), "Without tags");
        assertEquals(List.of("b", "e"), namesMatching(Map.of("tags", Not.value(null))), "With tags, also a null tag");
        assertEquals(List.of("a", "c", "d", "e"), namesMatching(Collections.singletonMap("flags", null)), "Without flags");
        assertEquals(List.of("b"), namesMatching(Map.of("flags", Not.value(null))), "With flags");
    }

    /**
     * A <code>null</code> criteria on a collection nested in a collection matches the rows of which no element has nested elements, including the rows without
     * elements, and <code>Not(null)</code> the rows of which an element has nested elements.
     */
    @Test
    void load_nullCriteriaOnNestedCollection_matchesRowsWithoutNestedElements() {
        assertEquals(List.of("a", "b", "d", "e"), namesMatching(Collections.singletonMap("children.tags", null)), "Without tags of children");
        assertEquals(List.of("c"), namesMatching(Map.of("children.tags", Not.value(null))), "With tags of children");
    }

    /**
     * An optional <code>Not(null)</code> criteria on a collection matches the rows with elements.
     */
    @Test
    void load_optionalNotNullCriteriaOnCollections_matchesRowsWithElementsInAny() {
        var page = Page.with().anyMatch(Map.of("tags", Not.value(null), "children.tags", Not.value(null))).build();
        assertEquals(List.of("b", "c", "e"), namesOf(page));
    }

    /**
     * A criteria and an ordering on a field reached through a set work as through a list. The rows are ordered by the smallest element when ascending and by
     * the largest when descending, and the elements of a {@link LinkedHashSet} are reordered as well.
     */
    @Test
    void load_criteriaAndOrderingThroughSet_workAsThroughList() {
        var ordered = new LinkedHashSet<>(List.of(child("m-c", List.of()), child("m-a", List.of())));
        var x = new Parent("x", List.of(), Set.of(), List.of(), ordered);
        var y = new Parent("y", List.of(), Set.of(), List.of(), Set.of(child("m-b", List.of("t"))));
        var reversed = new TreeSet<>(comparing(Parent::getName).reversed());
        reversed.addAll(List.of(child("m-0", List.of()), child("m-e", List.of())));
        var z = new Parent("z", List.of(), Set.of(), List.of(), reversed);
        var parents = List.of(z, y, x);

        assertEquals(
            List.of("z", "x"), namesOf(parents, Page.with().allMatch(Collections.singletonMap("members.tags", null)).build()), "Without tags of members"
        );
        assertEquals(List.of("z", "x", "y"), namesOf(parents, Page.with().orderBy("members.name", false).build()), "Descending");
        assertEquals(List.of("z", "x", "y"), namesOf(parents, Page.with().orderBy("members.name", true).build()), "Ascending");
        assertEquals(List.of("m-a", "m-c"), ordered.stream().map(Parent::getName).toList(), "Members of x in ascending order");
        assertEquals(List.of("m-e", "m-0"), reversed.stream().map(Parent::getName).toList(), "Members of z in their own order");
    }

    @Test
    void load_valueCriteriaOnCollection_matchesRowsContainingValue() {
        assertEquals(List.of("b"), namesMatching(Map.of("tags", "x")));
    }

    private static Parent child(String name, List<String> tags) {
        return new Parent(name, tags, Set.of(), List.of());
    }

    private static List<String> namesMatching(Map<String, Object> criteria) {
        return namesOf(Page.with().allMatch(criteria).build());
    }

    private static List<String> namesOf(Page page) {
        return namesOf(PARENTS, page);
    }

    private static List<String> namesOf(List<Parent> parents, Page page) {
        var table = mock(DataTable.class);
        when(table.resolveDataLocale()).thenReturn(Locale.ENGLISH);
        var model = (NonLazyPagedDataModel<Parent>) PagedDataModel.nonLazy(parents).build();

        try (var components = mockStatic(Components.class)) {
            components.when(Components::getCurrentComponent).thenReturn(table);
            return model.load(page, false).stream().map(Parent::getName).toList();
        }
    }

}
