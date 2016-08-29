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

import org.joda.time.LocalTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.joda.time.DateTimeConstants.MONDAY;
import static org.joda.time.DateTimeConstants.SUNDAY;
import static org.junit.Assert.assertEquals;


/**
 *
 */
public class TrainPathSlotCatalogueTest {
    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void testGetNextTrainPathSlotWithin24() {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 45);

        PeriodicalTimeFrame expectedNextStart;
        PeriodicalTimeFrame earliest;
        PeriodicalTrainPathSlot periodicalSlot;
        SystemNode from;
        SystemNode to;
        TrainPathSlot slot;

        // slots at :00 and :30
        from = macro.getSystemNode("A2");
        to = macro.getSystemNode("A3");
        earliest = new PeriodicalTimeFrame(MONDAY, 5, 10);
        expectedNextStart = new PeriodicalTimeFrame(MONDAY, 5, 30);
        slot = catalogue.getNextTrainPathSlotWithin24(from, to, earliest);
        assertEquals(expectedNextStart, slot.getStartTime());

        from = macro.getSystemNode("M10");
        to = macro.getSystemNode("D4");
        earliest = new PeriodicalTimeFrame(SUNDAY, 23, 45);
        expectedNextStart = new PeriodicalTimeFrame(MONDAY, 0, 0);
        slot = catalogue.getNextTrainPathSlotWithin24(from, to, earliest);
        assertEquals(expectedNextStart, slot.getStartTime());

