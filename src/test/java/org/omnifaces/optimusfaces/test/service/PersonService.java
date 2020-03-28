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
package org.omnifaces.optimusfaces.test.service;

import static org.omnifaces.persistence.Database.POSTGRESQL;
import static org.omnifaces.persistence.JPA.concat;
import static org.omnifaces.persistence.Provider.HIBERNATE;

import java.util.LinkedHashMap;

import javax.ejb.Stateless;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;

import org.omnifaces.optimusfaces.test.model.Address;
import org.omnifaces.optimusfaces.test.model.Person;
import org.omnifaces.optimusfaces.test.model.Phone;
import org.omnifaces.optimusfaces.test.model.dto.PersonCard;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.utils.collection.PartialResultList;
import org.omnifaces.utils.reflect.Getter;

@Stateless
public class PersonService extends BaseEntityService<Long, Person> {

	public PartialResultList<Person> getPageWithAddress(Page page, boolean count) {
		return getPage(page, count, (builder, query, person) -> {
			person.fetch("address");
		});
	}

	public PartialResultList<Person> getPageWithPhones(Page page, boolean count) {
		return getPage(page, count, (builder, query, person) -> {
			person.fetch("phones");
		});
	}

	public PartialResultList<Person> getPageWithGroups(Page page, boolean count) {
		return getPage(page, count, (builder, query, person) -> {
			person.fetch("groups");
		});
	}

	public PartialResultList<PersonCard> getPageOfPersonCards(Page page, boolean count) {
		return getPage(page, count, PersonCard.class, (builder, query, person) -> {
			Join<Person, Address> personAddress = person.join("address");
			Join<Person, Phone> personPhones = person.join("phones");

			LinkedHashMap<Getter<PersonCard>, Expression<?>> mapping = new LinkedHashMap<>();
			mapping.put(PersonCard::getId, person.get("id"));
			mapping.put(PersonCard::getEmail, person.get("email"));

			if (getProvider() == HIBERNATE) {
				mapping.put(PersonCard::getAddressString, personAddress.get("string")); // address.string uses Hibernate specific @Formula, so no need for manual concat().
			}
			else {
				mapping.put(PersonCard::getAddressString, concat(builder, personAddress.get("street"), " ", personAddress.get("houseNumber"), ", ", personAddress.get("postcode"), " ", personAddress.get("city"), ", ", personAddress.get("country")));
			}

			if (getDatabase() == POSTGRESQL) { // Doesn't hurt on other DBs but makes query unnecessarily bloated.
				query.groupBy(personAddress);
			}

			mapping.put(PersonCard::getTotalPhones, builder.count(personPhones));

			return mapping;
		});
	}

	public PartialResultList<Person> getAllWithAddress() {
		return getPageWithAddress(Page.ALL, false);
	}

	public PartialResultList<Person> getAllWithPhones() {
		return getPageWithPhones(Page.ALL, false);
	}

	public PartialResultList<Person> getAllWithGroups() {
		return getPageWithGroups(Page.ALL, false);
	}

	public PartialResultList<PersonCard> getAllPersonCards() {
		return getPageOfPersonCards(Page.ALL, false);
	}

}
