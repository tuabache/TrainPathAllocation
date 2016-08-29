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

import ch.oakmountain.tpa.web.GraphCSV;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

/**
 *
 */
public class MacroscopicTopology {
    static final Logger LOGGER = LogManager.getLogger();

    Hashtable<String, Pair<SystemNode, SystemNode>> trainPathSectionsList = new Hashtable<String, Pair<SystemNode, SystemNode>>();
    List<List<SystemNode>> routes = new LinkedList<List<SystemNode>>();
    private Hashtable<String, SystemNode> systemNodes = new Hashtable<String, SystemNode>();

    /* Topology as in TpaTestDataEmptyTemplate.xls */
    public static MacroscopicTopology getLargeTopology() {
        MacroscopicTopology macro = new MacroscopicTopology();
        macro.link("A1_A2", "A1", "A2");
        macro.link("A2_A3", "A2", "A3");
        macro.link("A3_A4", "A3", "A4");
        macro.link("A4_M1", "A4", "M1");
        macro.link("B1_B2", "B1", "B2");
        macro.link("B2_B3", "B2", "B3");
        macro.link("B3_B4", "B3", "B4");
        macro.link("B4_M1", "B4", "M1");
        macro.link("C1_C2", "C1", "C2");
        macro.link("C2_C3", "C2", "C3");
        macro.link("C3_C4", "C3", "C4");
        macro.link("C4_M10", "C4", "M10");
        macro.link("D1_D2", "D1", "D2");
        macro.link("D2_D3", "D2", "D3");
        macro.link("D3_D4", "D3", "D4");
        macro.link("D4_M10", "D4", "M10");
        macro.link("M1_M2", "M1", "M2");
        macro.link("M2_M3", "M2", "M3");
        macro.link("M3_M4", "M3", "M4");
        macro.link("M4_M5", "M4", "M5");
        macro.link("M5_M6", "M5", "M6");
        macro.link("M6_M7", "M6", "M7");
        macro.link("M7_M8", "M7", "M8");
        macro.link("M8_M9", "M8", "M9");
        macro.link("M9_M10", "M9", "M10");
        macro.link("A2_A1", "A2", "A1");
        macro.link("A3_A2", "A3", "A2");
        macro.link("A4_A3", "A4", "A3");
        macro.link("M1_A4", "M1", "A4");
        macro.link("B2_B1", "B2", "B1");
        macro.link("B3_B2", "B3", "B2");
        macro.link("B4_B3", "B4", "B3");
        macro.link("M1_B4", "M1", "B4");
        macro.link("C2_C1", "C2", "C1");
        macro.link("C3_C2", "C3", "C2");
        macro.link("C4_C3", "C4", "C3");
        macro.link("M10_C4", "M10", "C4");
        macro.link("D2_D1", "D2", "D1");
        macro.link("D3_D2", "D3", "D2");
        macro.link("D4_D3", "D4", "D3");
        macro.link("M10_D4", "M10", "D4");
        macro.link("M2_M1", "M2", "M1");
        macro.link("M3_M2", "M3", "M2");
        macro.link("M4_M3", "M4", "M3");
        macro.link("M5_M4", "M5", "M4");
        macro.link("M6_M5", "M6", "M5");
        macro.link("M7_M6", "M7", "M6");
        macro.link("M8_M7", "M8", "M7");
        macro.link("M9_M8", "M9", "M8");
        macro.link("M10_M9", "M10", "M9");

        List<SystemNode> endPoints = new LinkedList<>();
        endPoints.add(macro.getSystemNode("A1"));
        endPoints.add(macro.getSystemNode("B1"));
        endPoints.add(macro.getSystemNode("C1"));
        endPoints.add(macro.getSystemNode("D1"));
        List<List<SystemNode>> routes = macro.findRoutesByEndPoints(endPoints);
        macro.addRoutes(routes);
        return macro;
    }

    /* Topology as in TpaTestDataEmptyTemplateTiny.xls */
    public static MacroscopicTopology getTinyTopology() {
        MacroscopicTopology macro = new MacroscopicTopology();
        macro.link("A1_M1", "A1", "M1");
        macro.link("M1_A1", "M1", "A1");
        macro.link("B1_M1", "B1", "M1");
        macro.link("M1_B1", "M1", "B1");
        macro.link("M1_M2", "M1", "M2");
        macro.link("M2_M1", "M2", "M1");
        macro.link("C1_M2", "C1", "M2");
        macro.link("M2_C1", "M2", "C1");
        macro.link("D1_M2", "D1", "M2");
        macro.link("M2_D1", "M2", "D1");
        List<SystemNode> endPoints = new LinkedList<>();
        endPoints.add(macro.getSystemNode("A1"));
        endPoints.add(macro.getSystemNode("B1"));
        endPoints.add(macro.getSystemNode("C1"));
        endPoints.add(macro.getSystemNode("D1"));
        List<List<SystemNode>> routes = macro.findRoutesByEndPoints(endPoints);
        macro.addRoutes(routes);
        return macro;
    }

