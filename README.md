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
        <version>2.6.3</version>
    </dependency>
    <dependency>
        <groupId>org.primefaces</groupId>
        <artifactId>primefaces</artifactId>
        <version>6.1</version>
    </dependency>
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>optimusfaces</artifactId>
        <version>0.3</version>
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

[Check `PagedDataModel` javadoc](http://static.javadoc.io/org.omnifaces/optimusfaces/0.3/org/omnifaces/optimusfaces/model/PagedDataModel.html).


### Known Issues

- EclipseLink refuses to perform a `JOIN` with Criteria API when setFirstResult/setMaxResults is used. This returns a cartesian product. This has been workarounded, but this removes the ability to perform sorting on a column referenced by a join (`@OneToMany` and `@ElementCollection`). You should set such columns as `<op:column ... sortable="false">`.
- EclipseLink refuses to perform a `GROUP BY` with Criteria API when setFirstResult/setMaxResults is used. This has as consequence that an `IN` clause performed on a column referenced by `@ElementCollection` will return a cartesian product. There is no clear solution/workaround for that yet.
- HSQLDB (as used in TomEE) for some reason requires a `GROUP BY` on the `CONCAT` aggregate. Other DBs doesn't.
- HSQLDB (as used in TomEE) doesn't correctly sort values referenced in an inner join.

The [integration tests](https://github.com/omnifaces/optimusfaces/tree/develop/src/test/java/org/omnifaces/optimusfaces/test) currently run on following environments:
- WildFly 10.1.0 with Mojarra 2.2.13 and Hibernate 5.0.10
- WildFly 10.1.0 with Mojarra 2.2.13 and EclipseLink 2.6.4
- TomEE 7.0.3 with MyFaces 2.2.11 and OpenJPA 2.4.2

Hibernate successfully passes all tests while some of the tests will be skipped for EclipseLink due to abovementioned issues. OpenJPA technically also passes all tests, however TomEE's embedded HSQLDB has its problems as mentioned above and hence those tests will be skipped for TomEE+OpenJPA.
