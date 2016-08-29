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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.codehaus.plexus.util.dag.DAG;
import org.codehaus.plexus.util.dag.Vertex;
import org.joda.time.Duration;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Represents a train path DAG for a train path application.
 * <p/>
 *
 */
public class TrainPathDAG extends DAG {

    private static final Logger LOGGER = LogManager.getLogger();


    private final Vertex requestStartNode;
    private final Vertex requestEndNode;
    private Map<Infeasible, Set<TrainPathSlot>> infeasibilityMap = new HashMap<>(Infeasible.values().length);
    private HashSet<Pair<String, String>> removedEdges = new HashSet();
    private HashMap<Vertex, String> groupMap = new HashMap<>();
    private SimpleTrainPathApplication simpleTrainPathApplication;
    private TrainPathSlotCatalogue catalogue;
    private Map<Infeasible, Duration> minDurationMap = new HashMap<>();

    TrainPathDAG(SimpleTrainPathApplication simpleTrainPathApplication, TrainPathSlotCatalogue catalogue) {
        this.simpleTrainPathApplication = simpleTrainPathApplication;
        this.catalogue = catalogue;
        requestStartNode = addVertex(simpleTrainPathApplication.getName() + "_start", simpleTrainPathApplication.getName() + "_start");
        requestEndNode = addVertex(simpleTrainPathApplication.getName() + "_end", simpleTrainPathApplication.getName() + "_end");
        for (Infeasible infeasibleItem : Infeasible.values()) {
            minDurationMap.put(infeasibleItem, null);
            infeasibilityMap.put(infeasibleItem, new HashSet<TrainPathSlot>());
        }
    }

    /**
     * Construct the Train Path DAG for the application with the current pruning parameters.
     *
     * @param macro
     * @param simpleTrainPathApplication
     * @param catalogue
     * @return
     * @throws CycleDetectedException
     */
    public static TrainPathDAG constructDAG(MacroscopicTopology macro, SimpleTrainPathApplication simpleTrainPathApplication, TrainPathSlotCatalogue catalogue) throws CycleDetectedException {
        TrainPathDAG dag = new TrainPathDAG(simpleTrainPathApplication, catalogue);

        // Determine vertices at source node
        Set<Vertex> initialVerticesToCheck = new HashSet<>();
        for (SystemNode nextNode : macro.getSuccessors(simpleTrainPathApplication.getFrom(), simpleTrainPathApplication.getFrom(), simpleTrainPathApplication.getTo())) {
            PeriodicalTimeFrame earlierDepartureLowerBound = simpleTrainPathApplication.getParams().getDepartureLowerBound();
            PeriodicalTimeFrame laterDepartureUpperBound = simpleTrainPathApplication.getParams().getDepartureUpperBound();
            List<TrainPathSlot> newSlots = catalogue.getSortedTrainPathSlots(simpleTrainPathApplication.getFrom(), nextNode,
                    earlierDepartureLowerBound, laterDepartureUpperBound);
            for (TrainPathSlot newSlot : newSlots.subList(0, Math.min(newSlots.size(), TrainPathAllocationProblemPruningParameters.getMAX_OUTGOINGCONNECTIONS_PER_SLOT()))) {
                Vertex v = dag.addVertex(newSlot.getName(), newSlot.getPeriodicalTrainPathSlot().getTrainPathSectionName());
                initialVerticesToCheck.add(v);
                dag.addEdge(dag.getRequestStartNode().getLabel(), v.getLabel());
            }
        }
        dag = dag.constructIter(macro, simpleTrainPathApplication, initialVerticesToCheck, catalogue, new HashSet<Vertex>());
        dag.backtrackingIter();

        return dag;
    }

    /**
     * Returns the number of all slots the set of solution candidates.
     *
     * @param solutionCandidates
     * @return
     */
    @Deprecated
    public static int getSpan(Set<SolutionCandidate> solutionCandidates) {
        Set<TrainPathSlot> allSlots = new HashSet<>();
        for (SolutionCandidate solutionCandidate : solutionCandidates) {
            allSlots.addAll(solutionCandidate.getPath());
        }
        return allSlots.size();
    }

