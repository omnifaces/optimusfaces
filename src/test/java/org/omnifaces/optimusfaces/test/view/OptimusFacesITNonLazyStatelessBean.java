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
package org.omnifaces.optimusfaces.test.view;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.omnifaces.optimusfaces.model.PagedDataModel;
import org.omnifaces.optimusfaces.test.model.Person;
import org.omnifaces.optimusfaces.test.service.PersonService;

@Named
@RequestScoped
public class OptimusFacesITNonLazyStatelessBean {

	private PagedDataModel<Person> nonLazyPersons;

	@Inject
	private PersonService personService;

	@PostConstruct
	public void init() {
		nonLazyPersons = PagedDataModel.nonLazy(personService.list()).build();
	}

	public PagedDataModel<Person> getNonLazyPersons() {
		return nonLazyPersons;
	}

}
