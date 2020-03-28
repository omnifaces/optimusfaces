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
package org.omnifaces.optimusfaces.model;

import static org.omnifaces.persistence.model.Identifiable.ID;
import static org.omnifaces.util.Components.getCurrentComponent;
import static org.omnifaces.utils.stream.Streams.stream;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.faces.model.SelectItem;
import javax.persistence.ElementCollection;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;

import org.omnifaces.persistence.criteria.Between;
import org.omnifaces.persistence.criteria.Bool;
import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.criteria.Criteria.ParameterBuilder;
import org.omnifaces.persistence.criteria.Enumerated;
import org.omnifaces.persistence.criteria.IgnoreCase;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.criteria.Not;
import org.omnifaces.persistence.criteria.Numeric;
import org.omnifaces.persistence.criteria.Order;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.Identifiable;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.utils.collection.PartialResultList;
import org.omnifaces.utils.reflect.Getter;
import org.primefaces.component.column.Column;
import org.primefaces.component.columntoggler.ColumnToggler;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.event.ToggleEvent;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.Visibility;

/**
 * <p>
 * Paged data model specifically for <code>&lt;op:dataTable&gt;</code> which utilizes {@link BaseEntityService} from
 * OmniPersistence project. The <code>&lt;op:dataTable&gt;</code> basically wraps the powerful PrimeFaces
 * <code>&lt;p:dataTable&gt;</code> in a very DRY tagfile hereby further simplifying its usage and reducing down the
 * sometimes massive boilerplate code when having a bit advanced use case of <code>&lt;p:dataTable&gt;</code> with
 * its {@link LazyDataModel}.
 *
 *
 * <h3 id="usage"><a href="#usage">Basic Usage</a></h3>
 * <p>
 * First create your entity service extending {@link BaseEntityService} from OmniPersistence project.
 * <pre>
 * &#64;Stateless
 * public class YourEntityService extends BaseEntityService&lt;Long, YourEntity&gt; {
 *
 *     // ...
 *
 * }
 * </pre>
 * <p>
 * And make sure <code>YourEntity</code> extends {@link BaseEntity} or one of its subclasses from OmniPersistence project.
 * <pre>
 *
 * &#64;Entity
 * public class YourEntity extends GeneratedIdEntity&lt;Long&gt; {
 *
 *     private Instant created;
 *     private String name;
 *     private Type type;
 *     private boolean deleted;
 *
 *     // ...
 * }
 * </pre>
 * <p>
 * Then create a {@link PagedDataModel} in your backing bean as below.
 * <pre>
 * &#64;Named
 * &#64;ViewScoped
 * public class YourBackingBean implements Serializable {
 *
 *     private PagedDataModel&lt;YourEntity&gt; model;
 *
 *     &#64;Inject
 *     private YourEntityService service;
 *
 *     &#64;PostConstruct
 *     public void init() {
 *         model = PagedDataModel.lazy(service).build();
 *     }
 *
 *     public PagedDataModel&lt;YourEntity&gt; getModel() {
 *         return model;
 *     }
 *
 * }
 * </pre>
 * <p>
 * Finally use <code>&lt;op:dataTable&gt;</code> to have a semi-dynamic lazy-loaded, pageable, sortable and filterable
 * <code>&lt;p:dataTable&gt;</code> without much hassle.
 * <pre>
 * &lt;... xmlns:op="http://omnifaces.org/optimusfaces"&gt;
 *
 * &lt;h:form id="yourEntitiesForm"&gt;
 *     &lt;op:dataTable id="yourEntitiesTable" value="#{yourBackingBean.model}"&gt;
 *         &lt;op:column field="id" /&gt;
 *         &lt;op:column field="created" /&gt;
 *         &lt;op:column field="name" /&gt;
 *         &lt;op:column field="type" /&gt;
 *         &lt;op:column field="deleted" /&gt;
 *     &lt;/op:dataTable&gt;
 * &lt;/h:form&gt;
 * </pre>
 * <p>
 * The <code>field</code> attribute of <code>&lt;op:column&gt;</code> represents the entity property path. This will
 * in turn be used in <code>id</code>, <code>field</code>, <code>headerText</code> and <code>filterBy</code> attributes
 * of <code>&lt;p:column&gt;</code>.
 *
 *
 * <h3 id="relationships"><a href="#relationships">Relationships</a></h3>
 * <p>
 * The <code>&lt;op:dataTable&gt;</code> supports models with {@link OneToOne}, {@link ManyToOne}, {@link OneToMany} and
 * {@link ElementCollection} relationships. The <code>field</code> attribute of <code>&lt;op:column&gt;</code> can take
 * a JavaBean path, like as you would do in EL, <code>parent.child.subchild</code>. Below are some examples.
 *
 * <h4>OneToOne/ManyToOne</h4>
 * <p>
 * Given an <code>Invoice</code> with <code>&#64;OneToOne private Order order</code>, <code>&#64;ManyToOne User seller</code> and <code>&#64;ManyToOne User buyer</code>:
 * <pre>
 * &lt;op:dataTable id="invoicesTable" value="#{shop.invoices}"&gt;
 *     &lt;op:column field="id" /&gt;
 *     &lt;op:column field="seller.name" /&gt;
 *     &lt;op:column field="order.buyer.name" /&gt;
 *     &lt;op:column field="order.totalPrice" /&gt;
 * &lt;/op:dataTable&gt;
 * </pre>
 *
 * <h4>OneToMany</h4>
 * <p>
 * Given a <code>Order</code> with <code>&#64;OneToMany List&lt;Product&gt; products</code>:
 * <pre>
 * &lt;op:dataTable id="ordersTable" value="#{shop.orders}"&gt;
 *     &lt;op:column field="id" /&gt;
 *     &lt;op:column field="buyer.name" /&gt;
 *     &lt;op:column field="totalPrice" /&gt;
 *     &lt;op:column field="products.name" /&gt;
 *     &lt;op:column field="products.price" /&gt;
 * &lt;/op:dataTable&gt;
 * </pre>
 *
 * <h4>ElementCollection</h4>
 * <p>
 * Given a <code>Product</code> with <code>&#64;ElementCollection List&lt;Tag&gt; tags</code>:
 * <pre>
 * &lt;op:dataTable id="productsTable" value="#{shop.products}"&gt;
 *     &lt;op:column field="id" /&gt;
 *     &lt;op:column field="name" /&gt;
 *     &lt;op:column field="price" /&gt;
 *     &lt;op:column field="tags" sortable="false" /&gt;
 * &lt;/op:dataTable&gt;
 * </pre>
 * <p>
 * Note: the <code>&#64;ElementCollection</code> has currently one limitation, sorting is not supported in lazy models
 * due to the task not being trivial in JPQL (for now). It's only supported in non-lazy models.
 *
 * <h4>DTO</h4>
 * <p>
 * DTO subclasses of entities are also supported by providing an additional <code>Class&lt;DTO&gt; resultType</code>
 * argument to one of the protected {@link BaseEntityService#getPage(Page, boolean)} methods.
 * <pre>
 * public class YourEntityDTO extends YourEntity {
 *
 *     private BigDecimal totalPrice;
 *
 *     public YourEntityDTO(Long id, String name, BigDecimal totalPrice) {
 *         setId(id);
 *         setName(name);
 *         this.totalPrice = totalPrice;
 *     }
 *
 *     public BigDecimal getTotalPrice() {
 *         return totalPrice;
 *     }
 *
 * }
 * </pre>
 * <pre>
 * &#64;Stateless
 * public class YourEntityService extends BaseEntityService&lt;YourEntity&gt; {
 *
 *     public void getPageOfYourEntityDTO(Page page, boolean count) {
 *         return getPage(page, count, YourEntityDTO.class (criteriaBuilder, query, root) -&gt; {
 *             Join&lt;YourEntityDTO, YourChildEntity&gt; child = root.join("child");
 *
 *             LinkedHashMap&lt;Getter&lt;YourEntityDTO&gt;, Expression&lt;?&gt;&gt; mapping = new LinkedHashMap&lt;&gt;();
 *             mapping.put(YourEntityDTO::getId, root.get("id"));
 *             mapping.put(YourEntityDTO::getName, root.get("name"));
 *             mapping.put(YourEntityDTO::getTotalPrice, builder.sum(child.get("price")));
 *
 *             return mapping;
 *         });
 *     }
 *
 * }
 * </pre>
 * Note that you must return a {@link LinkedHashMap} with {@link Getter} as key and {@link Expression} as value and
 * that the mapping must be in exactly the same order as constructor arguments of your DTO.
 *
 *
 * <h3 id="criteria-backend"><a href="#criteria-backend">Providing specific criteria in backend</a></h3>
 * <p>
 * In the backend, create a new <code>getPageXxx()</code> method and delegate to one of
 * {@link BaseEntityService#getPage(Page, boolean)} methods which takes a <code>QueryBuilder</code> argument providing the
 * JPA Criteria API objects to build the query with. For example, to get a page of only entities of a specific type.
 * <pre>
 * &#64;Stateless
 * public class YourEntityService extends BaseEntityService&lt;YourEntity&gt; {
 *
 *     public void getPageOfFooType(Page page, boolean count) {
 *         return getPage(page, count, (criteriaBuilder, criteriaQuery, root) -&gt; {
 *             criteriaQuery.where(criteriaBuilder.equals(root.get("type"), Type.FOO));
 *         });
 *     }
 *
 * }
 * </pre>
 * <p>
 * And in the frontend, delegate to {@link PagedDataModel#lazy(PartialResultListLoader)}.
 * <pre>
 * &#64;Named
 * &#64;ViewScoped
 * public class YourBackingBean implements Serializable {
 *
 *     private PagedDataModel&lt;YourEntity&gt; model;
 *
 *     &#64;Inject
 *     private YourEntityService service;
 *
 *     &#64;PostConstruct
 *     public void init() {
 *         model = PagedDataModel.lazy(service::getPageOfFooType).build();
 *     }
 *
 *     public PagedDataModel&lt;YourEntity&gt; getModel() {
 *         return model;
 *     }
 *
 * }
 * </pre>
 *
 *
 * <h3 id="criteria-frontend"><a href="#criteria-frontend">Providing specific criteria in frontend</a></h3>
 * <p>
 * Specify a method reference to a <code>Map&lt;Getter&lt;E&gt;, Object&gt;</code> supplier in {@link Builder#criteria(Supplier)}
 * This way you can provide criteria from e.g. a separate form with custom filters.
 * <pre>
 * &#64;Named
 * &#64;ViewScoped
 * public class YourBackingBean implements Serializable {
 *
 *     private PagedDataModel&lt;YourEntity&gt; model;
 *     private String searchNameStartsWith;
 *     private Instant searchStartDate;
 *     private Type[] searchTypes;
 *     // ...
 *
 *     &#64;Inject
 *     private YourEntityService service;
 *
 *     &#64;PostConstruct
 *     public void init() {
 *         model = PagedDataModel.lazy(service).criteria(this::getCriteria).build();
 *     }
 *
 *     private Map&lt;Getter&lt;YourEntity&gt;, Object&gt; getCriteria() {
 *         Map&lt;Getter&lt;YourEntity&gt;, Object&gt; criteria = new HashMap&lt;&gt;();
 *         criteria.put(YourEntity::getName, Like.startsWith(searchNameStartsWith));
 *         criteria.put(YourEntity::getCreated, Order.greaterThanOrEqualTo(searchStartDate));
 *         criteria.put(YourEntity::getType, searchTypes);
 *         criteria.put(YourEntity::isDeleted, false);
 *         // ...
 *         return criteria;
 *     }
 *
 *     public PagedDataModel&lt;YourEntity&gt; getModel() {
 *         return model;
 *     }
 *
 *     // ...
 * }
 * </pre>
 * <p>
 * You can optionally wrap the value in any {@link Criteria}, such as {@link Like}, {@link Not}, {@link Between},
 * {@link Order}, {@link Enumerated}, {@link Numeric}, {@link Bool} and {@link IgnoreCase}. You can even create your
 * own ones by extending {@link Criteria}. Note that any <code>null</code> value is automatically interpreted as
 * <code>IS NULL</code>. In case you intend to search for <code>IS NOT NULL</code>, use <code>Not(null)</code> criteria.
 * Or in case you'd like to skip <code>IS NULL</code>, then simply don't add a <code>null</code> value to the criteria.
 * <p>
 * Those <code>searchNameStartsWith</code>, <code>searchStartDate</code> and <code>searchTypes</code> in the above
 * example can in turn be supplied via JSF input components in the same form the usual way. For example:
 * <pre>
 * &lt;o:importConstants type="com.example.model.Type" /&gt;
 *
 * &lt;p:selectManyCheckbox value="#{yourBackingBean.selectedTypes}"&gt;
 *     &lt;f:selectItems value="#{Type}" /&gt;
 *     &lt;p:ajax update="yourEntitiesTable" /&gt;
 * &lt;/p:selectManyCheckbox&gt;
 * </pre>
 *
 *
 * <h3 id="non-lazy"><a href="#non-lazy">Non-lazy data model</a></h3>
 * <p>
 * If you have a static list and you'd like to use <code>&lt;op:dataTable&gt;</code>, then you can use
 * either {@link PagedDataModel#nonLazy(List)} to create a non-lazy {@link PagedDataModel}.
 * <pre>
 * &#64;Named
 * &#64;ViewScoped
 * public class YourBackingBean implements Serializable {
 *
 *     private PagedDataModel&lt;YourEntity&gt; model;
 *
 *     &#64;PostConstruct
 *     public void init() {
 *         List&lt;YourEntity&gt; list = createItSomehow();
 *         model = PagedDataModel.nonLazy(list).build();
 *     }
 *
 *     public PagedDataModel&lt;YourEntity&gt; getModel() {
 *         return model;
 *     }
 *
 * }
 * </pre>
 * <p>
 * On contrary to lazy loading, which requires the entities to be of type {@link BaseEntity}, you can here provide
 * entities just of type {@link Identifiable} which is easier to apply on DTOs.
 *
 *
 * <h3 id="presentation"><a href="#presentation">Presentation</a></h3>
 * <p>
 * By default, the <code>field</code> attribute is shown as column header text. You can optionally use <code>head</code>
 * attribute of <code>&lt;op:column&gt;</code> to set the header text.
 * <pre>
 * &lt;op:column field="id" head="ID" /&gt;
 * </pre>
 * <p>
 * You can optionally use <code>tooltip</code> attribute to set the tooltip of the column value.
 * <pre>
 * &lt;op:column field="id" tooltip="The identifier" /&gt;
 * </pre>
 * <p>
 * You can optionally set <code>rendered</code> attribute to <code>false</code> to hide the column in server side.
 * <pre>
 * &lt;op:column ... rendered="false" /&gt;
 * </pre>
 * <p>
 * You can optionally set <code>visible</code> attribute to <code>false</code> to hide the column in client side.
 * <pre>
 * &lt;op:column ... visible="false" /&gt;
 * </pre>
 * <p>
 * The column visibility can be toggled via "Columns" dropdown button when <code>exportable</code> attribute of
 * <code>&lt;op:dataTable&gt;</code> is set to <code>true</code>. The export button provides the options to export only
 * visible columns, or to export all columns including invisible (but not non-rendered) columns.
 * <pre>
 * &lt;op:dataTable ... exportable="true"&gt;
 * </pre>
 * <p>
 * Any field property which is an instance of {@link Iterable} will automatically be wrapped in an <code>&lt;ui:repeat&gt;</code>.
 * You can always explicitly toggle this via <code>iterable</code> attribute.
 * <pre>
 * &lt;op:column ... iterable="true" /&gt;
 * </pre>
 *
 *
 * <h3 id="pagination"><a href="#pagination">Pagination</a></h3>
 * <p>
 * By default, the table is paginable on 10 rows which is overrideable via <code>rows</code> attribute.
 * <pre>
 * &lt;op:dataTable ... rows="20"&gt;
 * </pre>
 * <p>
 * And the table is using the following defaults as <code>&lt;p:dataTable&gt;</code> attributes which are also
 * overrideable by specifying the very same attributes on  <code>&lt;op:dataTable&gt;</code>.
 * <ul>
 * <li><code>rowsPerPage</code>: <code>10,25,50</code>
 * <li><code>paginatorTemplate</code>: <code>{CurrentPageReport} {FirstPageLink} {PreviousPageLink} {PageLinks} {NextPageLink} {LastPageLink}</code>
 * <li><code>currentPageReportTemplate</code>: <code>{startRecord} - {endRecord} of {totalRecords}</code>
 * </ul>
 * <p>
 * Additionally, the <code>&lt;op:dataTable&gt;</code> offers two more specific attributes which can be used to prefix
 * and suffix the paginator report template. They are shown below with their defaults.
 * <ul>
 * <li><code>currentPageReportPrefix</code>: <code>Showing</code>
 * <li><code>currentPageReportSuffix</code>: <code>records</code>
 * </ul>
 *
 *
 * <h3 id="sorting"><a href="#sorting">Sorting</a></h3>
 * <p>
 * By default, the model is sorted by {@link BaseEntity#getId()} in descending order. You can override this by
 * {@link Builder#orderBy(Getter, boolean)} passing the getter method reference and whether you want to sort ascending
 * or not.
 * <pre>
 * &#64;PostConstruct
 * public void init() {
 *     model = PagedDataModel.lazy(service).orderBy(YourEntity::getName, true).build();
 * }
 * </pre>
 * <p>
 * You can specify the <code>orderBy</code> multiple times.
 * <pre>
 * &#64;PostConstruct
 * public void init() {
 *     model = PagedDataModel.lazy(service).orderBy(YourEntity::getType, true).orderBy(YourEntity::getId, false).build();
 * }
 * </pre>
 * <p>
 * When the ID column is nowhere specified in custom ordering, then it will still be supplied as fallback ordering.
 * <p>
 * By default, every column is sortable. You can optionally set <code>sortable</code> attribute of
 * <code>&lt;op:column&gt;</code> to <code>false</code> to make a column non-sortable.
 * <pre>
 * &lt;op:column ... sortable="false" /&gt;
 * </pre>
 * <p>
 * Or if you want to make all columns non-sortable, then set <code>sortable</code> attribute of
 * <code>&lt;op:dataTable&gt;</code> to <code>false</code>.
 * <pre>
 * &lt;op:dataTable ... sortable="false" /&gt;
 * </pre>
 * <p>
 * This is still overrideable on specific columns by explicitly setting <code>sortable</code> attribute of
 * <code>&lt;op:column&gt;</code> to <code>true</code>.
 * <pre>
 * &lt;op:dataTable ... sortable="false" /&gt;
 *     &lt;op:column ... /&gt;
 *     &lt;op:column ... sortable="true" /&gt;
 *     &lt;op:column ... /&gt;
 * &lt;/op:dataTable&gt;
 * </pre>
 * <p>
 * By default, every first sorting action on a column will sort the column ascending. You can optionally set
 * <code>sortDescending</code> attribute of <code>&lt;op:column&gt;</code> to <code>true</code> to start descending.
 * <pre>
 * &lt;op:column ... sortDescending="true" /&gt;
 * </pre>
 *
 *
 * <h3 id="filtering"><a href="#filtering">Filtering</a></h3>
 * <p>
 * By default, every column is filterable. In the frontend you can optionally set <code>filterable</code> attribute of
 * <code>&lt;op:column&gt;</code> to <code>false</code> to make a column non-filterable.
 * <pre>
 * &lt;op:column ... filterable="false" /&gt;
 * </pre>
 * <p>
 * Or if you want to make all columns non-sortable, then set <code>filterable</code> attribute of
 * <code>&lt;op:dataTable&gt;</code> to <code>false</code>.
 * <pre>
 * &lt;op:dataTable ... filterable="false" /&gt;
 * </pre>
 * <p>
 * This is still overrideable on specific columns by explicitly setting <code>filterable</code> attribute of
 * <code>&lt;op:column&gt;</code> to <code>true</code>.
 * <pre>
 * &lt;op:dataTable ... filterable="false" /&gt;
 *     &lt;op:column ... /&gt;
 *     &lt;op:column ... filterable="true" /&gt;
 *     &lt;op:column ... /&gt;
 * &lt;/op:dataTable&gt;
 * </pre>
 * <p>
 * Note that turning off filtering applies client side only. In server side the column is still filterable via
 * externally provided criteria, see "Providing specific criteria" sections above.
 * <p>
 * By default, every column is filterable in "contains" mode. In the frontend you can optionally set
 * <code>filterMode</code> attribute of <code>&lt;op:column&gt;</code> to <code>startsWith</code>, <code>endsWith</code>,
 * <code>contains</code> or <code>exact</code> to set the desired filter mode.
 * <pre>
 * &lt;op:column ... filterMode="startsWith" /&gt;
 * </pre>
 * <p>
 * By default, the filter input is represented by a free text input field. In the frontend you can optionally provide a
 * fixed set of filter options via <code>filterOptions</code> attribute of <code>&lt;op:column&gt;</code>. This will be
 * presented as a dropdown. Supported types are <code>Object[]</code>, <code>Collection&lt;V&gt;</code> and
 * <code>Map&lt;V, L&gt;</code>.
 * <pre>
 * &lt;o:importConstants type="com.example.model.Type" /&gt;
 * ...
 * &lt;op:column field="type" filterOptions="#{Type}" /&gt;
 * </pre>
 * <p>
 * Note that this will change the default value of <code>filterMode</code> from "contains" to "exact". You can still
 * override this by explicitly specifying the <code>filterMode</code> attribute.
 * <pre>
 * &lt;op:column field="type" filterOptions="#{Type}" filterMode="contains" /&gt;
 * </pre>
 *
 *
 * <h3 id="global-search"><a href="#global-search">Global search</a></h3>
 * <p>
 * You can optionally turn on "global search" by setting <code>searchable</code> attribute of
 * <code>&lt;op:dataTable&gt;</code> to <code>true</code>.
 * <pre>
 * &lt;op:dataTable ... searchable="true"&gt;
 * </pre>
 * <p>
 * This will perform a "contains" search in every column having the <code>field</code> attribute, including any custom
 * <code>&lt;p:column&gt;</code>. Note that this won't override the values of any column filters, it will just expand
 * the filtering on them.
 * <p>
 * On the contrary to the column filters, the global search field does not run on keyup, but only on enter key or when
 * pressing the search button. This is done on purpose because the global search performs a relatively expensive LIKE
 * query on every single field.
 * <p>
 * The global search field placeholder and button label are customizable with following attributes on
 * <code>&lt;op:dataTable&gt;</code>.
 * <ul>
 * <li><code>searchPlaceholder</code>: <code>Search…</code>
 * <li><code>searchButtonLabel</code>: <code>Search</code>
 * </ul>
 *
 *
 * <h3 id="exporting"><a href="#exporting">Exporting</a></h3>
 * <p>
 * You can optionally show column toggler and CSV export buttons by setting <code>exportable</code> attribute of
 * <code>&lt;op:dataTable&gt;</code> to <code>true</code>.
 * <pre>
 * &lt;op:dataTable ... exportable="true"&gt;
 * </pre>
 * <p>
 * The column toggler allows you to show/hide specific columns in client side and the CSV export button with a split
 * button allows you to export all columns or only the visible columns. The export will take into account the current
 * filtering and sorting state, if any.
 * <p>
 * Below are the available export related attributes and their default values.
 * <ul>
 * <li><code>columnTogglerButtonLabel</code>: <code>Columns</code>
 * <li><code>exportType</code>: <code>csv</code>
 * <li><code>exportButtonLabel</code>: <code>CSV</code>
 * <li><code>exportVisibleColumnsButtonLabel</code>: <code>Visible Columns</code>
 * <li><code>exportAllColumnsButtonLabel</code>: <code>All Columns</code>
 * <li><code>exportFilename</code>: <code>#{id}-#{of:formatDate(now, 'yyyyMMddHHmmss')}</code>
 * </ul>
 * <p>
 * Note: the <code>#{id}</code> of the <code>exportFilename</code> represents the ID of the
 * <code>&lt;op:dataTable&gt;</code>.
 * <p>
 * By default, every column is exportable. In the frontend you can optionally set <code>exportable</code> attribute of
 * <code>&lt;op:column&gt;</code> to <code>false</code> to make a column non-exportable, irrespective of its visibility.
 * <pre>
 * &lt;op:column ... exportable="false" /&gt;
 * </pre>
 *
 *
 * <h3 id="selection"><a href="#selection">Selection</a></h3>
 * <p>
 * You can optionally make the rows selectable by setting <code>selectable</code> attribute of
 * <code>&lt;op:dataTable&gt;</code> to <code>true</code>.
 * <pre>
 * &lt;op:dataTable ... selectable="true"&gt;
 * </pre>
 * <p>
 * The selection is available as a {@link List} by {@link PagedDataModel#getSelection()}. The row select and unselect
 * events will automatically update components matching PrimeFaces Selector <code>@(.updateOnDataTableSelect)</code>.
 * So you could automatically show the selection as below:
 * <pre>
 * &lt;h:form id="yourEntitiesForm"&gt;
 *     &lt;op:dataTable id="yourEntitiesTable" value="#{yourBackingBean.model}" selectable="true"&gt;
 *         &lt;op:column field="id" /&gt;
 *         &lt;op:column field="created" /&gt;
 *         &lt;op:column field="name" /&gt;
 *         &lt;op:column field="type" /&gt;
 *         &lt;op:column field="deleted" /&gt;
 *     &lt;/op:dataTable&gt;
 *     &lt;p:dataTable id="selectionTable" value="#{yourBackingBean.model.selection}" var="item" styleClass="updateOnDataTableSelect"&gt;
 *         &lt;op:column field="id" /&gt;
 *         &lt;op:column field="created" /&gt;
 *         &lt;op:column field="name" /&gt;
 *         &lt;op:column field="type" /&gt;
 *         &lt;op:column field="deleted" /&gt;
 *     &lt;/p:dataTable&gt;
 * &lt;/h:form&gt;
 * </pre>
 * <p>
 * Note that you can't show the selection in a <code>&lt;op:dataTable&gt;</code> as the selection returns a {@link List}
 * not a {@link PagedDataModel}. You can however keep using <code>&lt;op:column&gt;</code> the usual way as long as you
 * use <code>var="item"</code> as shown above.
 * <p>
 * You can obtain the current selection in backing bean as below:
 * <pre>
 * List&lt;YourEntity&gt; selection = model.getSelection();
 * </pre>
 * <p>
 * Alternatively, you can obtain all records matching the current filtering and ordering as below:
 * <pre>
 * List&lt;YourEntity&gt; filtered = yourEntityService.getPage(model.getPage().all(), false);
 * </pre>
 *
 *
 * <h3 id="ajax-events"><a href="#ajax-events">Ajax events</a></h3>
 * <p>
 * On every paging, sorting, filtering, searching and selection action, an ajax event will be fired. The
 * <code>&lt;op:dataTable&gt;</code> makes use of PrimeFaces Selectors (PFS) to find components which need to be updated
 * during those events. Below is an overview of all PFS classes recognized by <code>&lt;op:dataTable&gt;</code>.
 * <ul>
 * <li><code>updateOnDataTablePage</code>: any JSF component with this style class will be updated on paging
 * <li><code>updateOnDataTableSort</code>: any JSF component with this style class will be updated on sorting
 * <li><code>updateOnDataTableFilter</code>: any JSF component with this style class will be updated on filtering/searching
 * <li><code>updateOnDataTableSelect</code>: any JSF component with this style class will be updated on selection
 * </ul>
 *
 *
 * <h3 id="query-parameters"><a href="#query-parameters">Query parameters</a></h3>
 * <p>
 * On every paging, sorting, filtering, searching and selection action the query parameter string in the URL will be
 * updated to reflect the current table's state. Every page after the first page gets a <code>p={pageNumber}</code>
 * parameter where <code>{pageNumber}</code> represents the current page number. Every sorting action other than the
 * default/initial sorting gets a <code>o={field}</code> parameter where <code>{field}</code> represents the field name.
 * If the sorting is descending, then the <code>{field}</code> will be prefixed with a <code>-</code> (a hyphen). Every
 * filtering action gets a <code>{field}={value}</code> parameter where <code>{value}</code> represents the filter value.
 * Every global search action gets a <code>q={value}</code> parameter. Every selection action gets a <code>s={id}</code>
 * parameter where <code>{id}</code> represents the entity ID.
 * <p>
 * You can optionally disable this behavior altogether by setting <code>updateQueryString</code> attribute of
 * <code>&lt;op:dataTable&gt;</code> to <code>false</code>.
 * <pre>
 * &lt;op:dataTable ... updateQueryString="false"&gt;
 * </pre>
 * <p>
 * In case you have multiple tables in same page (poor UI, but that aside), then you can optionally prefix the
 * query parameter name with a table-specific prefix via the <code>queryParameterPrefix</code> attribute, so that they
 * don't clash each other.
 * <pre>
 * &lt;op:dataTable ... queryParameterPrefix="t1"&gt;
 *     ...
 * &lt;/op:dataTable&gt;
 * &lt;op:dataTable ... queryParameterPrefix="t2"&gt;
 *     ...
 * &lt;/op:dataTable&gt;
 * </pre>
 *
 *
 * <h3 id="css"><a href="#css">CSS</a></h3>
 * <p>
 * Standard PrimeFaces CSS is being reused as much as possible, including the fix of missing <code>.ui-state-active</code>
 * class on a sorted column when sorting is done via <code>field</code> attribute. Below is a list of new additions:
 * <ul>
 * <li><code>.ui-datatable-actions</code>: the div holding the global search field and export buttons
 * <li><code>.ui-datatable-actions .ui-datatable-search</code>: the span holding the global search field
 * <li><code>.ui-datatable-actions .ui-datatable-export</code>: the span holding the export buttons
 * <li><code>.ui-datatable-actions .ui-inputfield.filter</code>: the global search input field
 * <li><code>.ui-datatable-actions .ui-button.search</code>: the global search button
 * <li><code>.ui-datatable-actions .ui-button.toggle</code>: the column toggler button
 * <li><code>.ui-datatable-actions .ui-splitbutton.export</code>: the export split button
 * <li><code>.ui-datatable-actions .ui-splitbutton.export .ui-button.ui-button-text-only</code>: the export action button
 * <li><code>.ui-datatable-actions .ui-splitbutton.export .ui-button.ui-splitbutton-menubutton</code>: the export menu button
 * </ul>
 * <p>
 * Further, the <code>&lt;op:dataTable&gt;</code> adds three new custom classes to the table and the column:
 * <ul>
 * <li><code>.ui-datatable.empty</code>: when the data table is empty
 * <li><code>.ui-datatable .ui-sortable-column.desc</code>: when <code>sortDescending=true</code>
 * <li><code>.ui-datatable .ui-filter-column.global</code>: when global search input field is focused (so you can e.g. highlight background)
 * </ul>
 * <p>
 * Finally, the <code>&lt;op:column&gt;</code> puts the entire cell content in a <code>&lt;span&gt;</code> which also
 * holds the tooltip. This allows more flexible CSS control of "entire cell content" via just
 * <code>.ui-datatable tbody td &gt; span</code>.
 *
 *
 * <h3 id="adding-buttons"><a href="#adding-buttons">Adding custom action buttons</a></h3>
 * <p>
 * When you want more buttons in the <code>.ui-datatable-actions</code> div, then you can use <code>&lt;ui:define name="actions"&gt;</code>
 * for this.
 * <pre>
 * &lt;op:dataTable ...&gt;
 *     &lt;ui:define name="actions"&gt;
 *         &lt;p:commandButton ... /&gt;
 *     &lt;/ui:define&gt;
 *     ...
 * &lt;/op:dataTable&gt;
 * </pre>
 * <p>
 * They will end up after the search and export buttons.
 *
 *
 * <h3 id="setting-attributes"><a href="#setting-attributes">Setting PrimeFaces-specific attributes</a></h3>
 * <p>
 * In case you'd like to finetune the underlying <code>&lt;p:dataTable&gt;</code> further with additional attributes
 * which are in turn not supported by <code>&lt;op:dataTable&gt;</code>, then you could always use
 * <code>&lt;f:attribute&gt;</code> for that.
 * <pre>
 * &lt;op:dataTable ...&gt;
 *     &lt;f:attribute name="caseSensitiveSort" value="#{true}" /&gt;
 *     &lt;f:attribute name="reflow" value="#{true}" /&gt;
 *     ...
 * &lt;/op:dataTable&gt;
 * </pre>
 * <p>
 * Note that you can also just nest any <code>&lt;p:ajax&gt;</code> and even a plain <code>&lt;p:column&gt;</code> the
 * usual way.
 * <pre>
 * &lt;op:dataTable ...&gt;
 *     &lt;p:ajax event="page" ... /&gt;
 *     ...
 *     &lt;p:column&gt;&lt;p:commandLink value="Delete" ... /&gt;&lt;/p:column&gt;
 * &lt;/op:dataTable&gt;
 * </pre>
 *
 *
 * <h3 id="extending-tagfiles"><a href="#extending-tagfiles">Extending tagfiles</a></h3>
 * <p>
 * In case you'd like to change the defaults of <code>&lt;op:dataTable&gt;</code>, then you can always extend it into
 * your own tagfile like below with desired defaults supplied via <code>&lt;ui:param&gt;</code>. The below example
 * extends it to always turn on global search and turn off column filtering.
 * <pre>
 * &lt;ui:composition template="/optimusfaces/tags/dataTable.xhtml" xmlns:ui="http://xmlns.jcp.org/jsf/facelets"&gt;
 *     &lt;!-- Override default attribute values of op:dataTable. --&gt;
 *     &lt;ui:param name="searchable" value="true" /&gt;
 *     &lt;ui:param name="filterable" value="false" /&gt;
 * &lt;/ui:composition&gt;
 * </pre>
 * <p>
 * The below example shows elaborately how you could add a new <code>type</code> attribute to the
 * <code>&lt;op:column&gt;</code> which allows fine grained control over default formatting of cell content.
 * <pre>
 * &lt;ui:composition template="/optimusfaces/tags/column.xhtml"
 *     xmlns="http://www.w3.org/1999/xhtml"
 *     xmlns:f="http://xmlns.jcp.org/jsf/core"
 *     xmlns:h="http://xmlns.jcp.org/jsf/html"
 *     xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
 *     xmlns:a="http://xmlns.jcp.org/jsf/passthrough"
 *     xmlns:c="http://xmlns.jcp.org/jsp/jstl/core"
 *     xmlns:o="http://omnifaces.org/ui"
 *     xmlns:of="http://omnifaces.org/functions"
 *     xmlns:p="http://primefaces.org/ui"
 * &gt;
 *     &lt;!-- New custom attributes. --&gt;
 *     &lt;ui:param name="type" value="#{empty type ? 'text' : type}" /&gt; &lt;!-- Value MAY NOT be an EL expression referencing #{item}! --&gt;
 *     &lt;ui:param name="emptyValue" value="#{empty emptyValue ? 'n/a' : emptyValue}" /&gt;
 *
 *     &lt;!-- Override default attribute values. --&gt;
 *     &lt;ui:param name="head" value="#{empty head ? i18n['general.' += field] : head}" /&gt; &lt;!-- #{i18n} refers to the resource bundle. --&gt;
 *     &lt;ui:param name="styleClass" value="#{type}" /&gt;
 *
 *     &lt;ui:define name="cell"&gt;
 *         &lt;c:choose&gt;
 *             &lt;c:when test="#{type eq 'date'}"&gt;#{empty value ? emptyValue : of:formatDate(value, 'dd-MM-yyyy')}&lt;/c:when&gt;
 *             &lt;c:when test="#{type eq 'timestamp'}"&gt;#{empty value ? emptyValue : of:formatDate(value, 'dd-MM-yyyy HH:mm:ss')}&lt;/c:when&gt;
 *             &lt;c:when test="#{type eq 'currency'}"&gt;#{empty value ? emptyValue : of:formatCurrency(value, '$')}&lt;/c:when&gt;
 *             &lt;c:when test="#{type eq 'percent'}"&gt;#{empty value ? emptyValue : of:formatPercent(value)}&lt;/c:when&gt;
 *             &lt;c:when test="#{type eq 'boolean'}"&gt;#{value ? 'Y' : 'N'}&lt;/c:when&gt;
 *             &lt;c:when test="#{type eq 'enum'}"&gt;#{empty value ? emptyValue : i18n[value['class'].simpleName += '.' += value]}&lt;/c:when&gt;
 *             &lt;c:when test="#{type eq 'custom'}"&gt;&lt;ui:insert /&gt;&lt;/c:when&gt;
 *
 *             &lt;!-- Add more types here! --&gt;
 *
 *             &lt;c:otherwise&gt;#{of:coalesce(value, emptyValue)}&lt;/c:otherwise&gt;
 *         &lt;/c:choose&gt;
 *     &lt;/ui:define&gt;
 * &lt;/ui:composition&gt;
 * </pre>
 * <p>
 * With both tagfiles in place, you could use them like below:
 * <pre>
 * &lt;x:dataTable id="yourEntitiesTable" value="#{yourBackingBean.model}"&gt;
 *     &lt;x:column field="id" type="custom"&gt;&lt;a href="edit/#{item.id}" title="Edit this item"&gt;#{item.id}&lt;/a&gt;&lt;/x:column&gt;
 *     &lt;x:column field="created" type="date" /&gt;
 *     &lt;x:column field="name" type="text" /&gt;
 *     &lt;x:column field="type" type="enum" /&gt;
 *     &lt;x:column field="deleted" type="boolean" /&gt;
 * &lt;/x:dataTable&gt;
 * </pre>
 * <p>
 * Note that the name of the EL variable representing the current item, <code>#{item}</code>, is predefined and cannot
 * be changed.
 * <p>
 * Also note that the <code>type</code> attribute is in above example set as a style class, so you could for example
 * define a CSS rule to always right-align the "number" and "currency" columns.
 * <pre>
 * .ui-datatable th.number, .ui-datatable th.currency {
 *     text-align: right;
 * }
 * </pre>
 *
 *
 * @author Bauke Scholtz
 * @see BaseEntityService
 * @see Page
 * @see Criteria
 */
