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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.joda.time.DateTimeConstants.TUESDAY;
import static org.joda.time.DateTimeConstants.WEDNESDAY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
/**
 *
 */
public class TrainPathSlotTest {

    @Test
    public void testSort() throws Exception {
        TrainPathSlot slot1 = mock(TrainPathSlot.class);
        when(slot1.getStartTime()).thenReturn(new PeriodicalTimeFrame(TUESDAY, 22, 0));
        TrainPathSlot slot2 = mock(TrainPathSlot.class);
        when(slot2.getStartTime()).thenReturn(new PeriodicalTimeFrame(WEDNESDAY, 10, 0));
        TrainPathSlot slot3 = mock(TrainPathSlot.class);
        when(slot3.getStartTime()).thenReturn(new PeriodicalTimeFrame(TUESDAY, 23, 0));

        List<TrainPathSlot> slots = new LinkedList<>(Arrays.asList(
                slot1, slot2, slot3));
        TrainPathSlot.sort(slots);
        assertTrue(slots.get(0).equals(slot1));
        assertTrue(slots.get(1).equals(slot3));
        assertTrue(slots.get(2).equals(slot2));

    }

    @Test
    public void testSort2() throws Exception {
        TrainPathSlot slot1 = mock(TrainPathSlot.class);
        when(slot1.getStartTime()).thenReturn(new PeriodicalTimeFrame(TUESDAY, 22, 0));
        TrainPathSlot slot2 = mock(TrainPathSlot.class);
        when(slot2.getStartTime()).thenReturn(new PeriodicalTimeFrame(TUESDAY, 22, 0));
        TrainPathSlot slot3 = mock(TrainPathSlot.class);
        when(slot3.getStartTime()).thenReturn(new PeriodicalTimeFrame(TUESDAY, 23, 0));

        List<TrainPathSlot> slots = new LinkedList<>(Arrays.asList(
                slot1, slot2, slot3));
        TrainPathSlot.sort(slots);
        assertTrue(slots.get(0).equals(slot1) || slots.get(0).equals(slot2));
        assertTrue(slots.get(1).equals(slot1) || slots.get(1).equals(slot2));
        assertFalse(slots.get(0).equals(slots.get(1)));
        assertTrue(slots.get(2).equals(slot3));

    }

    @Test
    public void testSort3() throws Exception {
        TrainPathSlot slot1 = mock(TrainPathSlot.class);
        when(slot1.getStartTime()).thenReturn(new PeriodicalTimeFrame(TUESDAY, 23, 0));
        TrainPathSlot slot2 = mock(TrainPathSlot.class);
        when(slot2.getStartTime()).thenReturn(new PeriodicalTimeFrame(TUESDAY, 22, 0));
        TrainPathSlot slot3 = mock(TrainPathSlot.class);
        when(slot3.getStartTime()).thenReturn(new PeriodicalTimeFrame(TUESDAY, 22, 0));

        List<TrainPathSlot> slots = new LinkedList<>(Arrays.asList(
                slot1, slot2, slot3));
        TrainPathSlot.sort(slots);
        assertTrue(slots.get(0).equals(slot2) || slots.get(0).equals(slot3));
        assertTrue(slots.get(1).equals(slot2) || slots.get(1).equals(slot3));
        assertFalse(slots.get(0).equals(slots.get(1)));
        assertTrue(slots.get(2).equals(slot1));

    }
}