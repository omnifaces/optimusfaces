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
package org.omnifaces.optimusfaces.test.model.dto;

import org.omnifaces.optimusfaces.test.model.Person;

public class PersonCard extends Person {

	private static final long serialVersionUID = 1L;

	private String addressString;
	private Long totalPhones;

	public PersonCard(Long id, String email, String addressString, Long totalPhones) {
		setId(id);
		setEmail(email);
		this.addressString = addressString;
		this.totalPhones = totalPhones;
	}

	public String getAddressString() {
		return addressString;
	}

	public Long getTotalPhones() {
		return totalPhones;
	}

}
