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
package org.omnifaces.optimusfaces.test;

import static java.lang.Math.min;
import static java.lang.System.getProperty;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.reverseOrder;
import static java.util.logging.Level.OFF;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.optimusfaces.model.PagedDataModel.QUERY_PARAMETER_ORDER;
import static org.omnifaces.optimusfaces.model.PagedDataModel.QUERY_PARAMETER_PAGE;
import static org.omnifaces.optimusfaces.model.PagedDataModel.QUERY_PARAMETER_SEARCH;
import static org.omnifaces.optimusfaces.model.PagedDataModel.QUERY_PARAMETER_SELECTION;
import static org.omnifaces.optimusfaces.test.service.StartupService.ROWS_PER_PAGE;
import static org.omnifaces.optimusfaces.test.service.StartupService.TOTAL_RECORDS;
import static org.omnifaces.persistence.Database.POSTGRESQL;
import static org.openqa.selenium.Keys.BACK_SPACE;
import static org.openqa.selenium.Keys.SPACE;
import static org.openqa.selenium.Keys.TAB;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.text.Collator;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.omnifaces.optimusfaces.test.model.Gender;
import org.omnifaces.persistence.Database;
import org.omnifaces.persistence.criteria.Between;
import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.criteria.Order;
import org.omnifaces.util.Servlets;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(OptimusFacesIT.TestLogger.class)
public abstract class OptimusFacesIT {

	private static final int TIMEOUT_IN_SECONDS = 5;

	private static Database database;

	private WebDriver browser;

	@ArquillianResource
	private URL baseURL;

    @BeforeAll
    public void setup() {
        WebDriverManager.chromedriver().setup();
        var chrome = new ChromeDriver(new ChromeOptions().addArguments("--no-sandbox", "--headless"));
        chrome.setLogLevel(Level.INFO);
        browser = chrome;
        PageFactory.initElements(browser, this);
    }

	protected static <T extends OptimusFacesIT> WebArchive createArchive(Class<T> testClass, Database database) {
		OptimusFacesIT.database = database;
		var packageName = testClass.getPackage().getName();
		var maven = Maven.resolver();

		var archive = create(WebArchive.class, testClass.getSimpleName() + ".war")
			.addPackage(packageName + ".model")
			.addPackage(packageName + ".model.dto")
			.addPackage(packageName + ".service")
			.addPackage(packageName + ".view")
			.addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
			.addAsLibrary(new File(getProperty("optimusfaces.jar")))
			.addAsLibraries(maven.loadPomFromFile("pom.xml").importCompileAndRuntimeDependencies().resolve().withTransitivity().asFile())
			.addAsLibraries(maven.resolve("org.omnifaces:omnifaces:" + getProperty("test.omnifaces.version"), "org.primefaces:primefaces:jar:jakarta:" + getProperty("test.primefaces.version")).withTransitivity().asFile());

		addDataSourceConfig(database, archive);
		addPersistenceConfig(maven, archive);
		addResources(new File(testClass.getClassLoader().getResource(packageName).getFile()), "", archive::addAsWebResource);

		return archive;
	}

	private static void addDataSourceConfig(Database database, WebArchive archive) {
		var dataSourceConfigXml = isWildFly() ? "wildfly-ds.xml" : isGlassFish() ? "glassfish-resources.xml" : isTomEE() ? "resources.xml" : null;

		if (dataSourceConfigXml != null) {
			archive.addAsWebInfResource("WEB-INF/" + dataSourceConfigXml + "/" + database.name().toLowerCase() + ".xml", dataSourceConfigXml);
		}
	}

	private static void addPersistenceConfig(MavenResolverSystem maven, WebArchive archive) {
		var persistenceConfigXml = getProperty("profile.id") + ".xml";
		var persistenceXml = "META-INF/persistence.xml";

		archive.addAsResource(persistenceXml + "/" + persistenceConfigXml, persistenceXml);

		if (isGlassFish() && isHibernate()) {
			// Does not work when placed in glassfish/modules? TODO: investigate.
			archive.addAsLibraries(maven.resolve("org.hibernate.orm:hibernate-core:" + getProperty("test.glassfish-hibernate.version"), "dom4j:dom4j:1.6.1").withTransitivity().asFile());
		}
	}

	private static void addResources(File root, String directory, BiConsumer<File, String> archiveConsumer) {
		for (File file : root.listFiles()) {
			var path = directory + "/" + file.getName();

			if (file.isFile()) {
				archiveConsumer.accept(file, path);
			}
			else if (file.isDirectory()) {
				addResources(file, path, archiveConsumer);
			}
		}
	}

	public static class TestLogger implements BeforeEachCallback, AfterEachCallback {

		@Override
		public void beforeEach(ExtensionContext context) throws Exception {
			System.out.println(""
					+ "\n"
					+ "\n    ============================================================================================="
					+ "\n    Starting " + context.getTestMethod().get().getName() + " ..."
					+ "\n    ============================================================================================="
					+ "\n"
				);
		}

		@Override
		public void afterEach(ExtensionContext context) throws Exception {
			System.out.println(""
					+ "\n    ============================================================================================="
					+ "\n    " + context.getTestMethod().get().getName() + " finished!"
					+ "\n    ============================================================================================="
					+ "\n"
					+ "\n"
				);
		}
	}

