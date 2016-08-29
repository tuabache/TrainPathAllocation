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

import java.lang.reflect.Field;
import java.util.List;

import static org.joda.time.DateTimeConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class SimpleTrainPathApplicationTest {

    @Test
    public void testGetBestPath() throws Exception {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 20);

        SimpleTrainPathApplication r = new SimpleTrainPathApplication("therequest", macro.getSystemNode("A1"), macro.getSystemNode("D1"), new PeriodicalTimeFrame(MONDAY, 5, 0), new PeriodicalTimeFrame(TUESDAY, 5, 0), null, 0, 0, 0);

        // Mon 5:00 + 16 * (0:20 + 0:10) + 0:20 => Mon 13:20
        parameterizedTestGetBestPath(macro, catalogue, r, 0, new PeriodicalTimeFrame(MONDAY, 13, 20));

        // Mon 5:00 + 16 * (0:20 + 0:30 + 0:10) + 0:20 => Mon 21:45
        parameterizedTestGetBestPath(macro, catalogue, r, 30, new PeriodicalTimeFrame(MONDAY, 21, 20));
    }

    @Test
    public void testGetBestPath2() throws Exception {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 20);

        SimpleTrainPathApplication r = new SimpleTrainPathApplication("therequest", macro.getSystemNode("A1"), macro.getSystemNode("D1"), new PeriodicalTimeFrame(SUNDAY, 19, 0), new PeriodicalTimeFrame(MONDAY, 12, 0), null, 0, 0, 0);

        // Mon 19:00 + 16 * (0:20 + 0:10) + 0:20 => Tue 1:20
        parameterizedTestGetBestPath(macro, catalogue, r, 0, new PeriodicalTimeFrame(MONDAY, 3, 20));

        // Mon 19:00 + 16 * (0:20 + 0:30 + 0:10) + 0:20 => Mon 21:45
        parameterizedTestGetBestPath(macro, catalogue, r, 30, new PeriodicalTimeFrame(MONDAY, 11, 20));
    }

    @Test
    public void testNoBestPath() throws Exception {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 20);

        SimpleTrainPathApplication r = new SimpleTrainPathApplication("therequest", macro.getSystemNode("A1"), macro.getSystemNode("D1"), new PeriodicalTimeFrame(MONDAY, 5, 0), new PeriodicalTimeFrame(MONDAY, 13, 0), null, 0, 0, 0);

        // Mon 5:00 + 16 * (0:20 + 0:10) + 0:20 => Mon 13:20
        assertNull(SolutionCandidateFinder.getEarliestPathWithinRequestedBounds(macro, catalogue, r));

    }


    private void parameterizedTestGetBestPath(MacroscopicTopology macro, TrainPathSlotCatalogue catalogue, SimpleTrainPathApplication r, int minDwellTime, PeriodicalTimeFrame expectedArrival) throws NoSuchFieldException, IllegalAccessException {
        TrainPathAllocationProblemPruningParameters params = new TrainPathAllocationProblemPruningParameters(r, 0, minDwellTime, 0);
        Field field = r.getClass().getDeclaredField("params");
        field.setAccessible(true);
        field.set(r, params);

        List<TrainPathSlot> path = SolutionCandidateFinder.getEarliestPathWithinRequestedBounds(macro, catalogue, r);
        assertEquals(17, path.size());
        assertEquals(expectedArrival, path.get(path.size() - 1).getEndTime());
    }
}