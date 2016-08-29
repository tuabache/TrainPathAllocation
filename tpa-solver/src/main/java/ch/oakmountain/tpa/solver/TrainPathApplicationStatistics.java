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

import ch.oakmountain.tpa.web.TablePersistor;
import org.codehaus.plexus.util.dag.Vertex;
import org.joda.time.Duration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 *
 */
class TrainPathApplicationStatistics {
    private final SimpleTrainPathApplication simpleTrainPathApplication;
    private final TrainPathDAG dag;
    private final Set<SolutionCandidate> solutionCandidates;
    private final TablePersistor table;

    /**
     * Data container for statistics of a train path applications's DAG.
     *
     * @param simpleTrainPathApplication
     * @param dag
     * @param solutionCandidates
     */
    TrainPathApplicationStatistics(String outputDir, SimpleTrainPathApplication simpleTrainPathApplication, TrainPathDAG dag, Set<SolutionCandidate> solutionCandidates) throws IOException {
        this.simpleTrainPathApplication = simpleTrainPathApplication;
        this.dag = dag;
        this.solutionCandidates = solutionCandidates;
        table = new TablePersistor("index", outputDir + File.separator + simpleTrainPathApplication.getName(), "Train Path Application " + simpleTrainPathApplication.getName(), getHeaderIndivi());
    }

    /**
     * Returns the header for the list of train path applications.
     *
     * @return
     */
    public static List<String> getHeader() {
        List<String> header = Arrays.asList(
                "Train Path Application",
                "From",
                "To",
                "Requested departure time",
                "Requested arrival time",
                "(Global) Hard maximum earlier departure",
                "(Global) Hard maximum later arrival",
                "(Global) Hard minimum dwell time",
                "Initial pruned maximum earlier departure",
                "Initial pruned maximum later arrival",
                "Nb Train Paths in the DAG",
                "Cyclomatic Complexity"
        );
        return header;
    }

    /**
     * Returns the header for the list of system nodes of the train path application.
     *
     * @return
     */
    private List<String> getHeaderIndivi() {
        List<String> header = Arrays.asList(
                "System Node",
                "Nb Successors",
                "Initial pruned departure/dwell/arrival time window",
                "Min/max/average slots",
                "Min/max/average dwell time",
                "Earliest/latest arrival",
                "Earliest/latest departure"
        );
        return header;
    }

