/*
 * Copyright 2016 OmniFaces
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
import static org.primefaces.model.SortOrder.DESCENDING;

import java.util.List;

import org.omnifaces.persistence.model.dto.SortFilterPage;

/**
 * <p>
 * Non lazy paged data model so &lt;op:dataTable&gt; can also be used on non-lazy data models.
 *
 * <h3>Usage:</h3>
 * <p>
 * Here's how you can utilize it:
 * <pre>
 * {@code @Named}
 * {@code @RequestScoped}
 * public class YourBackingBean {
 *
 *     private org.omnifaces.optimusfaces.model.PagedDataModel&lt;YourEntity&gt; model;
 *
 *     {@code @Inject}
 *     private YourEntityService yourEntityService;
 *
 *     {@code @PostConstruct}
 *     public void init() {
 *         model = new NonLazyPagedDataModel&lt;YourEntity&gt;(yourEntityService.getList());
 *     }
 *
 *     public PagedDataModel&lt;YourEntity&gt; getModel() {
 *         return model;
 *     }
 *
 * }
 * </pre>
 *
 * <pre>
 * &lt;op:dataTable id="yourEntitiesTable" value="#{bean.model}"&gt;
 *     ...
 * &lt;/op:dataTable&gt;
 * </pre>
 *
 * @author Bauke Scholtz
 */
public class NonLazyPagedDataModel<T> extends PagedDataModel<T> {

	private static final long serialVersionUID = 1L;

	private List<T> allData;

	public NonLazyPagedDataModel(List<T> allData) {
		super("id", DESCENDING);
		this.allData = allData;
	}

	@Override
	public List<T> load(SortFilterPage page, boolean countNeedsUpdate) {
		setRowCount(allData.size());
		return allData.subList(min(getRowCount(), page.getOffset()), min(getRowCount(), page.getOffset() + page.getLimit()));
	}

}