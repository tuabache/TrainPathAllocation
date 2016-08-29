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

import gurobi.*;
import org.codehaus.plexus.util.dag.CycleDetectedException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class TrainPathAllocationProblemModelPathBased extends TrainPathAllocationProblemModel {

    private Set<String> allMySolutionCandidatesNames = new HashSet<>(); // stateful!

    public TrainPathAllocationProblemModelPathBased(TrainPathAllocationProblem tpa) {
        super(tpa);
    }

    private String getVarName(SolutionCandidate solutionCandidate) {
        return solutionCandidate.toString();
    }


    @Override
    protected void addRequestToModel(GRBModel model, HashMap<TrainPathSlot, GRBLinExpr> slotRequestMap, GRBLinExpr objective, SimpleTrainPathApplication simpleTrainPathApplication, TrainPathDAG dag) throws CycleDetectedException, IOException, IllegalAccessException, GRBException {

        Set<SolutionCandidate> candidateList = SolutionCandidateFinder.getEnumerate(dag);
        for (SolutionCandidate solutionCandidate : candidateList) {
            allMySolutionCandidatesNames.add(solutionCandidate.toString());
        }

        LOGGER.debug("Adding choice constraint for request " + getChoiceConstraintName(simpleTrainPathApplication));


        for (SolutionCandidate solutionCandidate : candidateList) {
            GRBVar var = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, getVarName(solutionCandidate));
        }
        model.update();



        // Add constraints: choice of exactly one train path per request
        GRBLinExpr expr = new GRBLinExpr();
        for (SolutionCandidate solCandidate : candidateList) {
            if (!solCandidate.getTrainPathApplication().equals(simpleTrainPathApplication)) {
                throw new IllegalArgumentException("Solution Candidate " + solCandidate + " does not belong to request " + getChoiceConstraintName(simpleTrainPathApplication) + " but to " + getChoiceConstraintName(solCandidate.getTrainPathApplication()));
            }
            for (TrainPathSlot trainPathSlot : solCandidate.getPath()) {
                addSlotTermToUniquenessConstraint(trainPathSlot, getVarName(solCandidate), slotRequestMap, model);

            }
            GRBVar var = model.getVarByName(getVarName(solCandidate));
            expr.addTerm(1.0, var);

            // Set objective: minimize travel time + earliness + lateness
            double coeff = solCandidate.getWeight();
            objective.addTerm(coeff, var);
        }

        GRBConstr constr = model.addConstr(expr, GRB.EQUAL, 1.0, getChoiceConstraintName(simpleTrainPathApplication));
    }

    private String getChoiceConstraintName(SimpleTrainPathApplication simpleTrainPathApplication) {
        return simpleTrainPathApplication.getName();
    }

    @Override
    protected SimpleTrainPathApplication getTrainPathApplicationFromConstraintName(String name) {
        return tpa.getTrainPathApplication(name);
    }


    @Override
    protected Map<SimpleTrainPathApplication, SolutionCandidate> extractAllocations(GRBModel model) throws GRBException {
        LOGGER.debug("Going to extract allocations...");
        // Get the allocations
        Map<SimpleTrainPathApplication, SolutionCandidate> allocations = new HashMap<>();
        for (String solutionCandidateName : allMySolutionCandidatesNames) {
            GRBVar grbVar = model.getVarByName(solutionCandidateName);
            if (TPAUtil.doubleEquals(grbVar.get(GRB.DoubleAttr.X), 1.0)) {
                LOGGER.debug(" ==> " + solutionCandidateName);
                GRBColumn col = model.getCol(grbVar);
                Set<TrainPathSlot> slots = new HashSet<TrainPathSlot>();
                SimpleTrainPathApplication simpleTrainPathApplication = null;
                for (int i = 0; i < col.size(); i++) {
                    GRBConstr constr = col.getConstr(i);
                    String constrName = constr.get(GRB.StringAttr.ConstrName);
                    TrainPathSlot slot = getTrainPathSlotFromConstraintName(constrName);
                    SimpleTrainPathApplication simpleTrainPathApplicationForConstraintName = getTrainPathApplicationFromConstraintName(constrName);
                    if ((simpleTrainPathApplicationForConstraintName == null && slot == null) || (simpleTrainPathApplicationForConstraintName != null && slot != null) || (simpleTrainPathApplication != null && simpleTrainPathApplicationForConstraintName != null)) {
                        throw new IllegalArgumentException("Constraint must either be a choice or conflict constraint. And only one choice constraint must exist.");
                    }
                    if (slot != null) {
                        slots.add(slot);
                    }
                    if (simpleTrainPathApplicationForConstraintName != null) {
                        simpleTrainPathApplication = simpleTrainPathApplicationForConstraintName;
                    }

                }
                if (allocations.containsKey(simpleTrainPathApplication)) {
                    throw new InternalError("Found two allocations for request " + simpleTrainPathApplication);
                }
                allocations.put(simpleTrainPathApplication, getSolutionCandidateFromSlotSet(simpleTrainPathApplication, slots));
                //LOGGER.debug(solutionCandidate.describeFullPath());
            }
        }
        LOGGER.debug("... allocations extracted.");
        return allocations;
    }

}
