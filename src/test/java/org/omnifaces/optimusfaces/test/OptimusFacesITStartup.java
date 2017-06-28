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
package org.omnifaces.optimusfaces.test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.omnifaces.cdi.Eager;
import org.omnifaces.optimusfaces.test.model.Address;
import org.omnifaces.optimusfaces.test.model.Gender;
import org.omnifaces.optimusfaces.test.model.Person;
import org.omnifaces.optimusfaces.test.model.Phone;
import org.omnifaces.optimusfaces.test.service.PersonService;

@Eager
@ApplicationScoped
public class OptimusFacesITStartup {

	public static final int TOTAL_RECORDS = 200;
	public static final int ROWS_PER_PAGE = 10;

	@Inject
	private PersonService personService;

	@PostConstruct
	public void init() {
		createTestPersons();
	}

	private void createTestPersons() {
		Gender[] genders = Gender.values();
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int i = 0; i < TOTAL_RECORDS; i++) {
			Person person = new Person();
			person.setEmail("name" + i + "@example.com");
			person.setGender(genders[random.nextInt(genders.length)]);
			person.setDateOfBirth(LocalDate.ofEpochDay(random.nextLong(LocalDate.of(1900, 1, 1).toEpochDay(), LocalDate.of(2000, 1, 1).toEpochDay())));

			Address address = new Address();
			address.setStreet("Street" + i);
			address.setHouseNumber("" + i);
			address.setPostcode("Postcode" + i);
			address.setCity("City" + i);
			address.setCountry("Country" + i);
			person.setAddress(address);

			Phone mobile = new Phone();
			mobile.setType(Phone.Type.MOBILE);
			mobile.setNumber("" + random.nextInt());
			Phone home = new Phone();
			home.setType(Phone.Type.HOME);
			home.setNumber("" + random.nextInt());
			person.setTelephones(Arrays.asList(mobile, home));

			personService.save(person);
		}
	}

}
