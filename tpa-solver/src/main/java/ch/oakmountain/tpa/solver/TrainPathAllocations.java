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

import java.util.Map;
import java.util.Set;

/**
 * Collects the allocations of a train path allocation problem.
 *
 *
 */
public class TrainPathAllocations {
    private final Set<SimpleTrainPathApplication> simpleTrainPathApplications;
    private final TrainPathAllocationProblem tpa;
    private final Map<SimpleTrainPathApplication, SolutionCandidate> allocations;
    private final Set<SimpleTrainPathApplication> removedSimpleTrainPathApplications;

    public TrainPathAllocations(TrainPathAllocationProblem tpa, Map<SimpleTrainPathApplication, SolutionCandidate> allocations, Set<SimpleTrainPathApplication> simpleTrainPathApplications,
                                Set<SimpleTrainPathApplication> removedSimpleTrainPathApplications) {
        this.simpleTrainPathApplications = simpleTrainPathApplications;
        this.tpa = tpa;
        this.allocations = allocations;
        this.removedSimpleTrainPathApplications = removedSimpleTrainPathApplications;
    }

    public Set<SimpleTrainPathApplication> getRemovedSimpleTrainPathApplications() {
        return removedSimpleTrainPathApplications;
    }

    public Set<SimpleTrainPathApplication> getSimpleTrainPathApplications() {
        return simpleTrainPathApplications;
    }

    public TrainPathAllocationProblem getTpa() {
        return tpa;
    }

    public Map<SimpleTrainPathApplication, SolutionCandidate> getAllocations() {
        return allocations;
    }
}
