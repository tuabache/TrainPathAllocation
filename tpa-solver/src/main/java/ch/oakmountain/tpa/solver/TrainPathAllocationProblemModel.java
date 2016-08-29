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
import com.google.common.base.Stopwatch;
import gurobi.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.joda.time.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Represents a MIP model of a Train Path Allocation Problem.
 * <p/>
 *
 */
public abstract class
        TrainPathAllocationProblemModel {
    static final Logger LOGGER = LogManager.getLogger();
    final TrainPathAllocationProblem tpa;

    public TrainPathAllocationProblemModel(TrainPathAllocationProblem tpa) {
        this.tpa = tpa;
    }

    /**
     * Compiles statistical information on the train path application problem model's size and structure (like number of variables, constraints and terms) in the weboutput.
     *
     * @param tpa
     * @param outputDir
     * @throws IllegalAccessException
     * @throws CycleDetectedException
     * @throws IOException
     * @see TrainPathAllocationProblemModelStatistics
     */
    public static void statistics(TrainPathAllocationProblem tpa, String outputDir) throws IllegalAccessException, CycleDetectedException, IOException {
        TrainPathAllocationProblemStatistics statistcs = new TrainPathAllocationProblemStatistics(tpa, outputDir);
        statistcs.compile();
    }

    /**
     * Computes the IIS applications and slots in the Gurobi model.
     *
     * @param model
     * @return
     * @throws GRBException
     * @see <a href="http://www.gurobi.com/documentation/6.0/examples.pdf"/>
     */
    private InfeasibleTPAModelException infeasibilityAnalysis(GRBModel model) throws GRBException {
        // Compute IIS
        LOGGER.warn("The model is infeasible; computing IIS");
        model.computeIIS();
        LOGGER.warn("\nThe following constraint(s) "
                + "cannot be satisfied:");
        Set<SimpleTrainPathApplication> iisSimpleTrainPathApplications = new HashSet<>();
        Set<TrainPathSlot> iisSlots = new HashSet<>();


        for (GRBConstr c : model.getConstrs()) {
            if (c.get(GRB.IntAttr.IISConstr) == 1) {
                String constraintName = c.get(GRB.StringAttr.ConstrName);
                LOGGER.debug("Found IIS constraint " + constraintName);
                TrainPathSlot slot = getTrainPathSlotFromConstraintName(constraintName);
                if (slot != null) {
                    LOGGER.debug("  => IIS slot constraint " + constraintName);
                    iisSlots.add(slot);
                }
                SimpleTrainPathApplication simpleTrainPathApplication = getTrainPathApplicationFromConstraintName(constraintName);
                if (simpleTrainPathApplication != null) {
                    LOGGER.debug("  => IIS application constraint " + constraintName);
                    iisSimpleTrainPathApplications.add(simpleTrainPathApplication);
                }
                if (slot == null && simpleTrainPathApplication == null) {
                    throw new IllegalStateException("IIS Analysis: could find neither slot nor application for constraint " + constraintName);
                }
            }
        }
        LOGGER.warn("Found " + iisSlots.size() + " IIS slots and " + iisSimpleTrainPathApplications.size() + " IIS train path applications:");
        for (TrainPathSlot iisSlot : iisSlots) {
            LOGGER.warn(" IIS slot " + iisSlot.toString());
        }
        for (SimpleTrainPathApplication iisSimpleTrainPathApplication : iisSimpleTrainPathApplications) {
            LOGGER.warn(" IIS train path application " + iisSimpleTrainPathApplication.getDescription());
        }

        return new InfeasibleTPAModelException(iisSimpleTrainPathApplications, iisSlots);
    }

    private void removeApplicationFromModel(GRBModel model, SimpleTrainPathApplication appToRemove) throws GRBException {
        for (GRBConstr c : model.getConstrs()) {
            String constraintName = c.get(GRB.StringAttr.ConstrName);
            SimpleTrainPathApplication simpleTrainPathApplication = getTrainPathApplicationFromConstraintName(constraintName);
            if (appToRemove.equals(simpleTrainPathApplication)) {
                LOGGER.warn(" Removing constraint " + constraintName + " for IIS application " + simpleTrainPathApplication.getName());
                model.remove(c);
            }
        }
    }

    /**
     * Optimize the model as it is and throw exception in case of infeasibility .
     *
     * @param outputDir
     * @param model
     * @param ignoreinfeasibleapps
     * @throws InfeasibleTPAModelException
     */
    private void optimizeModel(String outputDir, GRBModel model, boolean ignoreinfeasibleapps, boolean relaxOnInfeasibility) throws InfeasibleTPAModelException {
        try {
            LOGGER.info("Gurobi model contains " + model.getConstrs().length + " constraints (rows) and " + model.getVars().length + " variables (columns)");

            // Skip presolve?
            //  model.getEnv().set(GRB.IntParam.Presolve, 0);

            // It does not seem easily possible to only write output to file and not to stdout: https://groups.google.com/forum/#!topic/gurobi/afoNway93tk
            //model.getEnv().set(GRB.IntParam.OutputFlag, 0);


            // Optimize model

            model.optimize();


            model.write(outputDir + File.separator + "model.lp");

            int status = model.get(GRB.IntAttr.Status);
            if (status == GRB.Status.UNBOUNDED) {
                LOGGER.warn("The model cannot be solved "
                        + "because it is unbounded");
                throw new IllegalStateException();

            } else if (status == GRB.Status.INF_OR_UNBD ||
                    status == GRB.Status.INFEASIBLE) {
                LOGGER.warn("Optimization was stopped with status " + status);

                InfeasibleTPAModelException infeasibleTPAException = infeasibilityAnalysis(model);


                // Don't relax when checking subproblem feasibility
                if (!relaxOnInfeasibility) {
                    throw infeasibleTPAException;
                }

                Set<SimpleTrainPathApplication> iisSimpleTrainPathApplications = infeasibleTPAException.getIisSimpleTrainPathApplications();
                String outputDirIISSubproblem = outputDir + File.separator + "iis_" + 1;

                if (isSubProblemFeasible(iisSimpleTrainPathApplications, outputDirIISSubproblem)) {
                    throw new IllegalStateException("Not implemented yet.");
                    //optimizeModel(outputDir, model, ignoreinfeasibleapps);
                }

                /*if (removeaniisapplicationmode) {
                    TrainPathApplication appToRemove = iisTrainPathApplications.iterator().next();
                    LOGGER.info("Greedy mode: remove random application " + appToRemove.getDescription());
                    removeApplicationFromModel(model, appToRemove);
                    optimizeModel(outputDir, model, ignoreinfeasibleapps, relaxOnInfeasibility);
                } else {*/
                    throw infeasibleTPAException;
                //}
            } else if (status != GRB.Status.OPTIMAL) {
                throw new IllegalStateException("Unhandled gurobi model status " + status);
            }
        } catch (GRBException e) {
            LOGGER.error(e);
            throw new IllegalStateException("Something went wrong: ", e);
        }
    }


    private void addTrainPathApplicationToTable(TablePersistor table, SimpleTrainPathApplication r, List<SolutionCandidate> solutionCandidates) throws IOException {
        List<String> row = new ArrayList<>();
        table.writeRow(row);
    }

    private void gurobiCleanup(GRBEnv env, GRBModel model) throws GRBException {
        model.dispose();
        env.dispose();
    }

    protected double getInterval(TrainPathSlot firstSlot, TrainPathSlot secondSlot) {
        return secondSlot.getStartTime().distanceAfter(firstSlot.getEndTime()).getMillis();
    }

    protected long getDuration(TrainPathSlot slot) {
        Duration duration = slot.getEndTime().distanceAfter(slot.getStartTime());
        return duration.getMillis();
    }

    /*
    @Override
    public List<String> getTableHeader() {
        List<String> header = new ArrayList<>();
        header.add("Request");
        header.add("Item");
        header.add("Duration");
        header.add("Earlyness");
        header.add("Lateness");
        header.add("Weight");
        header.add("Train Path");
        return header;
    }

    @Override
    public List<String> getTableRow(int i) {
        List<String> row = new ArrayList<>();
        SolutionCandidate solCandidate = candidateList.get(i);
        row.add("<a href=\"" + solCandidate.getTrainPathApplication().getName() + "_dag.html\">" + solCandidate.getTrainPathApplication().getName() + " (DAG),</a> " +
                "<a href=\"" + solCandidate.getTrainPathApplication().getName() + "_enum.html\">" + solCandidate.getTrainPathApplication().getName() + " (enum)</a>");
        row.add("" + 0);
        row.add("" + StringEscapeUtils.escapeHtml4(String.valueOf(solCandidate.getDuration())));
        row.add("" + StringEscapeUtils.escapeHtml4(String.valueOf(solCandidate.getEarliness())));
        row.add("" + StringEscapeUtils.escapeHtml4(String.valueOf(solCandidate.getLateness())));
        row.add("" + StringEscapeUtils.escapeHtml4(String.valueOf(solCandidate.getWeight())));
        row.add("" + StringEscapeUtils.escapeHtml4(solCandidate.describeFullPath()));
        return row;
    }

    @Override
    public int getNbTableRows() {
        return candidateList.size();
    }

    @Override
    public String getTableTitle() {
        return "Train Path Allocation Problem";
    }

    @Override
    public void writeLines(IGraphPersistor csv) throws IOException {
        // Add constraints: choice of exactly one train path per request
        for (Request request : getRequests()) {
            Set<SolutionCandidate> solutionCandidates = getSolutionCandidatesForRequest(request);

            for (SolutionCandidate solCandidate : solutionCandidates) {
                for (SolutionCandidate otherSolutionCandidate : solutionCandidates) {
                    if (!solCandidate.equals(otherSolutionCandidate)) {
                        csv.addGraphLink(solCandidate.toString(), otherSolutionCandidate.toString(), "1.0", request.getName(), solCandidate.getTrainPathApplication().toString(), otherSolutionCandidate.getTrainPathApplication().toString(), solCandidate.getTrainPathApplication().toString(), otherSolutionCandidate.getTrainPathApplication().toString());
                        csv.addGraphLink(otherSolutionCandidate.toString(), solCandidate.toString(), "1.0", request.getName(), otherSolutionCandidate.getTrainPathApplication().toString(), solCandidate.getTrainPathApplication().toString(), otherSolutionCandidate.getTrainPathApplication().toString(), solCandidate.getTrainPathApplication().toString());
                    }
                }

            }
        }

        // Add constraints: a slot must be chosen at most once
        Set<TrainPathSlot> addedSlots = new HashSet<>();
        for (SolutionCandidate solutionCandidate : candidateList) {
            for (TrainPathSlot slot : solutionCandidate.getPath()) {
                String name = slot.getName();
                if (addedSlots.contains(slot)) {
                    continue;
                }
                addedSlots.add(slot);

                Set<SolutionCandidate> solCandidates = new HashSet<>();
                for (SolutionCandidate candidate : candidateList) {
                    if (candidate.getPath().contains(slot)) {
                        solCandidates.add(candidate);
                    }
                }
                if (solCandidates.size() > 1) {
                    for (SolutionCandidate solCandidate : solCandidates) {
                        for (SolutionCandidate otherSolutionCandidate : solCandidates) {
                            if (!solCandidate.equals(otherSolutionCandidate)) {
                                csv.addGraphLink(solCandidate.toString(), otherSolutionCandidate.toString(), "2.0", slot.getName(), solCandidate.getTrainPathApplication().toString(), otherSolutionCandidate.getTrainPathApplication().toString(), solCandidate.getTrainPathApplication().toString(), otherSolutionCandidate.getTrainPathApplication().toString());
                                csv.addGraphLink(otherSolutionCandidate.toString(), solCandidate.toString(), "2.0", slot.getName(), otherSolutionCandidate.getTrainPathApplication().toString(), solCandidate.getTrainPathApplication().toString(), otherSolutionCandidate.getTrainPathApplication().toString(), solCandidate.getTrainPathApplication().toString());
                            }
                        }
                    }
                }
            }

        }
    }

    */

    /**
     * @param outputDir
     * @param ignoreinfeasibleapps               remove applications if the unrelaxed subproblem of IIS applications is infeasible.
     * @param relaxOnInfeasibility
     * @return
     * @throws CycleDetectedException
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InfeasibleTPAException
     */
    public TrainPathAllocations solve(String outputDir, boolean ignoreinfeasibleapps, boolean relaxOnInfeasibility) throws InfeasibleTPAException {
        try {
            GRBEnv env = new GRBEnv(outputDir + File.separator + "mip.log");
            GRBModel model = new GRBModel(env);

            // Build the model
            Stopwatch stopwatchBuildModel = TPAUtil.startStopWatch();
            List<SimpleTrainPathApplication> feasibleSimpleTrainPathApplications = buildModel(model, ignoreinfeasibleapps);
            TPAUtil.stopStopWatch(stopwatchBuildModel, "BUILD MODEL");


            LOGGER.info("Found" + feasibleSimpleTrainPathApplications.size() + " feasible applications out of " + tpa.getSimpleTrainPathApplications().size());
            for (SimpleTrainPathApplication simpleTrainPathApplication : tpa.getSimpleTrainPathApplications()) {
                if (feasibleSimpleTrainPathApplications.contains(simpleTrainPathApplication)) {
                    LOGGER.info(" Application " + simpleTrainPathApplication.getDescription() + " is infeasible, but continuing in ignoreinfeasibleapps mode.");
                }
            }

            Stopwatch stopwatchOptimizeModel = TPAUtil.startStopWatch();
            try {
                optimizeModel(outputDir, model, ignoreinfeasibleapps, relaxOnInfeasibility);
            } finally {
                TPAUtil.stopStopWatch(stopwatchOptimizeModel, "OPTIMIZE MODEL");
            }


            Map<SimpleTrainPathApplication, SolutionCandidate> allocations = extractAllocations(model);
            Set<SimpleTrainPathApplication> removedSimpleTrainPathApplications = new HashSet<>();
            if (!ignoreinfeasibleapps && (allocations.size() != feasibleSimpleTrainPathApplications.size())) {
                throw new IllegalStateException("Number of feasible applications and number of allocated applications are not the, but no option -ignoreinfeasibleapps.");
            } else {
                for (SimpleTrainPathApplication simpleTrainPathApplication : feasibleSimpleTrainPathApplications) {
                    if (!allocations.containsKey(simpleTrainPathApplication)) {
                        //if (!ignoreinfeasibleapps) {
                        throw new IllegalStateException("Feasible application " + simpleTrainPathApplication.getName() + " has not been allocated‡.");
                        /*} else {
                            LOGGER.info("Train path application " + trainPathApplication.getDescription() + " was removed in ignoreinfeasibleapps mode.");
                            feasibleTrainPathApplications.remove(trainPathApplication);
                            removedTrainPathApplications.add(trainPathApplication);
                        }*/
                    }
                }
            }
            return new TrainPathAllocations(tpa, allocations, tpa.getSimpleTrainPathApplications(), removedSimpleTrainPathApplications);
        } catch (GRBException e) {
            throw new IllegalStateException("Something went wrong", e);
        }
    }


    private boolean isSubProblemFeasible(Set<SimpleTrainPathApplication> iisSimpleTrainPathApplications, String outputDir) {
        try {
            if (!Files.isDirectory(Paths.get(outputDir))) {
                Files.createDirectory(Paths.get(outputDir));
            }
        } catch (IOException e1) {
            LOGGER.error(e1);
            throw new IllegalStateException("Something went wrong: ", e1);
        }

        TrainPathAllocationProblem iisSubproblem = new TrainPathAllocationProblem(tpa.getMacroscopicTopology(), iisSimpleTrainPathApplications, tpa.getCatalogue());
        String iisTrainPathApplicationsString = iisSubproblem.buildApplicationString();
        LOGGER.warn("Checking whether subproblem " + iisTrainPathApplicationsString + " is feasible");
        for (SimpleTrainPathApplication iisSimpleTrainPathApplication : iisSimpleTrainPathApplications) {
            iisSimpleTrainPathApplication.getParams().relaxToMax();
        }
        TrainPathAllocationProblemModelArcNode iisSubproblemModel = new TrainPathAllocationProblemModelArcNode(iisSubproblem);

        try {
            iisSubproblemModel.solve(outputDir, false, false);
        } catch (InfeasibleTPAException e) {
            LOGGER.warn("=> Subproblem " + iisTrainPathApplicationsString + " is not feasible");
            return false;
        }
        LOGGER.warn("=> Subproblem " + iisTrainPathApplicationsString + " is feasible");
        return true;
    }


    private List<SimpleTrainPathApplication> buildModel(GRBModel model, boolean ignoreinfeasibleapps) throws InfeasibleTPAException {
        try {
            List<SimpleTrainPathApplication> infeasibleSimpleTrainPathApplications = new LinkedList<>();
            List<SimpleTrainPathApplication> feasibleSimpleTrainPathApplications = new LinkedList<>();
            HashMap<TrainPathSlot, GRBLinExpr> slotRequestMap = new HashMap<>();
            GRBLinExpr objective = new GRBLinExpr();

            // Add variables, flow constraints, objective and construct slotRequestMap
            for (SimpleTrainPathApplication r : tpa.getSimpleTrainPathApplications()) {

                TrainPathDAG dag = SolutionCandidateFinder.findFeasibleDAG(tpa.getMacroscopicTopology(), tpa.getCatalogue(), r);

                if (dag.isTargetNodeReached()) {
                    feasibleSimpleTrainPathApplications.add(r);
                    addRequestToModel(model, slotRequestMap, objective, r, dag);
                } else {
                    infeasibleSimpleTrainPathApplications.add(r);
                }
            }

            // Ignore infeasible train path applications in ignoreinfeasibleapps mode
            if (!ignoreinfeasibleapps && infeasibleSimpleTrainPathApplications.size() > 0) {
                throw new InfeasibleTPAApplicationException(infeasibleSimpleTrainPathApplications);
            }

            finalizeBuildModel(model, slotRequestMap, objective);
            model.setObjective(objective, GRB.MINIMIZE);
            model.update();

            return feasibleSimpleTrainPathApplications;
        } catch (CycleDetectedException | IOException | IllegalAccessException | GRBException e) {
            LOGGER.error(e);
            throw new IllegalStateException("Something went wrong", e);
        }
    }

    private void finalizeBuildModel(GRBModel model, HashMap<TrainPathSlot, GRBLinExpr> slotRequestMap, GRBLinExpr objective) throws GRBException {
        // Unit capacity constraints
        for (TrainPathSlot trainPathSlot : slotRequestMap.keySet()) {
            String unitCapacityConstraintName = getUnitCapacityConstraintName(trainPathSlot);

            GRBLinExpr expr = slotRequestMap.get(trainPathSlot);
            model.addConstr(expr, GRB.LESS_EQUAL, 1.0, unitCapacityConstraintName);
        }
        model.update();
    }

    private String getUnitCapacityConstraintName(TrainPathSlot trainPathSlot) {
        return "ucc" + "|" + trainPathSlot.getName();
    }

    protected TrainPathSlot getTrainPathSlotFromConstraintName(String name) {
        if (!name.startsWith("ucc|")) {
            return null;
        }
        return tpa.getCatalogue().getSlot(name.split(Pattern.quote("ucc|"))[1]);

    }

    protected void addSlotTermToUniquenessConstraint(TrainPathSlot slot, String varName, HashMap<TrainPathSlot, GRBLinExpr> slotRequestMap, GRBModel model) throws GRBException {
        if (!slotRequestMap.containsKey(slot)) {
            slotRequestMap.put(slot, new GRBLinExpr());
        }
        GRBLinExpr expr = slotRequestMap.get(slot);
        GRBVar varByName = model.getVarByName(varName);
        expr.addTerm(1.0, varByName);
    }

    protected SolutionCandidate getSolutionCandidateFromSlotSet(SimpleTrainPathApplication simpleTrainPathApplication, Set<TrainPathSlot> slots) {
        List<TrainPathSlot> path = new LinkedList<>();
        SystemNode nextSystemNode = simpleTrainPathApplication.getFrom();
        int nb = slots.size();
        for (int i = 0; i < nb; i++) {
            TrainPathSlot slotFound = null;
            for (TrainPathSlot slot : slots) {
                if (slot.getFrom().equals(nextSystemNode)) {
                    slotFound = slot;
                    break;
                }
            }
            if (slotFound == null) {
                throw new IllegalStateException("Could not find slot starting at " + nextSystemNode.getName());
            }
            path.add(slotFound);
            slots.remove(slotFound);
            nextSystemNode = slotFound.getTo();
        }
        return new SolutionCandidate(path, tpa.getCatalogue(), simpleTrainPathApplication);
    }

    protected abstract void addRequestToModel(GRBModel model, HashMap<TrainPathSlot, GRBLinExpr> slotRequestMap, GRBLinExpr objective, SimpleTrainPathApplication r, TrainPathDAG dag) throws CycleDetectedException, IOException, IllegalAccessException, GRBException;

    protected abstract Map<SimpleTrainPathApplication, SolutionCandidate> extractAllocations(GRBModel model) throws GRBException;

    protected abstract SimpleTrainPathApplication getTrainPathApplicationFromConstraintName(String constrName);

}
