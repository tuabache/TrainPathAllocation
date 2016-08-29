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

import static org.junit.Assert.*;

/**
 *
 */
public class MacroscopicTopologyTest {


    @Test
    public void testIsLinked() throws Exception {
        MacroscopicTopology macro = new MacroscopicTopology();
        SystemNode testNode = macro.addSystemNodeIfNotExists("testNode");
        SystemNode testSuccessorNode1 = macro.addSystemNodeIfNotExists("testSuccessorNode1");
        SystemNode testSuccessorNode2 = macro.addSystemNodeIfNotExists("testSuccessorNode2");

        macro.link("testLinkName1", testNode, testSuccessorNode1);
        macro.link("testLinkName2", testNode, testSuccessorNode2);

        assertTrue(macro.isLinked(testNode, testSuccessorNode1));
        assertTrue(macro.isLinked(testNode, testSuccessorNode2));
        assertFalse(macro.isLinked(testSuccessorNode1, testNode));
        assertFalse(macro.isLinked(testSuccessorNode2, testNode));
        assertFalse(macro.isLinked(testSuccessorNode1, testSuccessorNode2));
        assertFalse(macro.isLinked(testSuccessorNode2, testSuccessorNode1));
        assertFalse(macro.isLinked(testNode, testNode));
        assertFalse(macro.isLinked(testSuccessorNode1, testSuccessorNode1));
        assertFalse(macro.isLinked(testSuccessorNode2, testSuccessorNode2));

        boolean thrown = false;
        try {
            macro.link("bla", testNode, testNode);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void routeTest1() throws Exception {
        MacroscopicTopology macro = new MacroscopicTopology();
        SystemNode testSuccessorNode1 = macro.addSystemNodeIfNotExists("testSuccessorNode1");
        SystemNode testSuccessorNode2 = macro.addSystemNodeIfNotExists("testSuccessorNode2");
        SystemNode testSuccessorNode3 = macro.addSystemNodeIfNotExists("testSuccessorNode3");
        SystemNode testSuccessorNode4 = macro.addSystemNodeIfNotExists("testSuccessorNode4");

        macro.link("testLinkName1", testSuccessorNode1, testSuccessorNode2);
        macro.link("testLinkName2", testSuccessorNode2, testSuccessorNode3);
        macro.link("testLinkName3", testSuccessorNode3, testSuccessorNode4);

        LinkedList<SystemNode> route = new LinkedList<>();
        route.add(testSuccessorNode1);
        route.add(testSuccessorNode2);
        route.add(testSuccessorNode3);
        route.add(testSuccessorNode4);
        macro.addRoute(route);

        assertEquals(0, macro.getRoutes(testSuccessorNode1, testSuccessorNode1).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode1, testSuccessorNode2).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode1, testSuccessorNode3).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode1, testSuccessorNode4).size());

