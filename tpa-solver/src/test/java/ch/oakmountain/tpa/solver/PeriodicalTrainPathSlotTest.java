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

import java.util.LinkedList;
import java.util.List;

import static org.joda.time.DateTimeConstants.*;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class PeriodicalTrainPathSlotTest {

    @Test
    public void testIsContainedInclusive() throws Exception {

    }

    private void testGetNextSlot(PeriodicalTrainPathSlot periodicalSlot, PeriodicalTimeFrame earliest, PeriodicalTimeFrame expectedNextStart) {
        TrainPathSlot slot = periodicalSlot.getNextOrQuickestTrainPathSlot(earliest);
        assertEquals(expectedNextStart, slot.getStartTime());
        slot = periodicalSlot.getNextOrQuickestTrainPathSlot(earliest);
        assertEquals(expectedNextStart, slot.getStartTime());
    }

    @Test
    public void testAddAllStartTimeContainedInclusive() throws Exception {
        MacroscopicTopology macro = MacroscopicTopology.getTinyTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 45);
        PeriodicalTimeFrame expectedNextStart;
        PeriodicalTimeFrame earliest;
        PeriodicalTimeFrame latest;
        PeriodicalTrainPathSlot periodicalSlot;
        List<TrainPathSlot> slots;

        // M1_B1_001_001 runs at 01:30 daily
        periodicalSlot = catalogue.getPeriodicalSlot("M1_B1_001_001");
        earliest = new PeriodicalTimeFrame(MONDAY, 5, 10);
        latest = new PeriodicalTimeFrame(TUESDAY, 5, 10);
        slots = new LinkedList<>();
        periodicalSlot.addAllStartTimeContainedInclusive(earliest, latest, slots);
        assertEquals(slots.size(), 1);

        earliest = new PeriodicalTimeFrame(MONDAY, 5, 10);
        latest = new PeriodicalTimeFrame(TUESDAY, 1, 10);
        slots = new LinkedList<>();
        periodicalSlot.addAllStartTimeContainedInclusive(earliest, latest, slots);
        assertEquals(slots.size(), 0);

        earliest = new PeriodicalTimeFrame(SUNDAY, 5, 10);
        latest = new PeriodicalTimeFrame(TUESDAY, 1, 10);
        slots = new LinkedList<>();
        periodicalSlot.addAllStartTimeContainedInclusive(earliest, latest, slots);
        assertEquals(slots.size(), 1);

        earliest = new PeriodicalTimeFrame(SUNDAY, 5, 10);
        latest = new PeriodicalTimeFrame(TUESDAY, 2, 10);
        slots = new LinkedList<>();
        periodicalSlot.addAllStartTimeContainedInclusive(earliest, latest, slots);
        assertEquals(slots.size(), 2);

    }

    @Test
    public void testGetNextSlot() throws Exception {
        MacroscopicTopology macro = MacroscopicTopology.getTinyTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 45);

        PeriodicalTimeFrame expectedNextStart;
        PeriodicalTimeFrame earliest;
        PeriodicalTrainPathSlot periodicalSlot;

        // M1_B1_001_001 runs at 01:30 daily
        periodicalSlot = catalogue.getPeriodicalSlot("M1_B1_001_001");
        earliest = new PeriodicalTimeFrame(MONDAY, 5, 10);
        expectedNextStart = new PeriodicalTimeFrame(TUESDAY, 1, 30);
        testGetNextSlot(periodicalSlot, earliest, expectedNextStart);


        // M1_B1_015_000 runs at 01:00 daily
        periodicalSlot = catalogue.getPeriodicalSlot("M1_B1_015_000");
        earliest = new PeriodicalTimeFrame(SUNDAY, 20, 10);
        expectedNextStart = new PeriodicalTimeFrame(MONDAY, 15, 00);
        testGetNextSlot(periodicalSlot, earliest, expectedNextStart);

        // M1_B1_015_000 runs at 01:00 daily
        periodicalSlot = catalogue.getPeriodicalSlot("M1_B1_015_000");
        earliest = new PeriodicalTimeFrame(SUNDAY, 13, 10);
        expectedNextStart = new PeriodicalTimeFrame(SUNDAY, 15, 00);
        testGetNextSlot(periodicalSlot, earliest, expectedNextStart);

        // M1_B1_015_000 runs at 01:00 daily
        periodicalSlot = catalogue.getPeriodicalSlot("M1_B1_015_000");
        earliest = new PeriodicalTimeFrame(SUNDAY, 15, 10);
        expectedNextStart = new PeriodicalTimeFrame(MONDAY, 15, 00);
        testGetNextSlot(periodicalSlot, earliest, expectedNextStart);
    }
}