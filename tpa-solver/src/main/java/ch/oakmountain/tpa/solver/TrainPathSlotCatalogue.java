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
import org.joda.time.Duration;
import org.joda.time.Hours;
import org.joda.time.LocalTime;
import org.joda.time.Minutes;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class TrainPathSlotCatalogue {
    private static final Logger LOGGER = LogManager.getLogger();
    private List<PeriodicalTrainPathSlot> trainPathSlots = new LinkedList<PeriodicalTrainPathSlot>();
    private Map<SystemNode, List<PeriodicalTrainPathSlot>> fromMap = new LinkedHashMap<>();
    private Map<SystemNode, List<PeriodicalTrainPathSlot>> toMap = new LinkedHashMap<>();
    private Map<Pair<SystemNode, SystemNode>, List<PeriodicalTrainPathSlot>> linkMap = new LinkedHashMap<>();
    private Map<String, TrainPathSlot> slotMap = new LinkedHashMap<>();
    private Map<String, PeriodicalTrainPathSlot> periodicalSlotMap = new LinkedHashMap<>();
    private Map<PeriodicalTrainPathSlot, String> trainPathSectionNameMap = new LinkedHashMap<>();

    public static TrainPathSlotCatalogue generateTestTrainPathCatalogue(MacroscopicTopology macroscopicTopology, int trainsPerHour, int durationMinutes) {
        TrainPathSlotCatalogue catalogue = new TrainPathSlotCatalogue();

        List<String> linkNames = macroscopicTopology.getLinkNames();
        for (String linkName : linkNames) {
            Pair<SystemNode, SystemNode> link = macroscopicTopology.getLink(linkName);
            SystemNode fromNode = link.first;
            SystemNode toNode = link.second;

            // train path slots hourly
            for (int i = 0; i < 24; i++) {
                for (int j = 0; j < trainsPerHour; j++) {
                    int hour = i;
                    int minutes = j * (60 / trainsPerHour);
                    String deptimestring =
                            String.format("%02d", hour) + ":" + String.format("%02d", minutes);
                    int arrtime = hour * 60 + minutes + durationMinutes;
                    String arrtimestring =
                            String.format("%02d", ((arrtime / 60)) % 24) + ":" + String.format("%02d", arrtime % 60);
                    String slotName = linkName + "_" + String.format("%03d", hour) + "_" + String.format("%03d", j);
                    catalogue.add(linkName, slotName, LocalTime.parse(deptimestring), LocalTime.parse(arrtimestring), fromNode, toNode, Periodicity.getWholeWeekPeriodicity());
                }
            }
        }
        return catalogue;
    }

    public List<PeriodicalTrainPathSlot> getTrainPathSlots() {
        return trainPathSlots;
    }

    public int getNbSlots() {
        return slotMap.keySet().size();
    }

    public int getNbPeriodicalSlots() {
        return periodicalSlotMap.keySet().size();
    }

    public String getTrainPathSectionName(PeriodicalTrainPathSlot slot) {
        return trainPathSectionNameMap.get(slot);
    }

    public PeriodicalTrainPathSlot add(String trainPathSectionName, String name, LocalTime startTime, LocalTime endTime, SystemNode from, SystemNode to, Periodicity periodicity) {
        if (periodicalSlotMap.containsKey(name)) {
            throw new IllegalArgumentException("There is already a periodical train path slot of name " + name + " in this train path catalogue; found for " + trainPathSectionName);
        }
        PeriodicalTrainPathSlot slot = new PeriodicalTrainPathSlot(trainPathSectionName, name, startTime, endTime, from, to, periodicity);
        Pair<SystemNode, SystemNode> link = new Pair(from, to);
        if (!linkMap.containsKey(link)) {
            linkMap.put(link, new LinkedList<PeriodicalTrainPathSlot>());
        }
        if (linkMap.get(link).size() > 0) {
            //PeriodicalTrainPathSlot ps =
            for (PeriodicalTrainPathSlot ps : linkMap.get(link)) {
                if (ps.getPeriodicity().getWeekDays().size() > 0 && slot.getPeriodicity().getWeekDays().size() > 0) {
                    TrainPathSlot referenceSlot = ps.getSlotOn(ps.getPeriodicity().getWeekDays().get(0));
                    TrainPathSlot thisSlot = slot.getSlotOn(slot.getPeriodicity().getWeekDays().get(0));
                    Duration referenceLb = referenceSlot.getDuration().minus(Minutes.minutes(10).toStandardDuration());
                    Duration referenceUb = referenceSlot.getDuration().plus(Minutes.minutes(10).toStandardDuration());
                    if (thisSlot.getDuration().isShorterThan(referenceLb) || thisSlot.getDuration().isLongerThan(referenceUb)) {
                        throw new IllegalArgumentException("Slot " + thisSlot.getName() + " (" + PeriodicalTimeFrame.formatDuration(thisSlot.getDuration()) + ") is more than 10 minutes shorter/longer than reference slot " + referenceSlot.getName() + " (" + PeriodicalTimeFrame.formatDuration(referenceSlot.getDuration()) + ").");
                    }
                }
            }
        }
        linkMap.get(link).add(slot);
        periodicalSlotMap.put(name, slot);
        for (TrainPathSlot trainPathSlot : slot.getSlots()) {
            if (slotMap.containsKey(trainPathSlot.getName())) {
                throw new IllegalArgumentException("There is alreaday a train path slot of this name " + trainPathSlot.getName() + " in this train path catalogue");
            }
            slotMap.put(trainPathSlot.getName(), trainPathSlot);
        }
        trainPathSlots.add(slot);
        if (!fromMap.containsKey(from)) {
            fromMap.put(from, new LinkedList<PeriodicalTrainPathSlot>());
        }
        fromMap.get(from).add(slot);
        if (!toMap.containsKey(to)) {
            toMap.put(to, new LinkedList<PeriodicalTrainPathSlot>());
        }
        toMap.get(to).add(slot);

        trainPathSectionNameMap.put(slot, trainPathSectionName);
        return slot;
    }


    public PeriodicalTrainPathSlot getPeriodicalSlot(String name) {
        return periodicalSlotMap.get(name);
    }

    public TrainPathSlot getSlot(String name) {
        return slotMap.get(name);
    }

    public void logInfo() {
        LOGGER.info("Parsed the following slots...");
        for (PeriodicalTrainPathSlot slot : trainPathSlots) {
            LOGGER.info("... Parsed slot " + slot.getName());
        }
    }

    /**
     * Get the
     *
     * @param from
     * @param to
     * @param earliest
     * @return
     */
    public TrainPathSlot getQuickestTrainPathSlot(SystemNode from, SystemNode to, PeriodicalTimeFrame earliest) {
        return getNextOrQuickestTrainPathSlot(from, to, earliest, false);
    }

    private TrainPathSlot getNextOrQuickestTrainPathSlot(SystemNode from, SystemNode to, PeriodicalTimeFrame earliest, boolean takeStartTime) {
        Pair<SystemNode, SystemNode> link = new Pair(from, to);
        TrainPathSlot bestSlot = null;
        Duration bestDistance = null;
        if (linkMap.get(link) == null) {
            throw new IllegalArgumentException("There is no edge from " + from.getName() + " to " + to.getName() + " in the macroscopic topology");
        }
        for (PeriodicalTrainPathSlot periodicalTrainPathSlot : linkMap.get(link)) {
            TrainPathSlot slotCand = periodicalTrainPathSlot.getNextOrQuickestTrainPathSlot(earliest);
            if (slotCand == null) {
                LOGGER.warn("Found no successor slot at " + periodicalTrainPathSlot.getName() + "; are there no slots for this periodical slot?");
                continue;
            }
            Duration distanceCand;
            if (takeStartTime) {
                distanceCand = slotCand.getStartTime().distanceAfter(earliest);
            } else {
                // situation [------>start-->earliest-->end---->[ vs. [---->earliest-->start-->end--[
                Duration durationEarliestToStart = slotCand.getStartTime().distanceAfter(earliest);
                Duration durationStartToEnd = slotCand.getEndTime().distanceAfter(slotCand.getStartTime());
                distanceCand = durationEarliestToStart.plus(durationStartToEnd);
            }

            if (bestDistance == null || distanceCand.isShorterThan(bestDistance)) {
                bestSlot = slotCand;
                bestDistance = distanceCand;
            }
        }
        return bestSlot;
    }

    public TrainPathSlot getNextTrainPathSlot(SystemNode from, SystemNode to, PeriodicalTimeFrame earliest) {
        return getNextOrQuickestTrainPathSlot(from, to, earliest, true);
    }

    public List<TrainPathSlot> getSortedTrainPathSlots(SystemNode from, SystemNode to, PeriodicalTimeFrame earliest, PeriodicalTimeFrame latest) {
        Pair<SystemNode, SystemNode> link = new Pair(from, to);
        List<TrainPathSlot> slots = new LinkedList<>();
        if (linkMap.get(link) != null) {
            for (PeriodicalTrainPathSlot periodicalTrainPathSlot : linkMap.get(link)) {
                periodicalTrainPathSlot.addAllStartTimeContainedInclusive(earliest, latest, slots);
            }
        }
        TrainPathSlot.sort(slots);
        return slots;
    }

    public TrainPathSlot getNextTrainPathSlotWithin24(SystemNode from, SystemNode to, PeriodicalTimeFrame earliest) {
        Pair<SystemNode, SystemNode> link = new Pair(from, to);
        List<TrainPathSlot> slots = new LinkedList<>();
        if (linkMap.get(link) == null) {
            throw new IllegalArgumentException("There is no edge from " + from.getName() + " to " + to.getName() + " in the macroscopic topology");
        }
        if (linkMap.get(link) != null) {
            for (PeriodicalTrainPathSlot periodicalTrainPathSlot : linkMap.get(link)) {
                periodicalTrainPathSlot.addAllStartTimeContainedInclusive(earliest, earliest.plus(Hours.hours(24).toStandardDuration()), slots);
            }
        }
        Duration minDistance = Hours.hours(24).toStandardDuration();
        TrainPathSlot slot = null;
        for (TrainPathSlot trainPathSlot : slots) {
            Duration distance = trainPathSlot.getStartTime().distanceAfter(earliest);
            if (distance.isShorterThan(minDistance)) {
                minDistance = distance;
                slot = trainPathSlot;
            }
        }
        return slot;
    }

}