    /**
     * Returns the cyclomatic complexity of the solution space spanned by the set of solution candidats.
     *
     * @param solutionCandidates
     * @return
     */
    public static int cyclomaticComplexityFromSolutionCandidates(Set<SolutionCandidate> solutionCandidates) {
        Set<String> vertices = new HashSet<>();
        Set<String> edges = new HashSet<>();
        vertices.add("start");
        vertices.add("end");
        for (SolutionCandidate solutionCandidate : solutionCandidates) {
            TrainPathSlot pathFrom = solutionCandidate.getPath().get(0);
            TrainPathSlot pathTo = solutionCandidate.getPath().get(solutionCandidate.getPath().size() - 1);
            edges.add("start_" + pathFrom.getName());
            edges.add(pathTo.getName() + "_end");
            vertices.add(pathFrom.getName());
            vertices.add(pathTo.getName());

            for (int i = 1; i < solutionCandidate.getPath().size(); i++) {
                TrainPathSlot from = solutionCandidate.getPath().get(i - 1);
                TrainPathSlot to = solutionCandidate.getPath().get(i);
                vertices.add(from.getName());
                vertices.add(to.getName());
                edges.add(from.getName() + "_" + to.getName());
            }
        }
        return edges.size() - vertices.size() + 2;
    }

    /**
     * Expands the DAG such that expanded DAG complies with the standard multicommodity flow formulation where unit capacity constraints are associated with the arcs.
     *
     * @return
     * @throws CycleDetectedException
     */
    @Deprecated
    public TrainPathDAG expandDag() throws CycleDetectedException {
        TrainPathDAG dag = this;

        TrainPathDAG expandedDag = new TrainPathDAG(dag.simpleTrainPathApplication, dag.catalogue);
        for (Vertex vertex : dag.getVerticies()) {
            if (vertex.isRoot() || vertex.isLeaf()) {
                continue;
            }
            String from = vertex.getLabel() + "_in";
            String trainPathSectionName = catalogue.getSlot(vertex.getLabel()).getPeriodicalTrainPathSlot().getTrainPathSectionName();
            expandedDag.addVertex(from, trainPathSectionName);
            String to = vertex.getLabel() + "_out";
            expandedDag.addVertex(to, trainPathSectionName);
            expandedDag.addEdge(from, to);
            if (vertex.getParents().size() <= 0 || vertex.getChildren().size() <= 0) {
                throw new IllegalStateException("Interior node " + vertex.getLabel() + " has either no parent or no children");
            }
        }
        for (Vertex vertex : dag.getVerticies()) {
            String from = vertex.getLabel() + "_out";
            if (vertex.isRoot()) {
                from = expandedDag.getRequestStartNode().getLabel();
            }
            for (Vertex child : vertex.getChildren()) {
                String to = child.getLabel() + "_in";
                if (child.isLeaf()) {
                    to = expandedDag.getRequestEndNode().getLabel();
                }
                expandedDag.addEdge(from, to);
            }
        }
        return expandedDag;
    }

    public SimpleTrainPathApplication getSimpleTrainPathApplication() {
        return simpleTrainPathApplication;
    }

    public Vertex getRequestEndNode() {
        return requestEndNode;
    }

    public Vertex getRequestStartNode() {
        return requestStartNode;
    }

    private void backtrackingIter() throws CycleDetectedException {
        for (Infeasible infeasible : infeasibilityMap.keySet()) {
            for (TrainPathSlot trainPathSlot : infeasibilityMap.get(infeasible)) {
                Vertex v = getVertex(trainPathSlot.getName());
                backtrackOnVertex(v, infeasible);
            }
        }
    }

