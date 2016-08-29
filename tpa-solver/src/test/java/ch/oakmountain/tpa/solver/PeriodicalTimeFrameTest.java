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


import org.joda.time.*;
import org.junit.Test;

import static org.joda.time.DateTimeConstants.*;
import static org.junit.Assert.*;


public class PeriodicalTimeFrameTest {

    @Test
    public void testLocalWeekTime() throws Exception {

        PeriodicalTimeFrame startTime = new PeriodicalTimeFrame(MONDAY, 0, 5);
        assertEquals(DateTimeConstants.MONDAY, startTime.getDayOfWeek());

        assertEquals(DateTimeConstants.TUESDAY, startTime.plusHours(24).getDayOfWeek());

        assertEquals(DateTimeConstants.SUNDAY, startTime.minusHours(24).getDayOfWeek());

        PeriodicalTimeFrame negativeStartTime = startTime.minus(Minutes.minutes(10));
        assertEquals(SUNDAY, negativeStartTime.getDayOfWeek());
        assertEquals(23, negativeStartTime.getHourOfDay());
        assertEquals(55, negativeStartTime.getMinuteOfHour());

        assertTrue(startTime.isWithinBounds(negativeStartTime, startTime));

    }

    @Test
    public void testLocalWeekTimeWrapping() throws Exception {

        PeriodicalTimeFrame startTime = new PeriodicalTimeFrame(SUNDAY, 23, 0);
        PeriodicalTimeFrame endTime = new PeriodicalTimeFrame(MONDAY, 8, 0);
        PeriodicalTimeFrame timeToTest = new PeriodicalTimeFrame(MONDAY, 7, 0);

        assertTrue(timeToTest.isWithinBounds(startTime, endTime));

        timeToTest = new PeriodicalTimeFrame(TUESDAY, 7, 0);
        assertFalse(timeToTest.isWithinBounds(startTime, endTime));

    }

    @Test
    public void testLocalWeekDuration3() throws Exception {

        PeriodicalTimeFrame startTime = new PeriodicalTimeFrame(TUESDAY, 22, 0);
        PeriodicalTimeFrame endTime = new PeriodicalTimeFrame(WEDNESDAY, 15, 0);

        assertTrue(endTime.isWithinBounds(startTime, startTime.plus(Days.days(1))));
        assertFalse(endTime.isWithinBounds(startTime.minus(Days.days(1)), startTime));
    }

    @Test
    public void testLocalWeekDuration4() throws Exception {

        PeriodicalTimeFrame startTime = new PeriodicalTimeFrame(SUNDAY, 22, 0);
        PeriodicalTimeFrame endTime = new PeriodicalTimeFrame(MONDAY, 15, 0);

        endTime.isWithinBounds(startTime, startTime.plus(Days.days(1)));
        assertTrue(endTime.isWithinBounds(startTime, startTime.plus(Days.days(1))));
        assertFalse(endTime.isWithinBounds(startTime.minus(Days.days(1)), startTime));
    }


