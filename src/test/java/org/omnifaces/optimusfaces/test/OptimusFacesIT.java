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
package org.omnifaces.optimusfaces.test;

import static java.lang.Math.min;
import static java.lang.System.getProperty;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.reverseOrder;
import static java.util.logging.Level.OFF;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.jboss.arquillian.graphene.Graphene.guardAjax;
import static org.jboss.arquillian.graphene.Graphene.waitGui;
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.omnifaces.optimusfaces.model.PagedDataModel.QUERY_PARAMETER_ORDER;
import static org.omnifaces.optimusfaces.model.PagedDataModel.QUERY_PARAMETER_PAGE;
import static org.omnifaces.optimusfaces.model.PagedDataModel.QUERY_PARAMETER_SEARCH;
import static org.omnifaces.optimusfaces.test.service.StartupService.ROWS_PER_PAGE;
import static org.omnifaces.optimusfaces.test.service.StartupService.TOTAL_RECORDS;
import static org.omnifaces.persistence.Database.POSTGRESQL;

import java.io.File;
import java.net.URL;
import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;

import org.jboss.arquillian.drone.api.annotation.Default;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.context.GrapheneContext;
import org.jboss.arquillian.graphene.proxy.GrapheneProxyInstance;
import org.jboss.arquillian.graphene.spi.configuration.GrapheneConfiguration;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.omnifaces.optimusfaces.test.model.Gender;
import org.omnifaces.persistence.Database;
import org.omnifaces.persistence.criteria.Between;
import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.criteria.Order;
import org.omnifaces.util.Servlets;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;

public abstract class OptimusFacesIT {

	private static final int TIMEOUT_IN_SECONDS = 5;

	private static Database database;

	@Drone
	private WebDriver browser;

	@ArquillianResource
	private URL baseURL;

	protected static <T extends OptimusFacesIT> WebArchive createArchive(Class<T> testClass, Database database) {
		OptimusFacesIT.database = database;
		String packageName = testClass.getPackage().getName();
		MavenResolverSystem maven = Maven.resolver();

		WebArchive archive = create(WebArchive.class, testClass.getSimpleName() + ".war")
			.addPackage(packageName + ".model")
			.addPackage(packageName + ".model.dto")
			.addPackage(packageName + ".service")
			.addPackage(packageName + ".view")
			.addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
			.addAsLibrary(new File(getProperty("optimusfaces.jar")))
			.addAsLibraries(maven.loadPomFromFile("pom.xml").importCompileAndRuntimeDependencies().resolve().withTransitivity().asFile())
			.addAsLibraries(maven.resolve("org.omnifaces:omnifaces:" + getProperty("test.omnifaces.version"), "org.primefaces:primefaces:" + getProperty("test.primefaces.version")).withTransitivity().asFile());

		addDataSourceConfig(database, archive);
		addPersistenceConfig(maven, archive);
		addResources(new File(testClass.getClassLoader().getResource(packageName).getFile()), "", archive::addAsWebResource);

		archive.as(ZipExporter.class).exportTo(new File("/tmp/test.war"), true);

		return archive;
	}

	private static void addDataSourceConfig(Database database, WebArchive archive) {
		String dataSourceConfigXml = isWildFly() ? "wildfly-ds.xml" : isPayara() ? "glassfish-resources.xml" : isTomEE() ? "resources.xml" : null;

		if (dataSourceConfigXml != null) {
			archive.addAsWebInfResource("WEB-INF/" + dataSourceConfigXml + "/" + database.name().toLowerCase() + ".xml", dataSourceConfigXml);
		}
	}

	private static void addPersistenceConfig(MavenResolverSystem maven, WebArchive archive) {
		String persistenceConfigXml = getProperty("profile.id") + ".xml";
		String persistenceXml = "META-INF/persistence.xml";
		String ormXml = "META-INF/orm.xml";

		archive.addAsResource(persistenceXml + "/" + persistenceConfigXml, persistenceXml);

		if (OptimusFacesIT.class.getClassLoader().getResource(ormXml + "/" + persistenceConfigXml) != null) {
			archive.addAsResource(ormXml + "/" + persistenceConfigXml, ormXml);
		}

		if (isPayara() && isHibernate()) {
			// Does not work when placed in glassfish/modules? TODO: investigate.
			archive.addAsLibraries(maven.resolve("org.hibernate:hibernate-core:" + getProperty("test.payara-hibernate.version"), "dom4j:dom4j:1.6.1").withTransitivity().asFile());
		}
	}

	private static void addResources(File root, String directory, BiConsumer<File, String> archiveConsumer) {
		for (File file : root.listFiles()) {
			String path = directory + "/" + file.getName();

			if (file.isFile()) {
				archiveConsumer.accept(file, path);
			}
			else if (file.isDirectory()) {
				addResources(file, path, archiveConsumer);
			}
		}
	}

	@Rule
	public TestRule watcher = new TestWatcher() {

		@Override
		protected void starting(Description description) {
			System.out.println(""
				+ "\n"
				+ "\n    ============================================================================================="
				+ "\n    Starting " + description.getMethodName() + " ..."
				+ "\n    ============================================================================================="
				+ "\n"
			);
		}

		@Override
		protected void finished(Description description) {
			System.out.println(""
				+ "\n    ============================================================================================="
				+ "\n    " + description.getMethodName() + " finished!"
				+ "\n    ============================================================================================="
				+ "\n"
				+ "\n"
			);
		}

	};

	@Before
	public void init() {
		Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(OFF); // MyFaces triggers for some reason a lot of awkward JS "illegal selector" and CSS "em has to be a px" warnings.
		configureTimeouts(browser);
	}