    List<String> compileAndGetTrainPathApplicationListRow() throws IOException {
        HashMap<TrainPathSlot, Vertex> slotVertexHashMap = new HashMap<>();
        HashMap<SystemNode, Set<TrainPathSlot>> systemNodeTrainPathSlotHashMap = new HashMap<>();
        HashMap<SystemNode, Set<Pair<TrainPathSlot, TrainPathSlot>>> connectionsThroughSystemNode = new HashMap<>();
        for (Vertex vertex : dag.getVerticies()) {
            if (vertex.isLeaf() || vertex.isRoot()) {
                continue;
            }
            TrainPathSlot trainPathSlot = dag.getSlotFromVertex(vertex.getLabel());
            slotVertexHashMap.put(trainPathSlot, vertex);
            SystemNode from = trainPathSlot.getFrom();
            SystemNode to = trainPathSlot.getTo();
            initSystemNodeInMaps(systemNodeTrainPathSlotHashMap, connectionsThroughSystemNode, to);
            initSystemNodeInMaps(systemNodeTrainPathSlotHashMap, connectionsThroughSystemNode, from);
            systemNodeTrainPathSlotHashMap.get(from).add(trainPathSlot);


            for (Vertex child : vertex.getChildren()) {
                if (vertex.isLeaf() || vertex.isRoot()) {
                    continue;
                }
                TrainPathSlot childSlot = dag.getSlotFromVertex(child.getLabel());
                Pair<TrainPathSlot, TrainPathSlot> connection = new Pair<TrainPathSlot, TrainPathSlot>(trainPathSlot, childSlot);

                connectionsThroughSystemNode.get(to).add(connection);
            }
        }
        int minSlotsPerSystemNode = Integer.MAX_VALUE;
        int maxSlotsPerSystemNode = Integer.MIN_VALUE;

        for (SystemNode systemNode : systemNodeTrainPathSlotHashMap.keySet()) {

            Set<TrainPathSlot> succSlots = systemNodeTrainPathSlotHashMap.get(systemNode);
            int nbSuccSlots = succSlots.size();
            maxSlotsPerSystemNode = Math.max(nbSuccSlots, maxSlotsPerSystemNode);
            minSlotsPerSystemNode = Math.min(nbSuccSlots, minSlotsPerSystemNode);

            Duration minDwellTime = new Duration(Long.MAX_VALUE);
            Duration maxDwellTime = Duration.ZERO;
            Duration totalDwellTime = Duration.ZERO;

            Set<Pair<TrainPathSlot, TrainPathSlot>> connections = connectionsThroughSystemNode.get(systemNode);
            String dwellStats = "--";
            if (!systemNode.equals(simpleTrainPathApplication.getTo()) && !systemNode.equals(simpleTrainPathApplication.getFrom())) {
                for (Pair<TrainPathSlot, TrainPathSlot> trainPathSlotTrainPathSlotPair : connections) {
                    Duration dwell = trainPathSlotTrainPathSlotPair.second.getStartTime().distanceAfter(trainPathSlotTrainPathSlotPair.first.getEndTime());
                    if (dwell.isShorterThan(Duration.ZERO)) {
                        throw new IllegalStateException("");
                    }
                    if (dwell.isLongerThan(maxDwellTime)) {
                        maxDwellTime = dwell;
                    }
                    if (dwell.isShorterThan(minDwellTime)) {
                        minDwellTime = dwell;
                    }
                    totalDwellTime = totalDwellTime.plus(dwell);
                }
                dwellStats = PeriodicalTimeFrame.formatDuration(minDwellTime) + "/" + PeriodicalTimeFrame.formatDuration(maxDwellTime) + "/" + PeriodicalTimeFrame.formatDuration(totalDwellTime.dividedBy(connectionsThroughSystemNode.get(systemNode).size()));
            }

            String timeWindow;
            if (systemNode.equals(simpleTrainPathApplication.getFrom())) {
                timeWindow = "[" + simpleTrainPathApplication.getParams().getDepartureLowerBound().toString() + "," + simpleTrainPathApplication.getParams().getDepartureUpperBound().toString() + "]";
            } else if (systemNode.equals(simpleTrainPathApplication.getTo())) {
                timeWindow = "[" + simpleTrainPathApplication.getParams().getArrivalLowerBound().toString() + "," + simpleTrainPathApplication.getParams().getArrivalUpperBound().toString() + "]";
            } else {
                timeWindow = "[arr+ " + PeriodicalTimeFrame.formatDuration(simpleTrainPathApplication.getParams().getMINIMUM_DWELL_TIME()) + ", arr+" + PeriodicalTimeFrame.formatDuration(simpleTrainPathApplication.getParams().getHARD_MINIMUM_DWELL_TIME().plus(simpleTrainPathApplication.getParams().getMAXIMUM_ADDITIONAL_DWELL_TIME(systemNode))) + "]";
            }
            table.writeRow(Arrays.asList(
                    systemNode.getName(),
                    String.valueOf(nbSuccSlots),
                    timeWindow,
                    "[" + PeriodicalTimeFrame.formatDuration(simpleTrainPathApplication.getParams().getMINIMUM_DWELL_TIME()) + "," + PeriodicalTimeFrame.formatDuration(simpleTrainPathApplication.getParams().getMAXIMUM_ADDITIONAL_DWELL_TIME(systemNode)) + "]",
                    "Min/max/average slots",
                    dwellStats
            ));
        }

        List<String> data = Arrays.asList(
                simpleTrainPathApplication.getName(),
                simpleTrainPathApplication.getFrom().getName(),
                simpleTrainPathApplication.getTo().getName(),
                simpleTrainPathApplication.getStartTime().toString(),
                simpleTrainPathApplication.getEndTime().toString(),
                PeriodicalTimeFrame.formatDuration(simpleTrainPathApplication.getParams().getHARD_MAXIMUM_EARLIER_DEPARTURE()),
                PeriodicalTimeFrame.formatDuration(simpleTrainPathApplication.getParams().getHARD_MAXIMUM_LATER_ARRIVAL()),
                PeriodicalTimeFrame.formatDuration(simpleTrainPathApplication.getParams().getHARD_MINIMUM_DWELL_TIME()),
                String.valueOf(dag.nbPaths()),
                String.valueOf(dag.getCyclomaticComplexity())
        );

        table.finishTable();
        return data;
    }

    private void initSystemNodeInMaps(HashMap<SystemNode, Set<TrainPathSlot>> systemNodeTrainPathSlotHashMap, HashMap<SystemNode, Set<Pair<TrainPathSlot, TrainPathSlot>>> connectionsThroughSystemNode, SystemNode to) {
        if (!systemNodeTrainPathSlotHashMap.containsKey(to)) {
            systemNodeTrainPathSlotHashMap.put(to, new HashSet<TrainPathSlot>());
        }
        if (!connectionsThroughSystemNode.containsKey(to)) {
            connectionsThroughSystemNode.put(to, new HashSet());
        }
    }
}