        from = macro.getSystemNode("M4");
        to = macro.getSystemNode("D1");
        earliest = new PeriodicalTimeFrame(SUNDAY, 23, 45);
        exception.expect(IllegalArgumentException.class);
        catalogue.getNextTrainPathSlotWithin24(from, to, earliest);
    }

    @Test
    public void testGetNextTrainPathSlot() throws Exception {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 45);

        PeriodicalTimeFrame expectedNextStart;
        PeriodicalTimeFrame earliest;
        PeriodicalTrainPathSlot periodicalSlot;
        SystemNode from;
        SystemNode to;
        TrainPathSlot slot;

        // slots at :00 and :30
        from = macro.getSystemNode("A2");
        to = macro.getSystemNode("A3");
        earliest = new PeriodicalTimeFrame(MONDAY, 5, 10);
        expectedNextStart = new PeriodicalTimeFrame(MONDAY, 5, 30);
        slot = catalogue.getQuickestTrainPathSlot(from, to, earliest);
        assertEquals(expectedNextStart, slot.getStartTime());
        slot = catalogue.getNextTrainPathSlot(from, to, earliest);
        assertEquals(expectedNextStart, slot.getStartTime());

        from = macro.getSystemNode("M10");
        to = macro.getSystemNode("D4");
        earliest = new PeriodicalTimeFrame(SUNDAY, 23, 45);
        expectedNextStart = new PeriodicalTimeFrame(MONDAY, 0, 0);
        slot = catalogue.getQuickestTrainPathSlot(from, to, earliest);
        assertEquals(expectedNextStart, slot.getStartTime());
        slot = catalogue.getNextTrainPathSlot(from, to, earliest);
        assertEquals(expectedNextStart, slot.getStartTime());

        from = macro.getSystemNode("M4");
        to = macro.getSystemNode("D1");
        earliest = new PeriodicalTimeFrame(SUNDAY, 23, 45);
        exception.expect(IllegalArgumentException.class);
        catalogue.getQuickestTrainPathSlot(from, to, earliest);

    }

    @Test
    public void testGetTrainPathSlots() throws Exception {

        MacroscopicTopology macro = new MacroscopicTopology();
        TrainPathSlotCatalogue catalogue = new TrainPathSlotCatalogue();
        SystemNode testNode = macro.addSystemNodeIfNotExists("testNode");
        SystemNode testSuccessorNode = macro.addSystemNodeIfNotExists("testSuccessorNode");
        macro.link("testLinkName", testNode, testSuccessorNode);


        PeriodicalTimeFrame earliest = new PeriodicalTimeFrame(MONDAY, 0, 6);
        PeriodicalTimeFrame latest = new PeriodicalTimeFrame(MONDAY, 0, 10);

        LocalTime startTime = new LocalTime(0, 5);
        LocalTime endTime = new LocalTime(0, 10);
        Periodicity p = new Periodicity();
        p.setWeekDay(MONDAY, true);

        /*
        // No slot
        Request request = new Request("sampleRequest", testNode, testSuccessorNode, new LocalWeekTime(MONDAY,0,5), new LocalWeekTime(MONDAY, 0, 15));
        List<TrainPathSlot> initialTrainPathSlots = catalogue.getSortedTrainPathSlots(request.getFrom(), request.getTo(), request.getStartTime().minus(request.MAXIMUM_EARLIER_DEPARTURE), request.getStartTime().plus(request.MAXIMUM_LATER_DEPARTURE));
        assertEquals(0, initialTrainPathSlots.size());
        List<TravelPath> tps = catalogue.constructDAG(macro, request);
        assertEquals(0, tps.size());

        // One slot
        TrainPathSlot tp0 = catalogue.add("tp0", startTime, endTime, testNode, testSuccessorNode, p).getSlotOn(MONDAY);
        assertEquals(0, catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).size());
        initialTrainPathSlots = catalogue.getSortedTrainPathSlots(request.getFrom(), request.getTo(),request.getStartTime().minus(request.MAXIMUM_EARLIER_DEPARTURE), request.getStartTime().plus(request.MAXIMUM_LATER_DEPARTURE));
        assertTrue(initialTrainPathSlots.contains(tp0));
        assertEquals(1, initialTrainPathSlots.size());
        tps = catalogue.constructDAG(macro, request);
        assertEquals(1, tps.size());

        // Two slots
        startTime = new LocalTime(0, 7);
        endTime = new LocalTime(0, 10);
        TrainPathSlot tp1 = catalogue.add("tp1", startTime, endTime, testNode, testSuccessorNode, p).getSlotOn(MONDAY);
        assertFalse(catalogue.getSortedTrainPathSlots(testNode,testSuccessorNode, earliest, latest).contains(tp0));
        assertTrue(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp1));
        initialTrainPathSlots = catalogue.getSortedTrainPathSlots(request.getFrom(), request.getTo(),request.getStartTime().minus(request.MAXIMUM_EARLIER_DEPARTURE), request.getStartTime().plus(request.MAXIMUM_LATER_DEPARTURE));
        assertTrue(initialTrainPathSlots.contains(tp0));
        assertTrue(initialTrainPathSlots.contains(tp1));
        assertEquals(2, initialTrainPathSlots.size());
        tps = catalogue.constructDAG(macro, request);
        assertEquals(2, tps.size());

        // Three slots
        startTime = new LocalTime(0, 6);
        endTime = new LocalTime(0, 10);
        TrainPathSlot tp2 = catalogue.add("tp2", startTime, endTime, testNode, testSuccessorNode, p).getSlotOn(MONDAY);
        assertFalse(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp0));
        assertTrue(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp1));
        assertTrue(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp2));
        initialTrainPathSlots = catalogue.getSortedTrainPathSlots(request.getFrom(), request.getTo(), request.getStartTime().minus(request.MAXIMUM_EARLIER_DEPARTURE), request.getStartTime().plus(request.MAXIMUM_LATER_DEPARTURE));
        assertTrue(initialTrainPathSlots.contains(tp0));
        assertTrue(initialTrainPathSlots.contains(tp1));
        assertTrue(initialTrainPathSlots.contains(tp2));
        assertEquals(3, initialTrainPathSlots.size());
        tps = catalogue.constructDAG(macro, request);
        assertEquals(3, tps.size());


        // Four slots
        startTime = new LocalTime(0, 10);
        endTime = new LocalTime(0, 10);
        TrainPathSlot tp3 = catalogue.add("tp3", startTime, endTime, testNode, testSuccessorNode, p).getSlotOn(MONDAY);
        assertFalse(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp0));
        assertTrue(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp1));
        assertTrue(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp2));
        assertTrue(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp3));
        initialTrainPathSlots = catalogue.getSortedTrainPathSlots(request.getFrom(), request.getTo(), request.getStartTime().minus(request.MAXIMUM_EARLIER_DEPARTURE), request.getStartTime().plus(request.MAXIMUM_LATER_DEPARTURE));
        assertTrue(initialTrainPathSlots.contains(tp0));
        assertTrue(initialTrainPathSlots.contains(tp1));
        assertTrue(initialTrainPathSlots.contains(tp2));
        assertTrue(initialTrainPathSlots.contains(tp3));
        assertEquals(4, initialTrainPathSlots.size());
        tps = catalogue.constructDAG(macro, request);
        assertEquals(4, tps.size());


        // Five slots
        startTime = new LocalTime(15, 10);
        endTime = new LocalTime(16, 10);
        TrainPathSlot tp4 = catalogue.add("tp4", startTime, endTime, testNode, testSuccessorNode, p).getSlotOn(MONDAY);
        assertFalse(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp0));
        assertTrue(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp1));
        assertTrue(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp2));
        assertTrue(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp3));
        assertFalse(catalogue.getSortedTrainPathSlots(testNode, testSuccessorNode, earliest, latest).contains(tp4));
        initialTrainPathSlots = catalogue.getSortedTrainPathSlots(request.getFrom(), request.getTo(), request.getStartTime().minus(request.MAXIMUM_EARLIER_DEPARTURE), request.getStartTime().plus(request.MAXIMUM_LATER_DEPARTURE));
        assertTrue(initialTrainPathSlots.contains(tp0));
        assertTrue(initialTrainPathSlots.contains(tp1));
        assertTrue(initialTrainPathSlots.contains(tp2));
        assertTrue(initialTrainPathSlots.contains(tp3));
        assertFalse(initialTrainPathSlots.contains(tp4));
        assertEquals(4, initialTrainPathSlots.size());
        tps = catalogue.constructDAG(macro, request);
        assertEquals(4, tps.size());
        */
    }
}