	protected static void configureTimeouts(WebDriver browser) {
		while (browser instanceof GrapheneProxyInstance) {
			browser = ((GrapheneProxyInstance) browser).unwrap();
		}

		GrapheneContext.setContextFor(new GrapheneConfiguration() {
			@Override
			public long getWaitAjaxInterval() {
				return TIMEOUT_IN_SECONDS;
			}

			@Override
			public long getWaitGuardInterval() {
				return TIMEOUT_IN_SECONDS;
			}

			@Override
			public long getWaitGuiInterval() {
				return TIMEOUT_IN_SECONDS;
			}

			@Override
			public long getWaitModelInterval() {
				return TIMEOUT_IN_SECONDS;
			}
		}, browser, Default.class);
	}

	protected void open(String type) {
		open(type, null);
	}

	protected void open(String type, String queryString) {
		String url = baseURL + "/" + OptimusFacesIT.class.getSimpleName() + type + ".xhtml";

		if (queryString != null) {
			url += "?" + queryString;
		}

		browser.manage().deleteAllCookies(); // Else IT on pagination/sorting may fail because they're apparently cached somewhere in session. TODO: investigate
		browser.get(url);
		waitGui(browser);
	}

	protected String getQueryParameter(String name) {
		if (browser.getCurrentUrl().contains("?")) {
			String queryString = browser.getCurrentUrl().split("\\?", 2)[1];
			Map<String, List<String>> params = Servlets.toParameterMap(queryString);
			List<String> values = params.get(name);

			if (values != null) {
				return values.get(0);
			}
		}

		return null;
	}

	protected int getRowCount() {
		return Integer.parseInt(browser.findElement(By.id("rowCount")).getText());
	}

	protected static boolean isWildFly() {
		return getProperty("profile.id").startsWith("wildfly-");
	}

	protected static boolean isPayara() {
		return getProperty("profile.id").startsWith("payara-");
	}

	protected static boolean isTomEE() {
		return getProperty("profile.id").startsWith("tomee-");
	}

	protected static boolean isHibernate() {
		return getProperty("profile.id").endsWith("-hibernate");
	}

	protected static boolean isEclipseLink() {
		return getProperty("profile.id").endsWith("-eclipselink");
	}

	protected static boolean isOpenJPA() {
		return getProperty("profile.id").endsWith("-openjpa");
	}

	protected boolean isPostgreSQL() {
		return database == POSTGRESQL;
	}

	protected boolean isLazy() {
		return browser.getCurrentUrl().contains("OptimusFacesITLazy");
	}


	// Elements -------------------------------------------------------------------------------------------------------

	@FindBy(id="form")
	private WebElement form;

	@FindBy(id="form:table")
	private WebElement table;

	@FindBy(id="form:table:id")
	private WebElement idColumn;

	@FindBy(id="form:table:email")
	private WebElement emailColumn;

	@FindBy(id="form:table:gender")
	private WebElement genderColumn;

	@FindBy(id="form:table:dateOfBirth")
	private WebElement dateOfBirthColumn;

	@FindBy(id="form:table:address_houseNumber")
	private WebElement address_houseNumberColumn;

	@FindBy(id="form:table:address_string")
	private WebElement address_stringColumn;

	@FindBy(id="form:table:addressString")
	private WebElement addressStringColumn;

	@FindBy(id="form:table:totalPhones")
	private WebElement totalPhonesColumn;

	@FindBy(id="form:table:phones_type")
	private WebElement phones_typeColumn;

	@FindBy(id="form:table:phones_number")
	private WebElement phones_numberColumn;

	@FindBy(id="form:table:groups")
	private WebElement groupsColumn;

	@FindBy(css="#form\\:table th.ui-state-active")
	private WebElement activeColumn;

	@FindBy(id="form:table:filter")
	private WebElement globalFilter;

	@FindBy(id="form:table:search")
	private WebElement globalFilterButton;

	@FindBy(id="form:table:id:filter")
	private WebElement idColumnFilter;

	@FindBy(id="form:table:email:filter")
	private WebElement emailColumnFilter;

	@FindBy(id="form:table:gender:filter")
	private WebElement genderColumnFilter;

	@FindBy(id="form:table:dateOfBirth:filter")
	private WebElement dateOfBirthColumnFilter;

	@FindBy(id="form:table:address_houseNumber:filter")
	private WebElement address_houseNumberColumnFilter;

	@FindBy(id="form:table:address_string:filter")
	private WebElement address_stringColumnFilter;

	@FindBy(id="form:table:addressString:filter")
	private WebElement addressStringColumnFilter;

	@FindBy(id="form:table:totalPhones:filter")
	private WebElement totalPhonesColumnFilter;

	@FindBy(id="form:table:phones_number:filter")
	private WebElement phones_numberColumnFilter;

	@FindBy(css="#form\\:table_data tr")
	private List<WebElement> rows;

	@FindBy(css="#form\\:table_paginator_bottom span.ui-paginator-current")
	private WebElement pageReport;

	@FindBy(css="#form\\:table_paginator_bottom a.ui-paginator-first")
	private WebElement pageFirst;

	@FindBy(css="#form\\:table_paginator_bottom a.ui-paginator-prev")
	private WebElement pagePrevious;

	@FindBy(css="#form\\:table_paginator_bottom a.ui-paginator-page")
	private List<WebElement> pages;

	@FindBy(css="#form\\:table_paginator_bottom a.ui-paginator-page.ui-state-active")
	private WebElement pageCurrent;

	@FindBy(css="#form\\:table_paginator_bottom a.ui-paginator-next")
	private WebElement pageNext;

	@FindBy(css="#form\\:table_paginator_bottom a.ui-paginator-last")
	private WebElement pageLast;

	@FindBy(id="form:criteria:0")
	private WebElement criteriaIdBetween50And150;

	@FindBy(id="form:criteria:1")
	private WebElement criteriaEmailLikeName1;

	@FindBy(id="form:criteria:2")
	private WebElement criteriaGenderIsFemale;

	@FindBy(id="form:criteria:3")
	private WebElement criteriaDateOfBirthBefore1950;