        assertEquals(0, macro.getRoutes(testSuccessorNode2, testSuccessorNode1).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode2, testSuccessorNode2).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode2, testSuccessorNode3).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode2, testSuccessorNode4).size());

        assertEquals(0, macro.getRoutes(testSuccessorNode3, testSuccessorNode1).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode3, testSuccessorNode2).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode3, testSuccessorNode3).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode3, testSuccessorNode4).size());

        assertEquals(0, macro.getRoutes(testSuccessorNode4, testSuccessorNode1).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode4, testSuccessorNode2).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode4, testSuccessorNode3).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode4, testSuccessorNode4).size());
    }

    @Test
    public void routeTest2() throws Exception {
        MacroscopicTopology macro = new MacroscopicTopology();
        SystemNode testSuccessorNode1 = macro.addSystemNodeIfNotExists("testSuccessorNode1");
        SystemNode testSuccessorNode2 = macro.addSystemNodeIfNotExists("testSuccessorNode2");
        SystemNode testSuccessorNode3 = macro.addSystemNodeIfNotExists("testSuccessorNode3");
        SystemNode testSuccessorNode3bis = macro.addSystemNodeIfNotExists("testSuccessorNode3bis");
        SystemNode testSuccessorNode4 = macro.addSystemNodeIfNotExists("testSuccessorNode4");

        macro.link("testLinkName1_2", testSuccessorNode1, testSuccessorNode2);
        macro.link("testLinkName2_3", testSuccessorNode2, testSuccessorNode3);
        macro.link("testLinkName2_3bis", testSuccessorNode2, testSuccessorNode3bis);
        macro.link("testLinkName3_4", testSuccessorNode3, testSuccessorNode4);
        macro.link("testLinkName3bis_4", testSuccessorNode3bis, testSuccessorNode4);

        LinkedList<SystemNode> route1 = new LinkedList<>();
        route1.add(testSuccessorNode1);
        route1.add(testSuccessorNode2);
        route1.add(testSuccessorNode3);
        route1.add(testSuccessorNode4);
        macro.addRoute(route1);
        LinkedList<SystemNode> route2 = new LinkedList<>();
        route2.add(testSuccessorNode1);
        route2.add(testSuccessorNode2);
        route2.add(testSuccessorNode3bis);
        route2.add(testSuccessorNode4);
        macro.addRoute(route2);

        assertEquals(0, macro.getRoutes(testSuccessorNode1, testSuccessorNode1).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode1, testSuccessorNode2).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode1, testSuccessorNode3).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode1, testSuccessorNode3bis).size());
        assertEquals(2, macro.getRoutes(testSuccessorNode1, testSuccessorNode4).size());

        assertEquals(0, macro.getRoutes(testSuccessorNode2, testSuccessorNode1).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode2, testSuccessorNode2).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode2, testSuccessorNode3).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode2, testSuccessorNode3bis).size());
        assertEquals(2, macro.getRoutes(testSuccessorNode2, testSuccessorNode4).size());

        assertEquals(0, macro.getRoutes(testSuccessorNode3, testSuccessorNode1).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode3, testSuccessorNode2).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode3, testSuccessorNode3).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode3, testSuccessorNode3bis).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode3, testSuccessorNode4).size());

        assertEquals(0, macro.getRoutes(testSuccessorNode3bis, testSuccessorNode1).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode3bis, testSuccessorNode2).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode3bis, testSuccessorNode3).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode3bis, testSuccessorNode3bis).size());
        assertEquals(1, macro.getRoutes(testSuccessorNode3bis, testSuccessorNode4).size());

        assertEquals(0, macro.getRoutes(testSuccessorNode4, testSuccessorNode1).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode4, testSuccessorNode2).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode4, testSuccessorNode3).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode4, testSuccessorNode3bis).size());
        assertEquals(0, macro.getRoutes(testSuccessorNode4, testSuccessorNode4).size());
    }

    @Test
    public void testGetSuccessors() throws Exception {
        MacroscopicTopology macro = new MacroscopicTopology();
        SystemNode testSuccessorNode1 = macro.addSystemNodeIfNotExists("testSuccessorNode1");
        SystemNode testSuccessorNode2 = macro.addSystemNodeIfNotExists("testSuccessorNode2");
        SystemNode testSuccessorNode3 = macro.addSystemNodeIfNotExists("testSuccessorNode3");
        SystemNode testSuccessorNode3bis = macro.addSystemNodeIfNotExists("testSuccessorNode3bis");
        SystemNode testSuccessorNode4 = macro.addSystemNodeIfNotExists("testSuccessorNode4");

        macro.link("testLinkName1_2", testSuccessorNode1, testSuccessorNode2);
        macro.link("testLinkName2_3", testSuccessorNode2, testSuccessorNode3);
        macro.link("testLinkName2_3bis", testSuccessorNode2, testSuccessorNode3bis);
        macro.link("testLinkName3_4", testSuccessorNode3, testSuccessorNode4);
        macro.link("testLinkName3bis_4", testSuccessorNode3bis, testSuccessorNode4);

        List<SystemNode> endPoints = new LinkedList<>();
        endPoints.add(testSuccessorNode1);
        endPoints.add(testSuccessorNode4);
        List<List<SystemNode>> routes = macro.findRoutesByEndPoints(endPoints);
        macro.addRoutes(routes);


        assertEquals(1, macro.getSuccessors(testSuccessorNode1, testSuccessorNode1, testSuccessorNode2).size());
        assertEquals(0, macro.getSuccessors(testSuccessorNode1, testSuccessorNode1, testSuccessorNode1).size());
        assertEquals(1, macro.getSuccessors(testSuccessorNode1, testSuccessorNode1, testSuccessorNode4).size());
        assertEquals(2, macro.getSuccessors(testSuccessorNode2, testSuccessorNode1, testSuccessorNode4).size());
        assertEquals(0, macro.getSuccessors(testSuccessorNode2, testSuccessorNode4, testSuccessorNode1).size());

    }

    @Test
    public void testFindRoutes() throws Exception {
        MacroscopicTopology macro = new MacroscopicTopology();
        SystemNode testSuccessorNode1 = macro.addSystemNodeIfNotExists("testSuccessorNode1");
        SystemNode testSuccessorNode2 = macro.addSystemNodeIfNotExists("testSuccessorNode2");
        SystemNode testSuccessorNode3 = macro.addSystemNodeIfNotExists("testSuccessorNode3");
        SystemNode testSuccessorNode3bis = macro.addSystemNodeIfNotExists("testSuccessorNode3bis");
        SystemNode testSuccessorNode4 = macro.addSystemNodeIfNotExists("testSuccessorNode4");

        macro.link("testLinkName1_2", testSuccessorNode1, testSuccessorNode2);
        macro.link("testLinkName2_3", testSuccessorNode2, testSuccessorNode3);
        macro.link("testLinkName2_3bis", testSuccessorNode2, testSuccessorNode3bis);
        macro.link("testLinkName3_4", testSuccessorNode3, testSuccessorNode4);
        macro.link("testLinkName3bis_4", testSuccessorNode3bis, testSuccessorNode4);

        assertEquals(0, macro.findRoutes(testSuccessorNode1, testSuccessorNode1).size());
        assertEquals(1, macro.findRoutes(testSuccessorNode1, testSuccessorNode2).size());
        assertEquals(1, macro.findRoutes(testSuccessorNode1, testSuccessorNode3).size());
        assertEquals(1, macro.findRoutes(testSuccessorNode1, testSuccessorNode3bis).size());
        assertEquals(2, macro.findRoutes(testSuccessorNode1, testSuccessorNode4).size());

        assertEquals(0, macro.findRoutes(testSuccessorNode2, testSuccessorNode1).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode2, testSuccessorNode2).size());
        assertEquals(1, macro.findRoutes(testSuccessorNode2, testSuccessorNode3).size());
        assertEquals(1, macro.findRoutes(testSuccessorNode2, testSuccessorNode3bis).size());
        assertEquals(2, macro.findRoutes(testSuccessorNode2, testSuccessorNode4).size());

        assertEquals(0, macro.findRoutes(testSuccessorNode3, testSuccessorNode1).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode3, testSuccessorNode2).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode3, testSuccessorNode3).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode3, testSuccessorNode3bis).size());
        assertEquals(1, macro.findRoutes(testSuccessorNode3, testSuccessorNode4).size());

        assertEquals(0, macro.findRoutes(testSuccessorNode3bis, testSuccessorNode1).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode3bis, testSuccessorNode2).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode3bis, testSuccessorNode3).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode3bis, testSuccessorNode3bis).size());
        assertEquals(1, macro.findRoutes(testSuccessorNode3bis, testSuccessorNode4).size());

        assertEquals(0, macro.findRoutes(testSuccessorNode4, testSuccessorNode1).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode4, testSuccessorNode2).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode4, testSuccessorNode3).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode4, testSuccessorNode3bis).size());
        assertEquals(0, macro.findRoutes(testSuccessorNode4, testSuccessorNode4).size());

    }
}