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
import org.joda.time.LocalTime;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents a train path slot with service-day periodicity.
 *
 *
 */

public class PeriodicalTrainPathSlot implements IPeriodical {
    private final String trainPathSectionName;
    private final Periodicity periodicity;
    private final String name;
    private final TrainPathSlot[] slots = new TrainPathSlot[8];
    private LocalTime startTime;
    private LocalTime endTime;
    private SystemNode from;
    private SystemNode to;

    public PeriodicalTrainPathSlot(String trainPathSectionName, String name, LocalTime startTime, LocalTime endTime, SystemNode from, SystemNode to, Periodicity periodicity) {
        this.trainPathSectionName = trainPathSectionName;
        this.periodicity = periodicity;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.from = from;
        this.to = to;

        for (Integer day : periodicity.getWeekDays()) {
            PeriodicalTimeFrame start = new PeriodicalTimeFrame(day, startTime.getHourOfDay(), startTime.getMinuteOfHour());
            PeriodicalTimeFrame end = new PeriodicalTimeFrame(day, endTime.getHourOfDay(), endTime.getMinuteOfHour());
            if (end.isBefore(start)) {
                end = end.plusHours(24);
            }
            if (!end.isWithinBounds(start, start.plusDays(1))) {
                throw new IllegalArgumentException("end " + end + " must be within 24h from " + start);
            }
            TrainPathSlot tp = new TrainPathSlot(name + "_" + day, start, end, from, to, this);
            slots[day] = tp;
        }
    }

    public String getTrainPathSectionName() {
        return trainPathSectionName;
    }

    public Periodicity getPeriodicity() {
        return periodicity;
    }

    @Override
    public boolean getWeekDay(int day) {
        return periodicity.getWeekDay(day);
    }

    public String getName() {
        return name;
    }

    public TrainPathSlot getSlotOn(int day) {
        return slots[day];
    }

    public List<TrainPathSlot> getSlots() {
        List<TrainPathSlot> effectiveSlots = new LinkedList<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                effectiveSlots.add(slots[i]);
            }
        }
        return effectiveSlots;
    }

    public boolean isContainedInclusive(PeriodicalTimeFrame earliest, PeriodicalTimeFrame latest) {
        for (Integer day : periodicity.getWeekDays()) {
            if (slots[day].isStartTimeContainedInclusive(earliest, latest)) {
                return true;
            }
        }
        return false;
    }

    public void addAllStartTimeContainedInclusive(PeriodicalTimeFrame earliest, PeriodicalTimeFrame latest, List<TrainPathSlot> slotList) {
        for (Integer day : periodicity.getWeekDays()) {
            if (slots[day].isStartTimeContainedInclusive(earliest, latest)) {
                slotList.add(slots[day]);
            }
        }
    }

    /**
     * Get a train path slot
     *
     * @param earliest
     * @return
     */
    public TrainPathSlot getNextOrQuickestTrainPathSlot(PeriodicalTimeFrame earliest) {
        TrainPathSlot nextSlot = null;
        Duration shortestDistance = null;
        for (Integer day : periodicity.getWeekDays()) {
            TrainPathSlot daySlot = slots[day];
            Duration thisDistance;
            Duration distanceAfterStartTime = daySlot.getStartTime().distanceAfter(earliest);
            thisDistance = distanceAfterStartTime;
            if (shortestDistance == null || thisDistance.isShorterThan(shortestDistance)) {
                nextSlot = daySlot;
                shortestDistance = thisDistance;
            }
        }
        return nextSlot;
    }

}
