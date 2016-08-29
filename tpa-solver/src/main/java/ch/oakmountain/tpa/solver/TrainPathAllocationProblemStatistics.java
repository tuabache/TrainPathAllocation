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
import ch.oakmountain.tpa.web.TpaWebPersistor;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.codehaus.plexus.util.dag.Vertex;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Collects structural information on initial arc-node and path-based model.
 * <p/>
 *
 */
class TrainPathAllocationProblemStatistics {
    static final Logger LOGGER = LogManager.getLogger();

    private final String outputDir;
    private final TrainPathAllocationProblem tpa;
    // Add variables, flow constraints, objective and construct slotRequestMap
    List<SimpleTrainPathApplication> infeasibleSimpleTrainPathApplications = new LinkedList<>();
    List<SimpleTrainPathApplication> feasibleSimpleTrainPathApplications = new LinkedList<>();
    HashMap<TrainPathSlot, Integer> arcNodeUnitCapacityCounts = new HashMap<>();
    HashMap<TrainPathSlot, Integer> pathBasedConflictCounts = new HashMap<>();
    int arcNodeFlowConstraintsCount = 0;
    int arcNodeFlowConstraintTermsCount = 0;
    int arcNodeUnitCapacityConstraints = 0;
    int arcNodeUnitCapacityConstraintTerms = 0;
    int arcNodeVariablesCount = 0;
    int pathBasedSolutionCandidateChoiceConstraintsCount = 0;
    int pathBasedSolutionCandidateChoiceTermsCount = 0;
    int pathBasedSolutionCandidateConflictConstraints = 0;
    int pathBasedSolutionCandidateConflictTerms = 0;
    int pathBasedVariablesCount = 0;
    int totalNbPaths = 0;

    public TrainPathAllocationProblemStatistics(TrainPathAllocationProblem tpa, String outputDir) throws IOException {
        this.outputDir = outputDir;
        this.tpa = tpa;
    }

    private List<String> getHeader() {
        List<String> header = Arrays.asList("Model", "Description", "Value");
        return header;
    }

    protected void persist(String outputDir, SimpleTrainPathApplication r, Set<SolutionCandidate> tps, TrainPathDAG dag) throws IOException, IllegalAccessException {
        //TpaWebPersistor.createTable("tpa", outputDir, this);
        //TpaWebPersistor.createGraph("constraints", outputDir, this, "Train Path Allocation Problem Constraints");
        //TpaWebPersistor.createMatrix("matrix", outputDir, this, "Train Path Allocation Problem Constraints");
        TpaWebPersistor.createGraph("dag", outputDir + File.separator + r.getName(), dag.toCSV(new HashSet<SolutionCandidate>(), true), "<h1>Train Path Slot DAG for Request " + r.getName() + "</h1>\n" + r.getHTMLDescription(tpa.getMacroscopicTopology(), tpa.getCatalogue()));
        if (tps != null && tps.size() > 0) {
            TpaWebPersistor.createGraph("enum", outputDir + File.separator + r.getName(), dag.toCSV(tps, false), "<h1>Solution Candidates for Request " + r.getName() + "</h1>\n" + r.getHTMLDescription(tpa.getMacroscopicTopology(), tpa.getCatalogue()));
        }
    }

