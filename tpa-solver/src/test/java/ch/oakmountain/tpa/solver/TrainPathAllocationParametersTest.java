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

import org.joda.time.Minutes;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 *
 */
public class TrainPathAllocationParametersTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void testBounds() throws Exception {
        SimpleTrainPathApplication r = getRequest();
        assertEquals(new PeriodicalTimeFrame(1, 5, 0), r.getParams().getDepartureLowerBound());
        assertEquals(new PeriodicalTimeFrame(1, 5, 0), r.getParams().getDepartureHardLowerBound());
        assertEquals(new PeriodicalTimeFrame(1, 14, 0), r.getParams().getArrivalUpperBound());
        assertEquals(new PeriodicalTimeFrame(1, 14, 0), r.getParams().getArrivalHardUpperBound());

        setHardBounds(r, 60, 0, 60);

        assertEquals(new PeriodicalTimeFrame(1, 5, 0), r.getParams().getDepartureLowerBound());
        assertEquals(new PeriodicalTimeFrame(1, 4, 0), r.getParams().getDepartureHardLowerBound());
        assertEquals(new PeriodicalTimeFrame(1, 14, 0), r.getParams().getArrivalUpperBound());
        assertEquals(new PeriodicalTimeFrame(1, 15, 0), r.getParams().getArrivalHardUpperBound());

        r.getParams().setMAXIMUM_EARLIER_DEPARTURE(Minutes.minutes(60).toStandardDuration());

        assertEquals(new PeriodicalTimeFrame(1, 4, 0), r.getParams().getDepartureLowerBound());
        assertEquals(new PeriodicalTimeFrame(1, 4, 0), r.getParams().getDepartureHardLowerBound());
        assertEquals(new PeriodicalTimeFrame(1, 14, 0), r.getParams().getArrivalUpperBound());
        assertEquals(new PeriodicalTimeFrame(1, 15, 0), r.getParams().getArrivalHardUpperBound());

        r.getParams().setMAXIMUM_EARLIER_DEPARTURE(Minutes.minutes(30).toStandardDuration());
        r.getParams().setMAXIMUM_LATER_ARRIVAL(Minutes.minutes(30).toStandardDuration());

        assertEquals(new PeriodicalTimeFrame(1, 4, 30), r.getParams().getDepartureLowerBound());
        assertEquals(new PeriodicalTimeFrame(1, 4, 0), r.getParams().getDepartureHardLowerBound());
        assertEquals(new PeriodicalTimeFrame(1, 14, 30), r.getParams().getArrivalUpperBound());
        assertEquals(new PeriodicalTimeFrame(1, 15, 0), r.getParams().getArrivalHardUpperBound());
    }

    @Test
    public void testIsWihinHardBounds() throws Exception {
        SimpleTrainPathApplication r = getRequest();

        setHardBounds(r, 120, 0, 70);

        assertTrue(r.getParams().isWithinHardBounds(new PeriodicalTimeFrame(1, 3, 0)));
        assertTrue(r.getParams().isWithinHardBounds(new PeriodicalTimeFrame(1, 15, 10)));
        assertTrue(r.getParams().isWithinHardBounds(new PeriodicalTimeFrame(1, 6, 0)));
        assertTrue(r.getParams().isWithinHardBounds(new PeriodicalTimeFrame(1, 13, 0)));
        assertFalse(r.getParams().isWithinHardBounds(new PeriodicalTimeFrame(1, 2, 0)));
        assertFalse(r.getParams().isWithinHardBounds(new PeriodicalTimeFrame(1, 15, 20)));
        assertFalse(r.getParams().isWithinHardBounds(new PeriodicalTimeFrame(7, 14, 0)));


    }

    @Test
    public void testUpperArrivalBoundCheck() throws Exception {
        SimpleTrainPathApplication r = getRequest();

        setHardBounds(r, 120, 0, 70);

        exception.expect(IllegalArgumentException.class);
        r.getParams().setMAXIMUM_LATER_ARRIVAL(Minutes.minutes(80).toStandardDuration());
    }

    @Test
    public void testLowerDepartureBoundCheck() throws Exception {
        SimpleTrainPathApplication r = getRequest();

        setHardBounds(r, 120, 0, 70);

        exception.expect(IllegalArgumentException.class);
        r.getParams().setMAXIMUM_EARLIER_DEPARTURE(Minutes.minutes(130).toStandardDuration());
    }

    @Test
    public void testIsWithinBounds() throws Exception {

        SimpleTrainPathApplication r = getRequest();

        assertTrue(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 5, 0)));
        assertTrue(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 14, 0)));
        assertTrue(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 6, 0)));
        assertTrue(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 13, 0)));
        assertFalse(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 15, 0)));
        assertFalse(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 3, 0)));
        assertFalse(r.getParams().isWithinBounds(new PeriodicalTimeFrame(7, 14, 0)));

        setHardBounds(r, 120, 0, 80);
        r.getParams().setMAXIMUM_LATER_ARRIVAL(Minutes.minutes(80).toStandardDuration());

        assertTrue(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 5, 0)));
        assertTrue(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 14, 0)));
        assertTrue(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 6, 0)));
        assertTrue(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 13, 0)));
        assertTrue(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 15, 20)));
        assertFalse(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 4, 0)));
        assertFalse(r.getParams().isWithinBounds(new PeriodicalTimeFrame(1, 3, 0)));
        assertFalse(r.getParams().isWithinBounds(new PeriodicalTimeFrame(7, 14, 0)));
    }

    private SimpleTrainPathApplication getRequest() throws NoSuchFieldException, IllegalAccessException {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 20);

        // links are directed arcs
        assertEquals(macro.getLinkNames().size() * 2 * 24, catalogue.getTrainPathSlots().size());

        SimpleTrainPathApplication r = new SimpleTrainPathApplication("therequest", macro.getSystemNode("A1"), macro.getSystemNode("D1"), new PeriodicalTimeFrame(1, 5, 0), new PeriodicalTimeFrame(1, 14, 0), null, 0, 0, 0);
        setHardBounds(r, 0, 0, 0);
        return r;
    }

    private void setHardBounds(SimpleTrainPathApplication r, int med, int mdt, int mla) throws NoSuchFieldException, IllegalAccessException {
        TrainPathAllocationProblemPruningParameters params = new TrainPathAllocationProblemPruningParameters(r, med, mdt, mla);
        Field field = r.getClass().getDeclaredField("params");
        field.setAccessible(true);
        field.set(r, params);
    }
}