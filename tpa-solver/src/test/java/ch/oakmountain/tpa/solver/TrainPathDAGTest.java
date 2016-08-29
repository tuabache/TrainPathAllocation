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

import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.codehaus.plexus.util.dag.Vertex;
import org.joda.time.Minutes;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public class TrainPathDAGTest {


    @Test
    public void testRecursiveRemove() throws Exception {
        SimpleTrainPathApplication r = mock(SimpleTrainPathApplication.class);
        TrainPathSlotCatalogue cat = mock(TrainPathSlotCatalogue.class);
        when(r.getName()).thenReturn("r");
        when(r.getEndTime()).thenReturn(PeriodicalTimeFrame.END_OF_WEEK);
        when(r.getParams()).thenReturn(new TrainPathAllocationProblemPruningParameters(r));
        TrainPathDAG dag = new TrainPathDAG(r, cat);
        Vertex root = dag.addVertex("root");
        Vertex a = dag.addVertex("a");
        Vertex b = dag.addVertex("b");
        Vertex c = dag.addVertex("c");
        Vertex d = dag.addVertex("d");
        /*
          root - a - b -c
                   |
                   - d
         */
        dag.addEdge(root, a);
        dag.addEdge(a, b);
        dag.addEdge(b, c);
        dag.addEdge(a, d);
        assertEquals(2, dag.pathsFromVertex(root));
        assertEquals(1, dag.pathsFromVertex(b));
        assertEquals(2, dag.pathsFromVertex(a));

        TrainPathSlot slota = mock(TrainPathSlot.class);
        TrainPathSlot slotb = mock(TrainPathSlot.class);
        TrainPathSlot slotc = mock(TrainPathSlot.class);
        TrainPathSlot slotd = mock(TrainPathSlot.class);
        when(cat.getSlot("a")).thenReturn(slota);
        when(cat.getSlot("b")).thenReturn(slotb);
        when(cat.getSlot("c")).thenReturn(slotc);
        when(cat.getSlot("d")).thenReturn(slotd);
        when(slota.getName()).thenReturn("a");
        when(slotb.getName()).thenReturn("b");
        when(slotc.getName()).thenReturn("c");
        when(slotd.getName()).thenReturn("d");

        Method method = dag.getClass().getDeclaredMethod("backtrackOnVertex", Vertex.class, TrainPathDAG.Infeasible.class);
        method.setAccessible(true);
        method.invoke(dag, c, TrainPathDAG.Infeasible.NOSUCCESSOR);

        /*
          root - a -
                   |
                   - d
         */
        assertFalse(dag.hasEdge("b", "c"));
        assertFalse(dag.hasEdge("a", "b"));
        assertTrue(dag.hasEdge("root", "a"));
        assertTrue(dag.hasEdge("a", "d"));
        assertEquals(1, dag.pathsFromVertex(root));
        assertEquals(1, dag.pathsFromVertex(b));
        assertEquals(1, dag.pathsFromVertex(a));
    }

    @Test
    public void testSpan1() throws Exception {
        Set<SolutionCandidate> solutionCandidateSet = new HashSet<>();
        SolutionCandidate sol1 = mock(SolutionCandidate.class);
        SolutionCandidate sol2 = mock(SolutionCandidate.class);
        SolutionCandidate sol3 = mock(SolutionCandidate.class);
        solutionCandidateSet.add(sol1);
        solutionCandidateSet.add(sol2);
        solutionCandidateSet.add(sol3);


        TrainPathSlot slot1 = mock(TrainPathSlot.class);
        TrainPathSlot slot2 = mock(TrainPathSlot.class);
        TrainPathSlot slot3 = mock(TrainPathSlot.class);
        TrainPathSlot slot4 = mock(TrainPathSlot.class);
        TrainPathSlot slot5 = mock(TrainPathSlot.class);
        when(slot1.getName()).thenReturn("slot1");
        when(slot2.getName()).thenReturn("slot2");
        when(slot3.getName()).thenReturn("slot3");
        when(slot4.getName()).thenReturn("slot4");
        when(slot5.getName()).thenReturn("slot5");

        List<TrainPathSlot> path1 = new LinkedList<TrainPathSlot>();
        path1.add(slot1);
        path1.add(slot2);
        path1.add(slot3);

        List<TrainPathSlot> path2 = new LinkedList<TrainPathSlot>();
        path2.add(slot2);
        path2.add(slot3);
        path2.add(slot4);

        List<TrainPathSlot> path3 = new LinkedList<TrainPathSlot>();
        path3.add(slot3);
        path3.add(slot4);
        path3.add(slot5);

        when(sol1.getPath()).thenReturn(path1);
        when(sol2.getPath()).thenReturn(path2);
        when(sol3.getPath()).thenReturn(path3);

        double span = TrainPathDAG.getSpan(solutionCandidateSet);
        assertEquals(5, span, 0.001);
    }

    @Test
    public void testSpan2() throws Exception {
        Set<SolutionCandidate> solutionCandidateSet = new HashSet<>();
        SolutionCandidate sol1 = mock(SolutionCandidate.class);
        SolutionCandidate sol2 = mock(SolutionCandidate.class);
        SolutionCandidate sol3 = mock(SolutionCandidate.class);
        solutionCandidateSet.add(sol1);
        solutionCandidateSet.add(sol2);
        solutionCandidateSet.add(sol3);


        TrainPathSlot slot1 = mock(TrainPathSlot.class);
        TrainPathSlot slot2 = mock(TrainPathSlot.class);
        TrainPathSlot slot3 = mock(TrainPathSlot.class);
        TrainPathSlot slot4 = mock(TrainPathSlot.class);
        TrainPathSlot slot5 = mock(TrainPathSlot.class);
        when(slot1.getName()).thenReturn("slot1");
        when(slot2.getName()).thenReturn("slot2");
        when(slot3.getName()).thenReturn("slot3");
        when(slot4.getName()).thenReturn("slot4");
        when(slot5.getName()).thenReturn("slot5");

        List<TrainPathSlot> path1 = new LinkedList<TrainPathSlot>();
        path1.add(slot1);
        path1.add(slot2);

        List<TrainPathSlot> path2 = new LinkedList<TrainPathSlot>();
        path2.add(slot3);

        List<TrainPathSlot> path3 = new LinkedList<TrainPathSlot>();
        path3.add(slot4);
        path3.add(slot5);

        when(sol1.getPath()).thenReturn(path1);
        when(sol2.getPath()).thenReturn(path2);
        when(sol3.getPath()).thenReturn(path3);

        double span = TrainPathDAG.getSpan(solutionCandidateSet);
        assertEquals(5, span, 0.001);
    }

    @Test
    public void testSpan3() throws Exception {
        Set<SolutionCandidate> solutionCandidateSet = new HashSet<>();
        SolutionCandidate sol1 = mock(SolutionCandidate.class);
        SolutionCandidate sol2 = mock(SolutionCandidate.class);
        SolutionCandidate sol3 = mock(SolutionCandidate.class);
        solutionCandidateSet.add(sol1);
        solutionCandidateSet.add(sol2);
        solutionCandidateSet.add(sol3);


        TrainPathSlot slot1 = mock(TrainPathSlot.class);
        TrainPathSlot slot2 = mock(TrainPathSlot.class);
        TrainPathSlot slot3 = mock(TrainPathSlot.class);
        TrainPathSlot slot4 = mock(TrainPathSlot.class);
        TrainPathSlot slot5 = mock(TrainPathSlot.class);
        when(slot1.getName()).thenReturn("slot1");
        when(slot2.getName()).thenReturn("slot2");
        when(slot3.getName()).thenReturn("slot3");
        when(slot4.getName()).thenReturn("slot4");
        when(slot5.getName()).thenReturn("slot5");

        List<TrainPathSlot> path1 = new LinkedList<TrainPathSlot>();
        path1.add(slot1);
        path1.add(slot2);

        List<TrainPathSlot> path2 = new LinkedList<TrainPathSlot>();
        path2.add(slot2);
        path2.add(slot3);

        List<TrainPathSlot> path3 = new LinkedList<TrainPathSlot>();
        path3.add(slot4);
        path3.add(slot5);

        when(sol1.getPath()).thenReturn(path1);
        when(sol2.getPath()).thenReturn(path2);
        when(sol3.getPath()).thenReturn(path3);

        double span = TrainPathDAG.getSpan(solutionCandidateSet);
        assertEquals(5, span, 0.001);
    }

    @Test
    public void constructionTest() throws CycleDetectedException {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 20);

        // links are directed arcs
        assertEquals(macro.getLinkNames().size() * 2 * 24, catalogue.getTrainPathSlots().size());

        // departure: 5:00, arrival: 13:30 = 5:00 + (18-1) * 0:30
        SimpleTrainPathApplication r = new SimpleTrainPathApplication("therequest", macro.getSystemNode("A1"), macro.getSystemNode("D1"), new PeriodicalTimeFrame(1, 5, 0), new PeriodicalTimeFrame(1, 13, 30), null, 0, 0, 0);
        TrainPathAllocationProblemPruningParameters params = r.getParams();
        params.setDefaultPruning();
        TrainPathDAG dag = TrainPathDAG.constructDAG(macro, r, catalogue);

        // exactly on train path is possible
        assertEquals(true, dag.isTargetNodeReached());
        assertEquals(1, dag.getCyclomaticComplexity());
        assertEquals(1, dag.bottleneckSize());

        assertEquals(1, dag.nbPaths());

    }

    @Test
    public void constructionTest1b() throws CycleDetectedException {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 20);

        // links are directed arcs
        assertEquals(macro.getLinkNames().size() * 2 * 24, catalogue.getTrainPathSlots().size());

        // departure: 5:00, arrival: 14:00
        SimpleTrainPathApplication r = new SimpleTrainPathApplication("therequest", macro.getSystemNode("A1"), macro.getSystemNode("D1"), new PeriodicalTimeFrame(1, 5, 0), new PeriodicalTimeFrame(1, 14, 00), null, 0, 0, 0);
        TrainPathAllocationProblemPruningParameters params = r.getParams();
        params.setMAXIMUM_LATER_DEPARTURE(Minutes.minutes(30).toStandardDuration());
        params.setMAXIMUM_ADDITIONAL_DWELL_TIME(Minutes.minutes(40).toStandardDuration());
        params.setMAXIMUM_EARLIER_ARRIVAL(Minutes.minutes(40).toStandardDuration());
        TrainPathDAG dag = TrainPathDAG.constructDAG(macro, r, catalogue);

        // there are 18 positions to spend the additional 30 minutes...
        assertEquals(true, dag.isTargetNodeReached());
        assertEquals(18, dag.nbPaths());

    }

    @Test
    public void constructionTest1c() throws CycleDetectedException {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 20);

        // links are directed arcs
        assertEquals(macro.getLinkNames().size() * 2 * 24, catalogue.getTrainPathSlots().size());

        // departure: 5:00, arrival: 14:00
        SimpleTrainPathApplication r = new SimpleTrainPathApplication("therequest", macro.getSystemNode("A1"), macro.getSystemNode("D1"), new PeriodicalTimeFrame(1, 5, 0), new PeriodicalTimeFrame(1, 14, 00), null, 0, 0, 0);
        TrainPathAllocationProblemPruningParameters params = r.getParams();
        params.setMAXIMUM_LATER_DEPARTURE(Minutes.minutes(0).toStandardDuration());
        params.setMAXIMUM_ADDITIONAL_DWELL_TIME(Minutes.minutes(40).toStandardDuration());
        params.setMAXIMUM_EARLIER_ARRIVAL(Minutes.minutes(40).toStandardDuration());
        TrainPathDAG dag = TrainPathDAG.constructDAG(macro, r, catalogue);

        // the additional 30 minutes cannot be spent at the departure node, so there are 18-1=17 positions to spend the additional 30 minutes...
        assertEquals(true, dag.isTargetNodeReached());
        assertEquals(17, dag.nbPaths());

    }

    @Test
    public void constructionTest1d() throws CycleDetectedException {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 20);

        // links are directed arcs
        assertEquals(macro.getLinkNames().size() * 2 * 24, catalogue.getTrainPathSlots().size());

        // departure: 5:00, arrival: 14:00
        SimpleTrainPathApplication r = new SimpleTrainPathApplication("therequest", macro.getSystemNode("A1"), macro.getSystemNode("D1"), new PeriodicalTimeFrame(1, 5, 0), new PeriodicalTimeFrame(1, 14, 00), null, 0, 0, 0);
        TrainPathAllocationProblemPruningParameters params = r.getParams();
        params.setMAXIMUM_LATER_DEPARTURE(Minutes.minutes(0).toStandardDuration());
        params.setMAXIMUM_ADDITIONAL_DWELL_TIME(Minutes.minutes(10).toStandardDuration());
        params.setMAXIMUM_EARLIER_ARRIVAL(Minutes.minutes(40).toStandardDuration());
        TrainPathDAG dag = TrainPathDAG.constructDAG(macro, r, catalogue);

        // the additional 30 minutes have to be spent at the final node
        assertEquals(true, dag.isTargetNodeReached());
        assertEquals(1, dag.nbPaths());

    }

    @Test
    public void constructionTest2() throws CycleDetectedException, IllegalAccessException, IOException {
        MacroscopicTopology macro = MacroscopicTopology.getLargeTopology();
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macro, 2, 20);

        // links are directed arcs
        assertEquals(macro.getLinkNames().size() * 2 * 24, catalogue.getTrainPathSlots().size());

        // departure: 5:00, arrival: 14:00, 18*30
        SimpleTrainPathApplication r = new SimpleTrainPathApplication("therequest", macro.getSystemNode("A1"), macro.getSystemNode("D1"), new PeriodicalTimeFrame(1, 5, 0), new PeriodicalTimeFrame(1, 15, 0), null, 0, 0, 0);
        TrainPathAllocationProblemPruningParameters params = r.getParams();
        params.setMAXIMUM_ADDITIONAL_DWELL_TIME(Minutes.minutes(40).toStandardDuration());
        params.setMAXIMUM_LATER_DEPARTURE(Minutes.minutes(40).toStandardDuration());
        params.setMAXIMUM_EARLIER_ARRIVAL(Minutes.minutes(60).toStandardDuration());
        TrainPathDAG dag = TrainPathDAG.constructDAG(macro, r, catalogue);

        // exactly on train path is possible
        assertEquals(true, dag.isTargetNodeReached());
//        assertEquals(1,dag.nbPaths());


        // enumeration
        for (double ratio = 0.1; ratio <= 1; ratio += 0.1) {
            TrainPathDAG.SolutionCandidateEnumerationResult result = dag.enumerate(ratio);
            Set<SolutionCandidate> solutionCandidates = result.getSolutionCandidates();
            //TpaWebPersistor.createGraph(r.getName() + "_dag" + ((int) ratio * 10), "output", dag.toCSV(solutionCandidates, false), "<h1>Train Path Slot DAG for Request " + r.getName() + "</h1>\n" + r.getHTMLDescription(macro, catalogue));
            System.out.println("ratio " + ratio + " => cc " + TrainPathDAG.cyclomaticComplexityFromSolutionCandidates(solutionCandidates) + "/" + dag.getCyclomaticComplexity() + ", nb " + solutionCandidates.size());
            if (TPAUtil.doubleEquals(1, ratio)) {
                // 816
                assertEquals(dag.nbPaths(), solutionCandidates.size());

            }
        }
        //assertEquals(1, dag.bottleneckSize());


    }

}