public interface PagedDataModel<E extends Identifiable<?>> extends Serializable {

	// Constants ------------------------------------------------------------------------------------------------------

	/** The query parameter name representing the value of the global search query. */
	public static final String QUERY_PARAMETER_SEARCH = "q";

	/** The query parameter name representing the current page number. */
	public static final String QUERY_PARAMETER_PAGE = "p";

	/** The query parameter name representing the current sort order. */
	public static final String QUERY_PARAMETER_ORDER = "o";

	/** The query parameter name representing the current selection. */
	public static final String QUERY_PARAMETER_SELECTION = "s";

	// Note that those names are intentionally kept single-char in order to not potentially clash with field names.


	// Default methods ------------------------------------------------------------------------------------------------

	/**
	 * Invoked when default <code>id</code> attribute of <code>&lt;op:column&gt;</code> is to be set. This is by default based on the
	 * <code>field</code> and the ID attribute does not support periods.
	 * @param field The column field.
	 * @return The column ID based on given field.
	 */
	default String computeColumnId(String field) {
		return field.replace('.', '_');
	}

	/**
	 * Invoked when <code>filterOptions</code> attribute of <code>&lt;op:column&gt;</code> is provided.
	 * Problem is, the underlying <code>&lt;p:column&gt;</code> only supports <code>SelectItem[]</code> or
	 * <code>List&lt;SelectItem&gt;</code>.
	 * @param filterOptions The filter options.
	 * @return The filter options converted to <code>SelectItem[]</code>.
	 */
	default SelectItem[] convertFilterOptionsIfNecessary(Object filterOptions) {
		Stream<SelectItem> stream;

		if (filterOptions instanceof SelectItem[]) {
			stream = stream(filterOptions);
		}
		if (filterOptions instanceof Object[]) {
			stream = stream(filterOptions).map(SelectItem::new);
		}
		else if (filterOptions instanceof Collection<?>) {
			stream = stream(filterOptions).map(item -> (item instanceof SelectItem) ? (SelectItem) item : new SelectItem(item));
		}
		else if (filterOptions instanceof Map<?, ?>) {
			stream = stream((Map<?, ?>) filterOptions).map(entry -> new SelectItem(entry.getKey(), String.valueOf(entry.getValue())));
		}
		else {
			throw new IllegalArgumentException("filterOptions must be an instance of SelectItem[], Object[], Collection or Map");
		}

		return Stream.concat(Stream.of(new SelectItem("")), stream).toArray(SelectItem[]::new);
	}