	@BeforeEach
	public void init() {
		Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(OFF); // MyFaces triggers for some reason a lot of awkward JS "illegal selector" and CSS "em has to be a px" warnings.
	}

	protected void open(String type) {
		open(type, null);
	}

	protected void open(String type, String queryString) {
		var url = new StringBuilder().append(baseURL).append(OptimusFacesIT.class.getSimpleName()).append(type).append(".xhtml");

		if (queryString != null) {
			url.append("?").append(queryString);
		}

		browser.manage().deleteAllCookies(); // Else IT on pagination/sorting may fail because they're apparently cached somewhere in session. TODO: investigate
		browser.get(url.toString());
		waitUntil(() -> executeScript("return !!Object.keys(window.PrimeFaces.widgets).length"));
	}

    protected void guardFacesAjax(Runnable action) {
        var uuid = UUID.randomUUID().toString();
        executeScript("window.$ajax=null;window.faces.ajax.addOnEvent(data=>{if(data.status=='complete')window.$ajax='" + uuid + "'})");
        action.run();
        waitUntil(() -> executeScript("return window.$ajax=='" + uuid + "'"));
    }

    protected void guardPrimeFacesAjax(Runnable action) {
        var uuid = UUID.randomUUID().toString();
        executeScript("window.$ajax=null;$(document).one('pfAjaxComplete', () => window.$ajax='" + uuid + "')");
        action.run();
        waitUntil(() -> executeScript("return window.$ajax=='" + uuid + "'"));
    }

    private void waitUntil(Supplier<Boolean> predicate) {
        new WebDriverWait(browser, ofSeconds(TIMEOUT_IN_SECONDS)).until($ -> predicate.get());
    }

    @SuppressWarnings("unchecked")
    protected <T> T executeScript(String script) {
        return (T) ((JavascriptExecutor) browser).executeScript(script);
    }

	protected String getQueryParameter(String name) {
		if (browser.getCurrentUrl().contains("?")) {
			var queryString = browser.getCurrentUrl().split("\\?", 2)[1];
			var params = Servlets.toParameterMap(queryString);
			var values = params.get(name);

			if (values != null) {
				return values.get(0);
			}
		}

		return null;
	}

	protected int getRowCount() {
		return Integer.parseInt(rowCount.getText());
	}

	protected static boolean isWildFly() {
		return getProperty("profile.id").startsWith("wildfly-");
	}

