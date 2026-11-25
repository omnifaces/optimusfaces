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

import java.util.regex.Pattern;

import jakarta.faces.context.FacesContext;

import org.omnifaces.optimusfaces.model.LazyPagedDataModel;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.context.PrimeApplicationContext;

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
 * restoring correct postback behavior for those bean scopes. The same applies to the model of a bean of a passivating scope, such as <code>@ViewScoped</code>
 * or <code>@SessionScoped</code>, after deserialization, e.g. after session passivation or replication, which does not retain its loaded page.
 *
 * @see LazyPagedDataModel#preloadPage
 * @author Bauke Scholtz
 * @since 1.0
 */
public class ExtendedDataTable extends DataTable {

    private static final int FIRST_PRIMEFACES_VERSION_WITH_P_CSS_PREFIX = 17;
    private static final Pattern MAJOR_VERSION = Pattern.compile("\\d{1,9}");
    private static final String P_CSS_PREFIX = "p-";
    private static final String UI_CSS_PREFIX = "ui-";

    private static volatile String cssPrefix;

    /**
     * Creates the component; this is done by the Faces runtime via the bundled <code>faces-config.xml</code>.
     */
    public ExtendedDataTable() {
        // NOOP.
    }

    /**
     * Preloads the lazy model's page before PrimeFaces {@link DataTable} runs decode, when the model has no wrapped data yet. This happens on postbacks against
     * request-scoped beans or stateless views whose model is constructed fresh on every request and therefore arrives at decode empty, and for the model of a
     * bean of a passivating scope, such as <code>@ViewScoped</code> or <code>@SessionScoped</code>, after deserialization, which does not retain its loaded
     * page.
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

    /**
     * Returns the prefix of the CSS classes of the PrimeFaces version in use: <code>p-</code> since PrimeFaces 17, and <code>ui-</code> up to PrimeFaces 16.
     * The <code>&lt;op:dataTable&gt;</code> and <code>&lt;op:column&gt;</code> tags render every CSS class they add with this prefix.
     *
     * @return The prefix of the CSS classes of the PrimeFaces version in use.
     * @since 2.0
     */
    public String getCssPrefix() {
        var prefix = cssPrefix;

        if (prefix == null) {
            prefix = resolveCssPrefix(PrimeApplicationContext.getCurrentInstance(getFacesContext()).getEnvironment().getBuildVersion());
            cssPrefix = prefix;
        }

        return prefix;
    }

    /**
     * Returns the prefix of the CSS classes of the given PrimeFaces version.
     *
     * @param primeFacesVersion The PrimeFaces build version, such as <code>16.0.2</code> or <code>17.0.0-SNAPSHOT</code>. May be <code>null</code>.
     * @return <code>p-</code> when the major version is at least 17, else <code>ui-</code>.
     */
    static String resolveCssPrefix(String primeFacesVersion) {
        if (primeFacesVersion != null) {
            var majorVersion = MAJOR_VERSION.matcher(primeFacesVersion);

            if (majorVersion.lookingAt() && Integer.parseInt(majorVersion.group()) >= FIRST_PRIMEFACES_VERSION_WITH_P_CSS_PREFIX) {
                return P_CSS_PREFIX;
            }
        }

        return UI_CSS_PREFIX;
    }

}
