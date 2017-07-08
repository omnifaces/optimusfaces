/*
 * Copyright 2017 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.optimusfaces.test;

import static java.lang.Math.min;
import static java.util.Arrays.stream;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.jboss.arquillian.graphene.Graphene.guardAjax;
import static org.jboss.arquillian.graphene.Graphene.waitGui;
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.omnifaces.optimusfaces.model.PagedDataModel.QUERY_PARAMETER_ORDER;
import static org.omnifaces.optimusfaces.model.PagedDataModel.QUERY_PARAMETER_PAGE;
import static org.omnifaces.optimusfaces.test.OptimusFacesITStartup.ROWS_PER_PAGE;
import static org.omnifaces.optimusfaces.test.OptimusFacesITStartup.TOTAL_RECORDS;

import java.io.File;
import java.net.URL;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.omnifaces.optimusfaces.test.model.Gender;
import org.omnifaces.util.Servlets;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;

@RunWith(Arquillian.class)
public class OptimusFacesIT {

	@Drone
	private WebDriver browser;

	@ArquillianResource
	private URL baseURL;

	@Deployment(testable=false)
	public static WebArchive createDeployment() {
		Class<?> testClass = OptimusFacesIT.class;
		String packageName = testClass.getPackage().getName();
		MavenResolverSystem maven = Maven.resolver();

		WebArchive archive = create(WebArchive.class, testClass.getSimpleName() + ".war")
			.addPackage(packageName)
			.deleteClass(testClass)
			.addPackage(packageName + ".model")
			.addPackage(packageName + ".model.dto")
			.addPackage(packageName + ".service")
			.addAsResource("META-INF/persistence.xml/" + System.getProperty("profile.id") + ".xml", "META-INF/persistence.xml")
			.addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
			.addAsLibrary(new File(System.getProperty("optimusfaces.jar")))
			.addAsLibraries(maven.loadPomFromFile("pom.xml").importRuntimeDependencies().resolve().withTransitivity().asFile())
			.addAsLibraries(maven.resolve("org.omnifaces:omnifaces:2.6.3", "org.primefaces:primefaces:6.1").withTransitivity().asFile());

		addWebResources(archive, new File(testClass.getClassLoader().getResource(packageName).getFile()), "");
		return archive;
	}

	private static void addWebResources(WebArchive archive, File root, String directory) {
		for (File file : root.listFiles()) {
			String path = directory + "/" + file.getName();

			if (file.isFile()) {
				archive.addAsWebResource(file, path);
			}
			else if (file.isDirectory()) {
				addWebResources(archive, file, path);
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

	protected void open(String type, String queryString) {
		String url = baseURL + getClass().getSimpleName() + type + ".xhtml";

		if (queryString != null) {
			url += "?" + queryString;
		}

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

	protected static boolean isEclipseLink() {
		return System.getProperty("profile.id").endsWith("-eclipselink");
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

	@FindBy(id="form:table:phones_number")
	private WebElement phones_numberColumn;

	@FindBy(id="form:table:groups")
	private WebElement groupsColumn;

	@FindBy(css="#form\\:table th.ui-state-active")
	private WebElement activeColumn;

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
		open("Lazy", null);
		testDefaultState();
	}

	@Test
	public void testNonLazyDefaultState() {
		open("NonLazy", null);
		testDefaultState();
	}

	@Test
	public void testLazyPaging() {
		open("Lazy", null);
		testPaging();
	}

	@Test
	public void testNonLazyPaging() {
		open("NonLazy", null);
		testPaging();
	}

	@Test
	public void testLazySorting() {
		open("Lazy", null);
		testSorting();
	}

	@Test
	public void testNonLazySorting() {
		open("NonLazy", null);
		testSorting();
	}

	@Test
	public void testLazyFiltering() {
		open("Lazy", null);
		testFiltering();
	}

	@Test
	public void testNonLazyFiltering() {
		open("NonLazy", null);
		testFiltering();
	}

	@Test
	public void testLazyPagingSortingAndFiltering() {
		open("Lazy", null);
		testPagingSortingAndFiltering();
	}

	@Test
	public void testNonLazyPagingSortingAndFiltering() {
		open("NonLazy", null);
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
		open("LazyWithCriteria", null);
		testCriteria();
	}

	@Test
	public void testNonLazyWithCriteria() {
		open("NonLazyWithCriteria", null);
		testCriteria();
	}

	@Test
	public void testLazyWithFilterOptions() {
		open("LazyWithFilterOptions", null);
		testFilterOptions();
	}

	@Test
	public void testNonLazyWithFilterOptions() {
		open("NonLazyWithFilterOptions", null);
		testFilterOptions();
	}

	@Test
	public void testLazyWithDTO() {
		open("LazyWithDTO", null);
		testDTO();
	}

	@Test
	public void testNonLazyWithDTO() {
		open("NonLazyWithDTO", null);
		testDTO();
	}

	@Test
	public void testLazyWithOneToOne() {
		open("LazyWithOneToOne", null);
		testOneToOne();
	}

	@Test
	public void testNonLazyWithOneToOne() {
		open("NonLazyWithOneToOne", null);
		testOneToOne();
	}

	@Test
	public void testLazyWithOneToMany() {
		if (isEclipseLink()) {
			return; // It stubbornly returns cartesian products. TODO: fix them.
		}

		open("LazyWithOneToMany", null);
		testOneToMany();
	}

	@Test
	public void testNonLazyWithOneToMany() {
		if (isEclipseLink()) {
			return; // It stubbornly returns cartesian products. TODO: fix them.
		}

		open("NonLazyWithOneToMany", null);
		testOneToMany();
	}

	@Test
	public void testLazyWithElementCollection() {
		if (isEclipseLink()) {
			return; // It stubbornly returns cartesian products. TODO: fix them.
		}

		open("LazyWithElementCollection", null);
		testElementCollection();
	}

	@Test
	public void testNonLazyWithElementCollection() {
		if (isEclipseLink()) {
			return; // It stubbornly returns cartesian products. TODO: fix them.
		}

		open("NonLazyWithElementCollection", null);
		testElementCollection();
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

	protected void testFiltering() {
		guardAjax(idColumnFilter).sendKeys("2");
		assertPaginatorState(1, 39);
		assertFilteredState(idColumnFilter, "2");

		idColumnFilter.clear();
		guardAjax(idColumnFilter).sendKeys(Keys.TAB);
		assertPaginatorState(1, TOTAL_RECORDS);

		guardAjax(idColumnFilter).sendKeys("3");
		assertPaginatorState(1, 38);
		assertFilteredState(idColumnFilter, "3");

		idColumnFilter.clear();
		guardAjax(idColumnFilter).sendKeys(Keys.TAB);
		assertPaginatorState(1, TOTAL_RECORDS);

		guardAjax(genderColumnFilter).sendKeys("FEMALE");
		int totalRecords1 = getRowCount();
		assertPaginatorState(1);
		assertFilteredState(genderColumnFilter, "FEMALE");

		guardAjax(emailColumnFilter).sendKeys("1");
		int totalRecords2 = getRowCount();
		assertTrue(totalRecords2 + " must be less than " + totalRecords1, totalRecords2 < totalRecords1);
		assertPaginatorState(1);
		assertFilteredState(emailColumnFilter, "1");
		assertFilteredState(genderColumnFilter, "FEMALE");

		genderColumnFilter.clear();
		guardAjax(genderColumnFilter).sendKeys(Keys.TAB);
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");

		emailColumnFilter.clear();
		guardAjax(emailColumnFilter).sendKeys(Keys.TAB);
		assertPaginatorState(1, TOTAL_RECORDS);
	}

	protected void testPagingSortingAndFiltering() {
		guardAjax(pageNext).click();
		assertPaginatorState(2);

		guardAjax(emailColumnFilter).sendKeys("1");
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");

		guardAjax(genderColumn).click();
		assertPaginatorState(1, 119);
		assertFilteredState(emailColumnFilter, "1");
		assertSortedState(genderColumn, true);

		guardAjax(pageNext).click();
		assertPaginatorState(2, 119);
		assertFilteredState(emailColumnFilter, "1");
		assertSortedState(genderColumn, true);

		emailColumnFilter.clear();
		guardAjax(emailColumnFilter).sendKeys(Keys.TAB);
		assertPaginatorState(1, TOTAL_RECORDS);
		assertSortedState(genderColumn, true);
	}

	protected void testQueryStringLoading(String type) {
		open(type, "p=5");
		assertPaginatorState(5, TOTAL_RECORDS);

		open(type, "p=4&email=5");
		assertPaginatorState(4, 38);
		assertFilteredState(emailColumnFilter, "5");

		open(type, "p=3&o=-dateOfBirth&gender=MALE");
		assertPaginatorState(3);

		if (!isEclipseLink()) { // For some reason it sorts years between 1900 and 1919 AFTER 1920-2000?! Looks like java.time isn't entirely correctly supported.
			assertSortedState(dateOfBirthColumn, false);
		}

		assertFilteredState(genderColumnFilter, "MALE");
	}

	protected void testCriteria() {
		guardAjax(criteriaIdBetween50And150).click();
		assertPaginatorState(1, 101);

		guardAjax(criteriaEmailLikeName1).click();
		int rowCount1 = getRowCount();
		assertTrue(rowCount1 + " must be less than 101", rowCount1 < 101);
		assertFilteredState(emailColumnFilter, "name1", true);

		guardAjax(criteriaGenderIsFemale).click();
		int rowCount2 = getRowCount();
		assertTrue(rowCount2 + " must be less than " + rowCount1, rowCount2 < rowCount1);
		assertFilteredState(genderColumnFilter, "FEMALE", true);

		guardAjax(criteriaDateOfBirthBefore1950).click();
		int rowCount3 = getRowCount();
		assertTrue(rowCount3 + " must be less than " + rowCount2, rowCount3 < rowCount2);

		guardAjax(criteriaIdBetween50And150).click(); // Uncheck
		int rowCount4 = getRowCount();
		assertTrue(rowCount4 + " must be more than " + rowCount3, rowCount4 > rowCount3);

		guardAjax(criteriaEmailLikeName1).click(); // Uncheck
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

		addressStringColumnFilter.clear();
		guardAjax(addressStringColumnFilter).sendKeys(Keys.TAB);
		assertPaginatorState(1, TOTAL_RECORDS);
		assertSortedState(totalPhonesColumn, true);
		assertNoCartesianProduct();

		guardAjax(totalPhonesColumnFilter).sendKeys("3");
		assertFilteredState(totalPhonesColumnFilter, "3");
		assertNoCartesianProduct();
	}

	protected void testOneToOne() {
		assertNoCartesianProduct();

		guardAjax(address_houseNumberColumn).click();
		assertSortedState(address_houseNumberColumn, true);
		assertNoCartesianProduct();

		guardAjax(address_houseNumberColumnFilter).sendKeys("11");
		assertPaginatorState(1, 11);
		assertFilteredState(address_houseNumberColumnFilter, "11");
		assertNoCartesianProduct();

		guardAjax(address_stringColumn).click();
		assertPaginatorState(1, 11);
		assertSortedState(address_stringColumn, true);
		assertNoCartesianProduct();

		address_houseNumberColumnFilter.clear();
		guardAjax(address_houseNumberColumnFilter).sendKeys(Keys.TAB);
		assertPaginatorState(1, TOTAL_RECORDS);
		assertSortedState(address_stringColumn, true);
		assertNoCartesianProduct();

		if (isEclipseLink()) {
			return; // EclipseLink doesn't have a reasonable equivalent for @Formula as used in address.string. The intented test is already covered by testDTO() anyway.
		}

		guardAjax(address_stringColumnFilter).sendKeys("11");
		assertPaginatorState(1, 11);
		assertFilteredState(address_stringColumnFilter, "11");
		assertNoCartesianProduct();
	}

	protected void testOneToMany() {
		assertNoCartesianProduct();

		guardAjax(phones_numberColumn).click();
		assertSortedState(phones_numberColumn, true);
		assertNoCartesianProduct();

		guardAjax(phones_numberColumnFilter).sendKeys("11");
		assertFilteredState(phones_numberColumnFilter, "11");
		assertNoCartesianProduct();
		int rowCount1 = getRowCount();
		assertTrue(rowCount1 + " must be less than " + TOTAL_RECORDS, rowCount1 < TOTAL_RECORDS);

		guardAjax(phones_numberColumn).click();
		assertSortedState(phones_numberColumn, false);
		assertNoCartesianProduct();
		int rowCount2 = getRowCount();
		assertEquals("rowcount is still the same", rowCount1, rowCount2);

		guardAjax(emailColumnFilter).sendKeys("1");
		assertFilteredState(emailColumnFilter, "1");
		assertFilteredState(phones_numberColumnFilter, "11");
		assertNoCartesianProduct();
		int rowCount3 = getRowCount();
		assertTrue(rowCount3 + " must be less than " + rowCount2, rowCount3 < rowCount2);

		phones_numberColumnFilter.clear();
		guardAjax(phones_numberColumnFilter).sendKeys(Keys.TAB);
		assertPaginatorState(1, 119);
		assertSortedState(phones_numberColumn, false);
		assertNoCartesianProduct();

		guardAjax(phones_numberColumn).click();
		assertSortedState(phones_numberColumn, true);
		assertNoCartesianProduct();

		emailColumnFilter.clear();
		guardAjax(emailColumnFilter).sendKeys(Keys.TAB);
		assertPaginatorState(1, TOTAL_RECORDS);
		assertSortedState(phones_numberColumn, true);
		assertNoCartesianProduct();
	}

	protected void testElementCollection() {
		assertNoCartesianProduct();
		assertPaginatorState(1, TOTAL_RECORDS);

		guardAjax(criteriaGroupUSER).click();
		int rowCount1 = getRowCount();
		assertTrue(rowCount1 + " must be less than " + TOTAL_RECORDS, rowCount1 < TOTAL_RECORDS);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupMANAGER).click();
		int rowCount2 = getRowCount();
		assertTrue(rowCount2 + " must be less than " + rowCount1, rowCount2 < rowCount1);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupADMINISTRATOR).click();
		int rowCount3 = getRowCount();
		assertTrue(rowCount3 + " must be less than " + rowCount2, rowCount3 < rowCount2);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupDEVELOPER).click();
		int rowCount4 = getRowCount();
		assertTrue(rowCount4 + " must be less than " + rowCount3, rowCount4 < rowCount3);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupUSER).click(); // Uncheck
		int rowCount5 = getRowCount();
		assertTrue(rowCount5 + " must be more than " + rowCount4, rowCount5 > rowCount4);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupMANAGER).click(); // Uncheck
		int rowCount6 = getRowCount();
		assertTrue(rowCount6 + " must be more than " + rowCount5, rowCount6 > rowCount5);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupADMINISTRATOR).click(); // Uncheck
		int rowCount7 = getRowCount();
		assertTrue(rowCount7 + " must be more than " + rowCount6, rowCount7 > rowCount6);
		assertNoCartesianProduct();

		guardAjax(criteriaGroupDEVELOPER).click(); // Uncheck
		assertPaginatorState(1, TOTAL_RECORDS);
		assertNoCartesianProduct();
	}


	// Assertions -----------------------------------------------------------------------------------------------------

	protected void assertPaginatorState(int currentPage) {
		assertPaginatorState(currentPage, getRowCount());
	}

	protected void assertPaginatorState(int currentPage, int expectedTotalRecords) {
		int totalRecords = getRowCount();
		int startRecord = ((currentPage - 1) * ROWS_PER_PAGE) + 1;
		int endRecord = min(startRecord + ROWS_PER_PAGE - 1, totalRecords);
		int pageCount = (totalRecords / ROWS_PER_PAGE) + ((totalRecords % ROWS_PER_PAGE > 0) ? 1 : 0);

		assertEquals("total records", expectedTotalRecords, totalRecords);
		assertEquals("page report", "Showing " + startRecord + " - " + endRecord + " of " + totalRecords + " records", pageReport.getText());
		assertEquals("page count", min(pageCount, 10), pages.size());
		assertEquals("page current", String.valueOf(currentPage), pageCurrent.getText());
		assertEquals("page query string", (currentPage == 1) ? null : String.valueOf(currentPage), getQueryParameter(QUERY_PARAMETER_PAGE));
	}

	protected void assertSortedState(WebElement column, boolean ascending) {
		String field = column.findElement(By.cssSelector(".ui-column-title")).getText();

		assertTrue(field + " column must be active", activeColumn.getText().equals(field));
		assertEquals(field + " column must be sorted", ascending ? "ascending" : "descending", activeColumn.getAttribute("aria-sort"));
		assertEquals("order query string", ("id".equals(field) && !ascending) ? null : ((ascending ? "" : "-") + field), getQueryParameter(QUERY_PARAMETER_ORDER));

		List<WebElement> cells = getCells(column);
		List<String> actualValues = cells.stream().map(this::sortIterableIfNecessary).collect(toList());
		List<String> expectedValues;

		if ("id".equals(field)) {
			expectedValues = actualValues.stream().map(Integer::valueOf).sorted(ascending ? naturalOrder() : reverseOrder()).map(String::valueOf).collect(toList());
		}
		else {
			expectedValues = actualValues.stream().sorted(ascending ? naturalOrder() : reverseOrder()).collect(toList());

			if (!expectedValues.equals(actualValues)) {
				expectedValues.sort(Collator.getInstance(Locale.ENGLISH)); // TODO: find a better way. Problem is, lazy model sorts by H2 collation and non-lazy model sorts by Java collation, however they don't agree on each other (e.g. @ before 0).
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
		assertFilteredState(filter, filterValue, false);
	}

	protected void assertFilteredState(WebElement filter, String filterValue, boolean criteria) {
		WebElement column = filter.findElement(By.xpath(".."));
		String field = column.findElement(By.cssSelector(".ui-column-title")).getText();

		if (!criteria) {
			WebElement input = "select".equals(filter.getTagName()) ? new Select(filter).getFirstSelectedOption() : filter;
			String actualFilterValue = input.getAttribute("value");
			assertEquals("filter value", filterValue, actualFilterValue);
			assertEquals("filter query string", actualFilterValue, getQueryParameter(field));
		}

		List<String> actualValues = getCells(column).stream().map(WebElement::getText).collect(toList());
		assertTrue(field + " filtering " + actualValues + " matches " + filterValue, actualValues.stream().allMatch(value -> value.contains(filterValue)));
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