	/**
	 * Invoked when "Columns" is adjusted.
	 * @param event Toggle event.
	 */
	default void toggleColumn(ToggleEvent event) {
		String tableId = ((ColumnToggler) event.getComponent()).getDatasource();
		DataTable table = (DataTable) getCurrentComponent().findComponent(tableId);
		((Column) table.getColumns().get((Integer) event.getData())).setVisible(event.getVisibility() == Visibility.VISIBLE);
	}

	/**
	 * Invoked when "Export Visible Columns" is chosen.
	 * @param tableId Table ID.
	 */
	default void prepareExportVisible(String tableId) {
		DataTable table = (DataTable) getCurrentComponent().findComponent(tableId);
		table.getColumns().forEach(column -> setExportable((Column) column, column.isVisible()));
	}

	/**
	 * Invoked when "Export All Columns" is chosen.
	 * @param tableId Table ID.
	 */
	default void prepareExportAll(String tableId) {
		DataTable table = (DataTable) getCurrentComponent().findComponent(tableId);
		table.getColumns().forEach(column -> setExportable((Column) column, true));
	}

	/**
	 * Remembers original value of "exportable" attribute in case it's been explicitly set.
	 * @param column The column.
	 * @param exportable Whether it should be set exportable if not already explicitly disabled.
	 */
	static void setExportable(Column column, boolean exportable) {
		boolean wasExportable = column.getAttributes().putIfAbsent("wasExportable", column.isExportable()) == Boolean.TRUE || column.isExportable();

		if (wasExportable) {
			column.setExportable(exportable);
		}
	}