    private void backtrackOnVertex(Vertex vertexNotReached, Infeasible infeasible) {
        //LOGGER.debug("Request " + request.getName() + " backtrackOnVertex at " + vertexNotReached.getLabel());
        if (vertexNotReached.getChildren().size() == 0 && vertexNotReached.getParents().size() > 0) {
            infeasibilityMap.get(infeasible).add(catalogue.getSlot(vertexNotReached.getLabel()));

            for (String parentLabel : vertexNotReached.getParentLabels()) {
                Vertex parent = getVertex(parentLabel);

                removedEdges.add(new Pair(parent.getLabel(), vertexNotReached.getLabel()));
                removeEdge(parent, vertexNotReached);

                // Don't do recursion to root node
                if (getRequestStartNode().equals(parent)) {
                    continue;
                }

                TrainPathSlot parentSlot = catalogue.getSlot(parent.getLabel());
                if (parentSlot == null) {
                    throw new IllegalStateException("Cannot find slot for vertex " + vertexNotReached.getLabel());
                }

                // backtracking
                backtrackOnVertex(parent, Infeasible.BACKTRACKING);
            }
        }
    }

    private TrainPathDAG constructIter(MacroscopicTopology macro, SimpleTrainPathApplication
            simpleTrainPathApplication, Set<Vertex> verticesToCheck, TrainPathSlotCatalogue catalogue, Set<Vertex> processedVertices) throws CycleDetectedException {

        if (verticesToCheck.size() == 0) {
            return this;
        }


        PeriodicalTimeFrame arrivalLowerBound = simpleTrainPathApplication.getParams().getArrivalLowerBound();
        PeriodicalTimeFrame arrivalUppderBound = simpleTrainPathApplication.getParams().getArrivalUpperBound();

        Set<Vertex> nextVerticesToCheck = new HashSet<>();
        for (Vertex leaf : verticesToCheck) {
            TrainPathSlot currentLastTrainPathSlot = catalogue.getSlot(leaf.getLabel());

            // Is the vertex already part of the DAG?
            if (processedVertices.contains(leaf)) {
                continue;
            }

            // At terminal node?
            if (currentLastTrainPathSlot.getTo().equals(simpleTrainPathApplication.getTo())) {

                if (currentLastTrainPathSlot.getEndTime().isWithinBounds(arrivalLowerBound, arrivalUppderBound)) {
                    addEdge(currentLastTrainPathSlot.getName(), getRequestEndNode().getLabel());
                } else {
                    if (currentLastTrainPathSlot.getEndTime().isWithinBounds(simpleTrainPathApplication.getParams().getDepartureLowerBound(), arrivalLowerBound)) {
                        markSlotInfeasible(currentLastTrainPathSlot, Infeasible.UNDERDUE);
                    } else {
                        markSlotInfeasible(currentLastTrainPathSlot, Infeasible.OVERDUE);
                    }
                }
            }
            // Overdue at non-terminal node?
            else if (!simpleTrainPathApplication.getParams().isWithinHardBounds(currentLastTrainPathSlot.getEndTime())) {
                markSlotInfeasible(currentLastTrainPathSlot, Infeasible.OVERDUE);
            }
            // Recursive search
            else {
                addSlotSuccessorsToNextVerticesToCheck(macro, simpleTrainPathApplication, catalogue, nextVerticesToCheck, currentLastTrainPathSlot);
            }
            // Vertex is processed
            processedVertices.add(leaf);
        }
        return constructIter(macro, simpleTrainPathApplication, nextVerticesToCheck, catalogue, processedVertices);
    }

