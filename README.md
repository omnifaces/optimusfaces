# OptimusFaces [![Build Status](https://travis-ci.org/omnifaces/optimusfaces.svg?branch=develop)](https://travis-ci.org/omnifaces/optimusfaces) [![Maven](https://maven-badges.herokuapp.com/maven-central/org.omnifaces/optimusfaces/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.omnifaces/optimusfaces) [![Javadocs](http://javadoc.io/badge/org.omnifaces/optimusfaces.svg)](http://javadoc.io/doc/org.omnifaces/optimusfaces)

Utility library for OmniFaces + PrimeFaces combined.


## This project is currently still in development stage!

This project basically combines best of [OmniFaces](http://omnifaces.org/) and [PrimeFaces](http://www.primefaces.org/) with help of [OmniPersistence](https://github.com/omnifaces/omnipersistence), an utility library for JPA. This project should make it a breeze to create semi-dynamic lazy-loaded, searchable, sortable and filterable `<p:dataTable>` based on a JPA model and a generic entity service.


### Installation

`pom.xml`

```XML
<dependencies>
    <!-- Target Java EE 7 server. E.g. WildFly 10. -->
    <dependency>
        <groupId>javax</groupId>
        <artifactId>javaee-api</artifactId>
        <version>7.0</version>
        <scope>provided</scope>
    </dependency>

    <!-- Runtime dependencies. -->
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>omnifaces</artifactId>
        <version>2.6.5</version><!-- Minimum supported version is 2.2 -->
    </dependency>
    <dependency>
        <groupId>org.primefaces</groupId>
        <artifactId>primefaces</artifactId>
        <version>6.1</version><!-- Minimum supported version is 5.2 -->
    </dependency>
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>optimusfaces</artifactId>
        <version>0.5</version>
    </dependency>
</dependencies>
```

### Basic Usage

First create your entity service extending `org.omnifaces.omnipersistence.service.BaseEntityService`. You don't necessarily need to add new methods, just extending it is sufficient. It's useful for other generic things too.

```Java
@Stateless
public class YourEntityService extends BaseEntityService<Long, YourEntity> {

   // ...

}
```

And make sure `YourEntity` extends `org.omnifaces.omnipersistence.model.BaseEntity`.

```Java
@Entity
public class YourEntity extends BaseEntity<Long> {

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

```XHTML
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

![example of op:dataTable](http://i.imgur.com/nnB6RJZ.png)


### Advanced Usage

[Check `PagedDataModel` javadoc](http://static.javadoc.io/org.omnifaces/optimusfaces/0.5/org/omnifaces/optimusfaces/model/PagedDataModel.html).


### Known Issues

- Hibernate performs the `GROUP BY` of a `@ElementCollection` on the wrong side (in the non-owning side) causing PostgreSQL to error on this. Other databases don't have a problem with it. There is no clear solution/workaround for that yet.
- EclipseLink refuses to perform a `JOIN` with Criteria API when setFirstResult/setMaxResults is used. This returns a cartesian product. This has been workarounded, but this removes the ability to perform sorting on a column referenced by a join (`@OneToMany` and `@ElementCollection`). You should set such columns as `<op:column ... sortable="false">`. Another consequence is that you cannot search with a multi-valued criteria in a field referenced by a `@OneToMany` relationship. You should consider using a DTO instead.
- EclipseLink refuses to perform a `GROUP BY` with Criteria API when setFirstResult/setMaxResults is used. This has as consequence that an `IN` clause performed on a column referenced by `@ElementCollection` will return a cartesian product. You can consider using `@OneToMany` instead. This will be workarounded later.
- OpenJPA adds internally a second `JOIN` when sorting a column referenced by a join (`@OneToMany` and `@ElementCollection`). This has as consequence that the sorting is performed on a different join than the one referenced in `GROUP BY` and will thus be off from what's presented. You should for now set such columns as `<op:column ... sortable="false">` or consider using a DTO instead.
- OpenJPA bugs on setting a criteria parameter in a nested subquery. This has as consequence that you cannot search with a multi-valued criteria in a field referenced by a `@OneToMany` relationship. You should consider using a DTO instead.
- OpenJPA does not correctly apply setFirstResult/setMaxResults when an `@OneToMany` relationship is involved in the query. It will basically apply it on the results of the `@OneToMany` relationship instead of on the query root, causing the page to contain fewer records than expected. There is no clear solution/workaround for that yet.
- OpenJPA ignores any `AttributeConverter` when setting a criteria parameter. This has as consequence that e.g. a `LocalDate`/`LocalDateTime` criteria parameter won't work until OpenJPA itself natively supports `java.time` API. You should for now declare such columns as `java.util.Date`/`java.util.Calendar`.

The [integration tests](https://github.com/omnifaces/optimusfaces/tree/develop/src/test/java/org/omnifaces/optimusfaces/test) currently run on following environments:
- WildFly 10.1.0 with Mojarra 2.2.13 and Hibernate 5.0.10
- WildFly 10.1.0 with Mojarra 2.2.13 and EclipseLink 2.6.4
- TomEE 7.0.3 with MyFaces 2.2.11 and OpenJPA 2.4.2

Each environment will run the IT on following databases:
- H2 1.3.173 on WildFly and H2 1.4.196 on TomEE (embedded database)
- MySQL 5.5.41 (provided by Travis) with JDBC driver 5.1.42
- PostgreSQL 9.1.14 (provided by Travis) with JDBC driver 42.1.1

Effectively, there are thus 9 full test runs of each [26 test cases](https://github.com/omnifaces/optimusfaces/blob/develop/src/test/java/org/omnifaces/optimusfaces/test/OptimusFacesIT.java#L389) on [16 XHTML files](https://github.com/omnifaces/optimusfaces/tree/develop/src/test/resources/org.omnifaces.optimusfaces.test).
