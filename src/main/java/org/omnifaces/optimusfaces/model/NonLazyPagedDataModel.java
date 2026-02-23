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

import static java.lang.Math.min;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static org.omnifaces.utils.Lang.isEmpty;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.stream.Streams.stream;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.Collator;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;

import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.model.Identifiable;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.utils.collection.PartialResultList;
import org.omnifaces.utils.reflect.Getter;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.component.datatable.feature.FilterFeature;
import org.primefaces.component.datatable.feature.SortFeature;
import org.primefaces.model.SortMeta;

/**
 * <p>
 * In-memory {@link PagedDataModel} implementation that applies filtering, sorting and pagination entirely in Java
 * on a pre-loaded list of entities, without touching the data store on every page request.
 * <p>
 * Filtering supports {@link org.omnifaces.persistence.criteria.Criteria}-based predicates as well as plain
 * equality and case-insensitive string comparison. Sorting uses a {@link java.util.Comparator} that handles
 * nested properties, {@link java.util.Collection}-valued paths, <code>null</code>-ordering and
 * locale-aware string comparison.
 * <p>
 * Construct via {@link PagedDataModel#nonLazy(List)}.
 *
 * @param <E> The entity type, which must extend {@link org.omnifaces.persistence.model.Identifiable}.
 * @see PagedDataModel
 * @author Bauke Scholtz
 * @since 1.0
 */
public final class NonLazyPagedDataModel<E extends Identifiable<?>> extends LazyPagedDataModel<E> {

    // Constants ------------------------------------------------------------------------------------------------------

    private static final long serialVersionUID = 1L;


    // Internal properties --------------------------------------------------------------------------------------------

    private List<E> allData;


    // Constructors ---------------------------------------------------------------------------------------------------

    NonLazyPagedDataModel(List<E> allData, LinkedHashMap<String, Boolean> defaultOrdering, Map<String, Object> predefinedCriteria, Supplier<Map<Getter<?>, Object>> dynamicCriteria) {
        super(null, defaultOrdering, predefinedCriteria, dynamicCriteria);
        this.allData = unmodifiableList(allData);
    }

    /**
     * Applies in-memory filtering, sorting and pagination on the full data list.
     * <p>
     * The full list is first filtered according to the required and optional criteria in the given {@link Page},
     * then sorted using a locale-aware, null-safe comparator, and finally sliced to the requested offset and
     * limit. The total count is always exact.
     * @param page Describes offset, limit, ordering and filter criteria.
     * @param estimateTotalNumberOfResults Unused; the total is always computed from the filtered list.
     * @return A {@link PartialResultList} containing the requested slice and the total filtered count.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected PartialResultList<E> load(Page page, boolean estimateTotalNumberOfResults) {
        DataTable table = (DataTable) getDataComponent();
        List<E> data = new ArrayList<>(allData);

        if (!data.isEmpty()) {
            Class<E> type = (Class<E>) data.stream().filter(Objects::nonNull).map(Object::getClass).findFirst().orElse(null);

            if (type != null) {
                if (!page.getRequiredCriteria().isEmpty() || !page.getOptionalCriteria().isEmpty()) {
                    Map<List<Method>, Entry<String, Object>> requiredCriteria = resolveGetters(type, page.getRequiredCriteria());
                    Map<List<Method>, Entry<String, Object>> optionalCriteria = resolveGetters(type, page.getOptionalCriteria());
                    BeanPropertyFilter filter = new BeanPropertyFilter(table, requiredCriteria, optionalCriteria);
                    data = data.stream().filter(filter::matches).collect(toList());
                }

                if (data.size() > 1) {
                    Map<List<Method>, Entry<String, Boolean>> ordering = resolveGetters(type, page.getOrdering());
                    data.sort(new BeanPropertyComparator(table, ordering));
                }
            }
        }

        int offset = min(data.size(), page.getOffset());
        int limit = min(data.size() - offset, page.getLimit());
        return new PartialResultList<>(new ArrayList<>(data.subList(offset, offset + limit)), offset, data.size());
    }

    /**
     * Optimized version of PrimeFaces {@link FilterFeature} for non-lazy models.
     */
    private class BeanPropertyFilter {

        private final Locale locale;
        private final Map<List<Method>, Entry<String, Object>> requiredCriteria;
        private final Map<List<Method>, Entry<String, Object>> optionalCriteria;

        public BeanPropertyFilter(DataTable table, Map<List<Method>, Entry<String, Object>> requiredCriteria, Map<List<Method>, Entry<String, Object>> optionalCriteria) {
            this.locale = table.resolveDataLocale();
            this.requiredCriteria = requiredCriteria;
            this.optionalCriteria = optionalCriteria;
        }

        public boolean matches(E entity) {
            if (entity == null) {
                return true; // Not our problem.
            }

            for (Entry<List<Method>, Entry<String, Object>> criteria : requiredCriteria.entrySet()) {
                if (!matches(entity, criteria)) {
                    return false;
                }
            }

            for (Entry<List<Method>, Entry<String, Object>> criteria : optionalCriteria.entrySet()) {
                if (matches(entity, criteria)) {
                    return true;
                }
            }

            return optionalCriteria.isEmpty();
        }

