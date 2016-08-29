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

import org.joda.time.Duration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Represents a solution candidate for a train path application.
 *
 *
 */
public class SolutionCandidate {


    private List<TrainPathSlot> path;
    private SimpleTrainPathApplication r;
    private String name;

    public SolutionCandidate(List<TrainPathSlot> path, TrainPathSlotCatalogue catalogue, SimpleTrainPathApplication r) {
        Set<Pair<SystemNode, SystemNode>> trainPathSections = new HashSet<>();
        sanityCheckPath(path, r);
        this.path = path;
        for (TrainPathSlot slot : path) {
            if (slot == null) {
                throw new IllegalArgumentException("No slot must be null in a solution candidate");
            }
            Pair<SystemNode, SystemNode> section = new Pair<SystemNode, SystemNode>(slot.getFrom(), slot.getTo());
            if (trainPathSections.contains(section)) {
                throw new IllegalStateException("Section " + section + " already visited; implementation error!");
            } else {
                trainPathSections.add(section);
            }
        }
        if (!getFirstSlot().getFrom().equals(r.getFrom())) {
            throw new IllegalStateException("Path does not start at request start node " + r.getFrom() + ", but at " + getFirstSlot().getFrom());
        }
        if (!getLastSlot().getTo().equals(r.getTo())) {
            throw new IllegalStateException("Path does not start at request end node " + r.getTo() + ", but at " + getLastSlot().getTo());
        }
        this.r = r;
        makeName();
    }

    /**
     * Verifies that summed distances and direct distance are the same, assuming a path never takes more than whole week, .
     *
     * @param path
     */
    public static void sanityCheckPath(List<TrainPathSlot> path, SimpleTrainPathApplication simpleTrainPathApplication) {

        if (path.size() == 0) {
            throw new IllegalStateException("Path should have size > 0 for request " + simpleTrainPathApplication.getName());
        } else if (!path.get(0).getFrom().equals(simpleTrainPathApplication.getFrom()) || !path.get(path.size() - 1).getTo().equals(simpleTrainPathApplication.getTo())) {
            throw new IllegalStateException("Path does not start or end at requested locations in " + simpleTrainPathApplication.getName());
        }


        Duration summedDistance = new Duration(0);
        for (TrainPathSlot trainPathSlot : path) {
            summedDistance = summedDistance.plus(trainPathSlot.getEndTime().distanceAfter(trainPathSlot.getStartTime()));
        }
        for (int i = 1; i < path.size(); i++) {
            TrainPathSlot previous = path.get(i - 1);
            TrainPathSlot current = path.get(i);
            summedDistance = summedDistance.plus(current.getStartTime().distanceAfter(previous.getEndTime()));

            if (!previous.getTo().equals(current.getFrom())) {
                throw new IllegalStateException("Path is not consistent" + simpleTrainPathApplication.getName());
            }

        }
        Duration directDistance = path.get(path.size() - 1).getEndTime().distanceAfter(path.get(0).getStartTime());
        if (!directDistance.isEqual(summedDistance)) {
            throw new IllegalStateException("Direct and summed distance should be the same in path for request" + simpleTrainPathApplication.getName());
        }
    }


    public static SimpleTrainPathApplication getTrainPathApplicationFromSolutionCandidateName(String name, TrainPathAllocationProblem tpa) {
        if (name.startsWith("sc|")) {
            String[] tokens = name.split(Pattern.quote("|"));
            String applicationName = tokens[1];
            return tpa.getTrainPathApplication(applicationName);
        }
        return null;
    }

    private void makeName() {
        String pathString = "";
        for (TrainPathSlot trainPathSlot : getPath()) {
            pathString += "_" + trainPathSlot.getName();
        }
        name = "sc|" + r.getName() + "|" + pathString.hashCode();
    }

    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SolutionCandidate && o.hashCode() == this.hashCode();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    public List<TrainPathSlot> getPath() {
        return path;
    }

    public SimpleTrainPathApplication getTrainPathApplication() {
        return r;
    }

    /**
     * Returns the duration in milliseconds from the start of the first of the first slot to the end of the last slot.
     *
     * @return
     */
    public long getDuration() {
        Duration duration = getEndTime().distanceAfter(getStartTime());
        if (duration.getMillis() < 0) {
            throw new IllegalStateException("Request " + getTrainPathApplication().getName() + ": duration must not be negative for path " + describeFullPath());
        }
        return duration.getMillis();
    }

    public PeriodicalTimeFrame getEndTime() {
        TrainPathSlot lastSlot = getLastSlot();
        return lastSlot.getEndTime();
    }

    private TrainPathSlot getLastSlot() {
        return path.get(path.size() - 1);
    }

    public PeriodicalTimeFrame getStartTime() {
        TrainPathSlot firstSlot = getFirstSlot();
        return firstSlot.getStartTime();
    }

    /**
     * Returns the first slot in the path.
     *
     * @return
     */
    public TrainPathSlot getFirstSlot() {
        return path.get(0);
    }

    /**
     * Returns how many milliseconds the solution candidate's departure is before the requested departure.
     *
     * @return
     */
    public long getEarliness() {
        return TrainPathAllocationProblem.getEarlyness(getTrainPathApplication(), getFirstSlot());
    }

    /**
     * Returns how many milliseconds the solution candidate's arrival is after the requested arrival.
     *
     * @return
     */
    public long getLateness() {
        return TrainPathAllocationProblem.getLateness(getTrainPathApplication(), getLastSlot());
    }

    /**
     * Returns the sum of the the solution candidate's duration, earliness and lateness in milliseconds.
     *
     * @return
     */
    public long getWeight() {
        return getDuration() + getEarliness() + getLateness();
    }

    /**
     * Returns true iff the solution candidate contains the slot.
     *
     * @param name
     * @return
     */
    public boolean containsSlot(String name) {
        for (TrainPathSlot trainPathSlot : path) {
            if (trainPathSlot.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives a string representation of the solution candidate's path.
     *
     * @return
     */
    public String describeFullPath() {
        StringBuilder sb = new StringBuilder();
        sb.append("Request ").append(r.getName()).append(" ").append(this.r.getFrom()).append(" => ").append(this.r.getTo()).append(" [").append(this.r.getStartTime()).append(", ").append(this.r.getEndTime()).append("] (day ").append(this.r.getStartTime().getDayOfWeek()).append(") has the solution candidate ").append(this.toString()).append(" of weight ").append(this.getWeight()).append("(duration ").append(this.getDuration()).append(", earlyness ").append(this.getEarliness()).append(", lateness ").append(this.getLateness()).append(")");
        for (TrainPathSlot trainPathSlot : path) {
            sb.append("\n " + trainPathSlot.getName() + " " + trainPathSlot.getFrom() + " => " + trainPathSlot.getTo() + " [" + trainPathSlot.getStartTime() + ", " + trainPathSlot.getEndTime() + "]");
        }
        return sb.toString();
    }


}
