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
package org.omnifaces.optimusfaces.component;

import jakarta.faces.context.FacesContext;
import jakarta.faces.model.DataModel;

import org.omnifaces.optimusfaces.model.LazyPagedDataModel;
import org.primefaces.component.datatable.DataTable;

/**
 * <p>
 * This extended data table is already automatically registered via our <code>faces-config.xml</code>.
 * This will preload lazy loaded model for decode against a request scoped bean or a stateless view.
 */
public class ExtendedDataTable extends DataTable {

	@Override
	protected void preDecode(FacesContext context) {
        if (context.isPostback() && isLazy()) {
        	DataModel<?> model = getDataModel();

        	if (model instanceof LazyPagedDataModel && model.getWrappedData() == null) {
       			((LazyPagedDataModel<?>) model).preloadPage(context, this);
        	}
        }

		super.preDecode(context);
	}

}
