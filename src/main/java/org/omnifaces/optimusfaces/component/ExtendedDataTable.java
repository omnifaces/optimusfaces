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

import org.omnifaces.optimusfaces.model.LazyPagedDataModel;
import org.primefaces.component.datatable.DataTable;

/**
 * <p>
 * Infrastructure extension of the PrimeFaces {@link DataTable} component, registered as the default <code>&lt;p:dataTable&gt;</code> renderer via the bundled
 * <code>faces-config.xml</code>. Users do not need reference or configure this class directly.
 *
 * <h2>Why this extension is necessary</h2>
 * <p>
 * PrimeFaces {@link DataTable} calls the <code>decode</code> lifecycle phase on the data table <em>before</em> invoking the lazy model's
 * {@link LazyPagedDataModel#load} method. For <code>@ViewScoped</code> backing beans this is not a problem because the bean instance, and with it the model and
 * its wrapped data, is kept alive in the view map between requests, tracked by identifiers in the Faces view state. However, for <strong>request-scoped
 * beans</strong> and <strong>stateless views</strong> (<code>&lt;f:view transient="true"&gt;</code>), which disable Faces state saving and therefore inherently
 * break <code>@ViewScoped</code>, the model is brand new on every postback and its wrapped data is <code>null</code> at the point decode runs, causing
 * selection, pagination and other postback actions to silently fail.
 * <p>
 * {@link #preDecode} detects this situation and calls {@link LazyPagedDataModel#preloadPage} to populate the model from the data store before decode proceeds,
 * restoring correct postback behaviour for those bean scopes.
 *
 * @see LazyPagedDataModel#preloadPage
 * @author Bauke Scholtz
 * @since 1.0
 */
public class ExtendedDataTable extends DataTable {

    /**
     * Preloads the lazy model's first page before PrimeFaces {@link DataTable} runs decode, when the model has no wrapped data yet. This happens on postbacks
     * against request-scoped beans or stateless views whose model is constructed fresh on every request and therefore arrives at decode empty.
     * 
     * @param context The current {@link FacesContext}.
     */
    @Override
    protected void preDecode(FacesContext context) {
        if (context.isPostback() && isLazy()) {
            var model = getDataModel();

            if (model instanceof LazyPagedDataModel lazyModel && lazyModel.getWrappedData() == null) {
                lazyModel.preloadPage(context, this);
            }
        }

        super.preDecode(context);
    }

}
