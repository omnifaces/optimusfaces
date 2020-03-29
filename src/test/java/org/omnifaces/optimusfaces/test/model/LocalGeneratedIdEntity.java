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

import static javax.persistence.GenerationType.IDENTITY;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.GeneratedIdEntity;

/**
 * This is needed by OpenJPA because it doesn't recognize a parameterized ID in a MappedSuperClass in a JAR.
 * OpenJPA 2.4.2 will fail as below:
 * <pre>
 * WARN openjpa.Runtime - Fields "org.omnifaces.persistence.model.GeneratedIdEntity.id" are not a default persistent type,
 * and do not have any annotations indicating their persistence strategy. They will be treated as non-persistent.
 * </pre>
 * And OpenJPA 2.4.3 will fail as below:
 * <pre>
 * org.apache.openjpa.persistence.ArgumentException: Type "class org.omnifaces.persistence.model.GeneratedIdEntity"
 * declares field "id" as a primary key, but keys of type "java.lang.Comparable" are not supported.
 * </pre>
 * This is <strong>NOT</strong> needed for Hibernate and EclipseLink. You can just extend from {@link GeneratedIdEntity} directly.
 */
@MappedSuperclass
public class LocalGeneratedIdEntity extends BaseEntity<Long> {

	private static final long serialVersionUID = 1L;

	@Id @GeneratedValue(strategy = IDENTITY)
	private Long id;

	@Override
	public Long getId() {
		return id;
	}

	@Override
	public void setId(Long id) {
		this.id = id;
	}
}
