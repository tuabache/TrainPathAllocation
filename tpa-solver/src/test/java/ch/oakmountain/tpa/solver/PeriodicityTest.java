/*
 * Copyright 2016 Christian Eichenberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.oakmountain.tpa.solver;

import org.junit.Test;

import static org.joda.time.DateTimeConstants.*;
import static org.junit.Assert.*;


public class PeriodicityTest {

    @Test
    public void testSetWeekDay() throws Exception {
        Periodicity p = new Periodicity();
        p.setWeekDay(MONDAY, true);
        assertTrue(p.getWeekDay(MONDAY));
        assertFalse(p.getWeekDay(TUESDAY));
        assertFalse(p.getWeekDay(WEDNESDAY));
        assertFalse(p.getWeekDay(THURSDAY));
        assertFalse(p.getWeekDay(FRIDAY));
        assertFalse(p.getWeekDay(SATURDAY));
        assertFalse(p.getWeekDay(SUNDAY));

        p.setWeekDay(THURSDAY, true);
        assertTrue(p.getWeekDay(MONDAY));
        assertFalse(p.getWeekDay(TUESDAY));
        assertFalse(p.getWeekDay(WEDNESDAY));
        assertTrue(p.getWeekDay(THURSDAY));
        assertFalse(p.getWeekDay(FRIDAY));
        assertFalse(p.getWeekDay(SATURDAY));
        assertFalse(p.getWeekDay(SUNDAY));

        p.setWeekDay(THURSDAY, false);
        assertTrue(p.getWeekDay(MONDAY));
        assertFalse(p.getWeekDay(TUESDAY));
        assertFalse(p.getWeekDay(WEDNESDAY));
        assertFalse(p.getWeekDay(THURSDAY));
        assertFalse(p.getWeekDay(FRIDAY));
        assertFalse(p.getWeekDay(SATURDAY));
        assertFalse(p.getWeekDay(SUNDAY));

    }

    @Test
    public void testIsSubPeriodicity() throws Exception {
        Periodicity filterP;
        Periodicity valueP;

        filterP = new Periodicity();
        valueP = new Periodicity();
        assertTrue(Periodicity.isSubPeriodicity(valueP, filterP));
        assertTrue(Periodicity.isSubPeriodicity(filterP, valueP));

        filterP = new Periodicity();
        valueP = new Periodicity();
        valueP.setWeekDay(THURSDAY, true);
        assertFalse(Periodicity.isSubPeriodicity(valueP, filterP));
        assertTrue(Periodicity.isSubPeriodicity(filterP, valueP));

        filterP = new Periodicity();
        filterP.setWeekDay(MONDAY, true);
        valueP = new Periodicity();
        valueP.setWeekDay(MONDAY, true);
        assertTrue(Periodicity.isSubPeriodicity(valueP, filterP));
        assertTrue(Periodicity.isSubPeriodicity(filterP, valueP));

        filterP = new Periodicity();
        filterP.setWeekDay(MONDAY, true);
        valueP = new Periodicity();
        valueP.setWeekDay(TUESDAY, true);
        assertFalse(Periodicity.isSubPeriodicity(valueP, filterP));
        assertTrue(Periodicity.isSubPeriodicity(filterP, filterP));
        assertFalse(Periodicity.isSubPeriodicity(filterP, valueP));
        assertTrue(Periodicity.isSubPeriodicity(valueP, valueP));

        filterP = new Periodicity();
        filterP.setWeekDay(MONDAY, true);
        filterP.setWeekDay(TUESDAY, true);
        valueP = new Periodicity();
        valueP.setWeekDay(TUESDAY, true);
        assertTrue(Periodicity.isSubPeriodicity(valueP, filterP));
        assertTrue(Periodicity.isSubPeriodicity(filterP, filterP));
        assertFalse(Periodicity.isSubPeriodicity(filterP, valueP));
        assertTrue(Periodicity.isSubPeriodicity(valueP, valueP));

        filterP = new Periodicity();
        filterP.setWeekDay(MONDAY, true);
        filterP.setWeekDay(TUESDAY, true);
        filterP.setWeekDay(TUESDAY, true);
        filterP.setWeekDay(WEDNESDAY, true);
        filterP.setWeekDay(THURSDAY, true);
        filterP.setWeekDay(FRIDAY, true);
        filterP.setWeekDay(SATURDAY, true);
        filterP.setWeekDay(SUNDAY, true);
        valueP = new Periodicity();
        assertTrue(Periodicity.isSubPeriodicity(valueP, filterP));
        assertTrue(Periodicity.isSubPeriodicity(filterP, filterP));
        assertFalse(Periodicity.isSubPeriodicity(filterP, valueP));
        assertTrue(Periodicity.isSubPeriodicity(valueP, valueP));
        valueP.setWeekDay(TUESDAY, true);
        assertTrue(Periodicity.isSubPeriodicity(valueP, filterP));
        assertTrue(Periodicity.isSubPeriodicity(filterP, filterP));
        assertFalse(Periodicity.isSubPeriodicity(filterP, valueP));
        assertTrue(Periodicity.isSubPeriodicity(valueP, valueP));
    }

    @Test
    public void testParsePeriodicity() throws Exception {
        Pair<Periodicity, Periodicity> pers = Periodicity.parsePeriodicityBounds("0000000");
        Periodicity lower = pers.first;
        Periodicity upper = pers.second;
        assertEquals(0,lower.getVal());
        assertEquals(0,upper.getVal());

        pers = Periodicity.parsePeriodicityBounds("???????");
        lower = pers.first;
        upper = pers.second;
        assertEquals(0,lower.getVal());
        assertEquals(Periodicity.getWholeWeekPeriodicity().getVal(),upper.getVal());

        pers = Periodicity.parsePeriodicityBounds("0000001");
        lower = pers.first;
        upper = pers.second;
        assertEquals(128,lower.getVal());
        assertEquals(128,upper.getVal());

        pers = Periodicity.parsePeriodicityBounds("000000?");
        lower = pers.first;
        upper = pers.second;
        assertEquals(0,lower.getVal());
        assertEquals(128,upper.getVal());

    }


}