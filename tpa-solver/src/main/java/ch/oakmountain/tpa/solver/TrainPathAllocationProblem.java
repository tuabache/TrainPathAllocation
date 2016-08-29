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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class TrainPathAllocationProblem /*implements ITable, IGraph, IMatrix*/ {

    private static final Logger LOGGER = LogManager.getLogger();
    private final MacroscopicTopology macroscopicTopology;
    private final Set<SimpleTrainPathApplication> simpleTrainPathApplications;
    private final TrainPathSlotCatalogue catalogue;

    public TrainPathAllocationProblem(MacroscopicTopology macroscopicTopology, Set<SimpleTrainPathApplication> simpleTrainPathApplications, TrainPathSlotCatalogue catalogue) {
        this.macroscopicTopology = macroscopicTopology;
        this.simpleTrainPathApplications = simpleTrainPathApplications;
        this.catalogue = catalogue;
    }

    public static double getTotalWeightOfSolutionCandidates(Map<SimpleTrainPathApplication, SolutionCandidate> allocations) {
        double objective = 0;
        for (SimpleTrainPathApplication simpleTrainPathApplication : allocations.keySet()) {
            SolutionCandidate allocation = allocations.get(simpleTrainPathApplication);
            objective += allocation.getWeight();
        }
        return objective;
    }

    public static long getEarlyness(SimpleTrainPathApplication simpleTrainPathApplication, TrainPathSlot slot) {
        return slot.getStartTime().distanceBeforeInterval(simpleTrainPathApplication.getStartTime(), simpleTrainPathApplication.getEndTime()).getMillis();
    }

    public static long getLateness(SimpleTrainPathApplication simpleTrainPathApplication, TrainPathSlot slot) {
        return slot.getEndTime().distanceAfterInterval(simpleTrainPathApplication.getStartTime(), simpleTrainPathApplication.getEndTime()).getMillis();
    }

    public MacroscopicTopology getMacroscopicTopology() {
        return macroscopicTopology;
    }

    public TrainPathSlotCatalogue getCatalogue() {
        return catalogue;
    }

    public int nbTrainPathApplications() {
        return getSimpleTrainPathApplications().size();
    }

    public Set<SimpleTrainPathApplication> getSimpleTrainPathApplications() {
        return simpleTrainPathApplications;
    }


    public SimpleTrainPathApplication getTrainPathApplication(String name) {
        for (SimpleTrainPathApplication simpleTrainPathApplication : simpleTrainPathApplications) {
            if (simpleTrainPathApplication.getName().equals(name)) {
                return simpleTrainPathApplication;
            }
        }
        return null;
    }

    public TrainPathSlot getSlotByName(String name) {
        return catalogue.getSlot(name);
    }

    public String buildApplicationString() {
        List<String> strings = new LinkedList<>();
        for (SimpleTrainPathApplication simpleTrainPathApplication : simpleTrainPathApplications) {
            strings.add(simpleTrainPathApplication.getDescription());
        }
        return "[ " + StringUtils.join(strings.toArray()) + " ]";
    }

}
