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
import org.codehaus.plexus.util.dag.Vertex;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 */
public class TrainPathAllocationProblemModelArcNode extends TrainPathAllocationProblemModel {
    public TrainPathAllocationProblemModelArcNode(TrainPathAllocationProblem tpa) {
        super(tpa);
    }


    private String getVarName(SimpleTrainPathApplication r, Vertex v1, Vertex v2) {
        return r.getName() + "|" + v1.getLabel() + "|" + v2.getLabel();
    }

    private TrainPathSlot getSlotFromArcNode(Vertex vertex) {
        String slotName = vertex.getLabel();
        TrainPathSlot trainPathSlot = tpa.getCatalogue().getSlot(slotName);
        if (trainPathSlot == null) {
            throw new IllegalStateException("Not found");
        }
        return trainPathSlot;
    }

    @Override
    protected Map<SimpleTrainPathApplication, SolutionCandidate> extractAllocations(GRBModel model) throws GRBException {
        LOGGER.debug("Going to extract allocations....");

        // Get the allocations
        Map<SimpleTrainPathApplication, SolutionCandidate> allocations = new HashMap<>();
        Map<SimpleTrainPathApplication, Set<TrainPathSlot>> slotMap = new HashMap<>();

        for (GRBVar grbVar : model.getVars()) {
            if (TPAUtil.doubleEquals(grbVar.get(GRB.DoubleAttr.X), 1.0)) {
                String varName = grbVar.get(GRB.StringAttr.VarName);
                String[] tokens = varName.split(Pattern.quote("|"));
                String requestName = tokens[0];
                if (tokens[1].endsWith("_start") || tokens[1].endsWith("_end")) {
                    continue;
                }
                String slotName = tokens[1];

                SimpleTrainPathApplication simpleTrainPathApplication = tpa.getTrainPathApplication(requestName);
                if (simpleTrainPathApplication == null) {
                    throw new IllegalStateException("Found no request " + requestName);
                }
                TrainPathSlot slot = tpa.getSlotByName(slotName);
                if (slot == null) {
                    throw new IllegalStateException("Found no slot " + slotName);
                }
                if (!slotMap.containsKey(simpleTrainPathApplication)) {
                    slotMap.put(simpleTrainPathApplication, new HashSet<TrainPathSlot>());
                }
                slotMap.get(simpleTrainPathApplication).add(slot);
            }
        }
        for (SimpleTrainPathApplication simpleTrainPathApplication : tpa.getSimpleTrainPathApplications()) {
            if (!slotMap.containsKey(simpleTrainPathApplication)) {
                //
                LOGGER.warn("Request " + simpleTrainPathApplication.getName() + " not in allocations.");
                continue;
            }

            Set<TrainPathSlot> slots = slotMap.get(simpleTrainPathApplication);
            SolutionCandidate sc = getSolutionCandidateFromSlotSet(simpleTrainPathApplication, slots);
            allocations.put(simpleTrainPathApplication, sc);
        }
        LOGGER.debug("... allocations extracted.");
        return allocations;
    }

    @Override
    protected SimpleTrainPathApplication getTrainPathApplicationFromConstraintName(String constrName) {
        if (constrName.startsWith("fc|")) {
            String[] tokens = constrName.split(Pattern.quote("|"));
            String applicationName = tokens[1];
            return tpa.getTrainPathApplication(applicationName);
        }
        return null;
    }


    @Override
    protected void addRequestToModel(GRBModel model, HashMap<TrainPathSlot, GRBLinExpr> slotRequestMap, GRBLinExpr objective, SimpleTrainPathApplication r, TrainPathDAG dag) throws CycleDetectedException, IOException, IllegalAccessException, GRBException {


        // Add variables, one per arc
        for (Vertex vertex : dag.getVerticies()) {
            String name;
            for (Vertex child : vertex.getChildren()) {
                name = getVarName(r, vertex, child);
                model.addVar(0.0, 1.0, 0.0, GRB.BINARY, name);
            }
        }
        model.update();

        // Add flow constraints
        for (Vertex vertex : dag.getVerticies()) {
            if (vertex.isLeaf() && vertex.isRoot()) {
                // skip vertices unconnected verticies (which should have been removed from the graph, instead of just removing the arcs?)
                continue;
            }
            String flowConstraintName = "fc|" + r.getName() + "|" + vertex.getLabel();


            GRBLinExpr flowConstraintExpr = new GRBLinExpr();

            for (Vertex child : vertex.getChildren()) {
                GRBVar varByName = model.getVarByName(getVarName(r, vertex, child));
                flowConstraintExpr.addTerm(1.0, varByName);
                double weight = 0;
                if (!child.isLeaf()) {
                    TrainPathSlot secondSlot = getSlotFromArcNode(child);
                    weight += getDuration(secondSlot);
                    if (vertex.isRoot()) {
                        weight += TrainPathAllocationProblem.getEarlyness(r, secondSlot);
                    } else {
                        TrainPathSlot firstSlot = getSlotFromArcNode(vertex);
                        weight += getInterval(firstSlot, secondSlot);
                    }
                } else {
                    TrainPathSlot firstSlot = getSlotFromArcNode(vertex);
                    weight += TrainPathAllocationProblem.getLateness(r, firstSlot);
                }
                if (!vertex.isLeaf() && !vertex.isRoot()) {
                    TrainPathSlot firstSlot = getSlotFromArcNode(vertex);
                    addSlotTermToUniquenessConstraint(firstSlot, getVarName(r, vertex, child), slotRequestMap, model);
                }

                objective.addTerm(weight, varByName);
            }
            for (Vertex parent : vertex.getParents()) {
                GRBVar parentVar = model.getVarByName(getVarName(r, parent, vertex));
                flowConstraintExpr.addTerm(-1.0, parentVar);
            }

            // add flow constraint for start and end node
            if (vertex.isLeaf()) {
                model.addConstr(flowConstraintExpr, GRB.EQUAL, -1.0, flowConstraintName);
            } else if (vertex.isRoot()) {
                model.addConstr(flowConstraintExpr, GRB.EQUAL, 1.0, flowConstraintName);
            } else {
                model.addConstr(flowConstraintExpr, GRB.EQUAL, 0.0, flowConstraintName);
            }
        }
    }

}
