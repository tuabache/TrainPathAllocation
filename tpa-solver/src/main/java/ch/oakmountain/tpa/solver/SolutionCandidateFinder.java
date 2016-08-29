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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.joda.time.Duration;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Pruning, DAG construction and partial enumeration of solution candidate for a train path application.
 *
 *
 */
public class SolutionCandidateFinder {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Returns the earliest path within the maximum earlier departure and later arrival times and null if no such path exists.
     *
     * @param macro
     * @param catalogue
     * @param app
     * @return
     */
    public static List<TrainPathSlot> getEarliestPathWithinHardBounds(MacroscopicTopology macro, TrainPathSlotCatalogue catalogue, SimpleTrainPathApplication app) {
        List<TrainPathSlot> path = getEarliestPath(macro, catalogue, app.getFrom(), app.getTo(), app.getParams().getDepartureHardLowerBound(), app.getParams().getHARD_MINIMUM_DWELL_TIME(), app.getParams().getArrivalHardUpperBound());
        if (path != null) {
            //SolutionCandidate.sanityCheckPath(path, app);
            SolutionCandidate cand = new SolutionCandidate(path, catalogue, app); // use side-effect: sanity check on path!
            LOGGER.debug("Found earliest path within hard bounds: " + cand.describeFullPath());
        }
        return path;
    }

    /**
     * Returns the earliest path within the requested departure and arrival times and null if no such path exists.
     *
     * @param macro
     * @param catalogue
     * @param app
     * @return
     */
    public static List<TrainPathSlot> getEarliestPathWithinRequestedBounds(MacroscopicTopology macro, TrainPathSlotCatalogue catalogue, SimpleTrainPathApplication app) {
        List<TrainPathSlot> path = getEarliestPath(macro, catalogue, app.getFrom(), app.getTo(), app.getStartTime(), app.getParams().getHARD_MINIMUM_DWELL_TIME(), app.getEndTime());
        if (path != null) {
            //SolutionCandidate.sanityCheckPath(path, app);
            SolutionCandidate cand = new SolutionCandidate(path, catalogue, app); // use side-effect: sanity check on path!
            LOGGER.debug("Found earliest path within request bounds: " + cand.describeFullPath());
        }
        return path;
    }

    /**
     * Gets the earliest path within the request's hard bounds.
     *
     * Greedy approach:
     * - at each system node A and for all next system nodes C, choose the slot that brings us first to C (this should be deterministic, if the construction of train path slots does not produce overtakings)
     * - finally choose arbitrarily one of the paths that brings us first to the destination (here, indeterminism happens as seen in the real-world data set)
     *
     * @param macro
     * @param catalogue
     * @param from
     * @param to
     * @param lb
     * @param ub
     * @return
     */
    // TODO make a data set with alternative routes, for instance---</=======>---<===
    // TODO remove indeterministic behaviour?
    private static List<TrainPathSlot> getEarliestPath(MacroscopicTopology macro, TrainPathSlotCatalogue catalogue, SystemNode from, SystemNode to, PeriodicalTimeFrame lb, Duration dt, PeriodicalTimeFrame ub) {
        HashSet<List<TrainPathSlot>> paths = new HashSet<>();
        Duration bestDuration = null;
        List<TrainPathSlot> bestPath = null;
        getEarliestPathIter(macro, catalogue, from, to, lb, dt, ub, from, new LinkedList<TrainPathSlot>(), paths);

        // N.B: the choice of the earliest path is Non-deterministic!
        for (List<TrainPathSlot> path : paths) {

            Duration durationCand = lb.distanceAfter(path.get(path.size() - 1).getEndTime());
            if (bestDuration == null || durationCand.isShorterThan(bestDuration)) {
                bestDuration = durationCand;
                bestPath = path;
            }
        }
        return bestPath;
    }