	@FindBy(id="form:phoneTypes:0")
	private WebElement criteriaPhoneTypeMOBILE;

	@FindBy(id="form:phoneTypes:1")
	private WebElement criteriaPhoneTypeHOME;

	@FindBy(id="form:phoneTypes:2")
	private WebElement criteriaPhoneTypeWORK;

	@FindBy(id="form:groups:0")
	private WebElement criteriaGroupUSER;

	@FindBy(id="form:groups:1")
	private WebElement criteriaGroupMANAGER;

	@FindBy(id="form:groups:2")
	private WebElement criteriaGroupADMINISTRATOR;

	@FindBy(id="form:groups:3")
	private WebElement criteriaGroupDEVELOPER;


	// Tests ----------------------------------------------------------------------------------------------------------

	@Test
	public void testLazyDefaultState() {
		open("Lazy");
		testDefaultState();
	}

	@Test
	public void testNonLazyDefaultState() {
		open("NonLazy");
		testDefaultState();
	}

	@Test
	public void testLazyPaging() {
		open("Lazy");
		testPaging();
	}

	@Test
	public void testNonLazyPaging() {
		open("NonLazy");
		testPaging();
	}

	@Test
	public void testLazySorting() {
		open("Lazy");
		testSorting();
	}

	@Test
	public void testNonLazySorting() {
		open("NonLazy");
		testSorting();
	}

	@Test
	public void testLazyWithDefaultOrderBy() {
		open("LazyWithDefaultOrderBy");
		testDefaultOrderBy();
	}

	@Test
	public void testLazyFiltering() {
		open("Lazy");
		testFiltering();
	}

	@Test
	public void testNonLazyFiltering() {
		open("NonLazy");
		testFiltering();
	}

	@Test
	public void testLazyPagingSortingAndFiltering() {
		open("Lazy");
		testPagingSortingAndFiltering();
	}

	@Test
	public void testNonLazyPagingSortingAndFiltering() {
		open("NonLazy");
		testPagingSortingAndFiltering();
	}

	@Test
	public void testLazyQueryStringLoading() {
		testQueryStringLoading("Lazy");
	}

	@Test
	public void testNonLazyQueryStringLoading() {
		testQueryStringLoading("NonLazy");
	}

	@Test
	public void testLazyWithCriteria() {
		open("LazyWithCriteria");
		testCriteria();
	}

	@Test
	public void testNonLazyWithCriteria() {
		open("NonLazyWithCriteria");
		testCriteria();
	}

	@Test
	public void testLazyWithFilterOptions() {
		open("LazyWithFilterOptions");
		testFilterOptions();
	}

	@Test
	public void testNonLazyWithFilterOptions() {
		open("NonLazyWithFilterOptions");
		testFilterOptions();
	}

	@Test
	public void testLazyWithDTO() {
		open("LazyWithDTO");
		testDTO();
	}

	@Test
	public void testNonLazyWithDTO() {
		open("NonLazyWithDTO");
		testDTO();
	}

	@Test
	public void testLazyWithOneToOne() {
		open("LazyWithOneToOne");
		testOneToOne();
	}

	@Test
	public void testNonLazyWithOneToOne() {
		open("NonLazyWithOneToOne");
		testOneToOne();
	}

	@Test
	public void testLazyWithOneToMany() {
		open("LazyWithOneToMany");
		testOneToMany();
	}

	@Test
	public void testNonLazyWithOneToMany() {
		open("NonLazyWithOneToMany");
		testOneToMany();
	}

	@Test
	public void testLazyWithElementCollection() {
		open("LazyWithElementCollection");
		testElementCollection();
	}

	@Test
	public void testNonLazyWithElementCollection() {
		open("NonLazyWithElementCollection");
		testElementCollection();
	}

	@Test
	public void testLazyWithManyToOne() {
		open("LazyWithManyToOne");
		testManyToOne();
	}

	@Test
	public void testNonLazyWithManyToOne() {
		open("NonLazyWithManyToOne");
		testManyToOne();
	}


	// Testers --------------------------------------------------------------------------------------------------------

	protected void testDefaultState() {
		assertEquals("row count", ROWS_PER_PAGE, rows.size());
		assertPaginatorState(1);
		assertSortedState(idColumn, false);
	}

	protected void testPaging() {
		guardAjax(pageNext).click();
		assertPaginatorState(2);
		assertSortedState(idColumn, false);

		guardAjax(pagePrevious).click();
		assertPaginatorState(1);
		assertSortedState(idColumn, false);

		guardAjax(pageLast).click();
		assertPaginatorState(TOTAL_RECORDS / ROWS_PER_PAGE);
		assertSortedState(idColumn, false);

		guardAjax(pagePrevious).click();
		assertPaginatorState((TOTAL_RECORDS / ROWS_PER_PAGE) - 1);
		assertSortedState(idColumn, false);

		guardAjax(pageNext).click();
		assertPaginatorState(TOTAL_RECORDS / ROWS_PER_PAGE);
		assertSortedState(idColumn, false);

		guardAjax(pageFirst).click();
		assertPaginatorState(1);
		assertSortedState(idColumn, false);
	}

	protected void testSorting() {
		guardAjax(idColumn).click();
		assertPaginatorState(1);
		assertSortedState(idColumn, true);

		guardAjax(idColumn).click();
		assertPaginatorState(1);
		assertSortedState(idColumn, false);

		guardAjax(idColumn).click();
		assertPaginatorState(1);
		assertSortedState(idColumn, true);

		guardAjax(emailColumn).click();
		assertPaginatorState(1);
		assertSortedState(emailColumn, true);

		guardAjax(emailColumn).click();
		assertPaginatorState(1);
		assertSortedState(emailColumn, false);

		guardAjax(genderColumn).click();
		assertPaginatorState(1);
		assertSortedState(genderColumn, true);

		guardAjax(emailColumn).click();
		assertPaginatorState(1);
		assertSortedState(emailColumn, true);

		guardAjax(dateOfBirthColumn).click();
		assertPaginatorState(1);
		assertSortedState(dateOfBirthColumn, true);

		guardAjax(dateOfBirthColumn).click();
		assertPaginatorState(1);
		assertSortedState(dateOfBirthColumn, false);
	}