	// PagedDataModel state -------------------------------------------------------------------------------------------

	/**
	 * Returns the current Page.
	 * @return The current Page.
	 */
	Page getPage();


	// op:dataTable properties ----------------------------------------------------------------------------------------

	Entry<String, Boolean> getOrdering();
	Map<String, Object> getFilters();

	List<E> getFilteredValue();
	void setFilteredValue(List<E> filteredValue);

	List<E> getSelection();
	void setSelection(List<E> selection);


	// Builder --------------------------------------------------------------------------------------------------------

	@FunctionalInterface
	public static interface PartialResultListLoader<E extends Identifiable<?>> {
		PartialResultList<E> getPage(Page page, boolean estimateTotalNumberOfResults);
	}

	/**
	 * Use this if you want to build a lazy paged data model using a {@link BaseEntityService}.
	 * @param <I> The generic ID type.
	 * @param <E> The generic base entity type.
	 * @param entityService The entity service.
	 * @return A new paged data model builder.
	 */
	public static <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> Builder<E> lazy(BaseEntityService<I, E> entityService) {
		return new Builder<>(entityService::getPage);
	}

	/**
	 * Use this if you want to build a lazy paged data model using a custom
	 * {@link BaseEntityService#getPage(Page, boolean)} implementation.
	 * @param <E> The generic base entity type.
	 * @param loader The custom {@link BaseEntityService#getPage(Page, boolean)} implementation.
	 * @return A new paged data model builder.
	 */
	public static <E extends Identifiable<?>> Builder<E> lazy(PartialResultListLoader<E> loader) {
		return new Builder<>(loader);
	}