    private void addTrainPathApplication(TablePersistor appsTable, SimpleTrainPathApplication r) throws CycleDetectedException, IOException, IllegalAccessException {
        TrainPathDAG dag = SolutionCandidateFinder.findFeasibleDAG(tpa.getMacroscopicTopology(), tpa.getCatalogue(), r);

        persist(outputDir, r, null, dag);

        if (dag.isTargetNodeReached()) {
            feasibleSimpleTrainPathApplications.add(r);
        } else {
            TrainPathAllocationProblemModel.LOGGER.info("train path application " + r.getName() + " is infeasible");
            infeasibleSimpleTrainPathApplications.add(r);
            return;
        }
        Set<SolutionCandidate> solutionCandidates = SolutionCandidateFinder.getEnumerate(dag);


        int dagEffectiveVerticies = 0;
        int myarcnodeflowconstraints = 0;

        for (Vertex vertex : dag.getVerticies()) {
            if (vertex.isLeaf() && vertex.isRoot()) {
                // skip unconnected vertices
                continue;
            }
            dagEffectiveVerticies += 1;
            if (!vertex.isRoot() && !vertex.isLeaf()) {
                TrainPathSlot trainPathSlot = dag.getSlotFromVertex(vertex.getLabel());
                incrementArcNodeUnityCapacityCountForSlotBy(trainPathSlot, vertex.getChildren().size());
            }
            int vertexFlowConstraintTerms = vertex.getChildren().size() + vertex.getParents().size();
            increaseArcNodeFlowConstraintTermsBy(vertexFlowConstraintTerms);
            incrementArcNodeFlowConstraintsBy(1);
            myarcnodeflowconstraints++;
            incrementArcNodeVariablesCountBy(vertex.getChildren().size());
        }
        LOGGER.debug("arcnodeflowconstraints|" + r.getName() + "|" + myarcnodeflowconstraints);
        LOGGER.debug("dagEffectiveVerticies|" + r.getName() + "|" + dagEffectiveVerticies);

        for (SolutionCandidate solutionCandidate : solutionCandidates) {
            for (TrainPathSlot trainPathSlot : solutionCandidate.getPath()) {
                incrementPathBasedConflictCount(trainPathSlot);
            }
        }
        int nbSolutionCandidates = solutionCandidates.size();
        int nbPaths = dag.nbPaths();

        incrementPathBasedVariablesBy(nbSolutionCandidates);
        incrementPathBasedSolutionCandidateChoiceConstraintsCount();
        incrementPathBasedSolutionCandidateChoiceTermsCountBy(nbSolutionCandidates);

        TrainPathApplicationStatistics trainPathApplicationStatistics = new TrainPathApplicationStatistics(outputDir, r, dag, solutionCandidates);
        appsTable.writeRow(trainPathApplicationStatistics.compileAndGetTrainPathApplicationListRow());
    }


    private void incrementPathBasedSolutionCandidateChoiceTermsCountBy(int nbSolutionCandidates) {
        pathBasedSolutionCandidateChoiceTermsCount += nbSolutionCandidates;
    }

    private void incrementPathBasedSolutionCandidateChoiceConstraintsCount() {
        pathBasedSolutionCandidateChoiceConstraintsCount += 1;
    }

    private void incrementArcNodeVariablesCountBy(int expDagNbVerticies) {
        arcNodeVariablesCount += expDagNbVerticies;
    }

    private void incrementPathBasedVariablesBy(int nbSolutionCandidates) {
        pathBasedVariablesCount += nbSolutionCandidates;
    }

    private void incrementArcNodeFlowConstraintsBy(int expDagNbVerticies) {
        arcNodeFlowConstraintsCount += expDagNbVerticies;
    }

    private void incrementPathBasedConflictCount(TrainPathSlot trainPathSlot) {
        if (!pathBasedConflictCounts.containsKey(trainPathSlot)) {
            pathBasedConflictCounts.put(trainPathSlot, 0);
        }
        pathBasedConflictCounts.put(trainPathSlot, pathBasedConflictCounts.get(trainPathSlot) + 1);
    }

    private void incrementTotalNbPathsBy(int nbPaths) {
        totalNbPaths += nbPaths;
    }

    private void increaseArcNodeFlowConstraintTermsBy(int flowConstraintTerms) {
        arcNodeFlowConstraintTermsCount += flowConstraintTerms;
    }

    private void incrementArcNodeUnityCapacityCountForSlotBy(TrainPathSlot trainPathSlot, int by) {
        if (!arcNodeUnitCapacityCounts.containsKey(trainPathSlot)) {
            arcNodeUnitCapacityCounts.put(trainPathSlot, 0);
        }
        arcNodeUnitCapacityCounts.put(trainPathSlot, arcNodeUnitCapacityCounts.get(trainPathSlot) + by);
    }

    public void compile() throws IOException, CycleDetectedException, IllegalAccessException {
        TablePersistor appsTable = new TablePersistor("apps", outputDir, "Train Path Allocation Problem", TrainPathApplicationStatistics.getHeader());

        for (SimpleTrainPathApplication r : tpa.getSimpleTrainPathApplications()) {
            addTrainPathApplication(appsTable, r);
        }
        appsTable.finishTable();
        compileSummaryTable();
    }