	protected void testDefaultOrderBy() {
		assertPaginatorState(1);
		assertSortedState(emailColumn, false, true);

		guardAjax(emailColumn).click();
		assertPaginatorState(1);
		assertSortedState(emailColumn, true);

		guardAjax(idColumn).click();
		assertPaginatorState(1);
		assertSortedState(idColumn, true);

		guardAjax(genderColumn).click();
		assertPaginatorState(1);
		assertSortedState(genderColumn, true);
	}

	protected void testFiltering() {
		guardAjax(idColumnFilter).sendKeys("3");
		int totalRecords1 = getRowCount();
		assertPaginatorState(1, 38);
		assertFilteredState(idColumnFilter, "3");

		globalFilter.sendKeys("FEMALE");
		guardAjax(globalFilterButton).click();
		int totalRecords2 = getRowCount();
		assertTrue(totalRecords2 + " must be less than " + totalRecords1, totalRecords2 < totalRecords1);
		assertGlobalFilterState("FEMALE");
		assertFilteredState(idColumnFilter, "3");

		globalFilter.sendKeys(Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE);
		guardAjax(globalFilterButton).click();
		assertPaginatorState(1, 38);
		assertFilteredState(idColumnFilter, "3");

		guardAjax(idColumnFilter).sendKeys(Keys.BACK_SPACE);
		assertPaginatorState(1, TOTAL_RECORDS);

		guardAjax(genderColumnFilter).sendKeys("FEMALE");
		int totalRecords3 = getRowCount();
		assertPaginatorState(1);
		assertFilteredState(genderColumnFilter, "FEMALE");

		guardAjax(emailColumnFilter).sendKeys("1");
		int totalRecords4 = getRowCount();
		assertTrue(totalRecords4 + " must be less than " + totalRecords3, totalRecords4 < totalRecords3);
		assertPaginatorState(1);
		assertFilteredState(emailColumnFilter, "1");
		assertFilteredState(genderColumnFilter, "FEMALE");

		guardAjax(genderColumnFilter).sendKeys(Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE);
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");

		guardAjax(emailColumnFilter).sendKeys(Keys.BACK_SPACE);
		assertPaginatorState(1, TOTAL_RECORDS);
	}

	protected void testPagingSortingAndFiltering() {
		guardAjax(pageNext).click();
		assertPaginatorState(2);

		guardAjax(emailColumnFilter).sendKeys("1");
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");

		guardAjax(emailColumn).click();
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");
		assertSortedState(emailColumn, true);

		for (int nextPage = 2; nextPage <= 10; nextPage++) {
			guardAjax(pageNext).click();
			assertPaginatorState(nextPage, 119);
			assertFilteredState(emailColumnFilter, "1");
			assertSortedState(emailColumn, true);
		}

		guardAjax(emailColumn).click();
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");
		assertSortedState(emailColumn, false);

		globalFilter.sendKeys("FEMALE");
		guardAjax(globalFilterButton).click();
		int totalRecords1 = getRowCount();
		assertTrue(totalRecords1 + " must be less than 119", totalRecords1 < 119);
		assertFilteredState(emailColumnFilter, "1");
		assertGlobalFilterState("FEMALE");
		assertSortedState(emailColumn, false);

		guardAjax(idColumn).click();
		int totalRecords2 = getRowCount();
		assertTrue(totalRecords1 + " must be equal to " + totalRecords2, totalRecords1 == totalRecords2);
		assertFilteredState(emailColumnFilter, "1");
		assertGlobalFilterState("FEMALE");
		assertSortedState(idColumn, true);

		globalFilter.sendKeys(Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE, Keys.BACK_SPACE);;
		guardAjax(globalFilterButton).click();
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");
		assertSortedState(idColumn, true);

		guardAjax(emailColumnFilter).sendKeys(Keys.BACK_SPACE);
		assertPaginatorState(1, TOTAL_RECORDS);
	}

	protected void testQueryStringLoading(String type) {
		open(type, "p=5");
		assertPaginatorState(5, TOTAL_RECORDS);
		guardAjax(emailColumn).click();
		assertPaginatorState(1, TOTAL_RECORDS);
		assertSortedState(emailColumn, true);

		open(type, "p=4&email=5");
		assertPaginatorState(4, 38);
		assertFilteredState(emailColumnFilter, "5");
		guardAjax(emailColumn).click();
		assertPaginatorState(1, 38);
		assertSortedState(emailColumn, true);

		open(type, "p=3&o=-email&q=MALE");
		int totalRecords = getRowCount();
		assertPaginatorState(3, totalRecords);
		assertSortedState(emailColumn, false);
		assertGlobalFilterState("MALE");
		guardAjax(emailColumn).click();
		assertPaginatorState(1, totalRecords);
		assertSortedState(emailColumn, true);
		assertGlobalFilterState("MALE");

		open(type, "o=dateOfBirth");
		assertPaginatorState(1);
		assertSortedState(dateOfBirthColumn, true);
	}