	/**
	 * Use this if you want to build a non-lazy paged data model based on given list.
	 * @param <E> The generic base entity type.
	 * @param allData List of all data.
	 * @return A new paged data model builder.
	 */
	public static <E extends Identifiable<?>> Builder<E> nonLazy(List<E> allData) {
		return new Builder<>(allData);
	}

	/**
	 * The paged data model builder.
	 *
	 * @param <E> The generic base entity type.
	 * @author Bauke Scholtz
	 */
	public static class Builder<E extends Identifiable<?>> {

		private List<E> allData;
		private PartialResultListLoader<E> loader;

		private LinkedHashMap<String, Boolean> ordering = new LinkedHashMap<>(2);
		private Supplier<Map<Getter<E>, Object>> criteria;

		private Builder(List<E> allData) {
			this.allData = allData;
		}

		private Builder(PartialResultListLoader<E> loader) {
			this.loader = loader;
		}

		/**
		 * <p>
		 * Set the criteria supplier.
		 * <p>
		 * You can optionally wrap the value in any {@link Criteria}, such as {@link Like}, {@link Not}, {@link Between}, {@link Order},
		 * {@link Enumerated}, {@link Numeric}, {@link Bool} and {@link IgnoreCase}.
		 * <p>
		 * At least, the following values are automatically supported, in this scanning order where <code>type</code> is the field type:
		 * <ul>
		 * <li>value = <code>null</code>, this will create IS NULL predicate.
		 * <li>value = {@link Criteria}, this will delegate to {@link Criteria#build(Expression, CriteriaBuilder, ParameterBuilder)}.
		 * <li>type = {@link ElementCollection}, this will treat given value as enumerated and create an IN predicate.
		 * <li>value = {@link Iterable} or {@link Array}, this will recursively create an OR disjunction of multiple predicates.
		 * <li>value = {@link BaseEntity}, this will create an EQUAL predicate on entity ID.
		 * <li>type = {@link Enum}, this will delegate to {@link Enumerated#build(Expression, CriteriaBuilder, ParameterBuilder)}.
		 * <li>type = {@link Number}, this will delegate to {@link Numeric#build(Expression, CriteriaBuilder, ParameterBuilder)}.
		 * <li>type = {@link Boolean}, this will delegate to {@link Bool#build(Expression, CriteriaBuilder, ParameterBuilder)}.
		 * <li>type = {@link String}, this will delegate to {@link IgnoreCase#build(Expression, CriteriaBuilder, ParameterBuilder)}.
		 * <li>value = {@link String}, this will delegate to {@link IgnoreCase#build(Expression, CriteriaBuilder, ParameterBuilder)}.
		 * </ul>
		 * If you want to support a new kind of criteria, just create a custom {@link Criteria} and supply this as criteria value.
		 * Its {@link Criteria#build(Expression, CriteriaBuilder, ParameterBuilder)} will then be invoked.
		 * <p>
		 * Note that any <code>null</code> value is automatically interpreted as <code>IS NULL</code>. In case you
		 * intend to search for <code>IS NOT NULL</code>, use <code>Not(null)</code> criteria. Or in case you'd like
		 * to skip <code>IS NULL</code>, then simply don't add a <code>null</code> value to the criteria.
		 * <p>
		 * The criteria supplier can be set only once in this builder.
		 *
		 * @param criteria The criteria supplier.
		 * @return This builder.
		 * @throws IllegalStateException When another criteria supplier is already set in this builder.
		 * @see Criteria
		 */
		public Builder<E> criteria(Supplier<Map<Getter<E>, Object>> criteria) {
			if (this.criteria != null) {
				throw new IllegalStateException("Criteria supplier is already set");
			}

			this.criteria = criteria;
			return this;
		}

		/**
		 * <p>
		 * Set the ordering.
		 * <p>
		 * This can be invoked multiple times and will be remembered in same order.
		 * The default ordering is <code>BaseEntity::getId, false</code>.
		 *
		 * @param sortField The sort field getter.
		 * @param sortAscending Whether to sort ascending.
		 * @return This builder.
		 */
		public Builder<E> orderBy(Getter<E> sortField, boolean sortAscending) {
			ordering.put(sortField.getPropertyName(), sortAscending);
			return this;
		}

		/**
		 * <p>
		 * Build the paged data model.
		 *
		 * @return The built paged data model.
		 */
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public PagedDataModel<E> build() {
			ordering.putIfAbsent(ID, false);
			Supplier rawCriteria = criteria;

			if (loader != null) {
				return new LazyPagedDataModel<>(loader, ordering, rawCriteria);
			}
			else if (allData != null) {
				return new NonLazyPagedDataModel<>(allData, ordering, rawCriteria);
			}
			else {
				throw new IllegalStateException("You must provide non-null loader or allData.");
			}
		}
	}

}