    public SystemNode addSystemNodeIfNotExists(String name) {
        if (!systemNodes.containsKey(name)) {
            systemNodes.put(name, new SystemNode(name));
        }
        return systemNodes.get(name);
    }

    public SystemNode getSystemNode(String name) {
        if (!systemNodes.containsKey(name)) {
            throw new IllegalArgumentException("There is no system node \"" + name + "\" defined in this macroscopic topology.");
        }
        return systemNodes.get(name);
    }

    public Pair<SystemNode, SystemNode> getLink(String linkName) {
        if (!trainPathSectionsList.containsKey(linkName)) {
            throw new IllegalArgumentException("There is no link \"" + linkName + "\" defined in this macroscopic topology.");
        }
        return trainPathSectionsList.get(linkName);
    }

    public void link(String name, String fromName, String toName) {
        SystemNode from = addSystemNodeIfNotExists(fromName);
        SystemNode to = addSystemNodeIfNotExists(toName);
        link(name, from, to);
    }

    public boolean isLinked(SystemNode from, SystemNode to) {
        Pair<SystemNode, SystemNode> trainPathSection = new Pair(from, to);
        return trainPathSectionsList.containsValue(trainPathSection);
    }

    public void link(String name, SystemNode from, SystemNode to) {
        Pair<SystemNode, SystemNode> trainPathSection = new Pair<SystemNode, SystemNode>(from, to);
        if (trainPathSectionsList.containsKey(name) && trainPathSectionsList.get(name).equals(trainPathSection)) {
            return;
        } else if (trainPathSectionsList.containsKey(name)) {
            throw new IllegalArgumentException("A different train path section with the same name already exists.");
        }
        if (trainPathSectionsList.containsValue(trainPathSection)) {
            if (!trainPathSectionsList.containsKey(name) || !trainPathSectionsList.get(name).equals(trainPathSection)) {
                LOGGER.warn("System Path Section " + trainPathSection + " already contained under different name => skipping");
                return;
            }
        }
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Name must not be blank.");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("A node cannot be linked to itself.");
        }