	protected void testCriteria() {
		guardAjax(criteriaIdBetween50And150).click();
		assertCriteriaState(idColumn, Between.range(50L, 150L), Long::valueOf);
		assertPaginatorState(1, 101);

		guardAjax(criteriaEmailLikeName1).click();
		assertCriteriaState(idColumn, Between.range(50L, 150L), Long::valueOf);
		assertCriteriaState(emailColumn, Like.startsWith("name1"), String::valueOf);
		int rowCount1 = getRowCount();
		assertTrue(rowCount1 + " must be less than 101", rowCount1 < 101);

		guardAjax(criteriaGenderIsFemale).click();
		assertCriteriaState(idColumn, Between.range(50L, 150L), Long::valueOf);
		assertCriteriaState(emailColumn, Like.startsWith("name1"), String::valueOf);
		assertCriteriaState(genderColumn, "FEMALE");
		int rowCount2 = getRowCount();
		assertTrue(rowCount2 + " must be less than " + rowCount1, rowCount2 < rowCount1);

		guardAjax(criteriaDateOfBirthBefore1950).click();
		assertCriteriaState(idColumn, Between.range(50L, 150L), Long::valueOf);
		assertCriteriaState(emailColumn, Like.startsWith("name1"), String::valueOf);
		assertCriteriaState(genderColumn, "FEMALE");
		assertCriteriaState(dateOfBirthColumn, Order.lessThan(LocalDate.of(1950, 1, 1)), LocalDate::parse);
		int rowCount3 = getRowCount();
		assertTrue(rowCount3 + " must be less than " + rowCount2, rowCount3 < rowCount2);

		guardAjax(criteriaIdBetween50And150).click(); // Uncheck
		assertCriteriaState(emailColumn, Like.startsWith("name1"), String::valueOf);
		assertCriteriaState(genderColumn, "FEMALE");
		int rowCount4 = getRowCount();
		assertTrue(rowCount4 + " must be more than " + rowCount3, rowCount4 > rowCount3);

		guardAjax(criteriaEmailLikeName1).click(); // Uncheck
		assertCriteriaState(genderColumn, "FEMALE");
		int rowCount5 = getRowCount();
		assertTrue(rowCount5 + " must be more than " + rowCount4, rowCount5 > rowCount4);

		guardAjax(criteriaGenderIsFemale).click(); // Uncheck
		int rowCount6 = getRowCount();
		assertTrue(rowCount6 + " must be more than " + rowCount5, rowCount6 > rowCount5);

		guardAjax(criteriaDateOfBirthBefore1950).click(); // Uncheck
		assertPaginatorState(1, TOTAL_RECORDS);
	}

	protected void testFilterOptions() {
		Select genderColumnFilterOptions = new Select(genderColumnFilter);
		int matches = 0;

		for (Gender gender : Gender.values()) {
			guardAjax(genderColumnFilterOptions).selectByValue(gender.name());
			assertFilteredState(genderColumnFilter, gender.name());
			matches += getRowCount();
		}

		assertEquals("total matches", TOTAL_RECORDS, matches);
	}

	protected void testDTO() {
		assertNoCartesianProduct();

		guardAjax(addressStringColumn).click();
		assertSortedState(addressStringColumn, true);
		assertNoCartesianProduct();

		guardAjax(addressStringColumnFilter).sendKeys("11");
		assertPaginatorState(1, 11);
		assertFilteredState(addressStringColumnFilter, "11");
		assertNoCartesianProduct();

		guardAjax(totalPhonesColumn).click();
		assertPaginatorState(1, 11);
		assertSortedState(totalPhonesColumn, true);
		assertNoCartesianProduct();

		guardAjax(addressStringColumnFilter).sendKeys(Keys.BACK_SPACE, Keys.BACK_SPACE);
		assertPaginatorState(1, TOTAL_RECORDS);
		assertSortedState(totalPhonesColumn, true);
		assertNoCartesianProduct();

		guardAjax(totalPhonesColumnFilter).sendKeys("3");
		assertFilteredState(totalPhonesColumnFilter, "3");
		assertNoCartesianProduct();
	}

	protected void testOneToOne() {
		assertNoCartesianProduct();
		testGlobalFilter(false);

		guardAjax(address_houseNumberColumn).click();
		assertSortedState(address_houseNumberColumn, true);
		assertNoCartesianProduct();

		guardAjax(address_houseNumberColumnFilter).sendKeys("11");
		assertPaginatorState(1, 11);
		assertFilteredState(address_houseNumberColumnFilter, "11");
		assertNoCartesianProduct();

		guardAjax(address_stringColumn).click();
		assertPaginatorState(1, 11);

		if (isHibernate() && database == POSTGRESQL) {
			System.out.println("SKIPPING assertSortedState(address.string) for Hibernate+PostgreSQL because it orders 'Street110, Street111, Street11, Street112, ...' instead of 'Street110, Street11, Street111, Street112, ...'."); // TODO: investigate
		}
		else {
			assertSortedState(address_stringColumn, true);
		}

		assertNoCartesianProduct();

		guardAjax(address_houseNumberColumnFilter).sendKeys(Keys.BACK_SPACE, Keys.BACK_SPACE);
		assertPaginatorState(1, TOTAL_RECORDS);

		if (!(isHibernate() && database == POSTGRESQL)) {
			assertSortedState(address_stringColumn, true);
		}

		assertNoCartesianProduct();

		if (isOpenJPA() || isEclipseLink()) {
			System.out.println("SKIPPING assertFilteredState(address.string) for OpenJPA and EclipseLink because it doesn't support derived properties like Hibernate @Formula; the intended test is however already covered by testDTO().");
		}
		else {
			guardAjax(address_stringColumnFilter).sendKeys("11");
			assertPaginatorState(1, 11);
			assertFilteredState(address_stringColumnFilter, "11");
			assertNoCartesianProduct();

			guardAjax(address_stringColumnFilter).sendKeys(Keys.BACK_SPACE, Keys.BACK_SPACE);
			assertPaginatorState(1, TOTAL_RECORDS);
		}
	}