    private void addSlotSuccessorsToNextVerticesToCheck(MacroscopicTopology macro, SimpleTrainPathApplication simpleTrainPathApplication, TrainPathSlotCatalogue catalogue, Set<Vertex> nextVerticesToCheck, TrainPathSlot currentLastTrainPathSlot) throws CycleDetectedException {
        for (SystemNode nextSystemNodeCandidate : macro.getSuccessors(currentLastTrainPathSlot.getTo(), simpleTrainPathApplication.getFrom(), simpleTrainPathApplication.getTo())) {
            PeriodicalTimeFrame lowerInclusiveBoundNewSlot = currentLastTrainPathSlot.getEndTime().plus(simpleTrainPathApplication.getParams().getMINIMUM_DWELL_TIME());
            SystemNode weAreAt = currentLastTrainPathSlot.getTo();
            PeriodicalTimeFrame upperInclusiveBoundNewSlot = currentLastTrainPathSlot.getEndTime().plus(simpleTrainPathApplication.getParams().getMINIMUM_DWELL_TIME()).plus(simpleTrainPathApplication.getParams().getMAXIMUM_ADDITIONAL_DWELL_TIME(weAreAt));
            List<TrainPathSlot> nextSlotCandidates = catalogue.getSortedTrainPathSlots(currentLastTrainPathSlot.getTo(), nextSystemNodeCandidate, lowerInclusiveBoundNewSlot, upperInclusiveBoundNewSlot);

            // No Successors
            if (nextSlotCandidates.size() == 0) {
                TrainPathSlot nextPossibleSlotFromUpperInclusiveBoundNewSlot = catalogue.getNextTrainPathSlotWithin24(currentLastTrainPathSlot.getTo(), nextSystemNodeCandidate, upperInclusiveBoundNewSlot);
                if (nextPossibleSlotFromUpperInclusiveBoundNewSlot != null) {
                    markSlotInfeasible(currentLastTrainPathSlot, Infeasible.NOSUCCESSOR);
                } else {
                    LOGGER.warn("no next slot found at " + currentLastTrainPathSlot.getName());
                }
            }
            // Chop successors if too many of them
            else {
                if (nextSlotCandidates.size() > TrainPathAllocationProblemPruningParameters.getMAX_OUTGOINGCONNECTIONS_PER_SLOT()) {
                    LOGGER.debug("Request " + simpleTrainPathApplication.getName() + ": Due to MAX_OUTGOINGCONNECTIONS_PER_SLOT=" + TrainPathAllocationProblemPruningParameters.getMAX_OUTGOINGCONNECTIONS_PER_SLOT() + ", not considerung " + (nextSlotCandidates.size() - TrainPathAllocationProblemPruningParameters.getMAX_OUTGOINGCONNECTIONS_PER_SLOT()) + " of " + nextSlotCandidates.size() + " outgoing connections at slot " + currentLastTrainPathSlot.getName());
                }
                for (TrainPathSlot nextSlotCandidate : nextSlotCandidates.subList(0, Math.min(nextSlotCandidates.size(), TrainPathAllocationProblemPruningParameters.getMAX_OUTGOINGCONNECTIONS_PER_SLOT()))) {
                    Vertex toVertex = addVertex(nextSlotCandidate.getName(), nextSlotCandidate.getPeriodicalTrainPathSlot().getTrainPathSectionName());
                    addEdge(currentLastTrainPathSlot.getName(), nextSlotCandidate.getName());
                    if (!nextVerticesToCheck.contains(toVertex)) {
                        nextVerticesToCheck.add(toVertex);
                    }
                }
            }
        }
    }

    public Vertex addVertex(final String label, final String group) {
        Vertex v = addVertex(label);
        groupMap.put(v, group);
        return v;
    }

    private void markSlotInfeasible(TrainPathSlot infeasibleSlot, Infeasible infeasibilityReason) {
        infeasibilityMap.get(infeasibilityReason).add(infeasibleSlot);
    }

    public boolean isTargetNodeReached() {
        return requestEndNode.getParents().size() > 0;
    }

    private boolean isMarkedInfeasible(TrainPathSlot slot) {
        for (Infeasible infeasibleItem : Infeasible.values()) {
            if (infeasibilityMap.get(infeasibleItem).contains(slot)) {
                return true;
            }
        }
        return false;
    }

    private List<Infeasible> getInfeasibilityReasons(TrainPathSlot slot) {
        List<Infeasible> reasons = new LinkedList<>();
        for (Infeasible infeasibleItem : Infeasible.values()) {
            if (infeasibilityMap.get(infeasibleItem).contains(slot)) {
                reasons.add(infeasibleItem);
            }
        }
        return reasons;
    }

