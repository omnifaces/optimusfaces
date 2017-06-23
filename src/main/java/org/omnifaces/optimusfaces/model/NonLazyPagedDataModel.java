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
import static java.util.stream.Collectors.toMap;
import static org.omnifaces.util.Faces.getELContext;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.stream.Streams.stream;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;

import javax.el.MethodExpression;

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
					Map<Method, Object> requiredCriteria = resolveGetters(type, page.getRequiredCriteria());
					Map<Method, Object> optionalCriteria = resolveGetters(type, page.getOptionalCriteria());
					BeanPropertyFilter filter = new BeanPropertyFilter(table, requiredCriteria, optionalCriteria);
					data = data.stream().filter(filter::matches).collect(toList());
				}

				if (data.size() > 1) {
					Map<Method, Boolean> ordering = resolveGetters(type, page.getOrdering());
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
		private final Map<Method, Object> requiredCriteria;
		private final Map<Method, Object> optionalCriteria;

		public BeanPropertyFilter(DataTable table, Map<Method, Object> requiredCriteria, Map<Method, Object> optionalCriteria) {
	        this.locale = table.resolveDataLocale();
			this.requiredCriteria = requiredCriteria;
			this.optionalCriteria = optionalCriteria;
		}

		public boolean matches(E entity) {
			if (entity == null) {
				return true;
			}

			for (Entry<Method, Object> criteria : requiredCriteria.entrySet()) {
				if (!matches(entity, criteria)) {
					return false;
				}
			}

			for (Entry<Method, Object> criteria : optionalCriteria.entrySet()) {
				if (matches(entity, criteria)) {
					return true;
				}
			}

			return optionalCriteria.isEmpty();
		}

		private boolean matches(E entity, Entry<Method, Object> criteria) {
			Object propertyValue = invokeMethod(entity, criteria.getKey());

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

		private final MethodExpression sortFunction;
		private final boolean caseSensitive;
		private final Locale locale;
		private final Collator collator;
		private final boolean nullsLast;
		private final Map<Method, Boolean> ordering;

		public BeanPropertyComparator(DataTable table, Map<Method, Boolean> ordering) {
	        this.sortFunction = table.getSortFunction();
	        this.caseSensitive = table.isCaseSensitiveSort();
	        this.locale = table.resolveDataLocale();
	        this.collator = Collator.getInstance(locale);
	        this.nullsLast = table.getNullSortOrder() == 1;
			this.ordering = ordering;
		}

		@Override
		public int compare(E left, E right) {
			for (Entry<Method, Boolean> getter : ordering.entrySet()) {
				Object leftProperty = left != null ? invokeMethod(left, getter.getKey()) : null;
				Object rightProperty = right != null ? invokeMethod(right, getter.getKey()) : null;
				int result = compareProperties(leftProperty, rightProperty) * (getter.getValue() ? 1 : -1);

				if (result != 0) {
					return result;
				}
			}

            return 0;
		}

		@SuppressWarnings("unchecked")
		private int compareProperties(Object left, Object right) {
			if (left == right) {
				return 0;
			}
			else if (left == null) {
				return nullsLast ? 1 : -1;
			}
			else if (right == null) {
				return nullsLast ? -1 : 1;
			}
			else if (sortFunction != null) {
				return (int) sortFunction.invoke(getELContext(), new Object[] { left, right });
			}
			else if (left instanceof String && right instanceof String) {
		        if (caseSensitive) {
		            return collator.compare(left, right);
		        }
		        else {
		            return collator.compare(lower(left, locale), lower(right, locale));
		        }
			}
			else {
				return ((Comparable<Object>) left).compareTo(right);
			}
		}
	}

	// Helpers --------------------------------------------------------------------------------------------------------

	private static <T> Map<Method, T> resolveGetters(Class<?> type, Map<String, T> properties) {
		BeanInfo beanInfo;

		try {
			beanInfo = Introspector.getBeanInfo(type);
		}
		catch (IntrospectionException e) {
			throw new UnsupportedOperationException(e);
		}

		return stream(beanInfo.getPropertyDescriptors())
			.filter(property -> properties.containsKey(property.getName()))
			.collect(toMap(PropertyDescriptor::getReadMethod, property -> properties.get(property.getName()), (l, r) -> l, LinkedHashMap::new));
	}

	private static String lower(Object value, Locale locale) {
		return value == null ? null : value.toString().toLowerCase(locale);
	}

}