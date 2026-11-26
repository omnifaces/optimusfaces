[![Maven](https://img.shields.io/maven-metadata/v/https/repo.maven.apache.org/maven2/org/omnifaces/optimusfaces/maven-metadata.xml.svg)](https://repo.maven.apache.org/maven2/org/omnifaces/optimusfaces/)
[![Javadoc](https://javadoc.io/badge/org.omnifaces/optimusfaces.svg)](https://javadoc.io/doc/org.omnifaces/optimusfaces)
[![Tests](https://github.com/omnifaces/optimusfaces/actions/workflows/maven.yml/badge.svg)](https://github.com/omnifaces/optimusfaces/actions)
[![License](https://img.shields.io/:license-apache-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

# OptimusFaces

Utility library for OmniFaces + PrimeFaces combined.


This project basically combines best of [OmniFaces](https://omnifaces.org/) and [PrimeFaces](https://primefaces.org/) with help of [OmniPersistence](https://github.com/omnifaces/omnipersistence), a utility library for Jakarta Persistence (JPA). This project should make it a breeze to create semi-dynamic lazy-loaded, searchable, sortable and filterable `<p:dataTable>` based on a Jakarta Persistence model and a generic entity repository.


### Installation

`pom.xml`

```XML
<dependencies>
    <!-- Target Jakarta EE server. -->
    <dependency>
        <groupId>jakarta.platform</groupId>
        <artifactId>jakarta.jakartaee-web-api</artifactId>
        <version>11.0.0</version><!-- Minimum supported version is 10.0.0 -->
        <scope>provided</scope>
    </dependency>

    <!-- Runtime dependencies. -->
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>omnifaces</artifactId>
        <version>5.5.8</version><!-- Minimum supported version is 4.0 -->
    </dependency>
    <dependency>
        <groupId>org.primefaces</groupId>
        <artifactId>primefaces</artifactId>
        <version>16.0.2</version><!-- Minimum supported version is 15.0.0:jakarta -->
    </dependency>
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>optimusfaces</artifactId>
        <version>2.0</version>
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
- 1.0 - 2.0: Java 17 / OmniFaces 4.0 / PrimeFaces 15.0.0:jakarta


### Basic Usage

Your entity does not need to extend anything.

```Java
@Entity
public class Person {

    @Id @GeneratedValue(strategy=IDENTITY)
    private Long id;
    private String email;
    private Status status;
    private LocalDate dateOfBirth;

    // ...
}
```

Inject an [`org.omnifaces.persistence.service.EntityRepository`](https://javadoc.io/doc/org.omnifaces/omnipersistence/latest/org/omnifaces/persistence/service/EntityRepository.html) of it into your backing bean, and create a [`org.omnifaces.optimusfaces.model.PagedDataModel`](https://javadoc.io/doc/org.omnifaces/optimusfaces/latest/org/omnifaces/optimusfaces/model/PagedDataModel.html) with it as below. You do not need to create a service class. Note that `@ViewScoped` is used here for basic usage, but `@RequestScoped` works equally fine thanks to the built-in bookmarkability support explained further below.

```Java
@Named
@ViewScoped
public class PersonBacking implements Serializable {

    private PagedDataModel<Person> model;

    @Inject
    private EntityRepository<Long, Person> repository;

    @PostConstruct
    public void init() {
        model = PagedDataModel.lazy(repository).build();
    }

    public PagedDataModel<Person> getModel() {
        return model;
    }

}
```

Finally use `<op:dataTable>` to have a semi-dynamic lazy-loaded, pageable, sortable and filterable `<p:dataTable>` without much hassle.

```XML
<... xmlns:op="optimusfaces">

<h:form id="personsForm">
    <op:dataTable id="personsTable" value="#{personBacking.model}">
        <op:column field="id" />
        <op:column field="email" />
        <op:column field="status" />
        <op:column field="dateOfBirth" />
    </op:dataTable>
</h:form>
```

NOTE: use XML namespace of `http://omnifaces.org/optimusfaces` when using version 0.17 or older.

The `field` attribute of `<op:column>` represents the entity property path.
This will in turn be used in `id`, `field`, `headerText` and `filterBy` attributes of `<p:column>`.

Here's how it looks with the default UI of PrimeFaces 17, and of PrimeFaces 16 and older.
This example uses **exactly** the above Java and XHTML code.

PrimeFaces 17:

![example of op:dataTable on PrimeFaces 17](https://github.com/user-attachments/assets/4a4fbbb5-b0a6-4235-924b-1e9828960f3e)

PrimeFaces 16 and older:

![example of op:dataTable on PrimeFaces 16 and older](https://github.com/user-attachments/assets/4a7684dc-5e21-4bea-bff4-5dbda5db597f)


### Data source

A lazy model needs two things of your application:

- A loader: a function `(Page, boolean) -> PartialResultList<E>`, which returns one page of entities plus an estimated total count.
- A row key: a value which identifies each entity, usually its ID.

`Page` and `PartialResultList` are value types on the signature of that function, not base classes to extend. Your entities and services do not need to extend or implement any OptimusFaces or OmniPersistence type. OmniPersistence is still a dependency, because `Page` and the `Criteria` come from it.

Choose the factory method by where the data comes from:

| Factory method | Data comes from | Row key |
|---|---|---|
| `lazy(EntityRepository)` | An injected `EntityRepository` | The ID |
| `lazy(EntityRepository, loader)` | An `EntityRepository` with a custom query | The ID |
| `lazy(BaseEntityService)` | An existing service extending `BaseEntityService` | The ID |
| `lazy(BaseEntityService, loader)` | A `BaseEntityService` with a custom query | The ID |
| `lazy(loader)` | Any other data store | The ID when the entity extends `BaseEntity`, else `rowKey(...)` and preferably also `rowData(...)` on the builder |

The `EntityRepository` and `BaseEntityService` already apply the whole `Page` to the query. A custom loader for another data store must do that itself. Else paging, sorting and filtering in the table silently do nothing. The `Page` contains:

- `getOffset()` and `getLimit()`: the range of the page.
- `getOrdering()`: the sorted fields, in order, with `true` for ascending. It always ends with `id`, so that the order is defined.
- `getRequiredCriteria()`: the criteria which must all match. These are the column filters and the criteria of `criteria(...)` on the builder.
- `getOptionalCriteria()`: the criteria of which at least one must match. These are the global search on all columns.

The fields in ordering and criteria are property paths, such as `address.city`. A criteria value is either a plain value, a collection or array of values of which one must match, or a `Criteria` such as `Like`. A `null` value means `IS NULL`. When the `boolean` argument is `false`, the count may be skipped. The `getLastOrderingValues()`, `getResidualOffset()` and `isReversed()` only enable faster paging via the ordering values of a neighboring row; a custom loader may ignore them, because the offset and limit alone give the correct page.


### Serialization

The model is held by your backing bean, not by the Faces view state. A `@ViewScoped` bean is stored in the HTTP session, so the model is only serialized when the session is passivated or replicated. The exception is the OmniFaces `@ViewScoped(saveInViewState = true)`, which stores the bean in the view state, so with client side state saving the model is serialized into every page. A `@RequestScoped` bean is not serialized at all.

When a lazy model is serialized, it leaves out the entities of the loaded page and of the selection. Those entities therefore do not need to be `Serializable`. After deserialization, the page is loaded again and the selection is resolved again from its row keys. All else is serialized as is: the loader, the row key getter, the row data loader, the criteria and the ordering, including the cached ordering values of a few rows. So these must be `Serializable`. The functional interfaces of `PagedDataModel` are `Serializable`, and an `EntityRepository` is too. A lambda which captures a non-serializable object, such as an `EntityManager`, will fail to serialize. A non-lazy model does serialize its whole list, so its entities must be `Serializable`.


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

**DTO projections** are also supported by providing an additional `Class<DTO> resultType` and a `MappedQueryBuilder` mapping to the `getPage()` overload in `EntityRepository`. The model is then of the DTO type, so build it via `lazy(loader)` with `rowKey(...)` of the DTO. See [`PagedDataModel`](https://javadoc.io/doc/org.omnifaces/optimusfaces/latest/org/omnifaces/optimusfaces/model/PagedDataModel.html) javadoc for the full DTO example.


### Non-lazy model

For a static in-memory list, use `PagedDataModel.nonLazy(list)`. The entities can be of any type, and the list can come from anywhere. When the entity does not extend `BaseEntity`, specify its row key via `rowKey(...)` on the builder.

```Java
@PostConstruct
public void init() {
    List<Person> list = createItSomehow();
    model = PagedDataModel.nonLazy(list).build();
}
```


### Providing criteria in the backend

To apply fixed query constraints, delegate to the `QueryBuilder` overload of `getPage()` in `EntityRepository` via `PagedDataModel.lazy(repository, loader)`.

```Java
@PostConstruct
public void init() {
    model = PagedDataModel.lazy(repository, this::getPageOfActivePersons).build();
}

private PartialResultList<Person> getPageOfActivePersons(Page page, boolean count) {
    return repository.getPage(page, count, (criteriaBuilder, criteriaQuery, root) -> {
        criteriaQuery.where(criteriaBuilder.equal(root.get("status"), Status.ACTIVE));
    });
}
```

The same goes for fetch fields, e.g. `PagedDataModel.lazy(repository, (page, count) -> repository.getPage(page, count, "address"))`.

With a service extending `BaseEntityService`, create a `getPageXxx()` method in the service which delegates to the same `getPage()` method, and wire it up via `PagedDataModel.lazy(service, service::getPageXxx)`.


### Providing criteria in the frontend

To drive filtering from a separate form in the same view, pass a `Map<Getter<E>, Object>` supplier to `.criteria()`. The supplier is called on every page load so changes to the backing bean fields are picked up automatically.

```Java
@PostConstruct
public void init() {
    model = PagedDataModel.lazy(repository).criteria(this::getCriteria).build();
}

private Map<Getter<Person>, Object> getCriteria() {
    Map<Getter<Person>, Object> criteria = new HashMap<>();
    criteria.put(Person::getEmail, Like.startsWith(searchEmail));
    criteria.put(Person::getDateOfBirth, Order.lessThan(searchBornBefore));
    criteria.put(Person::getStatus, searchStatuses);
    return criteria;
}
```

Available `Criteria` wrappers: `Like`, `Not`, `Between`, `Order`, `Enumerated`, `Numeric`, `Bool`, `IgnoreCase`. A `null` value produces `IS NULL`; use `Not.value(null)` for `IS NOT NULL`; omit the key entirely to skip the criterion.


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
| `sortMode` | `single` | Set to `multiple` to sort by more than one column: a click on a column header with Ctrl or Cmd pressed adds the column to the sort, a click without replaces the sort. |
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
<op:dataTable id="personsTable" value="#{personBacking.model}">
    <f:attribute name="draggableRows" value="true" />
    <p:ajax event="rowReorder" listener="#{personBacking.saveRowOrder}" />
    <op:column field="id" />
    ...
</op:dataTable>
```

When `searchable`, `exportable` or `actionable` is enabled, a `<ui:insert name="actions">` slot is available inside the header toolbar for custom buttons:

```XML
<op:dataTable id="personsTable" value="#{personBacking.model}" actionable="true">
    <ui:define name="actions">
        <p:commandButton value="New" action="#{personBacking.create}" icon="pi pi-plus" />
    </ui:define>
    <op:column field="id" />
    ...
</op:dataTable>
```


### `<op:column>` attributes

| Attribute | Default | Description |
|---|---|---|
| `field` | | Entity property path; supports dot-notation for nested paths, e.g. `address.street`. |
| `head` | the `field` value | Column header text. |
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
| `filterMode` | `contains`, or `exact` with `filterOptions` | Filter match mode: `startsWith`, `endsWith`, `contains`, `exact`. |
| `filterOptions` | | When set, renders a `<p:selectOneMenu>` dropdown filter instead of a text input. Accepts a `List` or `Map` of options. |
| `exportable` | `true` | Whether this column is included in exports. |
| `exportValue` | same as `value` | Value used during export, e.g. to strip HTML markup from CSV output. |

The `value` attribute overrides what is displayed in the cell while `field` still drives sorting and filtering. This is the right approach for e.g. an enum with a human-readable `label` property:

```XML
<op:column field="status" value="#{item.status.label}" head="Status" />
```

The default `<ui:insert>` in `<op:column>` is nested directly inside the cell's `<h:outputText>`, so converters can be attached without overriding the cell layout:

```XML
<op:column field="dateOfBirth">
    <f:convertDateTime type="localDate" pattern="yyyy-MM-dd" />
</op:column>

<op:column field="price">
    <f:convertNumber type="currency" currencySymbol="$" />
</op:column>
```

For fully custom cell content, use `<ui:define name="cell">`. The `exportValue` attribute controls what is written to the export file independently of the custom cell rendering:

```XML
<op:column field="id">
    <ui:define name="cell">
        <h:link value="#{item.id}" outcome="detail">
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

This also means that stateless Faces views (`<f:view transient="true">`), which disable Faces state saving entirely and therefore inherently break `@ViewScoped` beans and require `@RequestScoped` beans, are simply not an option in plain PrimeFaces when you need a lazy data table.

OptimusFaces solves this differently. After every page load, `LazyPagedDataModel` collects the current table state and emits a JavaScript callback to `OptimusFaces.Util.updateQueryString()` in `optimusfaces.js`. That function calls `window.history.replaceState()` to update the browser URL without a page reload and also updates the Faces ViewState in all forms on the page. The URL now always reflects the exact current table state: page number, sort columns, sort directions, active column filters and row selection.

Each sorted column is one `o` query parameter, in order of priority, with a `-` prefix for descending, e.g. `?o=lastName&o=-dateOfBirth`.
Only the sort which differs from the default ordering of the model is in the URL.
Fields which are not a column of the table are ignored, and with `sortMode="single"` only the first valid one is used.

On the next request, whether it is a postback, a browser refresh or a bookmarked URL, `ExtendedDataTable` takes over. It overrides `preDecode()` and detects that the lazy model has no wrapped data yet. Instead of letting decode fail, it calls `LazyPagedDataModel#preloadPage()`, which reads the table state back from the URL query string parameters and loads the correct page from the data store before decode proceeds. Faces state is never needed for this. The query string is the state.

The practical consequence is that `<op:dataTable>` works fully with `@RequestScoped` backing beans and stateless Faces views (`<f:view transient="true">`), and the table is fully bookmarkable and shareable at the same time.

```Java
@Named
@RequestScoped // Works fine with both regular and stateless (<f:view transient="true">) views.
public class PersonBacking {

    private PagedDataModel<Person> model;

    @Inject
    private EntityRepository<Long, Person> repository;

    @PostConstruct
    public void init() {
        model = PagedDataModel.lazy(repository).build();
    }

    public PagedDataModel<Person> getModel() {
        return model;
    }

}
```


### Upgrading from 1.x

OptimusFaces 2.0 requires OmniPersistence 2.0, see also its [upgrade notes](https://github.com/omnifaces/omnipersistence#15-upgrading-from-1x).
OptimusFaces 1.x does not run on OmniPersistence 2.0.
Recompile your code against both.

- **Data model**
   - `NestedBaseEntityELResolver` is renamed to `NestedEntityELResolver`. It is registered automatically, so this only matters when your code references it.
   - Your entities no longer need to extend `BaseEntity`. See [Data source](#data-source) for the row key.

- **Sorting and paging**
   - `PagedDataModel#getOrdering()` returns a `List<SortMeta>` instead of a single `SortMeta`, in order of priority. So does the `protected` method `LazyPagedDataModel#getInitialOrdering()`.

- **Filtering**
   - A `null` criteria on a collection property, such as `groups`, matches the rows without elements, also in a non-lazy model, where it used to match every row. So a non-lazy model now agrees with a lazy one. For no restriction, leave the criteria out or pass an empty collection. A bound selection which is still `null` therefore needs a fallback such as `singletonMap(Person::getGroups, coalesce(selection, emptySet()))`.

     | Wanted | Criteria value |
     |---|---|
     | No restriction | none, or an empty collection such as `Set.of()` |
     | No elements at all | `null` |
     | At least one element | `Not.value(null)` |
     | Any of the given elements | the elements, such as `Set.of(USER, ADMIN)` |


### Known Issues

- OpenJPA correlates a nested subquery in the count query with an extra cross-joined entity instead of the counted one, because it incorrectly declares the root of the enclosing subquery once more when the nested subquery refers to it. Therefore, on OpenJPA, a negated or multi-valued criteria on a `@OneToMany`, such as a set of phone types, and a negated criteria or a `Criteria` such as `Like` on an `@ElementCollection`, such as the global filter, are ignored in the total result count: the count is that of the query without them. The rows themselves are filtered correctly. In case this is undesirable, use a DTO projection instead.

### Integration Tests

The [integration tests](https://github.com/omnifaces/optimusfaces/tree/main/src/test/java/org/omnifaces/optimusfaces/test) currently run on [the following environments](https://github.com/omnifaces/optimusfaces/actions/workflows/maven.yml):
- WildFly 42.0.0 with Mojarra 4.1.13 and Hibernate 7.4.13
- GlassFish 8.0.5 with Mojarra 4.1.17 and EclipseLink 5.0.2
- TomEE 10.2.0 with MyFaces 4.0.3 and OpenJPA 4.1.1

Each environment runs the IT on H2 2.5.252 (embedded database).
The SQL of the lazy model is built by OmniPersistence, whose own [integration tests](https://github.com/omnifaces/omnipersistence#14-integration-tests-and-further-examples) cover every query shape which OptimusFaces generates on H2, MySQL, PostgreSQL, SQL Server and DB2.

Effectively, there are thus 3 full test runs of each [41 test cases](https://github.com/omnifaces/optimusfaces/blob/main/src/test/java/org/omnifaces/optimusfaces/test/OptimusFacesIT.java) on [23 XHTML files](https://github.com/omnifaces/optimusfaces/tree/main/src/test/resources/org.omnifaces.optimusfaces.test).

### Commercial support

OptimusFaces is maintained by [BalusC BV](https://balusc.org). For commercial support, Jakarta Faces consulting or migration work, mail <bauke@balusc.org>.