    public void printInfeasibilities() {
        for (Infeasible infeasibleItem : Infeasible.values()) {
            for (TrainPathSlot trainPathSlot : infeasibilityMap.get(infeasibleItem)) {
                LOGGER.info("Request " + simpleTrainPathApplication.getName() + " has infeasible slot " + trainPathSlot.getName() + " since " + infeasibleItem.toString()); // + ", distance " + LocalWeekTime.formatDuration(getInfeasibilityDistance(infeasibleItem, trainPathSlot)));
            }
        }
    }

    /**
     * The bottleneck is the macroscopic link that has the least slots in the DAG.
     * The bottlneck size is the number of slots of the bottleneck.
     *
     * @return
     */
    @Deprecated
    public int bottleneckSize() {
        Map<Pair<SystemNode, SystemNode>, Set<Vertex>> sizes = new HashMap<>();
        Set<Vertex> doneVertices = new HashSet<>();
        recursiveBottleneckFrom(getRequestEndNode(), sizes, doneVertices);
        int minBottleneck = Integer.MAX_VALUE;
        for (Pair<SystemNode, SystemNode> link : sizes.keySet()) {
            Set<Vertex> vertices = sizes.get(link);
            minBottleneck = Math.min(minBottleneck, vertices.size());
        }
        return minBottleneck;
    }

    public int nbPaths() {
        return pathsFromVertex(getRequestStartNode());
    }

    public int pathsFromVertex(Vertex v) {
        if (v.isLeaf()) {
            return 1;
        } else {
            int sum = 0;
            for (Vertex child : v.getChildren()) {
                sum += pathsFromVertex(child);
            }
            return sum;
        }
    }

    private void recursiveBottleneckFrom(Vertex leaf, Map<Pair<SystemNode, SystemNode>, Set<Vertex>> sizes, Set<Vertex> doneVertices) {
        if (doneVertices.contains(leaf)) {
            return;
        }
        for (Vertex vertex : leaf.getParents()) {
            if (vertex.equals(getRequestStartNode())) {
                continue;
            }
            Pair<SystemNode, SystemNode> link = new Pair<>(catalogue.getSlot(vertex.getLabel()).getFrom(), catalogue.getSlot(vertex.getLabel()).getTo());
            if (!sizes.containsKey(link)) {
                sizes.put(link, new HashSet<Vertex>());
            }
            sizes.get(link).add(vertex);
            recursiveBottleneckFrom(vertex, sizes, doneVertices);
            doneVertices.add(vertex);
        }
    }

    /****************************************************
     * Enumeration
     ****************************************************/

    public SolutionCandidateEnumerationResult enumerate(double randomRatio) {
        SolutionCandidateEnumerationResult solutionCandidateEnumerationResult = new SolutionCandidateEnumerationResult();
        Set<SolutionCandidate> solutionCandidates = solutionCandidateEnumerationResult.getSolutionCandidates();
        Set<Pair<Vertex, Vertex>> excessVertices = solutionCandidateEnumerationResult.getExcessVertices();
        Set<Pair<Vertex, Vertex>> unsampledEdges = solutionCandidateEnumerationResult.getUnsampledEdges();
        LinkedList<Vertex> path = new LinkedList<Vertex>();
        enumerateFrom(requestStartNode, path, solutionCandidates, randomRatio, excessVertices, unsampledEdges);
        return solutionCandidateEnumerationResult;
    }

    /**
     * Depth-first and amount-bound stochastic enumeration.
     *
     * @param parent
     * @param path
     * @param travelPaths
     * @param randomRatio
     * @param excessVertices
     * @param unsampledEdges
     */