    private static void getEarliestPathIter(MacroscopicTopology macro, TrainPathSlotCatalogue catalogue, SystemNode from, SystemNode to, PeriodicalTimeFrame lb, Duration dt, PeriodicalTimeFrame ub, SystemNode currentNode, List<TrainPathSlot> path, Set<List<TrainPathSlot>> paths) {

        if (currentNode.equals(to)) {
            if (path.get(path.size() - 1).getEndTime().isWithinBounds(lb, ub)) {
                paths.add(path);
            }
            return;
        }

        PeriodicalTimeFrame earliest;
        if (path.size() == 0) {
            earliest = lb;
        } else {
            earliest = path.get(path.size() - 1).getEndTime().plus(dt);
        }

        for (SystemNode nextSystemNodeCandidate : macro.getSuccessors(currentNode, from, to)) {
            TrainPathSlot quickestTrainPathSlot = catalogue.getQuickestTrainPathSlot(currentNode, nextSystemNodeCandidate, earliest);
            if (quickestTrainPathSlot == null) {
                throw new IllegalStateException("No next slot found in catalogue between " + currentNode.getName() + " and " + nextSystemNodeCandidate.getName());
            }
            List<TrainPathSlot> newPath = addToCopy(path, quickestTrainPathSlot);
            getEarliestPathIter(macro, catalogue, from, to, lb, dt, ub, nextSystemNodeCandidate, newPath, paths);
        }
    }

    private static List<TrainPathSlot> addToCopy(List<TrainPathSlot> path, TrainPathSlot v) {
        List<TrainPathSlot> newPath = (List<TrainPathSlot>) ((LinkedList<TrainPathSlot>) path).clone();
        newPath.add(v);
        return newPath;
    }

    /**
     * Find initial pruning that makes DAG feasible.
     *
     * @param macroscopicTopology
     * @param trainPathSlotCatalogue
     * @param application
     * @return
     * @throws CycleDetectedException
     * @throws IOException
     * @throws IllegalAccessException
     */
    public static TrainPathDAG findFeasibleDAG(MacroscopicTopology macroscopicTopology, TrainPathSlotCatalogue trainPathSlotCatalogue, SimpleTrainPathApplication application) throws CycleDetectedException, IOException, IllegalAccessException {
        sanityCheck(macroscopicTopology, trainPathSlotCatalogue, application);

        // 1. Try to construct a DAG with default parameters and best path
        application.getParams().setDefaultPruning();
        TrainPathDAG dag = TrainPathDAG.constructDAG(macroscopicTopology, application, trainPathSlotCatalogue);


        // 2. Try to guess parameters from the earliest path found if dag is not feasible
        if (!dag.isTargetNodeReached()) {
            List<TrainPathSlot> earliestPath = getEarliestPathWithinRequestedBounds(macroscopicTopology, trainPathSlotCatalogue, application);

            // 3. If no best path, try again start at hard max early departure point
            if (earliestPath == null) {
                LOGGER.warn("No path found for " + application.getName() + " within requested bounds => maybe we can find a path within hard bounds?");
                earliestPath = getEarliestPathWithinHardBounds(macroscopicTopology, trainPathSlotCatalogue, application);
            }

            if (earliestPath == null) {
                LOGGER.warn("No path found for " + application.getName() + " => giving up.");
                dag.printInfeasibilities();
                // construct DAG so infeasibility can be analysed
                dag = TrainPathDAG.constructDAG(macroscopicTopology, application, trainPathSlotCatalogue);
                return dag;
            } else {
                LOGGER.warn("Now, a path found for " + application.getName() + ", coo!");
            }
            // derive params from earliest path and construct DAG
            derivePruningParamsFromEarliestPath(application, earliestPath);
            dag = TrainPathDAG.constructDAG(macroscopicTopology, application, trainPathSlotCatalogue);

            if (!dag.isTargetNodeReached()) {
                /*earliestPath = getEarliestPathWithinRequestedBounds(macroscopicTopology, trainPathSlotCatalogue, application);
                earliestPath = getEarliestPathWithinHardBounds(macroscopicTopology, trainPathSlotCatalogue, application);
                dag = TrainPathDAG.constructDAG(macroscopicTopology, application, trainPathSlotCatalogue);*/
                throw new IllegalStateException("Found a path, but dag construction does not reach target node.");
            }
        }

        return dag;
    }