	protected static boolean isGlassFish() {
		return getProperty("profile.id").startsWith("glassfish-");
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

    @FindBy(id="form:table:genderFilter")
    private WebElement genderColumnDropdownFilter;

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

	@FindBy(css="#form\\:table_data tr:nth-child(5) td:first-child")
	private WebElement fifthRow;

	@FindBy(css="#form\\:table_data tr.ui-state-highlight")
	private WebElement selectedRow;

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

	@FindBy(id="rowCount")
	private WebElement rowCount;

	@FindBy(id="selection")
	private WebElement selection;


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
	public void testLazySelection() {
		open("Lazy");
		testSelection();
	}

	@Test
	public void testNonLazySelection() {
		open("NonLazy");
		testSelection();
	}

	@Test
	public void testLazyPagingSortingFilteringAndSelection() {
		open("Lazy");
		testPagingSortingFilteringAndSelection();
	}

	@Test
	public void testNonLazyPagingSortingFilteringAndSelection() {
		open("NonLazy");
		testPagingSortingFilteringAndSelection();
	}

	@Test
	public void testLazyStatelessPagingSortingFilteringAndSelection() {
		open("LazyStateless");
		testPagingSortingFilteringAndSelection();
	}

	@Test
	public void testNonLazyStatelessPagingSortingFilteringAndSelection() {
		open("NonLazyStateless");
		testPagingSortingFilteringAndSelection();
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
		assertEquals(ROWS_PER_PAGE, rows.size(), "row count");
		assertPaginatorState(1);
		assertSortedState(idColumn, false);
	}

	protected void testPaging() {
		guardPrimeFacesAjax(pageNext::click);
		assertPaginatorState(2);
		assertSortedState(idColumn, false);

		guardPrimeFacesAjax(pagePrevious::click);
		assertPaginatorState(1);
		assertSortedState(idColumn, false);

		guardPrimeFacesAjax(pageLast::click);
		assertPaginatorState(TOTAL_RECORDS / ROWS_PER_PAGE);
		assertSortedState(idColumn, false);

		guardPrimeFacesAjax(pagePrevious::click);
		assertPaginatorState(TOTAL_RECORDS / ROWS_PER_PAGE - 1);
		assertSortedState(idColumn, false);

		guardPrimeFacesAjax(pageNext::click);
		assertPaginatorState(TOTAL_RECORDS / ROWS_PER_PAGE);
		assertSortedState(idColumn, false);

		guardPrimeFacesAjax(pageFirst::click);
		assertPaginatorState(1);
		assertSortedState(idColumn, false);
	}

	protected void testSorting() {
		clickColumn(idColumn);
		assertPaginatorState(1);
		assertSortedState(idColumn, true);

		clickColumn(idColumn);
		assertPaginatorState(1);
		assertSortedState(idColumn, false);

		clickColumn(idColumn);
		assertPaginatorState(1);
		assertSortedState(idColumn, true);

		clickColumn(emailColumn);
		assertPaginatorState(1);
		assertSortedState(emailColumn, true);

		clickColumn(emailColumn);
		assertPaginatorState(1);
		assertSortedState(emailColumn, false);

		clickColumn(genderColumn);
		assertPaginatorState(1);
		assertSortedState(genderColumn, true);

		clickColumn(emailColumn);
		assertPaginatorState(1);
		assertSortedState(emailColumn, true);

		clickColumn(dateOfBirthColumn);
		assertPaginatorState(1);
		assertSortedState(dateOfBirthColumn, true);

		clickColumn(dateOfBirthColumn);
		assertPaginatorState(1);
		assertSortedState(dateOfBirthColumn, false);
	}

	private void clickColumn(WebElement column) {
	    guardPrimeFacesAjax(column.findElement(By.cssSelector(".ui-column-title"))::click);
	}

	protected void testDefaultOrderBy() {
		assertPaginatorState(1);
		assertSortedState(emailColumn, false, true);

		clickColumn(emailColumn);
		assertPaginatorState(1);
		assertSortedState(emailColumn, true);

		clickColumn(idColumn);
		assertPaginatorState(1);
		assertSortedState(idColumn, true);

		clickColumn(genderColumn);
		assertPaginatorState(1);
		assertSortedState(genderColumn, true);
	}

	protected void testFiltering() {
		guardPrimeFacesAjax(() -> idColumnFilter.sendKeys("3"));
		var totalRecords1 = getRowCount();
		assertPaginatorState(1, 38);
		assertFilteredState(idColumnFilter, "3");

		globalFilter.sendKeys("FEMALE");
		guardPrimeFacesAjax(globalFilterButton::click);
		var totalRecords2 = getRowCount();
		assertTrue(totalRecords2 < totalRecords1, totalRecords2 + " must be less than " + totalRecords1);
		assertGlobalFilterState("FEMALE");
		assertFilteredState(idColumnFilter, "3");

		globalFilter.clear();
		guardPrimeFacesAjax(globalFilterButton::click);
		assertPaginatorState(1, 38);
		assertFilteredState(idColumnFilter, "3");

		clearColumnFilter(idColumnFilter);
		assertPaginatorState(1, TOTAL_RECORDS);

		guardPrimeFacesAjax(() -> genderColumnFilter.sendKeys("FEMALE"));
		var totalRecords3 = getRowCount();
		assertPaginatorState(1);
		assertFilteredState(genderColumnFilter, "FEMALE");

		guardPrimeFacesAjax(() -> emailColumnFilter.sendKeys("1"));
		var totalRecords4 = getRowCount();
		assertTrue(totalRecords4 < totalRecords3, totalRecords4 + " must be less than " + totalRecords3);
		assertPaginatorState(1);
		assertFilteredState(emailColumnFilter, "1");
		assertFilteredState(genderColumnFilter, "FEMALE");

		clearColumnFilter(genderColumnFilter);
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");

		clearColumnFilter(emailColumnFilter);
		assertPaginatorState(1, TOTAL_RECORDS);
	}

	private void clearColumnFilter(WebElement columnFilter) {
		columnFilter.clear();
		guardPrimeFacesAjax(() -> columnFilter.sendKeys(SPACE, BACK_SPACE, TAB)); // Should trigger blur event.
	}

	protected void testSelection() {
		guardPrimeFacesAjax(fifthRow::click);
		assertSelectedState(196);

		for (var nextPage = 2; nextPage <= 5; nextPage++) {
			guardPrimeFacesAjax(pageNext::click);
			assertPaginatorState(nextPage);
			guardPrimeFacesAjax(fifthRow::click);
			assertSelectedState(206 - nextPage * 10);
		}

		for (var previousPage = 4; previousPage >= 1; previousPage--) {
			guardPrimeFacesAjax(pagePrevious::click);
			assertPaginatorState(previousPage);
			guardPrimeFacesAjax(fifthRow::click);
			assertSelectedState(206 - previousPage * 10);
		}
	}

	protected void testPagingSortingFilteringAndSelection() {
		guardPrimeFacesAjax(pageNext::click);
		assertPaginatorState(2);

		guardPrimeFacesAjax(() -> emailColumnFilter.sendKeys("1"));
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");

		clickColumn(emailColumn);
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");
		assertSortedState(emailColumn, true);

		for (var nextPage = 2; nextPage <= 5; nextPage++) {
			guardPrimeFacesAjax(pageNext::click);
			assertPaginatorState(nextPage, 119);
			assertFilteredState(emailColumnFilter, "1");
			assertSortedState(emailColumn, true);
		}

		for (var previousPage = 4; previousPage >= 1; previousPage--) {
			guardPrimeFacesAjax(pagePrevious::click);
			assertPaginatorState(previousPage, 119);
			assertFilteredState(emailColumnFilter, "1");
			assertSortedState(emailColumn, true);
		}

		clickColumn(emailColumn);
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");
		assertSortedState(emailColumn, false);

		globalFilter.sendKeys("FEMALE");
		guardPrimeFacesAjax(globalFilterButton::click);
		var totalRecords1 = getRowCount();
		assertTrue(totalRecords1 < 119, totalRecords1 + " must be less than 119");
		assertFilteredState(emailColumnFilter, "1");
		assertGlobalFilterState("FEMALE");
		assertSortedState(emailColumn, false);

		clickColumn(idColumn);
		var totalRecords2 = getRowCount();
		assertTrue(totalRecords1 == totalRecords2, totalRecords1 + " must be equal to " + totalRecords2);
		assertFilteredState(emailColumnFilter, "1");
		assertGlobalFilterState("FEMALE");
		assertSortedState(idColumn, true);

		globalFilter.clear();
		guardPrimeFacesAjax(globalFilterButton::click);
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");
		assertSortedState(idColumn, true);

		clearColumnFilter(emailColumnFilter);
		assertPaginatorState(1, TOTAL_RECORDS);
	}

	protected void testQueryStringLoading(String type) {
		open(type, "p=5");
		assertPaginatorState(5, TOTAL_RECORDS);
		clickColumn(emailColumn);
		assertPaginatorState(1, TOTAL_RECORDS);
		assertSortedState(emailColumn, true);

		open(type, "p=4&email=5");
		assertPaginatorState(4, 38);
		assertFilteredState(emailColumnFilter, "5");
		clickColumn(emailColumn);
		assertPaginatorState(1, 38);
		assertSortedState(emailColumn, true);

		open(type, "p=3&o=-email&q=MALE");
		var totalRecords = getRowCount();
		assertPaginatorState(3, totalRecords);
		assertSortedState(emailColumn, false);
		assertGlobalFilterState("MALE");
		clickColumn(emailColumn);
		assertPaginatorState(1, totalRecords);
		assertSortedState(emailColumn, true);
		assertGlobalFilterState("MALE");

		open(type, "o=dateOfBirth");
		assertPaginatorState(1);
		assertSortedState(dateOfBirthColumn, true);

		open(type, "s=195");
		assertSelectedState(195);
	}

	protected void testCriteria() {
		guardFacesAjax(criteriaIdBetween50And150::click);
		assertCriteriaState(idColumn, Between.range(50L, 150L), Long::valueOf);
		assertPaginatorState(1, 101);

		guardFacesAjax(criteriaEmailLikeName1::click);
		assertCriteriaState(idColumn, Between.range(50L, 150L), Long::valueOf);
		assertCriteriaState(emailColumn, Like.startsWith("name1"), String::valueOf);
		var rowCount1 = getRowCount();
		assertTrue(rowCount1 < 101, rowCount1 + " must be less than 101");

		guardFacesAjax(criteriaGenderIsFemale::click);
		assertCriteriaState(idColumn, Between.range(50L, 150L), Long::valueOf);
		assertCriteriaState(emailColumn, Like.startsWith("name1"), String::valueOf);
		assertCriteriaState(genderColumn, "FEMALE");
		var rowCount2 = getRowCount();
		assertTrue(rowCount2 < rowCount1, rowCount2 + " must be less than " + rowCount1);

		guardFacesAjax(criteriaDateOfBirthBefore1950::click);
		assertCriteriaState(idColumn, Between.range(50L, 150L), Long::valueOf);
		assertCriteriaState(emailColumn, Like.startsWith("name1"), String::valueOf);
		assertCriteriaState(genderColumn, "FEMALE");
		assertCriteriaState(dateOfBirthColumn, Order.lessThan(LocalDate.of(1950, 1, 1)), LocalDate::parse);
		var rowCount3 = getRowCount();
		assertTrue(rowCount3 < rowCount2, rowCount3 + " must be less than " + rowCount2);

		guardFacesAjax(criteriaIdBetween50And150::click); // Uncheck
		assertCriteriaState(emailColumn, Like.startsWith("name1"), String::valueOf);
		assertCriteriaState(genderColumn, "FEMALE");
		var rowCount4 = getRowCount();
		assertTrue(rowCount4 > rowCount3, rowCount4 + " must be more than " + rowCount3);

		guardFacesAjax(criteriaEmailLikeName1::click); // Uncheck
		assertCriteriaState(genderColumn, "FEMALE");
		var rowCount5 = getRowCount();
		assertTrue(rowCount5 > rowCount4, rowCount5 + " must be more than " + rowCount4);

		guardFacesAjax(criteriaGenderIsFemale::click); // Uncheck
		var rowCount6 = getRowCount();
		assertTrue(rowCount6 > rowCount5, rowCount6 + " must be more than " + rowCount5);

		guardFacesAjax(criteriaDateOfBirthBefore1950::click); // Uncheck
		assertPaginatorState(1, TOTAL_RECORDS);
	}

	protected void testFilterOptions() {
		var matches = 0;

		for (Gender gender : Gender.values()) {
		    setPrimeFacesSelectOneMenuValue(genderColumnDropdownFilter, gender.name());
			assertFilteredState(genderColumnDropdownFilter.findElement(By.id("form:table:genderFilter_input")), gender.name());
			matches += getRowCount();
		}

		assertEquals(TOTAL_RECORDS, matches, "total matches");
	}

    private void setPrimeFacesSelectOneMenuValue(WebElement selectOneMenu, Serializable value) {
        String clientId = selectOneMenu.getAttribute("id");
        var input = selectOneMenu.findElement(By.id(clientId + "_input"));
        var itemValue = value.toString();
        var itemLabel = (String) executeScript("return $(document.getElementById('" + input.getAttribute("id") + "')).find('option[value=\"" + itemValue + "\"]').text()"); // getText() doesn't work as option is hidden. It's needed because ui-selectonemenu-item doesn't have a data-value.
        selectOneMenu.findElement(By.cssSelector(".ui-selectonemenu-trigger")).click(); // Open panel.
        var document = selectOneMenu.findElement(By.xpath("/*"));
        var panel = document.findElement(By.id(clientId + "_panel"));
        var selectItem = panel.findElement(By.cssSelector(".ui-selectonemenu-item[data-label='" + itemLabel + "']"));

        if (input.getAttribute("onchange") != null) {
            guardPrimeFacesAjax(selectItem::click);
        }
        else {
            selectItem.click();
        }
    }

	protected void testDTO() {
		assertNoCartesianProduct();

		clickColumn(addressStringColumn);
		assertSortedState(addressStringColumn, true);
		assertNoCartesianProduct();

		guardPrimeFacesAjax(() -> addressStringColumnFilter.sendKeys("11"));
		assertPaginatorState(1, 11);
		assertFilteredState(addressStringColumnFilter, "11");
		assertNoCartesianProduct();

		clickColumn(totalPhonesColumn);
		assertPaginatorState(1, 11);
		assertSortedState(totalPhonesColumn, true);
		assertNoCartesianProduct();

		clearColumnFilter(addressStringColumnFilter);
		assertPaginatorState(1, TOTAL_RECORDS);
		assertSortedState(totalPhonesColumn, true);
		assertNoCartesianProduct();

		guardPrimeFacesAjax(() -> totalPhonesColumnFilter.sendKeys("3"));
		assertFilteredState(totalPhonesColumnFilter, "3");
		assertNoCartesianProduct();
	}

	protected void testOneToOne() {
		assertNoCartesianProduct();
		testGlobalFilter(false);

		clickColumn(address_houseNumberColumn);
		assertSortedState(address_houseNumberColumn, true);
		assertNoCartesianProduct();

		guardPrimeFacesAjax(() -> address_houseNumberColumnFilter.sendKeys("11"));
		assertPaginatorState(1, 11);
		assertFilteredState(address_houseNumberColumnFilter, "11");
		assertNoCartesianProduct();

		clickColumn(address_stringColumn);
		assertPaginatorState(1, 11);

		if (isHibernate() && database == POSTGRESQL) {
			System.out.println("SKIPPING assertSortedState(address.string) for Hibernate+PostgreSQL because it orders 'Street110, Street111, Street11, Street112, ...' instead of 'Street110, Street11, Street111, Street112, ...'."); // TODO: investigate
		}
		else {
			assertSortedState(address_stringColumn, true);
		}

		assertNoCartesianProduct();

		clearColumnFilter(address_houseNumberColumnFilter);
		assertPaginatorState(1, TOTAL_RECORDS);

		if (!isHibernate() || database != POSTGRESQL) {
			assertSortedState(address_stringColumn, true);
		}

		assertNoCartesianProduct();

		if (isOpenJPA() || isEclipseLink()) {
			System.out.println("SKIPPING assertFilteredState(address.string) for OpenJPA and EclipseLink because it doesn't support derived properties like Hibernate @Formula; the intended test is however already covered by testDTO().");
		}
		else {
			guardPrimeFacesAjax(() -> address_stringColumnFilter.sendKeys("11"));
			assertPaginatorState(1, 11);
			assertFilteredState(address_stringColumnFilter, "11");
			assertNoCartesianProduct();

			clearColumnFilter(address_stringColumnFilter);
			assertPaginatorState(1, TOTAL_RECORDS);
		}
	}

	protected void testOneToMany() {
		assertNoCartesianProduct();
		assertPaginatorState(1, TOTAL_RECORDS, true);
		testGlobalFilter(true);

		var skipAssertSortedState = (isEclipseLink() || isOpenJPA()) && isLazy();

		if (skipAssertSortedState) {
			if (isEclipseLink()) {
				System.out.println("SKIPPING assertSortedState(phones.number) for EclipseLink because it doesn't support join fetch with range and therefore sorting can't run in same query"); // TODO: improve?
			}
			else if (isOpenJPA()) {
				System.out.println("SKIPPING assertSortedState(phones.number) for OpenJPA because BaseEntityService somehow performs a double join for the table"); // TODO: fix it
			}
		}
		else {
			clickColumn(phones_numberColumn);
			assertSortedState(phones_numberColumn, true);
			assertNoCartesianProduct();
		}

		guardPrimeFacesAjax(() -> phones_numberColumnFilter.sendKeys("11"));
		assertFilteredState(phones_numberColumnFilter, "11");
		assertNoCartesianProduct();
		var rowCount1 = getRowCount();
		assertTrue(rowCount1 < TOTAL_RECORDS, rowCount1 + " must be less than " + TOTAL_RECORDS);

		if (!skipAssertSortedState) {
			clickColumn(phones_numberColumn);
			assertSortedState(phones_numberColumn, false);
			assertNoCartesianProduct();
			var rowCount2 = getRowCount();
			assertEquals(rowCount1, rowCount2, "rowcount is still the same");
		}

		guardPrimeFacesAjax(() -> emailColumnFilter.sendKeys("1"));
		assertFilteredState(emailColumnFilter, "1");
		assertFilteredState(phones_numberColumnFilter, "11");
		assertNoCartesianProduct();
		var rowCount3 = getRowCount();
		assertTrue(rowCount3 < rowCount1, rowCount3 + " must be less than " + rowCount1);

		clearColumnFilter(phones_numberColumnFilter);
		assertPaginatorState(1, 119, true);
		assertNoCartesianProduct();

		clearColumnFilter(emailColumnFilter);
		assertPaginatorState(1, TOTAL_RECORDS, true);
		assertNoCartesianProduct();

		if (!skipAssertSortedState) {
			clickColumn(phones_numberColumn);
			assertSortedState(phones_numberColumn, true);
		}

		assertNoCartesianProduct();

		var skipAssertCriteriaState = isEclipseLink() && isLazy();

		if (skipAssertCriteriaState) {
			System.out.println("SKIPPING assertCriteriaState(phones.type) for EclipseLink because it refuses to perform a JOIN when setFirstResult/setMaxResults is used");
		}
		else {
			var skipAssertRowCount = isOpenJPA() && isLazy();

			if (skipAssertRowCount) {
				System.out.println("SKIPPING skipAssertRowCount(phones.type) for OpenJPA because BaseEntityService somehow performs a double join for the table"); // TODO: fix it
			}

			guardFacesAjax(criteriaPhoneTypeMOBILE::click);
			assertCriteriaState(phones_typeColumn, "MOBILE");
			var rowCount4 = getRowCount();
			if (!skipAssertRowCount) {
				assertTrue(rowCount4 < TOTAL_RECORDS, rowCount4 + " must be less than " + TOTAL_RECORDS);
			}
			assertNoCartesianProduct();

			guardFacesAjax(criteriaPhoneTypeHOME::click);
			assertCriteriaState(phones_typeColumn, "MOBILE", "HOME");
			var rowCount5 = getRowCount();
			if (!skipAssertRowCount) {
				assertTrue(rowCount5 < rowCount4, rowCount5 + " must be less than " + rowCount4);
			}
			assertNoCartesianProduct();

			guardFacesAjax(criteriaPhoneTypeWORK::click);
			assertCriteriaState(phones_typeColumn, "MOBILE", "HOME", "WORK");
			var rowCount6 = getRowCount();
			if (!skipAssertRowCount) {
				assertTrue(rowCount6 < rowCount5, rowCount6 + " must be less than " + rowCount5);
			}
			assertNoCartesianProduct();

			guardFacesAjax(criteriaPhoneTypeMOBILE::click); // Uncheck
			assertCriteriaState(phones_typeColumn, "HOME", "WORK");
			var rowCount7 = getRowCount();
			if (!skipAssertRowCount) {
				assertTrue(rowCount7 > rowCount6, rowCount7 + " must be more than " + rowCount6);
			}
			assertNoCartesianProduct();

			guardFacesAjax(criteriaPhoneTypeHOME::click); // Uncheck
			assertCriteriaState(phones_typeColumn, "WORK");
			var rowCount8 = getRowCount();
			if (!skipAssertRowCount) {
				assertTrue(rowCount8 > rowCount7, rowCount8 + " must be more than " + rowCount7);
			}
			assertNoCartesianProduct();

			guardFacesAjax(criteriaPhoneTypeWORK::click); // Uncheck
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

		guardFacesAjax(criteriaGroupUSER::click);
		assertCriteriaState(groupsColumn, "USER");
		var rowCount1 = getRowCount();
		assertTrue(rowCount1 < TOTAL_RECORDS, rowCount1 + " must be less than " + TOTAL_RECORDS);
		assertNoCartesianProduct();

		guardFacesAjax(criteriaGroupMANAGER::click);
		assertCriteriaState(groupsColumn, "USER", "MANAGER");
		var rowCount2 = getRowCount();
		assertTrue(rowCount2 < rowCount1, rowCount2 + " must be less than " + rowCount1);
		assertNoCartesianProduct();

		guardFacesAjax(criteriaGroupADMINISTRATOR::click);
		assertCriteriaState(groupsColumn, "USER", "MANAGER", "ADMINISTRATOR");
		var rowCount3 = getRowCount();
		assertTrue(rowCount3 < rowCount2, rowCount3 + " must be less than " + rowCount2);
		assertNoCartesianProduct();

		guardFacesAjax(criteriaGroupDEVELOPER::click);
		assertCriteriaState(groupsColumn, "USER", "MANAGER", "ADMINISTRATOR", "DEVELOPER");
		var rowCount4 = getRowCount();
		assertTrue(rowCount4 < rowCount3, rowCount4 + " must be less than " + rowCount3);
		assertNoCartesianProduct();

		guardFacesAjax(criteriaGroupUSER::click); // Uncheck
		assertCriteriaState(groupsColumn, "MANAGER", "ADMINISTRATOR", "DEVELOPER");
		var rowCount5 = getRowCount();
		assertTrue(rowCount5 > rowCount4, rowCount5 + " must be more than " + rowCount4);
		assertNoCartesianProduct();

		guardFacesAjax(criteriaGroupMANAGER::click); // Uncheck
		assertCriteriaState(groupsColumn, "ADMINISTRATOR", "DEVELOPER");
		var rowCount6 = getRowCount();
		assertTrue(rowCount6 > rowCount5, rowCount6 + " must be more than " + rowCount5);
		assertNoCartesianProduct();

		guardFacesAjax(criteriaGroupADMINISTRATOR::click); // Uncheck
		assertCriteriaState(groupsColumn, "DEVELOPER");
		var rowCount7 = getRowCount();
		assertTrue(rowCount7 > rowCount6, rowCount7 + " must be more than " + rowCount6);
		assertNoCartesianProduct();

		guardFacesAjax(criteriaGroupDEVELOPER::click); // Uncheck
		assertPaginatorState(1, TOTAL_RECORDS, true);
		assertNoCartesianProduct();
	}

	protected void testManyToOne() {
		assertNoCartesianProduct();
		assertPaginatorState(1);
		var totalRecords = getRowCount();

		guardPrimeFacesAjax(() -> idColumnFilter.sendKeys("2"));
		assertFilteredState(idColumnFilter, "2");
		assertNoCartesianProduct();

		globalFilter.sendKeys("19");
		guardPrimeFacesAjax(globalFilterButton::click);
		assertGlobalFilterState("19");
		assertFilteredState(idColumnFilter, "2");
		assertNoCartesianProduct();

		clickColumn(emailColumn);
		assertSortedState(emailColumn, true);
		assertGlobalFilterState("19");
		assertFilteredState(idColumnFilter, "2");
		assertNoCartesianProduct();

		clearColumnFilter(idColumnFilter);
		assertGlobalFilterState("19");
		assertSortedState(emailColumn, true);
		assertNoCartesianProduct();

		guardPrimeFacesAjax(() -> emailColumnFilter.sendKeys("2"));
		assertGlobalFilterState("19");
		assertFilteredState(emailColumnFilter, "2");
		assertSortedState(emailColumn, true);
		assertNoCartesianProduct();

		globalFilter.clear();
		guardPrimeFacesAjax(globalFilterButton::click);
		assertFilteredState(emailColumnFilter, "2");
		assertSortedState(emailColumn, true);
		assertNoCartesianProduct();

		clearColumnFilter(emailColumnFilter);
		assertPaginatorState(1, totalRecords);
		assertSortedState(emailColumn, true);
		assertNoCartesianProduct();
	}

	protected void testGlobalFilter(boolean oneToManyOrElementCollection) {
		guardPrimeFacesAjax(() -> idColumnFilter.sendKeys("2"));
		assertFilteredState(idColumnFilter, "2");
		assertPaginatorState(1, 39, oneToManyOrElementCollection);
		assertNoCartesianProduct();

		globalFilter.sendKeys("name1");
		guardPrimeFacesAjax(globalFilterButton::click);
		assertPaginatorState(1, 23);
		assertGlobalFilterState("name1");
		assertFilteredState(idColumnFilter, "2");
		assertNoCartesianProduct();

		clickColumn(emailColumn);
		assertPaginatorState(1, 23);
		assertSortedState(emailColumn, true);
		assertGlobalFilterState("name1");
		assertFilteredState(idColumnFilter, "2");
		assertNoCartesianProduct();

		clearColumnFilter(idColumnFilter);
		assertPaginatorState(1, 111);
		assertSortedState(emailColumn, true);
		assertGlobalFilterState("name1");
		assertNoCartesianProduct();

		globalFilter.clear();
		guardPrimeFacesAjax(globalFilterButton::click);
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
		var totalRecords = getRowCount();
		var startRecord = (currentPage - 1) * ROWS_PER_PAGE + 1;
		var endRecord = min(startRecord + ROWS_PER_PAGE - 1, totalRecords);
		var pageCount = totalRecords / ROWS_PER_PAGE + (totalRecords % ROWS_PER_PAGE > 0 ? 1 : 0);
		var visibleRecords = endRecord - startRecord + 1;

		if (oneToManyOrElementCollection && isOpenJPA()) {
			System.out.println("SKIPPING assertEquals(visibleRecords, getCells(idColumn).size()) for OpenJPA because it doesn't correctly limit @OneToMany and @ElementCollection on root"); // TODO: improve?
		}
		else {
			assertEquals(visibleRecords, getCells(idColumn).size(), "visible records");
		}

		assertEquals(expectedTotalRecords, totalRecords, "total records");
		assertEquals("Showing " + startRecord + " - " + endRecord + " of " + totalRecords + " records", pageReport.getText(), "page report");
		assertEquals(min(pageCount, 10), pages.size(), "page count");
		assertEquals(String.valueOf(currentPage), pageCurrent.getText(), "page current");
		assertEquals(currentPage == 1 ? null : String.valueOf(currentPage), getQueryParameter(QUERY_PARAMETER_PAGE), "page query string");
	}

	protected void assertSortedState(WebElement column, boolean ascending) {
		var field = column.findElement(By.cssSelector(".ui-column-title")).getText();

		assertSortedState(column, ascending, "id".equals(field));
	}

	protected void assertSortedState(WebElement column, boolean ascending, boolean isDefaultOrderBy) {
		var field = column.findElement(By.cssSelector(".ui-column-title")).getText();
		var sortableColumnClass = column.findElement(By.cssSelector(".ui-sortable-column-icon")).getAttribute("class");

		assertTrue(activeColumn.findElement(By.cssSelector(".ui-column-title")).getText().equals(field), field + " column must be active");
		assertEquals(ascending, sortableColumnClass.contains("ui-icon-triangle-1-n"), field + " column must be sorted ascending");
		assertEquals(!ascending, sortableColumnClass.contains("ui-icon-triangle-1-s"), field + " column must be sorted descending");
		assertEquals(isDefaultOrderBy && !ascending ? null : (ascending ? "" : "-") + field, getQueryParameter(QUERY_PARAMETER_ORDER), "order query string");

		var cells = getCells(column);
		List<String> actualValues = cells.stream().map(this::sortIterableIfNecessary).collect(toList());
		List<String> expectedValues;

		if ("id".equals(field)) {
			expectedValues = actualValues.stream().map(Integer::valueOf).sorted(ascending ? naturalOrder() : reverseOrder()).map(String::valueOf).collect(toList());
		}
		else {
			expectedValues = actualValues.stream().sorted(ascending ? naturalOrder() : reverseOrder()).collect(toList());

			if (!expectedValues.equals(actualValues)) {
				var collator = Collator.getInstance(Locale.ENGLISH);
				expectedValues.sort(ascending ? collator : collator.reversed()); // TODO: find a better way. Problem is, lazy model sorts by DB collation and non-lazy model sorts by Java collation, however they don't necessarily agree on each other (e.g. @ before 0).
			}
		}

		assertEquals(expectedValues, actualValues, field + " ordering");
	}

	private String sortIterableIfNecessary(WebElement cell) {
		var text = cell.getText();

		if (text.contains("\n")) {
			text = stream(text.split("\n")).sorted(isSortedAscending(activeColumn) ? naturalOrder() : reverseOrder()).collect(joining("\n"));
		}

		return text;
	}

	private static boolean isSortedAscending(WebElement column) {
		return "ascending".equals(column.getAttribute("aria-sort"));
	}

	protected void assertFilteredState(WebElement filter, String filterValue) {
		var column = filter.findElement(By.xpath(".."));

		while (!"th".equals(column.getTagName())) {
		    column = column.findElement(By.xpath(".."));
		}

		var field = column.findElement(By.cssSelector(".ui-column-title")).getText();
        var input = "select".equals(filter.getTagName()) ? new Select(filter).getFirstSelectedOption() : filter;
        String actualFilterValue = input.getAttribute("value");
		assertEquals(filterValue, actualFilterValue, "filter value");
		assertEquals(actualFilterValue, getQueryParameter(field), "filter query string");

		List<String> actualValues = getCells(column).stream().map(WebElement::getText).collect(toList());
		assertTrue(actualValues.stream().allMatch(value -> value.contains(filterValue)), field + " filtering " + actualValues + " matches " + filterValue);
	}

	protected void assertSelectedState(int selectedId) {
		assertTrue(selectedRow.findElement(By.cssSelector("td:first-child")).getText().equals(String.valueOf(selectedId)), selectedId + " must be selected");
		assertTrue(("[Person[" + selectedId + "]]").equals(selection.getText()), selectedId + " must be in the selection");
		assertEquals(String.valueOf(selectedId), getQueryParameter(QUERY_PARAMETER_SELECTION), "select query string");
	}

	protected void assertGlobalFilterState(String filterValue) {
		var actualFilterValue = globalFilter.getAttribute("value");
		assertEquals(filterValue, actualFilterValue, "filter value");
		assertEquals(actualFilterValue, getQueryParameter(QUERY_PARAMETER_SEARCH), "filter query string");

		for (WebElement row : rows) {
			var rowAsString = row.getText();
			assertTrue(rowAsString.contains(filterValue), "row " + rowAsString + " matches global filter " + filterValue);
		}
	}

	protected void assertCriteriaState(WebElement column, Criteria<?> criteria, Function<String, ?> parser) {
		var field = column.findElement(By.cssSelector(".ui-column-title")).getText();
		List<String> actualValues = getCells(column).stream().map(WebElement::getText).collect(toList());
		assertTrue(actualValues.stream().allMatch(value -> criteria.applies(parser.apply(value))), field + " criteria " + actualValues + " matches " + criteria);
	}

	protected void assertCriteriaState(WebElement column, String... criteriaValues) {
		var field = column.findElement(By.cssSelector(".ui-column-title")).getText();
		List<String> expectedValues = asList(criteriaValues);
		getCells(column).stream().map(WebElement::getText).forEach(text -> {
			List<String> actualValues = asList(text.split("\n"));
			assertTrue(actualValues.stream().anyMatch(value -> expectedValues.contains(value)), field + " criteria " + actualValues + " contains any " + expectedValues);
		});
	}

	private List<WebElement> getCells(WebElement column) {
		var columnIndex = column.findElement(By.xpath("..")).findElements(By.tagName("th")).stream().map(WebElement::getText).collect(toList()).indexOf(column.getText()); // Awkward.
		return browser.findElements(By.cssSelector("#form\\:table_data td:nth-child(" + (columnIndex + 1) + ")"));
	}

	protected void assertNoCartesianProduct() {
		List<Integer> actualIds = getCells(idColumn).stream().map(WebElement::getText).map(Integer::valueOf).sorted().collect(toList());
		List<Integer> expectedIds = actualIds.stream().distinct().collect(toList());
		assertEquals(expectedIds, actualIds, "No cartesian product");
	}

}
