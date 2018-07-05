/*
 * Copyright 2018 OmniFaces
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
package org.omnifaces.optimusfaces.renderer;

import static java.util.Arrays.asList;
import static org.omnifaces.util.FacesLocal.getRequestParameter;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;

import org.primefaces.component.datatable.DataTable;
import org.primefaces.component.datatable.DataTableRenderer;

/**
 * <p>
 * This extended data table renderer is already automatically registered via our <code>faces-config.xml</code>. This
 * will prevent hackers from being able to exceed the <code>rows</code> attribute of the <code>&lt;p:dataTable&gt;</code>
 * which could cause a heavy load on large datasets. PrimeFaces integrates this code as of version 6.3.
 * @see <a href="https://github.com/primefaces/primefaces/issues/3519">PrimeFaces #3519</a>
 */
public class ExtendedDataTableRenderer extends DataTableRenderer {

	@Override
	public void decode(FacesContext context, UIComponent component) {
		String rowsParam = getRequestParameter(context, component.getClientId() + "_rows");

		if (rowsParam != null && !asList(((DataTable) component).getRowsPerPageTemplate().split("[,\\s]+")).contains(rowsParam)) {
			throw new IllegalArgumentException("Unsupported rows per page value: " + rowsParam); // Prevent hackers from being able to exceed this.
		}

		super.decode(context, component);
	}

}