    /**
     * Checks that feasibility of earliest path approach matches result in fully relaxed DAG.
     *
     * @param macroscopicTopology
     * @param trainPathSlotCatalogue
     * @param application
     * @throws CycleDetectedException
     */
    private static void sanityCheck(MacroscopicTopology macroscopicTopology, TrainPathSlotCatalogue trainPathSlotCatalogue, SimpleTrainPathApplication application) throws CycleDetectedException {
        application.getParams().relaxToMax();
        TrainPathDAG maxDag = TrainPathDAG.constructDAG(macroscopicTopology, application, trainPathSlotCatalogue);
        List<TrainPathSlot> earliestPath1 = getEarliestPathWithinHardBounds(macroscopicTopology, trainPathSlotCatalogue, application);
        if (!((maxDag.isTargetNodeReached() && earliestPath1 != null) || (!maxDag.isTargetNodeReached() && earliestPath1 == null))) {
            throw new IllegalStateException("Sanity check failed: Path found <=/=> target node in DAG reached");
        }
    }

    private static void derivePruningParamsFromEarliestPath(SimpleTrainPathApplication application, List<TrainPathSlot> earliestPath) {
        PeriodicalTimeFrame earliestPathEndTime = earliestPath.get(earliestPath.size() - 1).getEndTime();
        PeriodicalTimeFrame earliestPathStartTime = earliestPath.get(0).getStartTime();

        // Maximum earlier/later arrival from path's arrival time
        Duration maxEarlierArrivalFromEarliestPath;
        Duration maxLaterArrivalFromEarliestPath;
        if (earliestPathEndTime.isWithinBounds(application.getStartTime(), application.getEndTime())) {
            maxEarlierArrivalFromEarliestPath = application.getEndTime().distanceAfter(earliestPathEndTime);
            maxLaterArrivalFromEarliestPath = new Duration(0);
        } else {
            maxEarlierArrivalFromEarliestPath = new Duration(0);
            maxLaterArrivalFromEarliestPath = earliestPathEndTime.distanceAfter(application.getEndTime());
        }
        application.getParams().setMAXIMUM_EARLIER_ARRIVAL(maxEarlierArrivalFromEarliestPath);
        application.getParams().setMAXIMUM_LATER_ARRIVAL(maxLaterArrivalFromEarliestPath);

        // Maximum earlier/later departure from path's departure time
        Duration maxLaterDepartureFromEarliestPath;
        Duration maxEarlierDepartureFromEarliestPath;
        if (earliestPathStartTime.isWithinBounds(application.getStartTime(), application.getEndTime())) {
            maxLaterDepartureFromEarliestPath = earliestPathStartTime.distanceAfter(application.getStartTime());
            maxEarlierDepartureFromEarliestPath = new Duration(0);
        } else {
            maxLaterDepartureFromEarliestPath = new Duration(0);
            maxEarlierDepartureFromEarliestPath = application.getStartTime().distanceAfter(earliestPathStartTime);
        }
        application.getParams().setMAXIMUM_LATER_DEPARTURE(maxLaterDepartureFromEarliestPath);
        application.getParams().setMAXIMUM_EARLIER_DEPARTURE(maxEarlierDepartureFromEarliestPath);

        // Mnimum dwell time as hard minimum dwell time
        application.getParams().setMINIMUM_DWELL_TIME(application.getParams().getHARD_MINIMUM_DWELL_TIME());

        // Maximum additional dwell time per system node
        for (int i = 1; i < earliestPath.size(); i++) {
            TrainPathSlot fromSlot = earliestPath.get(i - 1);
            TrainPathSlot toSlot = earliestPath.get(i);
            SystemNode weAreAt = toSlot.getFrom();
            Duration dwellTimeAdditionalToHardMinimum = toSlot.getStartTime().distanceAfter(fromSlot.getEndTime()).minus(application.getParams().getHARD_MINIMUM_DWELL_TIME());
            application.getParams().setMAXIMUM_ADDITIONAL_DWELL_TIME(weAreAt, dwellTimeAdditionalToHardMinimum);
        }
    }

