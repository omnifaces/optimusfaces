[![Maven](https://img.shields.io/maven-metadata/v/https/repo.maven.apache.org/maven2/org/omnifaces/optimusfaces/maven-metadata.xml.svg)](https://repo.maven.apache.org/maven2/org/omnifaces/optimusfaces/)
[![Javadoc](https://javadoc.io/badge/org.omnifaces/optimusfaces.svg)](https://javadoc.io/doc/org.omnifaces/optimusfaces) 
[![Tests](https://github.com/omnifaces/optimusfaces/actions/workflows/maven.yml/badge.svg)](https://github.com/omnifaces/optimusfaces/actions)
[![License](https://img.shields.io/:license-apache-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

# OptimusFaces

Utility library for OmniFaces + PrimeFaces combined.


This project basically combines best of [OmniFaces](https://omnifaces.org/) and [PrimeFaces](https://primefaces.org/) with help of [OmniPersistence](https://github.com/omnifaces/omnipersistence), an utility library for JPA. This project should make it a breeze to create semi-dynamic lazy-loaded, searchable, sortable and filterable `<p:dataTable>` based on a JPA model and a generic entity service.


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
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

**Minimum supported Java / OmniFaces / PrimeFaces versions**

Java 17 / OmniFaces 4.0 / PrimeFaces 15.0.0:jakarta


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


### Advanced Usage

[Check `PagedDataModel` javadoc](http://static.javadoc.io/org.omnifaces/optimusfaces/latest/org/omnifaces/optimusfaces/model/PagedDataModel.html).


### Known Issues

- OpenJPA generates broken nested correlated subqueries for `@OneToMany` in count subquery context, this has been work arounded, but the total result count when filtering might be inaccurate there. In case this is undesireable, use a DTO projection instead.
- OpenJPA and Hibernate+PostgreSQL/SQLServer generate broken nested correlated subqueries for `@ElementCollection` in count subquery context when a `LIKE`-based filter (e.g. global filter) is involved, this has been work arounded, but the total result count may be inaccurate when the search term matches entities exclusively via the element collection field and not via any other field. In case this is undesireable, use a DTO projection instead.

### Integration Tests

The [integration tests](https://github.com/omnifaces/optimusfaces/tree/main/src/test/java/org/omnifaces/optimusfaces/test) currently run on [following environments](https://github.com/omnifaces/optimusfaces/actions):
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