    private void compileSummaryTable() throws IOException {
        TablePersistor summaryTable = new TablePersistor("summary", outputDir, "Train Path Allocation Problem", getHeader());

        for (TrainPathSlot trainPathSlot : arcNodeUnitCapacityCounts.keySet()) {
            int count = arcNodeUnitCapacityCounts.get(trainPathSlot);
            arcNodeUnitCapacityConstraints += 1;
            arcNodeUnitCapacityConstraintTerms += count;
        }
        for (TrainPathSlot trainPathSlot : pathBasedConflictCounts.keySet()) {
            int count = pathBasedConflictCounts.get(trainPathSlot);
            pathBasedSolutionCandidateConflictConstraints += 1;
            pathBasedSolutionCandidateConflictTerms += count;
        }

        // arc-node
        // - flow constraints: sum of nb verticies of all DAGs (terms per constraint: nb of solution candidates)
        // - unit capacity: nb of slots of all DAGs (terms per constraint: at most one term per application)
        summaryTable.writeRow(Arrays.asList("arc-node/path-based", "feasible requests", String.valueOf(feasibleSimpleTrainPathApplications.size())));
        summaryTable.writeRow(Arrays.asList("arc-node/path-based", "infeasible requests", String.valueOf(infeasibleSimpleTrainPathApplications.size())));
        summaryTable.writeRow(Arrays.asList("arc-node/path-based", "total number of train paths", String.valueOf(totalNbPaths)));
        summaryTable.writeRow(Arrays.asList("arc-node/path-based", "global minimum dwell time", PeriodicalTimeFrame.formatDuration(feasibleSimpleTrainPathApplications.get(0).getParams().getHARD_MINIMUM_DWELL_TIME())));
        summaryTable.writeRow(Arrays.asList("arc-node/path-based", "global maximum earlier departure time", PeriodicalTimeFrame.formatDuration(feasibleSimpleTrainPathApplications.get(0).getParams().getHARD_MAXIMUM_EARLIER_DEPARTURE())));
        summaryTable.writeRow(Arrays.asList("arc-node/path-based", "global maximum later arrival time", PeriodicalTimeFrame.formatDuration(feasibleSimpleTrainPathApplications.get(0).getParams().getHARD_MAXIMUM_LATER_ARRIVAL())));

        summaryTable.writeRow(Arrays.asList("arc-node", "flow constraints", String.valueOf(arcNodeFlowConstraintsCount)));
        summaryTable.writeRow(Arrays.asList("arc-node", "terms in flow constraints", String.valueOf(arcNodeFlowConstraintTermsCount)));
        summaryTable.writeRow(Arrays.asList("arc-node", "unit capacity constraints", String.valueOf(arcNodeUnitCapacityConstraints)));
        summaryTable.writeRow(Arrays.asList("arc-node", "terms in unit capacity constraints", String.valueOf(arcNodeUnitCapacityConstraintTerms)));
        BigInteger arcNodeConstraints = BigInteger.valueOf(arcNodeFlowConstraintsCount + arcNodeUnitCapacityConstraints);
        summaryTable.writeRow(Arrays.asList("arc-node", "rows (constraints)", String.valueOf(arcNodeConstraints)));
        summaryTable.writeRow(Arrays.asList("arc-node", "columns (variables)", String.valueOf(arcNodeVariablesCount)));
        summaryTable.writeRow(Arrays.asList("arc-node", "train path slots", String.valueOf(arcNodeUnitCapacityCounts.keySet().size())));
        BigInteger arcNodeTerms = BigInteger.valueOf(arcNodeFlowConstraintTermsCount + arcNodeUnitCapacityConstraintTerms);
        BigInteger arcNodeFlowConstraintMatrixSize = BigInteger.valueOf(arcNodeFlowConstraintTermsCount).multiply(BigInteger.valueOf(arcNodeVariablesCount));
        BigInteger arcNodeUnitCapacityConstraintMatrixSize = BigInteger.valueOf(arcNodeUnitCapacityConstraintTerms).multiply(BigInteger.valueOf(arcNodeVariablesCount));
        BigInteger arcNodeMatrixSize = arcNodeConstraints.multiply(BigInteger.valueOf(arcNodeVariablesCount));
        summaryTable.writeRow(Arrays.asList("arc-node", "sparsity in flow constraints", String.valueOf(arcNodeFlowConstraintTermsCount + "/" + arcNodeFlowConstraintMatrixSize + " (" + (new BigDecimal(arcNodeFlowConstraintTermsCount)).divide((new BigDecimal(arcNodeFlowConstraintMatrixSize)), 10, RoundingMode.HALF_UP) + ")")));
        summaryTable.writeRow(Arrays.asList("arc-node", "sparsity in unit capacity constraints", String.valueOf(arcNodeUnitCapacityConstraintTerms + "/" + arcNodeUnitCapacityConstraintMatrixSize + " (" + (new BigDecimal(arcNodeUnitCapacityConstraintTerms)).divide((new BigDecimal(arcNodeUnitCapacityConstraintMatrixSize)), 10, RoundingMode.HALF_UP) + ")")));
        summaryTable.writeRow(Arrays.asList("arc-node", "sparsity in all constraints", String.valueOf(arcNodeTerms + "/" + arcNodeMatrixSize + " (" + (new BigDecimal(arcNodeTerms)).divide(new BigDecimal(arcNodeMatrixSize), 10, RoundingMode.HALF_UP) + ")")));

        // path-based
        // - solution candidate choice: nb of applications (terms per constraint: nb of solution canidates)
        // - conflict sets: nb of slots of all DAGs (terms per constraint: possibly many terms per application)
        summaryTable.writeRow(Arrays.asList("path-based", "choice constraints", String.valueOf(pathBasedSolutionCandidateChoiceConstraintsCount)));
        summaryTable.writeRow(Arrays.asList("path-based", "terms in choice constraints", String.valueOf(pathBasedSolutionCandidateChoiceTermsCount)));
        summaryTable.writeRow(Arrays.asList("path-based", "conflict constraints", String.valueOf(pathBasedSolutionCandidateConflictConstraints)));
        summaryTable.writeRow(Arrays.asList("path-based", "terms in conflict constraints", String.valueOf(pathBasedSolutionCandidateConflictTerms)));
        summaryTable.writeRow(Arrays.asList("path-based", "enumeration rate ", String.valueOf(pathBasedSolutionCandidateChoiceTermsCount + "/" + totalNbPaths + "(" + ((double) pathBasedSolutionCandidateChoiceTermsCount / totalNbPaths) + ")")));
        BigInteger pathBasedConstraints = BigInteger.valueOf(pathBasedSolutionCandidateChoiceConstraintsCount).add(BigInteger.valueOf(pathBasedSolutionCandidateConflictConstraints));
        summaryTable.writeRow(Arrays.asList("path-based", "rows (constraints)", String.valueOf(pathBasedConstraints)));
        summaryTable.writeRow(Arrays.asList("path-based", "columns (variables)", String.valueOf(pathBasedVariablesCount)));
        summaryTable.writeRow(Arrays.asList("path-based", "train path slots", String.valueOf(pathBasedConflictCounts.keySet().size())));
        BigInteger pathBasedTerms = BigInteger.valueOf(pathBasedSolutionCandidateConflictTerms).add(BigInteger.valueOf(pathBasedSolutionCandidateChoiceTermsCount));
        BigInteger pathBasedMatrixSize = pathBasedConstraints.multiply(BigInteger.valueOf(pathBasedVariablesCount));
        BigInteger pathBasedSolutionCandidateChoiceMatrixSize = BigInteger.valueOf(pathBasedSolutionCandidateChoiceConstraintsCount).multiply(BigInteger.valueOf(pathBasedVariablesCount));
        BigInteger pathBasedSolutionCandidateConflictMatrixSize = BigInteger.valueOf(pathBasedSolutionCandidateConflictConstraints).multiply(BigInteger.valueOf(pathBasedVariablesCount));
        summaryTable.writeRow(Arrays.asList("path-based", "sparsity in choice constraints", String.valueOf(pathBasedSolutionCandidateChoiceTermsCount + "/" + pathBasedSolutionCandidateChoiceMatrixSize + " (" + (new BigDecimal(pathBasedSolutionCandidateChoiceTermsCount)).divide(new BigDecimal(pathBasedSolutionCandidateChoiceMatrixSize), 10, RoundingMode.HALF_UP) + ")")));
        summaryTable.writeRow(Arrays.asList("path-based", "sparsity in conflict constraints", String.valueOf(pathBasedSolutionCandidateConflictTerms + "/" + pathBasedSolutionCandidateConflictMatrixSize + " (" + (new BigDecimal(pathBasedSolutionCandidateConflictTerms)).divide((new BigDecimal(pathBasedSolutionCandidateConflictMatrixSize)), 10, RoundingMode.HALF_UP) + ")")));
        summaryTable.writeRow(Arrays.asList("path-based", "sparsity in all constraints", String.valueOf(pathBasedTerms + "/" + pathBasedMatrixSize + " (" + (new BigDecimal(pathBasedTerms)).divide((new BigDecimal(pathBasedMatrixSize)), 10, RoundingMode.HALF_UP) + ")")));

        summaryTable.finishTable();

        if (!(arcNodeUnitCapacityCounts.keySet().size() >= pathBasedConflictCounts.keySet().size())) {
            throw new IllegalStateException("nb of train path slots in arc node model has to be larger or equal to the number of train path slots in the path based model (because of partial enumeration)");
        }

        // LaTeX table output
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance();
        //DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();

        //symbols.setGroupingSeparator(' ');
        //formatter.setDecimalFormatSymbols(symbols);
        File latexFile = new File(outputDir + File.separator + "computational.tex");
        FileUtils.writeStringToFile(latexFile, "Number of feasible applications:&\\multicolumn{2}{c|}{" + formatter.format(feasibleSimpleTrainPathApplications.size()) + "}\\\\\n");
        FileUtils.writeStringToFile(latexFile, "Number of variables      &" + formatter.format(arcNodeVariablesCount) + "&" + formatter.format(pathBasedVariablesCount) + "\\\\\n", true);
        FileUtils.writeStringToFile(latexFile, "Number of constraints   &" + formatter.format(arcNodeConstraints) + "&" + formatter.format(pathBasedConstraints) + "\\\\\n", true);
        FileUtils.writeStringToFile(latexFile, "Number of unit capacity / conflict constraints", true);
        FileUtils.writeStringToFile(latexFile, "&" + formatter.format(arcNodeUnitCapacityConstraints) + "                                         &" + formatter.format(pathBasedSolutionCandidateConflictConstraints) + "\\\\\n", true);
        FileUtils.writeStringToFile(latexFile, "%    Matrix size                     &" + formatter.format(arcNodeMatrixSize) + "&" + formatter.format(pathBasedMatrixSize) + "\\\\\n", true);
        FileUtils.writeStringToFile(latexFile, "%Number of terms           &" + formatter.format(arcNodeTerms) + "&" + formatter.format(pathBasedTerms) + "\\\\\n", true);
        FileUtils.writeStringToFile(latexFile, "Number of terms in unit capacity/", true);
        FileUtils.writeStringToFile(latexFile, "&" + formatter.format(arcNodeUnitCapacityConstraintTerms) + "& \\\\\n", true);
        FileUtils.writeStringToFile(latexFile, "\\hspace{0.5cm}choice constraints", true);
        FileUtils.writeStringToFile(latexFile, "&&" + formatter.format(pathBasedSolutionCandidateChoiceTermsCount) + "\\\\\n", true);
        FileUtils.writeStringToFile(latexFile, "Number of terms in flow conservation/", true);
        FileUtils.writeStringToFile(latexFile, "&" + formatter.format(arcNodeFlowConstraintTermsCount) + "& \\\\\n", true);
        FileUtils.writeStringToFile(latexFile, "\\hspace{0.5cm}conflict constraints", true);
        FileUtils.writeStringToFile(latexFile, "&&" + formatter.format(pathBasedSolutionCandidateConflictTerms) + "\\\\\n", true);
    }


}