    private void enumerateFrom(Vertex parent, List<Vertex> path, Set<SolutionCandidate> travelPaths, double randomRatio, Set<Pair<Vertex, Vertex>> excessVertices, Set<Pair<Vertex, Vertex>> unsampledEdges) {
        for (Vertex vertex : parent.getChildren()) {
            if (vertex.equals(requestEndNode)) {
                travelPaths.add(new SolutionCandidate(convertVertexListToTrainPathList(path), catalogue, simpleTrainPathApplication));
            }
            List<Vertex> newPath = addToCopy(path, vertex);

            if (travelPaths.size() > simpleTrainPathApplication.getParams().getMAX_TRAINPATHS_PER_REQUEST()) {
                excessVertices.add(new Pair(parent, vertex));
            } else if (Math.random() > randomRatio) {
                unsampledEdges.add(new Pair(parent, vertex));
            } else {
                enumerateFrom(vertex, newPath, travelPaths, randomRatio, excessVertices, unsampledEdges);
            }
        }
    }

    private List<TrainPathSlot> convertVertexListToTrainPathList(List<Vertex> path) {
        List<TrainPathSlot> slotPath = new LinkedList<TrainPathSlot>();
        for (Vertex vertex : path) {
            TrainPathSlot slot = catalogue.getSlot(vertex.getLabel());
            if (slot == null) {
                throw new IllegalArgumentException("No slot must be null in a solution candidate");
            }
            slotPath.add(slot);
        }
        return slotPath;
    }

    /**
     * The cyclomatic complexity is the potential number of train paths in the DAG and thus allows to compare different DAGs.
     * Computed correctly only if one connected component and disconnected components are singleton nodes.
     *
     * @return
     */
    public int getCyclomaticComplexity() {
        if (!isTargetNodeReached()) {
            return 0;
        }
        int nbEdges = 0;
        int nbVerticies = 0;
        for (Vertex vertex : this.getVerticies()) {
            // N.B. Vertex.isConnected is implmented wrongly!!!
            if (vertex.getParents().size() == 0 && vertex.getChildren().size() == 0) {
                continue;
            }
            nbVerticies = nbVerticies + 1;
            nbEdges = nbEdges + vertex.getChildren().size();
        }
        return nbEdges - nbVerticies + 2;
    }

    public GraphCSV toCSV(Set<SolutionCandidate> solutionCandidates, boolean includeRemovedEdges) {
        GraphCSV csv = new GraphCSV();
        for (Vertex vertex : this.getVerticies()) {
            for (Vertex child : vertex.getChildren()) {
                boolean partOfSolutionCandidate = isPartOfSolutionCandidate(vertex, child, solutionCandidates);
                String macroLinkNameSource = getMacroLinkName(vertex.getLabel());
                String macroLinkNameTarget = getMacroLinkName(child.getLabel());

                csv.appendLine(vertex.getLabel(), child.getLabel(), partOfSolutionCandidate ? "3.0" : "1.0", vertex.getLabel() + "_" + child.getLabel(), groupMap.get(vertex), groupMap.get(child), macroLinkNameSource, macroLinkNameTarget);
            }
        }
        if (includeRemovedEdges) {
            for (Pair<String, String> removedEdge : removedEdges) {
                String macroLinkNameSource = getMacroLinkName(removedEdge.first);
                String macroLinkNameTarget = getMacroLinkName(removedEdge.second);
                List<Infeasible> reasons = getInfeasibilityReasons(catalogue.getSlot(removedEdge.second));
                String fromNodeString = removedEdge.first.toString();
                if (!removedEdge.first.equals(getRequestStartNode().getLabel())) {
                    fromNodeString = catalogue.getSlot(removedEdge.first).toString();
                }
                String toNodeString = removedEdge.second.toString();
                if (!removedEdge.second.equals(getRequestEndNode().getLabel())) {
                    toNodeString = catalogue.getSlot(removedEdge.second).toString();
                }
                String description = "Arc " + fromNodeString + " => " + toNodeString + " removed because of " + reasons.toString();
                csv.appendLine(removedEdge.first, removedEdge.second, "2.0", description, groupMap.get(getVertex(removedEdge.first)), groupMap.get(getVertex(removedEdge.second)), macroLinkNameSource, macroLinkNameTarget);
            }
        }
        return csv;
    }

