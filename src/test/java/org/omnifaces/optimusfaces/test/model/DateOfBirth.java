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
package org.omnifaces.optimusfaces.test.model;

import java.time.LocalDate;
import java.time.Year;

/**
 * Dates of birth of the test persons: from {@link #OLDEST} until {@link #YOUNGEST}, with {@link #THRESHOLD} in the middle.
 */
public final class DateOfBirth {

    private static final int MIN_AGE = 15;
    private static final int MAX_AGE = 75;
    private static final int THRESHOLD_AGE = (MIN_AGE + MAX_AGE) / 2;
    private static final int THIS_YEAR = Year.now().getValue();

    public static final LocalDate OLDEST = yearsAgo(MAX_AGE);
    public static final LocalDate YOUNGEST = yearsAgo(MIN_AGE);
    public static final LocalDate THRESHOLD = yearsAgo(THRESHOLD_AGE);

    private DateOfBirth() {
        throw new AssertionError();
    }

    private static LocalDate yearsAgo(int age) {
        return LocalDate.of(THIS_YEAR - age, 1, 1);
    }

}
