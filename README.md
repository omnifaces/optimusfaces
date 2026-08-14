[![Maven](https://img.shields.io/maven-metadata/v/https/repo.maven.apache.org/maven2/org/omnifaces/optimusfaces/maven-metadata.xml.svg)](https://repo.maven.apache.org/maven2/org/omnifaces/optimusfaces/)
[![Javadoc](https://javadoc.io/badge/org.omnifaces/optimusfaces.svg)](https://javadoc.io/doc/org.omnifaces/optimusfaces) 
[![Tests](https://github.com/omnifaces/optimusfaces/actions/workflows/maven.yml/badge.svg)](https://github.com/omnifaces/optimusfaces/actions)
[![License](https://img.shields.io/:license-apache-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

# OptimusFaces

Utility library for OmniFaces + PrimeFaces combined.


This project basically combines best of [OmniFaces](https://omnifaces.org/) and [PrimeFaces](https://primefaces.org/) with help of [OmniPersistence](https://github.com/omnifaces/omnipersistence), an utility library for Jakarta Persistence (JPA). This project should make it a breeze to create semi-dynamic lazy-loaded, searchable, sortable and filterable `<p:dataTable>` based on a Jakarta Persistence model and a generic entity service.


### Installation

`pom.xml`

```XML
<dependencies>
    <!-- Target Jakarta EE server. -->
    <dependency>
        <groupId>jakarta.platform</groupId>
        <artifactId>jakarta.jakartaee-web-api</artifactId>
        <version>10.0.0</version><!-- Minimum supported version is 10.0.0 -->
        <scope>provided</scope>
    </dependency>

    <!-- Runtime dependencies. -->
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>omnifaces</artifactId>
        <version>4.0</version><!-- Minimum supported version is 4.0 -->
    </dependency>
    <dependency>
        <groupId>org.primefaces</groupId>
        <artifactId>primefaces</artifactId>
        <classifier>jakarta</classifier>
        <version>15.0.0</version><!-- Minimum supported version is 15.0.0 -->
    </dependency>
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>optimusfaces</artifactId>
        <version>1.1</version>
    </dependency>
</dependencies>
```

**Minimum supported Java / OmniFaces / PrimeFaces versions**

Java EE (javax) namespace:
- 0.1 - 0.12: Java 8 / OmniFaces 2.2 / PrimeFaces 5.2
- 0.13 - 0.15: Java 8 / OmniFaces 2.2 / PrimeFaces 7.0
- 0.16: Java 8 / OmniFaces 3.0 / PrimeFaces 10.0.0

Jakarta EE (jakarta) namespace:
- 0.14.J1 - 0.16.J1: Java 11 / OmniFaces 4.0 / PrimeFaces 10.0.0:jakarta
- 0.17.J1 - 0.20.J1: Java 17 / OmniFaces 4.0 / PrimeFaces 13.0.0:jakarta
- 0.21.J1 - 0.22.J1: Java 17 / OmniFaces 4.0 / PrimeFaces 15.0.0:jakarta
- 1.0: Java 17 / OmniFaces 4.0 / PrimeFaces 15.0.0:jakarta


### Basic Usage

First create your entity service extending [`org.omnifaces.omnipersistence.service.BaseEntityService`](https://javadoc.io/doc/org.omnifaces/omnipersistence/latest/org/omnifaces/persistence/service/BaseEntityService.html). You don't necessarily need to add new methods, just extending it is sufficient. It's useful for other generic things too.

```Java
@Stateless
public class YourEntityService extends BaseEntityService<Long, YourEntity> {

   // ...

}
```

And make sure `YourEntity` extends [`org.omnifaces.omnipersistence.model.BaseEntity`](https://javadoc.io/doc/org.omnifaces/omnipersistence/latest/org/omnifaces/persistence/model/BaseEntity.html) or one of its subclasses `GeneratedIdEntity`, `TimestampedEntity`, `TimestampedBaseEntity`, `VersionedEntity` or `VersionedBaseEntity`.

```Java
@Entity
public class YourEntity extends BaseEntity<Long> {

    @Id @GeneratedValue(strategy=IDENTITY)
    private Long id;
    private Instant created;
    private String name;
    private Type type;
    private boolean deleted;

    // ...
}
```

Then create a [`org.omnifaces.optimusfaces.model.PagedDataModel`](https://javadoc.io/doc/org.omnifaces/optimusfaces/latest/org/omnifaces/optimusfaces/model/PagedDataModel.html) in your backing bean as below. Noted should be that `@ViewScoped` is used here for basic usage, but `@RequestScoped` works equally fine thanks to the built-in bookmarkability support explained further below.

```Java
@Named
@ViewScoped
public class YourBackingBean implements Serializable {

    private PagedDataModel<YourEntity> model;

    @Inject
    private YourEntityService service;
    
    @PostConstruct
    public void init() {
        model = PagedDataModel.lazy(service).build();
    }

    public PagedDataModel<YourEntity> getModel() {
        return model;
    }

}
```

Finally use `<op:dataTable>` to have a semi-dynamic lazy-loaded, pageable, sortable and filterable 
`<p:dataTable>` without much hassle.

```XML
<... xmlns:op="optimusfaces">

<h:form id="yourEntitiesForm">
    <op:dataTable id="yourEntitiesTable" value="#{yourBackingBean.model}">
        <op:column field="id" />
        <op:column field="created" />
        <op:column field="name" />
        <op:column field="type" />
        <op:column field="deleted" />
    </op:dataTable>
</h:form>
```

NOTE: use XML namespace of `http://omnifaces.org/optimusfaces` when using version 0.17 or older.

The `field` attribute of `<op:column>` represents the entity property path. This will
in turn be used in `id`, `field`, `headerText` and `filterBy` attributes
of `<p:column>`.

Here's how it looks like with default PrimeFaces 15 UI and all. This example uses **exactly** the above Java and XHTML code with a `Person` entity with `Long id`, `String email`, `Gender gender` and `LocalDate dateOfBirth` fields.

![example of op:dataTable](https://github.com/user-attachments/assets/682ad9d2-4458-4b4f-a866-5e3571394663)


### Relationships

`<op:dataTable>` supports `@OneToOne`, `@ManyToOne`, `@OneToMany` and `@ElementCollection` relationships. The `field` attribute of `<op:column>` takes a dot-notation path, just as you would in EL.

**`@OneToOne` / `@ManyToOne`** — given an `Invoice` with `@OneToOne Order order`, `@ManyToOne User seller` and `@ManyToOne User buyer` on the `Order`:

```XML
<op:dataTable id="invoicesTable" value="#{shop.invoices}">
    <op:column field="id" />
    <op:column field="seller.name" />
    <op:column field="order.buyer.name" />
    <op:column field="order.totalPrice" />
</op:dataTable>
```

**`@OneToMany`** — given an `Order` with `@OneToMany List<Product> products`. Each element is automatically rendered on a separate line:

```XML
<op:dataTable id="ordersTable" value="#{shop.orders}">
    <op:column field="id" />
    <op:column field="buyer.name" />
    <op:column field="products.name" />
    <op:column field="products.price" />
</op:dataTable>
```

**`@ElementCollection`** — given a `Product` with `@ElementCollection List<Tag> tags`. Note: sorting on `@ElementCollection` fields is not supported in lazy models.

```XML
<op:dataTable id="productsTable" value="#{shop.products}">
    <op:column field="id" />
    <op:column field="name" />
    <op:column field="tags" sortable="false" />
</op:dataTable>
```

**DTO projections** are also supported by providing an additional `Class<DTO> resultType` and a `QueryBuilder` mapping to the `getPage()` overload in `BaseEntityService`. See [`PagedDataModel`](https://javadoc.io/doc/org.omnifaces/optimusfaces/latest/org/omnifaces/optimusfaces/model/PagedDataModel.html) javadoc for the full DTO example.


### Non-lazy model

For a static in-memory list, use `PagedDataModel.nonLazy(list)`. Unlike the lazy model, entities only need to implement `Identifiable` (not `BaseEntity`), making it convenient for DTOs.

```Java
@PostConstruct
public void init() {
    List<YourEntity> list = createItSomehow();
    model = PagedDataModel.nonLazy(list).build();
}
```


### Providing criteria in the backend

To apply fixed query constraints at the service level, create a custom `getPageXxx()` method using the `QueryBuilder` overload of `getPage()`, then wire it up via `PagedDataModel.lazy(service::getPageXxx)`.

```Java
@Stateless
public class YourEntityService extends BaseEntityService<Long, YourEntity> {

    public PartialResultList<YourEntity> getPageOfFooType(Page page, boolean count) {
        return getPage(page, count, (criteriaBuilder, criteriaQuery, root) -> {
            criteriaQuery.where(criteriaBuilder.equal(root.get("type"), Type.FOO));
        });
    }

}
```

```Java
@PostConstruct
public void init() {
    model = PagedDataModel.lazy(service::getPageOfFooType).build();
}
```


### Providing criteria in the frontend

To drive filtering from a separate form in the same view, pass a `Map<Getter<E>, Object>` supplier to `.criteria()`. The supplier is called on every page load so changes to the backing bean fields are picked up automatically.

```Java
@PostConstruct
public void init() {
    model = PagedDataModel.lazy(service).criteria(this::getCriteria).build();
}

private Map<Getter<YourEntity>, Object> getCriteria() {
    Map<Getter<YourEntity>, Object> criteria = new HashMap<>();
    criteria.put(YourEntity::getName, Like.startsWith(searchName));
    criteria.put(YourEntity::getCreated, Order.greaterThanOrEqualTo(searchStartDate));
    criteria.put(YourEntity::getType, searchTypes);
    criteria.put(YourEntity::isDeleted, false);
    return criteria;
}
```

Available `Criteria` wrappers: `Like`, `Not`, `Between`, `Order`, `Enumerated`, `Numeric`, `Bool`, `IgnoreCase`. A `null` value produces `IS NULL`; use `Not(null)` for `IS NOT NULL`; omit the key entirely to skip the criterion.


### `<op:dataTable>` attributes

| Attribute | Default | Description |
|---|---|---|
| `id` | | ID of the underlying `<p:dataTable>` and its `widgetVar`. |
| `value` | | The `PagedDataModel` instance from the backing bean. |
| `styleClass` | | Additional CSS class on the `<p:dataTable>`. |
| `responsive` | `true` | Enables PrimeFaces reflow/responsive mode. |
| `rendered` | `true` | |
| `updateQueryString` | `true` | Reflects paging, sorting, filtering and selection state in the browser URL as query parameters, making the table state bookmarkable and enabling `@RequestScoped` backing beans and stateless views. |
| `queryParameterPrefix` | | Prefix for all query parameters; useful when multiple tables share a page to avoid parameter name collisions. |
| `sortable` | `true` | Enables sorting on all columns; can be overridden per column via `<op:column sortable="false">`. |
| `filterable` | `true` | Enables filtering on all columns; can be overridden per column via `<op:column filterable="false">`. |
| `paginable` | `true` | Enables the paginator. |
| `rows` | `10` | Rows per page. |
| `rowsPerPage` | `10,25,50` | Comma-separated rows-per-page options shown in the paginator. |
| `paginatorTemplate` | `{CurrentPageReport} {FirstPageLink} {PreviousPageLink} {PageLinks} {NextPageLink} {LastPageLink}` | PrimeFaces paginator template. |
| `currentPageReportPrefix` | `Showing` | |
| `currentPageReportTemplate` | `{startRecord} - {endRecord} of {totalRecords}` | |
| `currentPageReportSuffix` | `records` | |
| `searchable` | `false` | Adds a global search bar to the table header that searches across all filterable columns. |
| `searchPlaceholder` | `Search…` | |
| `searchButtonLabel` | `Search` | |
| `exportable` | `false` | Adds a column toggler and a CSV export split-button to the table header. |
| `columnTogglerButtonLabel` | `Columns` | |
| `exportType` | `csv` | PrimeFaces DataExporter type (`csv`, `pdf`, `xlsx`, …). |
| `exportButtonLabel` | `CSV` | |
| `exportVisibleColumnsButtonLabel` | `Visible Columns` | |
| `exportAllColumnsButtonLabel` | `All Columns` | |
| `exportFilename` | `<id>-<timestamp>` | Export file name without extension. |
| `exportPreProcessorMethod` | | Method expression called before export, e.g. `#{bean.onBeforeExport}`. |
| `exportPostProcessorMethod` | | Method expression called after export, e.g. `#{bean.onAfterExport}`. |
| `selectable` | `false` | Enables multi-row selection; selected rows are available as `model.selection`. |
| `actionable` | `false` | Enables the header actions toolbar without requiring `searchable` or `exportable`. |

The default `<ui:insert>` in `<op:dataTable>` passes content directly into the underlying `<p:dataTable>` body. Use this to attach `<f:attribute>` tags or `<p:ajax>` listeners to the underlying `<p:dataTable>`:

```XML
<op:dataTable id="yourEntitiesTable" value="#{yourBackingBean.model}">
    <f:attribute name="draggableRows" value="true" />
    <p:ajax event="rowReorder" listener="#{yourBackingBean.saveRowOrder}" />
    <op:column field="id" />
    ...
</op:dataTable>
```

When `searchable`, `exportable` or `actionable` is enabled, a `<ui:insert name="actions">` slot is available inside the header toolbar for custom buttons:

```XML
<op:dataTable id="yourEntitiesTable" value="#{yourBackingBean.model}" actionable="true">
    <ui:define name="actions">
        <p:commandButton value="New" action="#{yourBackingBean.create}" icon="pi pi-plus" />
    </ui:define>
    <op:column field="id" />
    ...
</op:dataTable>
```


### `<op:column>` attributes

| Attribute | Default | Description |
|---|---|---|
| `field` | | Entity property path; supports dot-notation for nested paths, e.g. `address.street`. |
| `head` | field name | Column header text. |
| `value` | `#{item[field]}` | Cell value expression; override to compute or format, e.g. `value="#{item.firstName} #{item.lastName}"`. |
| `tooltip` | | `title` attribute on the cell wrapper `<span>`. |
| `styleClass` | | |
| `rendered` | `true` | |
| `visible` | `true` | Initial column visibility; user can toggle via the column toggler when `exportable="true"` on the table. |
| `responsivePriority` | `0` | PrimeFaces responsive priority; higher values are hidden sooner on narrow screens. |
| `width` | | Column width, e.g. `100px` or `10%`. |
| `iterable` | auto | When `true`, collection values are rendered one item per line. Auto-detected from the value type. |
| `emptyValue` | | Text rendered when the value is `null` or empty. |
| `sortable` | inherits | Overrides the table-level `sortable` setting for this column. |
| `sortDescending` | `false` | Sets the initial sort direction to descending for this column. |
| `filterable` | inherits | Overrides the table-level `filterable` setting for this column. |
| `filterMode` | `contains` | Filter match mode: `startsWith`, `endsWith`, `contains`, `exact`. |
| `filterOptions` | | When set, renders a `<p:selectOneMenu>` dropdown filter instead of a text input. Accepts a `List` or `Map` of options. |
| `exportable` | `true` | Whether this column is included in exports. |
| `exportValue` | same as `value` | Value used during export, e.g. to strip HTML markup from CSV output. |

The `value` attribute overrides what is displayed in the cell while `field` still drives sorting and filtering. This is the right approach for e.g. an enum with a human-readable `label` property:

```XML
<op:column field="gender" value="#{item.gender.label}" />
<op:column field="status" value="#{item.status.label}" head="Status" />
```

The default `<ui:insert>` in `<op:column>` is nested directly inside the cell's `<h:outputText>`, so converters can be attached without overriding the cell layout:

```XML
<op:column field="price">
    <f:convertNumber type="currency" currencySymbol="$" />
</op:column>

<op:column field="created">
    <f:convertDateTime type="localDate" pattern="yyyy-MM-dd" />
</op:column>
```

For fully custom cell content, use `<ui:define name="cell">`. The `exportValue` attribute controls what is written to the export file independently of the custom cell rendering:

```XML
<op:column field="name">
    <ui:define name="cell">
        <h:link value="#{item.name}" outcome="detail">
            <f:param name="id" value="#{item.id}" />
        </h:link>
    </ui:define>
</op:column>

<op:column field="status" exportValue="#{item.status.label}">
    <ui:define name="cell">
        <h:graphicImage value="/images/#{item.status}.png" title="#{item.status.label}" />
    </ui:define>
</op:column>
```


### Ajax events

On every paging, sorting, filtering, searching and selection action an Ajax event fires. `<op:dataTable>` uses PrimeFaces Selectors (PFS) to auto-update any component carrying the matching style class:

| Style class | Updated on |
|---|---|
| `updateOnDataTablePage` | paging |
| `updateOnDataTableSort` | sorting |
| `updateOnDataTableFilter` | filtering / searching |
| `updateOnDataTableSelect` | row selection |

For example, to keep a summary panel in sync with the current filter state:

```XML
<p:outputPanel styleClass="updateOnDataTableFilter">
    ...
</p:outputPanel>
```


### Bookmarkability and request scope support

In plain PrimeFaces, a lazy `<p:dataTable>` requires a `@ViewScoped` backing bean. The reason is that the model instance needs to survive across postbacks, including its wrapped data. `@ViewScoped` achieves this by keeping the bean instance alive in the view map between requests, tracked by identifiers in the Faces view state. When the bean is `@RequestScoped` the model is brand new on every request, its wrapped data is `null` at the point decode runs, and pagination, sorting and selection postbacks silently fail.

This also means that stateless JSF views (`<f:view transient="true">`), which disable Faces state saving entirely and therefore inherently break `@ViewScoped` beans and require `@RequestScoped` beans, are simply not an option in plain PrimeFaces when you need a lazy data table.

OptimusFaces solves this differently. After every page load, `LazyPagedDataModel` collects the current table state and emits a JavaScript callback to `OptimusFaces.Util.updateQueryString()` in `optimusfaces.js`. That function calls `window.history.replaceState()` to update the browser URL without a page reload and also updates the JSF ViewState in all forms on the page. The URL now always reflects the exact current table state: page number, sort column, sort direction, active column filters and row selection.

On the next request, whether it is a postback, a browser refresh or a bookmarked URL, `ExtendedDataTable` takes over. It overrides `preDecode()` and detects that the lazy model has no wrapped data yet. Instead of letting decode fail, it calls `LazyPagedDataModel#preloadPage()`, which reads the table state back from the URL query string parameters and loads the correct page from the data store before decode proceeds. Faces state is never needed for this. The query string is the state.

The practical consequence is that `<op:dataTable>` works fully with `@RequestScoped` backing beans and stateless JSF views (`<f:view transient="true">`), and the table is fully bookmarkable and shareable at the same time.

```Java
@Named
@RequestScoped // Works fine with both regular and stateless (<f:view transient="true">) views.
public class YourBackingBean {

    private PagedDataModel<YourEntity> model;

    @Inject
    private YourEntityService service;

    @PostConstruct
    public void init() {
        model = PagedDataModel.lazy(service).build();
    }

    public PagedDataModel<YourEntity> getModel() {
        return model;
    }

}
```


### Known Issues

- OpenJPA generates broken nested correlated subqueries for `@OneToMany` in count subquery context, this has been work arounded, but the total result count when filtering might be inaccurate there. In case this is undesireable, use a DTO projection instead.
- OpenJPA and Hibernate+PostgreSQL/SQLServer generate broken nested correlated subqueries for `@ElementCollection` in count subquery context when a `LIKE`-based filter (e.g. global filter) is involved, this has been work arounded, but the total result count may be inaccurate when the search term matches entities exclusively via the element collection field and not via any other field. In case this is undesireable, use a DTO projection instead.

### Integration Tests

The [integration tests](https://github.com/omnifaces/optimusfaces/tree/main/src/test/java/org/omnifaces/optimusfaces/test) currently run on [following environments](https://github.com/omnifaces/optimusfaces/actions/workflows/maven.yml):
- WildFly Preview 39.0.1 with Mojarra 4.1.5 and Hibernate 7.1.11
- GlassFish 8.0.0 with Mojarra 4.1.6 and EclipseLink 5.0.0-B13
- TomEE 10.1.4 with MyFaces 4.0.3 and OpenJPA 4.1.1

Each environment will run the IT on following databases:
- H2 2.4.240 (embedded database)
- MySQL latest 8.x (provided by GitHub Actions Ubuntu environment) with JDBC driver 9.6.0
- PostgreSQL latest 15.x (provided by GitHub Actions Ubuntu environment) with JDBC driver 42.7.10
- SQL Server latest 2022 (provided by MicroSoft's Docker image) with JDBC driver 13.2.1.jre11
- DB2 latest 12 (provided by IBM's Docker image) with JDBC driver 12.1.3.0

Effectively, there are thus 15 full test runs of each [31 test cases](https://github.com/omnifaces/optimusfaces/blob/main/src/test/java/org/omnifaces/optimusfaces/test/OptimusFacesIT.java) on [19 XHTML files](https://github.com/omnifaces/optimusfaces/tree/main/src/test/resources/org.omnifaces.optimusfaces.test).
