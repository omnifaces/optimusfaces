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

import static java.lang.Math.min;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.stream.Streams.stream;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.Collator;
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

import org.omnifaces.persistence.constraint.Constraint;
import org.omnifaces.persistence.model.Identifiable;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.utils.collection.PartialResultList;
import org.omnifaces.utils.reflect.Getter;
import org.primefaces.component.datatable.DataTable;

/**
 * Use {@link PagedDataModel#nonLazy(List)} to build one.
 *
 * @see PagedDataModel
 * @author Bauke Scholtz
 */
public final class NonLazyPagedDataModel<E extends Identifiable<?>> extends LazyPagedDataModel<E> {

	// Constants ------------------------------------------------------------------------------------------------------

	private static final long serialVersionUID = 1L;


	// Internal properties --------------------------------------------------------------------------------------------

	private List<E> allData;


	// Constructors ---------------------------------------------------------------------------------------------------

	NonLazyPagedDataModel(List<E> allData, LinkedHashMap<String, Boolean> defaultOrdering, Supplier<Map<Getter<?>, Object>> criteria) {
		super(null, defaultOrdering, criteria);
		this.allData = unmodifiableList(allData);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected PartialResultList<E> load(Page page, boolean estimateTotalNumberOfResults) {
		DataTable table = getTable();
		List<E> data = new ArrayList<>(allData);

		if (!data.isEmpty()) {
			Class<E> type = (Class<E>) data.stream().filter(Objects::nonNull).map(Object::getClass).findFirst().orElse(null);

			if (type != null) {
				if (!page.getRequiredCriteria().isEmpty() || !page.getOptionalCriteria().isEmpty()) {
					Map<List<Method>, Object> requiredCriteria = resolveGetters(type, page.getRequiredCriteria());
					Map<List<Method>, Object> optionalCriteria = resolveGetters(type, page.getOptionalCriteria());
					BeanPropertyFilter filter = new BeanPropertyFilter(table, requiredCriteria, optionalCriteria);
					data = data.stream().filter(filter::matches).collect(toList());
				}

				if (data.size() > 1) {
					Map<List<Method>, Boolean> ordering = resolveGetters(type, page.getOrdering());
					data.sort(new BeanPropertyComparator(table, ordering));
				}
			}
		}

		int offset = min(data.size(), page.getOffset());
		int limit = min(data.size() - offset, page.getLimit());
		return new PartialResultList<>(data.subList(offset, offset + limit), offset, data.size());
	}

	/**
	 * Optimized version of PrimeFaces FilterFeature which does not use EL to resolve properties.
	 */
	private class BeanPropertyFilter {

		private final Locale locale;
		private final Map<List<Method>, Object> requiredCriteria;
		private final Map<List<Method>, Object> optionalCriteria;

		public BeanPropertyFilter(DataTable table, Map<List<Method>, Object> requiredCriteria, Map<List<Method>, Object> optionalCriteria) {
	        this.locale = table.resolveDataLocale();
			this.requiredCriteria = requiredCriteria;
			this.optionalCriteria = optionalCriteria;
		}

		public boolean matches(E entity) {
			if (entity == null) {
				return true; // Not our problem.
			}

			for (Entry<List<Method>, Object> criteria : requiredCriteria.entrySet()) {
				if (!matches(entity, criteria)) {
					return false;
				}
			}

			for (Entry<List<Method>, Object> criteria : optionalCriteria.entrySet()) {
				if (matches(entity, criteria)) {
					return true;
				}
			}

			return optionalCriteria.isEmpty();
		}

		private boolean matches(E entity, Entry<List<Method>, Object> criteria) {
			Object propertyValue = invokeMethods(entity, criteria.getKey());

			return stream(criteria.getValue()).anyMatch(criteriaValue -> {
				return (criteriaValue instanceof Constraint && ((Constraint<?>) criteriaValue).applies(propertyValue))
					|| (Objects.equals(propertyValue, criteriaValue))
					|| (Objects.equals(lower(propertyValue, locale), lower(criteriaValue, locale)));
			});
		}
	}

	/**
	 * Optimized version of PrimeFaces SortFeature which does not use EL to resolve properties.
	 */
	private class BeanPropertyComparator implements Comparator<E> {

		private final Locale locale;
		private final Collator collator;
		private final boolean caseSensitive;
		private final boolean nullsLast;
		private final Map<List<Method>, Boolean> ordering;

		public BeanPropertyComparator(DataTable table, Map<List<Method>, Boolean> ordering) {
			this.locale = table.resolveDataLocale();
			this.collator = Collator.getInstance(locale);
	        this.caseSensitive = table.isCaseSensitiveSort();
	        this.nullsLast = table.getNullSortOrder() == 1;
			this.ordering = ordering;
		}

		@Override
		public int compare(E left, E right) {
			for (Entry<List<Method>, Boolean> getter : ordering.entrySet()) {
				Object leftProperty = left != null ? invokeMethods(left, getter.getKey()) : null;
				Object rightProperty = right != null ? invokeMethods(right, getter.getKey()) : null;

				sortOneToManyIfNecessary(leftProperty, getter.getValue());
				sortOneToManyIfNecessary(rightProperty, getter.getValue());

				int result = compareProperties(leftProperty, rightProperty) * (getter.getValue() ? 1 : -1);

				if (result != 0) {
					return result;
				}
			}

			return 0;
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private void sortOneToManyIfNecessary(Object property, boolean ascending) {
			if (property instanceof List && ((List<?>) property).size() > 1) {
				((List) property).sort(ascending ? Comparator.naturalOrder() : Comparator.reverseOrder());
			}
		}

		@SuppressWarnings("unchecked")
		private int compareProperties(Object left, Object right) {
			if (Objects.equals(left, right)) {
				return 0;
			}
			else if (left == null) {
				return nullsLast ? 1 : -1;
			}
			else if (right == null) {
				return nullsLast ? -1 : 1;
			}
			else if (left instanceof String && right instanceof String) {
				if (caseSensitive) {
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
				return compareProperties(left.toString(), right.toString());
			}
		}
	}


	// Helpers --------------------------------------------------------------------------------------------------------

	private static <T> Map<List<Method>, T> resolveGetters(Class<?> type, Map<String, T> properties) {
		Map<List<Method>, T> getters = new LinkedHashMap<>();

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

			getters.put(methods, entry.getValue());
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

	private static Object invokeMethods(Object instance, List<Method> methods) {
		Object result = instance;

		for (int i = 0; i < methods.size(); i++) {
			if (result instanceof Collection) {
				List<Method> remainingMethods = methods.subList(i, methods.size());
				return stream(result).map(item -> invokeMethods(item, remainingMethods)).collect(toList());
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