    /**
     * Enumerate all solution candidates from DAG.
     *
     * @param dag
     * @return
     */
    public static Set<SolutionCandidate> getEnumerate(TrainPathDAG dag) {
        // Full enumeration
        TrainPathDAG.SolutionCandidateEnumerationResult enumerate = dag.enumerate(1);
        Set<SolutionCandidate> solutionCandidates = enumerate.getSolutionCandidates();

        if (dag.isTargetNodeReached() && solutionCandidates.size() == 0) {
            throw new IllegalStateException("DAG feasible but no enumeration");
        }
        return solutionCandidates;
    }

    /**
     * Experimental: enumerate solution candidates by path sampling that maximises cyclomatic complexity of the solutions.
     *
     * @param dag
     * @return
     */
    @Deprecated
    public static Set<SolutionCandidate> getEnumerateWithSampling(TrainPathDAG dag) {
        // Initialize with full enumeration
        double bestRatio = 1;
        TrainPathDAG.SolutionCandidateEnumerationResult bestResult = dag.enumerate(bestRatio);
        Set<SolutionCandidate> bestSolutionCandidates = bestResult.getSolutionCandidates();
        int bestSolutionCandiatesNb = bestSolutionCandidates.size();
        int bestCC = TrainPathDAG.cyclomaticComplexityFromSolutionCandidates(bestSolutionCandidates);
        int maxCC = dag.getCyclomaticComplexity();
        sanityCheckCC(dag, maxCC, bestSolutionCandidates, bestCC);

        // If not everything is enumerated in large DAGs, try with different ratios to get the best enumeration cyclomatic complexity.
        if (!bestResult.everythingEnumerated()) {
            for (double ratio = 0.1; ratio < 1; ratio += 0.1) {
                TrainPathDAG.SolutionCandidateEnumerationResult currResult = dag.enumerate(ratio);
                Set<SolutionCandidate> currSolutionCandidates = currResult.getSolutionCandidates();
                int currCC = TrainPathDAG.cyclomaticComplexityFromSolutionCandidates(currSolutionCandidates);
                int currSolutionCandidatesNb = currSolutionCandidates.size();
                LOGGER.debug("Request " + dag.getSimpleTrainPathApplication().getName() + ": enumeration with ratio " + ratio + " => cc " + currCC + "/" + maxCC + ", nb " + currSolutionCandidatesNb);
                sanityCheckCC(dag, maxCC, currSolutionCandidates, currCC);
                if (currCC > bestCC || (currCC == bestCC && currSolutionCandidatesNb > bestSolutionCandiatesNb)) {
                    bestCC = currCC;
                    bestSolutionCandiatesNb = currSolutionCandidatesNb;
                    bestRatio = ratio;
                    bestSolutionCandidates = currSolutionCandidates;
                    bestResult = currResult;
                }
            }
        }
        if (dag.isTargetNodeReached() && bestCC == 0) {
            throw new IllegalStateException("DAG feasible but no enumeration");
        }
        LOGGER.info("Request " + dag.getSimpleTrainPathApplication().getName() + ": found best enumeration with ratio " + bestRatio + " => cc " + bestCC + "/" + maxCC + ", nb " + bestSolutionCandiatesNb);

        return bestSolutionCandidates;
    }

    private static void sanityCheckCC(TrainPathDAG dag, int maxCC, Set<SolutionCandidate> currSolutionCandidates, int currCC) {
        if (currCC < 0 || currCC > maxCC) {
            dag.getCyclomaticComplexity();
            TrainPathDAG.cyclomaticComplexityFromSolutionCandidates(currSolutionCandidates);
            throw new IllegalStateException("currCC" + currCC + "< 0 or > maxCC " + maxCC);
        }
    }

}