    public TrainPathSlot getSlotFromVertex(String label) {
        if (label.endsWith("_out")) {
            label = label.split(Pattern.quote("_out"))[0];
        } else if (label.endsWith("_in")) {
            label = label.split(Pattern.quote("_in"))[0];
        }
        TrainPathSlot slot = catalogue.getSlot(label);
        return slot;
    }

    public String getMacroLinkName(String label) {
        TrainPathSlot slot = getSlotFromVertex(label);
        String macroLinkName = "";
        if (slot != null) {
            macroLinkName = slot.getFrom().toString() + " => " + slot.getTo().toString();
        }
        return macroLinkName;
    }

    /****************************************************
     * MISCELLANEOUS
     ****************************************************/

    private List<Vertex> addToCopy(List<Vertex> path, Vertex v) {
        List<Vertex> newPath = (List<Vertex>) ((LinkedList<Vertex>) path).clone();
        newPath.add(v);
        return newPath;
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.write(toCSV(null, true).toString().getBytes());
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        throw new IllegalStateException("Not implemented");
    }

    private String getGroup(Vertex v) {
        return groupMap.get(v);
    }

    private boolean isPartOfSolutionCandidate(Vertex vertex, Vertex child, Set<SolutionCandidate> solutionCandidates) {
        boolean partOfSolutionCandidate = false;
        for (SolutionCandidate solutionCandidate : solutionCandidates) {
            List<TrainPathSlot> path = solutionCandidate.getPath();
            for (int i = 0; i < path.size(); i++) {
                TrainPathSlot slot = path.get(i);
                if (i > 0) {
                    TrainPathSlot previous = path.get(i - 1);
                    if (previous.getName().equals(vertex.getLabel()) && slot.getName().equals(child.getLabel())) {
                        partOfSolutionCandidate = true;
                        break;
                    }
                }
                if (i < path.size() - 1) {
                    TrainPathSlot next = path.get(i + 1);
                    if (slot.getName().equals(vertex.getLabel()) && next.getName().equals(child.getLabel())) {
                        partOfSolutionCandidate = true;
                        break;
                    }
                }
            }
            // N.B. Dummy nodes are not part of solution candidates
            if (vertex.equals(getRequestStartNode()) && child.getLabel().equals(solutionCandidate.getPath().get(0).getName())) {
                partOfSolutionCandidate = true;
                break;
            }
            if (child.equals(getRequestEndNode()) && vertex.getLabel().equals(solutionCandidate.getPath().get(solutionCandidate.getPath().size() - 1).getName())) {
                partOfSolutionCandidate = true;
                break;
            }
        }
        return partOfSolutionCandidate;
    }

    /****************************************************
     * DAG CONSTRUCTION: INFEASIBILITY
     ****************************************************/
    public enum Infeasible {
        OVERDUE, UNDERDUE, BACKTRACKING, NOSUCCESSOR
    }

    public class SolutionCandidateEnumerationResult {
        private Set<SolutionCandidate> solutionCandidates;
        private Set<Pair<Vertex, Vertex>> excessVertices;
        private Set<Pair<Vertex, Vertex>> unsampledEdges;

        public SolutionCandidateEnumerationResult() {
            solutionCandidates = new HashSet<>();
            excessVertices = new HashSet<>();
            unsampledEdges = new HashSet<>();
        }

        public Set<SolutionCandidate> getSolutionCandidates() {
            return solutionCandidates;
        }

        public Set<Pair<Vertex, Vertex>> getExcessVertices() {
            return excessVertices;
        }

        public Set<Pair<Vertex, Vertex>> getUnsampledEdges() {
            return unsampledEdges;
        }

        public boolean everythingEnumerated() {
            return getExcessVertices().size() == 0 && getUnsampledEdges().size() == 0;
        }
    }
}