        private boolean matches(E entity, Entry<List<Method>, Entry<String, Object>> criteria) {
            Object propertyValue = invokeMethods(entity, criteria.getKey(), null, false);
            Object criteriaValue = criteria.getValue().getValue();

            if (propertyValue instanceof Collection && !(criteriaValue instanceof Criteria)) {
                return isEmpty(criteriaValue) || stream(criteriaValue).allMatch(value -> ((Collection<?>) propertyValue).contains(value));
            }
            else {
                return stream(criteriaValue).anyMatch(value -> ((value instanceof Criteria && ((Criteria<?>) value).applies(propertyValue))
                        || (Objects.equals(propertyValue, value))
                        || (Objects.equals(lower(propertyValue, locale), lower(value, locale)))));
            }
        }
    }

    /**
     * Optimized version of PrimeFaces {@link SortFeature} for non-lazy models.
     */
    private class BeanPropertyComparator implements Comparator<E> {

        private final Locale locale;
        private final Collator collator;
        private final Map<String, SortMeta> sortBy;
        private final Map<List<Method>, Entry<String, Boolean>> ordering;

        public BeanPropertyComparator(DataTable table, Map<List<Method>, Entry<String, Boolean>> ordering) {
            this.locale = table.resolveDataLocale();
            this.collator = Collator.getInstance(locale);
            this.sortBy = table.getActiveSortMeta();
            this.ordering = ordering;
        }

        public BeanPropertyComparator(BeanPropertyComparator parent, Map<List<Method>, Entry<String, Boolean>> remainingOrdering) {
            this.locale = parent.locale;
            this.collator = parent.collator;
            this.sortBy = parent.sortBy;
            this.ordering = remainingOrdering;
        }

        @Override
        public int compare(E left, E right) {
            for (Entry<List<Method>, Entry<String, Boolean>> getter : ordering.entrySet()) {
                Object leftProperty = left != null ? invokeMethods(left, getter.getKey(), this, getter.getValue().getValue()) : null;
                Object rightProperty = right != null ? invokeMethods(right, getter.getKey(), this, getter.getValue().getValue()) : null;
                SortMeta sortMeta = sortBy.get(getter.getValue().getKey());
                int result = compareProperties(leftProperty, rightProperty, sortMeta) * (getter.getValue().getValue() ? 1 : -1);

                if (result != 0) {
                    return result;
                }
            }

            return 0;
        }

        @SuppressWarnings("unchecked")
        private int compareProperties(Object left, Object right, SortMeta sortMeta) {
            if (Objects.equals(left, right)) {
                return 0;
            }
            else if (left == null) {
                return sortMeta != null ? sortMeta.getNullSortOrder() : 1;
            }
            else if (right == null) {
                return sortMeta != null ? sortMeta.getNullSortOrder() : -1;
            }
            else if (left instanceof String && right instanceof String) {
                if (sortMeta != null && sortMeta.isCaseSensitiveSort()) {
                    return collator.compare(left, right);
                }
                else {
                    return collator.compare(lower(left, locale), lower(right, locale));
                }
            }
            else if (left instanceof Comparable && right instanceof Comparable) {
                return ((Comparable<Object>) left).compareTo(right);
            }
            else {
                return compareProperties(left.toString(), right.toString(), sortMeta);
            }
        }
    }


    // Helpers --------------------------------------------------------------------------------------------------------

    private static <T> Map<List<Method>, Entry<String, T>> resolveGetters(Class<?> type, Map<String, T> properties) {
        Map<List<Method>, Entry<String, T>> getters = new LinkedHashMap<>();

        for (Entry<String, T> entry : properties.entrySet()) {
            Class<?> beanClass = type;
            List<Method> methods = new ArrayList<>(2);

            for (String propertyName : entry.getKey().split("\\.")) {
                Method getter = resolveGetter(beanClass, propertyName);
                methods.add(getter);
                beanClass = getter.getReturnType();

                if (Collection.class.isAssignableFrom(beanClass)) {
                    Type genericReturnType = getter.getGenericReturnType();

                    if (genericReturnType instanceof ParameterizedType) {
                        beanClass = (Class<?>) ((ParameterizedType) genericReturnType).getActualTypeArguments()[0];
                    }
                }
            }

            getters.put(methods, entry);
        }

        return getters;
    }

    private static Method resolveGetter(Class<?> beanClass, String propertyName) {
        try {
            return stream(Introspector.getBeanInfo(beanClass).getPropertyDescriptors())
                .filter(property -> property.getName().equals(propertyName))
                .map(PropertyDescriptor::getReadMethod)
                .findFirst().orElse(null);
        }
        catch (IntrospectionException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object invokeMethods(Object instance, List<Method> methods, BeanPropertyComparator comparator, boolean ascending) {
        Object result = instance;

        for (int i = 0; i < methods.size(); i++) {
            if (result instanceof List) {
                List<Method> remainingMethods = methods.subList(i, methods.size());

                if (!remainingMethods.isEmpty() && comparator != null && ((List<?>) result).size() > 1) {
                    ((List) result).sort(new BeanPropertyComparator(comparator, singletonMap(remainingMethods, new AbstractMap.SimpleEntry<>(null, ascending))));
                }

                return stream(result).map(item -> invokeMethods(item, remainingMethods, comparator, ascending)).collect(toList());
            }
            else {
                result = invokeMethod(result, methods.get(i));
            }
        }

        return result;
    }

    private static String lower(Object value, Locale locale) {
        return value == null ? null : value.toString().toLowerCase(locale);
    }

}