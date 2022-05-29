[![Maven](https://img.shields.io/maven-metadata/v/https/repo.maven.apache.org/maven2/org/omnifaces/optimusfaces/maven-metadata.xml.svg)](https://repo.maven.apache.org/maven2/org/omnifaces/optimusfaces/)
[![Javadoc](https://javadoc.io/badge/org.omnifaces/optimusfaces.svg)](https://javadoc.io/doc/org.omnifaces/optimusfaces) 
[![Tests](https://github.com/omnifaces/optimusfaces/actions/workflows/maven.yml/badge.svg)](https://github.com/omnifaces/optimusfaces/actions)
[![License](https://img.shields.io/:license-apache-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

# OptimusFaces

Utility library for OmniFaces + PrimeFaces combined.


## This project is currently still in development stage!

This project basically combines best of [OmniFaces](http://omnifaces.org/) and [PrimeFaces](http://www.primefaces.org/) with help of [OmniPersistence](https://github.com/omnifaces/omnipersistence), an utility library for JPA. This project should make it a breeze to create semi-dynamic lazy-loaded, searchable, sortable and filterable `<p:dataTable>` based on a JPA model and a generic entity service.


### Installation

`pom.xml`

```XML
<dependencies>
    <!-- Target Java EE server. -->
    <dependency>
        <groupId>javax</groupId>
        <artifactId>javaee-api</artifactId>
        <version>8.0</version><!-- Minimum supported version is 7.0 -->
        <scope>provided</scope>
    </dependency>

    <!-- Runtime dependencies. -->
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>omnifaces</artifactId>
        <version>3.13.3</version><!-- Minimum supported version is 3.0 -->
    </dependency>
    <dependency>
        <groupId>org.primefaces</groupId>
        <artifactId>primefaces</artifactId>
        <version>10.0.0</version><!-- Minimum supported version is 10.0.0 -->
    </dependency>
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>optimusfaces</artifactId>
        <version>0.15</version>
    </dependency>
</dependencies>
```

**Minumum supported OmniFaces / PrimeFaces versions**

- OptimusFaces 0.1 - 0.14: OmniFaces 2.2 / PrimeFaces 7.0
- OptimusFaces 0.15+: OmniFaces 3.0 / PrimeFaces 10.0.0
- OptimusFaces 0.14-J1+: OmniFaces 4.0 / PrimeFaces 10.0.0:jakarta


### Basic Usage

First create your entity service extending [`org.omnifaces.omnipersistence.service.BaseEntityService`](https://static.javadoc.io/org.omnifaces/omnipersistence/latest/org/omnifaces/persistence/service/BaseEntityService.html). You don't necessarily need to add new methods, just extending it is sufficient. It's useful for other generic things too.

```Java
@Stateless
public class YourEntityService extends BaseEntityService<Long, YourEntity> {

   // ...

}
```

And make sure `YourEntity` extends [`org.omnifaces.omnipersistence.model.BaseEntity`](https://static.javadoc.io/org.omnifaces/omnipersistence/latest/org/omnifaces/persistence/model/BaseEntity.html) or one of its subclasses `GeneratedIdEntity`, `TimestampedEntity`, `TimestampedBaseEntity`, `VersionedEntity` or `VersionedBaseEntity`.

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

Then create a `org.omnifaces.optimusfaces.model.PagedDataModel` in your backing bean as below.

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
<... xmlns:op="http://omnifaces.org/optimusfaces">

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

The `field` attribute of `<op:column>` represents the entity property path. This will
in turn be used in `id`, `field`, `headerText` and `filterBy` attributes
of `<p:column>`.

Here's how it looks like with default PrimeFaces UI and all. This example uses **exactly** the above Java and XHTML code with a `Person` entity with `Long id`, `String email`, `Gender gender` and `LocalDate dateOfBirth` fields.

![example of op:dataTable](https://i.imgur.com/VJyNKMH.png)


### Advanced Usage

[Check `PagedDataModel` javadoc](http://static.javadoc.io/org.omnifaces/optimusfaces/latest/org/omnifaces/optimusfaces/model/PagedDataModel.html).


### Known Issues

- EclipseLink refuses to perform a `JOIN` with Criteria API when setFirstResult/setMaxResults is used. This returns a cartesian product. This has been workarounded, but this removes the ability to perform sorting on a column referenced by a join (`@OneToMany` and `@ElementCollection`). You should set such columns as `<op:column ... sortable="false">`. Another consequence is that you cannot search with a multi-valued criteria in a field referenced by a `@OneToMany` relationship. You should consider using a DTO instead.
- OpenJPA adds internally a second `JOIN` when sorting a column referenced by a join (`@OneToMany` and `@ElementCollection`). This has as consequence that the sorting is performed on a different join than the one referenced in `GROUP BY` and will thus be off from what's presented. You should for now set such columns as `<op:column ... sortable="false">` or consider using a DTO instead.
- OpenJPA does not correctly apply setFirstResult/setMaxResults when an `@OneToMany` relationship is involved in the query. It will basically apply it on the results of the `@OneToMany` relationship instead of on the query root, causing the page to contain fewer records than expected. There is no clear solution/workaround for that yet.

The [integration tests](https://github.com/omnifaces/optimusfaces/tree/develop/src/test/java/org/omnifaces/optimusfaces/test) currently run on [following environments](https://github.com/omnifaces/optimusfaces/actions/workflows/maven.yml):
- WildFly 26.1.1 with Mojarra 2.3.17 and Hibernate 5.3.24
- WildFly 26.1.1 with Mojarra 2.3.17 and EclipseLink 2.7.10
- Payara 5.2022.2 with Mojarra 2.3.14 and Hibernate 5.4.33
- Payara 5.2022.2 with Mojarra 2.3.14 and EclipseLink 2.7.9
- TomEE 8.0.11 with MyFaces 2.3.9 and OpenJPA 3.2.2

Each environment will run the IT on following databases:
- H2 1.4.200 (embedded database)
- MySQL latest 8.x (provided by GitHub Actions) with JDBC driver 8.0.29
- PostgreSQL latest 12.x (provided by GitHub Actions) with JDBC driver 42.3.5

Effectively, there are thus 15 full test runs of each [31 test cases](https://github.com/omnifaces/optimusfaces/blob/develop/src/test/java/org/omnifaces/optimusfaces/test/OptimusFacesIT.java#L429) on [19 XHTML files](https://github.com/omnifaces/optimusfaces/tree/develop/src/test/resources/org.omnifaces.optimusfaces.test).
