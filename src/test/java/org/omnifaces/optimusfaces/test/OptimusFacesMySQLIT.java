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

import static org.omnifaces.optimusfaces.test.OptimusFacesIT.DB.MYSQL;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.runner.RunWith;

import com.wix.mysql.EmbeddedMysql;
import static com.wix.mysql.config.MysqldConfig.aMysqldConfig;
import static com.wix.mysql.EmbeddedMysql.anEmbeddedMysql;
import static com.wix.mysql.distribution.Version.v5_7_17;

@RunWith(Arquillian.class)
public class OptimusFacesMySQLIT extends OptimusFacesIT {

	private static EmbeddedMysql mysql;

	@Deployment(testable=false)
	public static WebArchive createDeployment() {
		installMySQL();
		return createArchive(OptimusFacesMySQLIT.class, MYSQL);
	}

	private static void installMySQL() {
		System.out.println(""
				+ "\n"
				+ "\n    ---------------------------------------------------------------------------------------------"
				+ "\n    Installing MySQL ..."
				+ "\n    ---------------------------------------------------------------------------------------------"
				+ "\n"
			);

		mysql = anEmbeddedMysql(aMysqldConfig(v5_7_17).withUser("test", "test").build()).addSchema("test").start();

		System.out.println(""
				+ "\n    ---------------------------------------------------------------------------------------------"
				+ "\n    MySQL installed!"
				+ "\n    ---------------------------------------------------------------------------------------------"
				+ "\n"
				+ "\n"
			);
	}

	@AfterClass
	public static void stopMySQL() {
		System.out.println(""
				+ "\n"
				+ "\n    ---------------------------------------------------------------------------------------------"
				+ "\n    Stopping MySQL ..."
				+ "\n    ---------------------------------------------------------------------------------------------"
				+ "\n"
			);

		mysql.stop();

		System.out.println(""
				+ "\n    ---------------------------------------------------------------------------------------------"
				+ "\n    MySQL stopped!"
				+ "\n    ---------------------------------------------------------------------------------------------"
				+ "\n"
				+ "\n"
			);
	}

}
