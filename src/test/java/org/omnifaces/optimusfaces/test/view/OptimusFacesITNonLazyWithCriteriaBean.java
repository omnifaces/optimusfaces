/*
 * Copyright 2020 OmniFaces
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
package org.omnifaces.optimusfaces.test.view;

import static java.util.stream.Collectors.toMap;
import static org.omnifaces.utils.stream.Streams.stream;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.AbstractMap.SimpleEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import org.omnifaces.cdi.ViewScoped;
import org.omnifaces.optimusfaces.model.PagedDataModel;
import org.omnifaces.optimusfaces.test.model.Gender;
import org.omnifaces.optimusfaces.test.model.Person;
import org.omnifaces.optimusfaces.test.service.PersonService;
import org.omnifaces.persistence.criteria.Between;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.criteria.Order;
import org.omnifaces.utils.reflect.Getter;

@Named
@ViewScoped
public class OptimusFacesITNonLazyWithCriteriaBean implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final Map<String, Entry<Getter<Person>, Object>> AVAILABLE_CRITERIA = new LinkedHashMap<>();

	static {
		AVAILABLE_CRITERIA.put("Id BETWEEN 50 AND 150", new SimpleEntry<>(Person::getId, Between.range(50L, 150L)));
		AVAILABLE_CRITERIA.put("Email LIKE name1%", new SimpleEntry<>(Person::getEmail, Like.startsWith("name1")));
		AVAILABLE_CRITERIA.put("Gender = FEMALE", new SimpleEntry<>(Person::getGender, Gender.FEMALE));
		AVAILABLE_CRITERIA.put("DateOfBirth < 1950", new SimpleEntry<>(Person::getDateOfBirth, Order.lessThan(LocalDate.of(1950, 1, 1))));
	}

	private PagedDataModel<Person> nonLazyPersonsWithCriteria;
	private List<Entry<Getter<Person>, Object>> selectedCriteria;

	@Inject
	private PersonService personService;

	@PostConstruct
	public void init() {
		nonLazyPersonsWithCriteria = PagedDataModel.nonLazy(personService.list()).criteria(this::mapSelectedCriteria).build();
	}

	private Map<Getter<Person>, Object> mapSelectedCriteria() {
		return stream(selectedCriteria).collect(toMap(Entry::getKey, Entry::getValue));
	}

	public PagedDataModel<Person> getNonLazyPersonsWithCriteria() {
		return nonLazyPersonsWithCriteria;
	}

	public Map<String, Entry<Getter<Person>, Object>> getAvailableCriteria() {
		return AVAILABLE_CRITERIA;
	}

	public List<Entry<Getter<Person>, Object>> getSelectedCriteria() {
		return selectedCriteria;
	}

	public void setSelectedCriteria(List<Entry<Getter<Person>, Object>> selectedCriteria) {
		this.selectedCriteria = selectedCriteria;
	}

}
