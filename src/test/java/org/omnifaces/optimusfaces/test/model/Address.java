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
package org.omnifaces.optimusfaces.test.model;

import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Formula;

@Entity
public class Address extends LocalGeneratedIdEntity {

	private static final long serialVersionUID = 1L;

	private @NotNull String street;
	private @NotNull String houseNumber;
	private @NotNull String postcode;
	private @NotNull String city;
	private @NotNull String country;

	@Formula("CONCAT(street, ' ', houseNumber, ', ', postcode, ' ', city, ', ', country)") // NOTE: EclipseLink doesn't have a reasonable equivalent for @Formula.
	private String string;

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public String getHouseNumber() {
		return houseNumber;
	}

	public void setHouseNumber(String houseNumber) {
		this.houseNumber = houseNumber;
	}

	public String getPostcode() {
		return postcode;
	}

	public void setPostcode(String postcode) {
		this.postcode = postcode;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getString() {
		return string;
	}

	public void setString(String string) {
		this.string = string;
	}

}