	protected void testOneToMany() {
		assertNoCartesianProduct();
		assertPaginatorState(1, TOTAL_RECORDS, true);
		testGlobalFilter(true);

		boolean skipAssertSortedState = (isEclipseLink() || isOpenJPA()) && isLazy();

		if (skipAssertSortedState) {
			if (isEclipseLink()) {
				System.out.println("SKIPPING assertSortedState(phones.number) for EclipseLink because it doesn't support join fetch with range and therefore sorting can't run in same query"); // TODO: improve?
			}
			else if (isOpenJPA()) {
				System.out.println("SKIPPING assertSortedState(phones.number) for OpenJPA because BaseEntityService somehow performs a double join for the table"); // TODO: fix it
			}
		}
		else {
			guardAjax(phones_numberColumn).click();
			assertSortedState(phones_numberColumn, true);
			assertNoCartesianProduct();
		}

		guardAjax(phones_numberColumnFilter).sendKeys("11");
		assertFilteredState(phones_numberColumnFilter, "11");
		assertNoCartesianProduct();
		int rowCount1 = getRowCount();
		assertTrue(rowCount1 + " must be less than " + TOTAL_RECORDS, rowCount1 < TOTAL_RECORDS);

		if (!skipAssertSortedState) {
			guardAjax(phones_numberColumn).click();
			assertSortedState(phones_numberColumn, false);
			assertNoCartesianProduct();
			int rowCount2 = getRowCount();
			assertEquals("rowcount is still the same", rowCount1, rowCount2);
		}

		guardAjax(emailColumnFilter).sendKeys("1");
		assertFilteredState(emailColumnFilter, "1");
		assertFilteredState(phones_numberColumnFilter, "11");
		assertNoCartesianProduct();
		int rowCount3 = getRowCount();
		assertTrue(rowCount3 + " must be less than " + rowCount1, rowCount3 < rowCount1);

		guardAjax(phones_numberColumnFilter).sendKeys(Keys.BACK_SPACE, Keys.BACK_SPACE);
		assertPaginatorState(1, 119, true);
		assertNoCartesianProduct();

		guardAjax(emailColumnFilter).sendKeys(Keys.BACK_SPACE);
		assertPaginatorState(1, TOTAL_RECORDS, true);
		assertNoCartesianProduct();

		if (!skipAssertSortedState) {
			guardAjax(phones_numberColumn).click();
			assertSortedState(phones_numberColumn, true);
			assertNoCartesianProduct();
		}

		guardAjax(emailColumnFilter).sendKeys(Keys.BACK_SPACE);
		assertPaginatorState(1, TOTAL_RECORDS, true);

		if (!skipAssertSortedState) {
			assertSortedState(phones_numberColumn, true);
		}

		assertNoCartesianProduct();

		boolean skipAssertCriteriaState = isEclipseLink() && isLazy();

		if (skipAssertCriteriaState) {
			System.out.println("SKIPPING assertCriteriaState(phones.type) for EclipseLink because it refuses to perform a JOIN when setFirstResult/setMaxResults is used");
		}
		else {
			boolean skipAssertRowCount = isOpenJPA() && isLazy();

			if (skipAssertRowCount) {
				System.out.println("SKIPPING skipAssertRowCount(phones.type) for OpenJPA because BaseEntityService somehow performs a double join for the table"); // TODO: fix it
			}

			guardAjax(criteriaPhoneTypeMOBILE).click();
			assertCriteriaState(phones_typeColumn, "MOBILE");
			int rowCount4 = getRowCount();
			if (!skipAssertRowCount) {
				assertTrue(rowCount4 + " must be less than " + TOTAL_RECORDS, rowCount4 < TOTAL_RECORDS);
			}
			assertNoCartesianProduct();

			guardAjax(criteriaPhoneTypeHOME).click();
			assertCriteriaState(phones_typeColumn, "MOBILE", "HOME");
			int rowCount5 = getRowCount();
			if (!skipAssertRowCount) {
				assertTrue(rowCount5 + " must be less than " + rowCount4, rowCount5 < rowCount4);
			}
			assertNoCartesianProduct();

			guardAjax(criteriaPhoneTypeWORK).click();
			assertCriteriaState(phones_typeColumn, "MOBILE", "HOME", "WORK");
			int rowCount6 = getRowCount();
			if (!skipAssertRowCount) {
				assertTrue(rowCount6 + " must be less than " + rowCount5, rowCount6 < rowCount5);
			}
			assertNoCartesianProduct();

			guardAjax(criteriaPhoneTypeMOBILE).click(); // Uncheck
			assertCriteriaState(phones_typeColumn, "HOME", "WORK");
			int rowCount7 = getRowCount();
			if (!skipAssertRowCount) {
				assertTrue(rowCount7 + " must be more than " + rowCount6, rowCount7 > rowCount6);
			}
			assertNoCartesianProduct();

			guardAjax(criteriaPhoneTypeHOME).click(); // Uncheck
			assertCriteriaState(phones_typeColumn, "WORK");
			int rowCount8 = getRowCount();
			if (!skipAssertRowCount) {
				assertTrue(rowCount8 + " must be more than " + rowCount7, rowCount8 > rowCount7);
			}
			assertNoCartesianProduct();

			guardAjax(criteriaPhoneTypeWORK).click(); // Uncheck
			assertPaginatorState(1, TOTAL_RECORDS, true);
			assertNoCartesianProduct();
		}
	}