    @Test
    public void testDistanceFromInterval() throws Exception {
        // case 1: in the interval
        PeriodicalTimeFrame earliest = new PeriodicalTimeFrame(TUESDAY, 22, 0);
        PeriodicalTimeFrame latest = new PeriodicalTimeFrame(WEDNESDAY, 15, 0);
        PeriodicalTimeFrame moment = new PeriodicalTimeFrame(WEDNESDAY, 13, 0);
        assertEquals(new Duration(0), moment.distanceAfterInterval(earliest, latest));
        assertEquals(new Duration(0), moment.distanceBeforeInterval(earliest, latest));

        earliest = new PeriodicalTimeFrame(TUESDAY, 22, 0);
        latest = new PeriodicalTimeFrame(WEDNESDAY, 15, 0);
        moment = new PeriodicalTimeFrame(TUESDAY, 22, 0);
        assertEquals(new Duration(0), moment.distanceAfterInterval(earliest, latest));
        assertEquals(new Duration(0), moment.distanceBeforeInterval(earliest, latest));

        earliest = new PeriodicalTimeFrame(TUESDAY, 22, 0);
        latest = new PeriodicalTimeFrame(WEDNESDAY, 15, 0);
        moment = new PeriodicalTimeFrame(WEDNESDAY, 15, 0);
        assertEquals(new Duration(0), moment.distanceAfterInterval(earliest, latest));
        assertEquals(new Duration(0), moment.distanceBeforeInterval(earliest, latest));

        // case 2: 0__X___[earliest, latest]____T
        earliest = new PeriodicalTimeFrame(TUESDAY, 22, 0);
        latest = new PeriodicalTimeFrame(WEDNESDAY, 15, 0);
        moment = new PeriodicalTimeFrame(THURSDAY, 13, 0);
        assertTrue(Hours.hours(9 + 13).toStandardDuration().equals(moment.distanceAfterInterval(earliest, latest).toDuration()));
        assertTrue(Hours.hours(11 + 4 * 24 + 22).toStandardDuration().equals(moment.distanceBeforeInterval(earliest, latest).toDuration()));

        // case 3:  0______[earliest, latest]_X___T
        earliest = new PeriodicalTimeFrame(TUESDAY, 22, 0);
        latest = new PeriodicalTimeFrame(WEDNESDAY, 15, 0);
        moment = new PeriodicalTimeFrame(THURSDAY, 13, 0);
        assertTrue(Hours.hours(9 + 13).toStandardDuration().equals(moment.distanceAfterInterval(earliest, latest).toDuration()));
        assertTrue(Hours.hours(11 + 4 * 24 + 22).toStandardDuration().equals(moment.distanceBeforeInterval(earliest, latest).toDuration()));

        earliest = new PeriodicalTimeFrame(TUESDAY, 22, 0);
        latest = new PeriodicalTimeFrame(WEDNESDAY, 15, 0);
        moment = new PeriodicalTimeFrame(SUNDAY, 13, 0);
        assertTrue(Hours.hours(9 + 3 * 24 + 13).toStandardDuration().equals(moment.distanceAfterInterval(earliest, latest).toDuration()));
        assertTrue(Hours.hours(11 + 24 + 22).toStandardDuration().equals(moment.distanceBeforeInterval(earliest, latest).toDuration()));

        // 0___,latest]__X___[earliest,____T
        earliest = new PeriodicalTimeFrame(FRIDAY, 22, 0);
        latest = new PeriodicalTimeFrame(TUESDAY, 15, 0);
        moment = new PeriodicalTimeFrame(THURSDAY, 13, 0);
        assertTrue(Hours.hours(9 + 24 + 13).toStandardDuration().equals(moment.distanceAfterInterval(earliest, latest).toDuration()));
        assertTrue(Hours.hours(11 + 22).toStandardDuration().equals(moment.distanceBeforeInterval(earliest, latest).toDuration()));

    }

    @Test
    public void testDistance() throws Exception {

        // case 1: same
        PeriodicalTimeFrame reference = new PeriodicalTimeFrame(TUESDAY, 22, 0);
        PeriodicalTimeFrame moment = new PeriodicalTimeFrame(TUESDAY, 22, 0);
        assertEquals(new Duration(0), moment.distanceAfter(reference));
        assertFalse(moment.distanceAfter(reference).toDuration().isShorterThan(Hours.hours(0).toStandardDuration()));
        assertFalse(moment.distanceAfter(reference).toDuration().isLongerThan(Hours.hours(0).toStandardDuration()));

        // case 2:  0__X___R____T
        reference = new PeriodicalTimeFrame(THURSDAY, 22, 0);
        moment = new PeriodicalTimeFrame(TUESDAY, 13, 0);
        assertTrue(Hours.hours(2 + 4 * 24 + 13).toStandardDuration().equals(moment.distanceAfter(reference).toDuration()));
        assertFalse(moment.distanceAfter(reference).toDuration().isShorterThan(Hours.hours(2 + 4 * 24 + 13).toStandardDuration()));
        assertFalse(moment.distanceAfter(reference).toDuration().isLongerThan(Hours.hours(2 + 4 * 24 + 13).toStandardDuration()));
        assertFalse(moment.distanceAfter(reference).toDuration().isShorterThan(Hours.hours(0).toStandardDuration()));

        // case 3:   0__R___X____T
        reference = new PeriodicalTimeFrame(SATURDAY, 13, 0);
        moment = new PeriodicalTimeFrame(MONDAY, 22, 0);
        assertTrue(Hours.hours(11 + 24 + 22).toStandardDuration().equals(moment.distanceAfter(reference).toDuration()));
        assertTrue(moment.distanceAfter(reference).toDuration().isLongerThan(Hours.hours(0).toStandardDuration()));

    }


}