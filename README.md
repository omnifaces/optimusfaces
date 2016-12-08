# OptimusFaces [![Build Status](https://travis-ci.org/omnifaces/optimusfaces.svg?branch=develop)](https://travis-ci.org/omnifaces/optimusfaces)
<sup>Utility library for OmniFaces + PrimeFaces combined</sup>


##This project is currently still in development stage!

This project basically combines best of [OmniFaces](http://omnifaces.org/) and [PrimeFaces](http://www.primefaces.org/) with help of [OmniPersistence](https://github.com/omnifaces/omnipersistence), an utility library for JPA and [Hibernate](http://hibernate.org/). This project should make it a breeze to create semi-dynamic lazy-loaded, searchable, sortable and filterable `<p:dataTable>` based on a JPA model and a generic entity service.

###Installation

`pom:xml`

```
<repositories>
    <!-- Enable retrieving OptimusFaces snapshot. -->
    <repository>
        <id>ossrh</id>
        <url>http://oss.sonatype.org/content/repositories/snapshots</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </snapshots>
    </repository>
</repositories>

<dependencies>
    <!-- Target Java EE 7 server with Hibernate. E.g. WildFly 10. -->
    <dependency>
        <groupId>javax</groupId>
        <artifactId>javaee-api</artifactId>
        <version>7.0</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.hibernate</groupId>
        <artifactId>hibernate-core</artifactId>
        <version>5.0.0.Final</version>
        <scope>provided</scope>
    </dependency>

    <!-- Runtime dependencies. -->
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>omnifaces</artifactId>
        <version>2.5.1</version>
    </dependency>
    <dependency>
        <groupId>org.primefaces</groupId>
        <artifactId>primefaces</artifactId>
        <version>6.0</version>
    </dependency>
    <dependency>
        <groupId>org.omnifaces</groupId>
        <artifactId>optimusfaces</artifactId>
        <version>0.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```
