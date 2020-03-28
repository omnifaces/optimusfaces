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

import javax.ejb.Stateless;

import org.omnifaces.optimusfaces.test.model.Phone;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.utils.collection.PartialResultList;

@Stateless
public class PhoneService extends BaseEntityService<Long, Phone> {

	public PartialResultList<Phone> getPageWithOwners(Page page, boolean count) {
		return getPage(page, count, (builder, query, phone) -> {
			phone.fetch("owner");
		});
	}

	public PartialResultList<Phone> getAllWithOwners() {
		return getPageWithOwners(Page.ALL, false);
	}

}