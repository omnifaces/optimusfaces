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

import static java.util.Collections.emptyMap;
import static org.omnifaces.utils.Lang.isEmpty;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import org.omnifaces.cdi.ViewScoped;
import org.omnifaces.optimusfaces.model.PagedDataModel;
import org.omnifaces.optimusfaces.test.model.Person;
import org.omnifaces.optimusfaces.test.model.Phone;
import org.omnifaces.optimusfaces.test.service.PersonService;
import org.omnifaces.utils.reflect.Getter;

@Named
@ViewScoped
public class OptimusFacesITNonLazyWithOneToManyBean implements Serializable {

	private static final long serialVersionUID = 1L;

	private PagedDataModel<Person> nonLazyPersonsWithPhones;
	private Set<Phone.Type> selectedPhoneTypes;

	@Inject
	private PersonService personService;

	@PostConstruct
	public void init() {
		nonLazyPersonsWithPhones = PagedDataModel.nonLazy(personService.getAllWithPhones()).criteria(this::mapSelectedCriteria).build();
	}

	private Map<Getter<Person>, Object> mapSelectedCriteria() {
		if (isEmpty(selectedPhoneTypes)) {
			return emptyMap();
		}

		Map<Getter<Phone>, Object> phoneCriteria = new HashMap<>();
		phoneCriteria.put(Phone::getType, selectedPhoneTypes);

		Map<Getter<Person>, Object> personCriteria = new HashMap<>();
		personCriteria.put(Person::getPhones, phoneCriteria);

		return personCriteria;
	}

	public PagedDataModel<Person> getNonLazyPersonsWithPhones() {
		return nonLazyPersonsWithPhones;
	}

	public Set<Phone.Type> getSelectedPhoneTypes() {
		return selectedPhoneTypes;
	}

	public void setSelectedPhoneTypes(Set<Phone.Type> selectedPhoneTypes) {
		this.selectedPhoneTypes = selectedPhoneTypes;
	}

}