        trainPathSectionsList.put(name, trainPathSection);
    }

    public GraphCSV toCSV() {
        GraphCSV csv = new GraphCSV();
        for (String trainPathSectionName : trainPathSectionsList.keySet()) {
            Pair<SystemNode, SystemNode> trainPathSection = trainPathSectionsList.get(trainPathSectionName);

            // Trick out d3.js: add only one edge per pair
            if (trainPathSection.first.getName().hashCode() < trainPathSection.second.getName().hashCode()) {
                csv.appendLine(trainPathSection.first.getName(), trainPathSection.second.getName(), "4.0", trainPathSectionName, "", "", "", "");
            }
        }
        return csv;
    }

    public List<String> getLinkNames() {
        return new ArrayList<String>(trainPathSectionsList.keySet());
    }

    public void addRoutes(List<List<SystemNode>> routes) {
        for (List<SystemNode> route : routes) {
            addRoute(route);
        }
    }

    public void addRoute(List<SystemNode> route) {
        if (route.size() < 2) {
            throw new IllegalArgumentException("A route must containt at least two elements.");
        }
        for (int i = 0; i <= route.size() - 2; i++) {
            SystemNode el = route.get(i);
            SystemNode nextEl = route.get(i + 1);
            if (!isLinked(el, nextEl)) {
                throw new IllegalArgumentException("A route must consist of train path sections");
            }
            if (route.indexOf(nextEl) < i + 1) {
                throw new IllegalArgumentException("A route must not be circular");
            }
        }
        routes.add(route);

    }

    public boolean sameRoute(List<SystemNode> r1, List<SystemNode> r2) {
        if (r1.size() != r2.size()) {
            return false;
        }
        for (int i = 0; i < r1.size(); i++) {
            if (!r1.get(i).equals(r2.get(i))) {
                return false;
            }
        }
        return true;
    }

    private List<List<SystemNode>> removeDuplicateRoutes(List<List<SystemNode>> routes) {
        if (routes.size() == 0) {
            return routes;
        }
        List<List<SystemNode>> cleanedRoutes = new LinkedList<List<SystemNode>>();
        cleanedRoutes.add(routes.get(0));
        for (int i = 1; i < routes.size(); i++) {
            boolean duplicate = false;
            List<SystemNode> routeToCheck = routes.get(i);
            for (List<SystemNode> cleanedRoute : cleanedRoutes) {
                if (sameRoute(routeToCheck, cleanedRoute)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                cleanedRoutes.add(routeToCheck);
            }
        }
        return cleanedRoutes;
    }

    protected List<List<SystemNode>> getRoutes(List<List<SystemNode>> routes, SystemNode from, SystemNode to) {
        List<List<SystemNode>> routesFound = new LinkedList<List<SystemNode>>();
        for (List<SystemNode> route : routes) {
            int fromIndex = route.indexOf(from);
            int toIndex = route.indexOf(to);
            if (fromIndex >= 0 && toIndex >= 0 && toIndex > fromIndex) {
                routesFound.add(route.subList(fromIndex, toIndex + 1));
            }
        }
        return removeDuplicateRoutes(routesFound);
    }

    /**
     * Get the routes from {@code from} to {@code to}.
     *
     * @param from
     * @param to
     * @return
     */
    public List<List<SystemNode>> getRoutes(SystemNode from, SystemNode to) {
        return getRoutes(this.routes, from, to);
    }

    /**
     * Get the possible subroutes from {@code from} to {@code to} contained in routes {@code alongFrom} to {@code alongTo}.
     *
     * @param from
     * @param alongFrom
     * @param alongTo
     * @return
     */
    public List<SystemNode> getSuccessors(SystemNode from, SystemNode alongFrom, SystemNode alongTo) {
        List<List<SystemNode>> alongRoutes = getRoutes(alongFrom, alongTo);
        List<SystemNode> successors = new LinkedList<>();
        for (List<SystemNode> route : alongRoutes) {
            int fromIndex = route.indexOf(from);
            if (fromIndex >= 0 && route.size() > fromIndex + 1) {
                SystemNode successor = route.get(fromIndex + 1);
                if (!successors.contains(successor)) {
                    successors.add(successor);
                }
            }
        }
        return successors;
    }

    public List<List<SystemNode>> findRoutesByEndPoints(List<SystemNode> endPoints) {
        List<List<SystemNode>> routes = new LinkedList<List<SystemNode>>();
        for (SystemNode from : endPoints) {
            for (SystemNode to : endPoints) {
                routes.addAll(findRoutes(from, to));
            }

        }
        return routes;
    }

    private List<SystemNode> getSuccessors(SystemNode node, List<SystemNode> predecessors) {
        List<SystemNode> successors = new LinkedList<>();
        for (String sectionName : trainPathSectionsList.keySet()) {
            Pair<SystemNode, SystemNode> link = trainPathSectionsList.get(sectionName);
            if (link.first.equals(node) && !predecessors.contains(link.second)) {
                successors.add(link.second);
            }
        }
        return successors;
    }

    public List<List<SystemNode>> findRoutes(SystemNode from, SystemNode to) {
        List<List<SystemNode>> routesToCheck = new LinkedList<List<SystemNode>>();
        List<SystemNode> route = new LinkedList<>();
        route.add(from);
        routesToCheck.add(route);
        List<List<SystemNode>> foundRoutes = new LinkedList<List<SystemNode>>();
        return findRoutes(from, to, routesToCheck, foundRoutes);
    }

    private List<List<SystemNode>> findRoutes(SystemNode from, SystemNode to, List<List<SystemNode>> routesToCheck, List<List<SystemNode>> foundRoutes) {
        if (routesToCheck.isEmpty()) {
            return removeDuplicateRoutes(foundRoutes);
        }

        List<List<SystemNode>> nextRoutes = new LinkedList<List<SystemNode>>();
        for (List<SystemNode> route : routesToCheck) {
            SystemNode last = route.get(route.size() - 1);
            if (last.equals(to)) {
                if (route.size() >= 2) {
                    foundRoutes.add(route);
                }
            } else {
                List<SystemNode> successors = getSuccessors(last, route);
                for (SystemNode successor : successors) {
                    List<SystemNode> nextRoute = (List<SystemNode>) ((LinkedList<SystemNode>) route).clone();
                    nextRoute.add(successor);
                    nextRoutes.add(nextRoute);
                }

            }
        }
        return findRoutes(from, to, nextRoutes, foundRoutes);
    }
}