	protected void testElementCollection() {
		assertNoCartesianProduct();
		assertPaginatorState(1, TOTAL_RECORDS, true);

		if (isOpenJPA()) {
			System.out.println("SKIPPING testGlobalFilter() in testElementCollection() for OpenJPA because it doesn't like a LIKE on @ElementCollection"); // TODO: improve?
		}
		else {
			testGlobalFilter(true);
		}

		guardAjax(criteriaGroupUSER).click();
		assertCriteriaState(groupsColumn, "USER");
		int rowCount1 = getRowCount();
		assertTrue(rowCount1 + " must be less than " + TOTAL_RECORDS, rowCount1 < TOTAL_RECORDS);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupMANAGER).click();
		assertCriteriaState(groupsColumn, "USER", "MANAGER");
		int rowCount2 = getRowCount();
		assertTrue(rowCount2 + " must be less than " + rowCount1, rowCount2 < rowCount1);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupADMINISTRATOR).click();
		assertCriteriaState(groupsColumn, "USER", "MANAGER", "ADMINISTRATOR");
		int rowCount3 = getRowCount();
		assertTrue(rowCount3 + " must be less than " + rowCount2, rowCount3 < rowCount2);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupDEVELOPER).click();
		assertCriteriaState(groupsColumn, "USER", "MANAGER", "ADMINISTRATOR", "DEVELOPER");
		int rowCount4 = getRowCount();
		assertTrue(rowCount4 + " must be less than " + rowCount3, rowCount4 < rowCount3);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupUSER).click(); // Uncheck
		assertCriteriaState(groupsColumn, "MANAGER", "ADMINISTRATOR", "DEVELOPER");
		int rowCount5 = getRowCount();
		assertTrue(rowCount5 + " must be more than " + rowCount4, rowCount5 > rowCount4);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupMANAGER).click(); // Uncheck
		assertCriteriaState(groupsColumn, "ADMINISTRATOR", "DEVELOPER");
		int rowCount6 = getRowCount();
		assertTrue(rowCount6 + " must be more than " + rowCount5, rowCount6 > rowCount5);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupADMINISTRATOR).click(); // Uncheck
		assertCriteriaState(groupsColumn, "DEVELOPER");
		int rowCount7 = getRowCount();
		assertTrue(rowCount7 + " must be more than " + rowCount6, rowCount7 > rowCount6);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupDEVELOPER).click(); // Uncheck
		assertPaginatorState(1, TOTAL_RECORDS, true);
		assertNoCartesianProduct();
	}

	protected void testManyToOne() {
		assertNoCartesianProduct();
		assertPaginatorState(1);
		int totalRecords = getRowCount();

		guardAjax(idColumnFilter).sendKeys("2");
		assertFilteredState(idColumnFilter, "2");
		assertNoCartesianProduct();

		globalFilter.sendKeys("19");
		guardAjax(globalFilterButton).click();
		assertGlobalFilterState("19");
		assertFilteredState(idColumnFilter, "2");
		assertNoCartesianProduct();

		guardAjax(emailColumn).click();
		assertSortedState(emailColumn, true);
		assertGlobalFilterState("19");
		assertFilteredState(idColumnFilter, "2");
		assertNoCartesianProduct();

		guardAjax(idColumnFilter).sendKeys(Keys.BACK_SPACE);
		assertGlobalFilterState("19");
		assertSortedState(emailColumn, true);
		assertNoCartesianProduct();

		guardAjax(emailColumnFilter).sendKeys("2");
		assertGlobalFilterState("19");
		assertFilteredState(emailColumnFilter, "2");
		assertSortedState(emailColumn, true);
		assertNoCartesianProduct();

		globalFilter.clear();
		guardAjax(globalFilterButton).click();
		assertFilteredState(emailColumnFilter, "2");
		assertSortedState(emailColumn, true);
		assertNoCartesianProduct();

		guardAjax(emailColumnFilter).sendKeys(Keys.BACK_SPACE);
		assertPaginatorState(1, totalRecords);
		assertSortedState(emailColumn, true);
		assertNoCartesianProduct();
	}

	protected void testGlobalFilter(boolean oneToManyOrElementCollection) {
		guardAjax(idColumnFilter).sendKeys("2");
		assertFilteredState(idColumnFilter, "2");
		assertPaginatorState(1, 39, oneToManyOrElementCollection);
		assertNoCartesianProduct();

		globalFilter.sendKeys("name1");
		guardAjax(globalFilterButton).click();
		assertPaginatorState(1, 23);
		assertGlobalFilterState("name1");
		assertFilteredState(idColumnFilter, "2");
		assertNoCartesianProduct();

		guardAjax(emailColumn).click();
		assertPaginatorState(1, 23);
		assertSortedState(emailColumn, true);
		assertGlobalFilterState("name1");
		assertFilteredState(idColumnFilter, "2");
		assertNoCartesianProduct();

		guardAjax(idColumnFilter).sendKeys(Keys.BACK_SPACE);
		assertPaginatorState(1, 111);
		assertSortedState(emailColumn, true);
		assertGlobalFilterState("name1");
		assertNoCartesianProduct();

		globalFilter.clear();
		guardAjax(globalFilterButton).click();
		assertPaginatorState(1, TOTAL_RECORDS, oneToManyOrElementCollection);
		assertNoCartesianProduct();
	}


	// Assertions -----------------------------------------------------------------------------------------------------

	protected void assertPaginatorState(int currentPage) {
		assertPaginatorState(currentPage, getRowCount(), false);
	}

	protected void assertPaginatorState(int currentPage, int expectedTotalRecords) {
		assertPaginatorState(currentPage, expectedTotalRecords, false);
	}

	protected void assertPaginatorState(int currentPage, int expectedTotalRecords, boolean oneToManyOrElementCollection) {
		int totalRecords = getRowCount();
		int startRecord = ((currentPage - 1) * ROWS_PER_PAGE) + 1;
		int endRecord = min(startRecord + ROWS_PER_PAGE - 1, totalRecords);
		int pageCount = (totalRecords / ROWS_PER_PAGE) + ((totalRecords % ROWS_PER_PAGE > 0) ? 1 : 0);
		int visibleRecords = endRecord - startRecord + 1;

		if (oneToManyOrElementCollection && isOpenJPA()) {
			System.out.println("SKIPPING assertEquals(visibleRecords, getCells(idColumn).size()) for OpenJPA because it doesn't correctly limit @OneToMany and @ElementCollection on root"); // TODO: improve?
		}
		else {
			assertEquals("visible records", visibleRecords, getCells(idColumn).size());
		}

		assertEquals("total records", expectedTotalRecords, totalRecords);
		assertEquals("page report", "Showing " + startRecord + " - " + endRecord + " of " + totalRecords + " records", pageReport.getText());
		assertEquals("page count", min(pageCount, 10), pages.size());
		assertEquals("page current", String.valueOf(currentPage), pageCurrent.getText());
		assertEquals("page query string", (currentPage == 1) ? null : String.valueOf(currentPage), getQueryParameter(QUERY_PARAMETER_PAGE));
	}

	protected void assertSortedState(WebElement column, boolean ascending) {
		String field = column.findElement(By.cssSelector(".ui-column-title")).getText();

		assertSortedState(column, ascending, "id".equals(field));
	}

	protected void assertSortedState(WebElement column, boolean ascending, boolean isDefaultOrderBy) {
		String field = column.findElement(By.cssSelector(".ui-column-title")).getText();
		String sortableColumnClass = column.findElement(By.cssSelector(".ui-sortable-column-icon")).getAttribute("class");

		assertTrue(field + " column must be active", activeColumn.findElement(By.cssSelector(".ui-column-title")).getText().equals(field));
		assertEquals(field + " column must be sorted", ascending, sortableColumnClass.contains("ui-icon-triangle-1-n"));
		assertEquals("order query string", (isDefaultOrderBy && !ascending) ? null : ((ascending ? "" : "-") + field), getQueryParameter(QUERY_PARAMETER_ORDER));

		List<WebElement> cells = getCells(column);
		List<String> actualValues = cells.stream().map(this::sortIterableIfNecessary).collect(toList());
		List<String> expectedValues;

		if ("id".equals(field)) {
			expectedValues = actualValues.stream().map(Integer::valueOf).sorted(ascending ? naturalOrder() : reverseOrder()).map(String::valueOf).collect(toList());
		}
		else {
			expectedValues = actualValues.stream().sorted(ascending ? naturalOrder() : reverseOrder()).collect(toList());

			if (!expectedValues.equals(actualValues)) {
				Collator collator = Collator.getInstance(Locale.ENGLISH);
				expectedValues.sort(ascending ? collator : collator.reversed()); // TODO: find a better way. Problem is, lazy model sorts by DB collation and non-lazy model sorts by Java collation, however they don't necessarily agree on each other (e.g. @ before 0).
			}
		}

		assertEquals(field + " ordering", expectedValues, actualValues);
	}

	private String sortIterableIfNecessary(WebElement cell) {
		String text = cell.getText();

		if (text.contains("\n")) {
			Comparator<String> comparator = isSortedAscending(activeColumn) ? naturalOrder() : reverseOrder();
			text = stream(text.split("\n")).sorted(comparator).collect(joining("\n"));
		}

		return text;
	}

	private static boolean isSortedAscending(WebElement column) {
		return "ascending".equals(column.getAttribute("aria-sort"));
	}

	protected void assertFilteredState(WebElement filter, String filterValue) {
		WebElement column = filter.findElement(By.xpath(".."));
		String field = column.findElement(By.cssSelector(".ui-column-title")).getText();

		WebElement input = "select".equals(filter.getTagName()) ? new Select(filter).getFirstSelectedOption() : filter;
		String actualFilterValue = input.getAttribute("value");
		assertEquals("filter value", filterValue, actualFilterValue);
		assertEquals("filter query string", actualFilterValue, getQueryParameter(field));

		List<String> actualValues = getCells(column).stream().map(WebElement::getText).collect(toList());
		assertTrue(field + " filtering " + actualValues + " matches " + filterValue, actualValues.stream().allMatch(value -> value.contains(filterValue)));
	}

	protected void assertGlobalFilterState(String filterValue) {
		String actualFilterValue = globalFilter.getAttribute("value");
		assertEquals("filter value", filterValue, actualFilterValue);
		assertEquals("filter query string", actualFilterValue, getQueryParameter(QUERY_PARAMETER_SEARCH));

		for (WebElement row : rows) {
			String rowAsString = row.getText();
			assertTrue("row " + rowAsString + " matches global filter " + filterValue, rowAsString.contains(filterValue));
		}
	}

	protected void assertCriteriaState(WebElement column, Criteria<?> criteria, Function<String, ?> parser) {
		String field = column.findElement(By.cssSelector(".ui-column-title")).getText();
		List<String> actualValues = getCells(column).stream().map(WebElement::getText).collect(toList());
		assertTrue(field + " criteria " + actualValues + " matches " + criteria, actualValues.stream().allMatch(value -> criteria.applies(parser.apply(value))));
	}

	protected void assertCriteriaState(WebElement column, String... criteriaValues) {
		String field = column.findElement(By.cssSelector(".ui-column-title")).getText();
		List<String> expectedValues = asList(criteriaValues);
		getCells(column).stream().map(WebElement::getText).forEach(text -> {
			List<String> actualValues = asList(text.split("\n"));
			assertTrue(field + " criteria " + actualValues + " contains any " + expectedValues, actualValues.stream().anyMatch(value -> expectedValues.contains(value)));
		});
	}

	private List<WebElement> getCells(WebElement column) {
		int columnIndex = column.findElement(By.xpath("..")).findElements(By.tagName("th")).stream().map(WebElement::getText).collect(toList()).indexOf(column.getText()); // Awkward.
		return browser.findElements(By.cssSelector("#form\\:table_data td:nth-child(" + (columnIndex + 1) + ")"));
	}

	protected void assertNoCartesianProduct() {
		List<Integer> actualIds = getCells(idColumn).stream().map(WebElement::getText).map(Integer::valueOf).sorted().collect(toList());
		List<Integer> expectedIds = actualIds.stream().distinct().collect(toList());
		assertEquals("No cartesian product", expectedIds, actualIds);
	}

}
