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

import java.util.List;

/**
 *
 */
public class TrainPathSlot {
    private final PeriodicalTrainPathSlot periodicalTrainPathSlot;
    private final String name;
    private PeriodicalTimeFrame startTime;
    private PeriodicalTimeFrame endTime;
    private SystemNode from;
    private SystemNode to;

    public TrainPathSlot(String name, PeriodicalTimeFrame startTime, PeriodicalTimeFrame endTime, SystemNode from, SystemNode to, PeriodicalTrainPathSlot periodicalTrainPathSlot) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.from = from;
        this.to = to;
        this.periodicalTrainPathSlot = periodicalTrainPathSlot;

    }

    public static void sort(List<TrainPathSlot> slots) {
        // insertion sort: the first i elements (0...i-1) of the list are sorted
        // therefore, preferrably, use linked list instead of array list
        for (int i = 1; i < slots.size(); i++) {
            // insert the i-th element into the list
            int j = i - 1;
            while (j >= 0 && slots.get(i).getStartTime().isBefore(slots.get(j).getStartTime())) {
                j--;
            }
            j++;
            if (j < i) {
                slots.add(j, slots.get(i));
                slots.remove(i + 1);
            }
        }
    }

    public PeriodicalTrainPathSlot getPeriodicalTrainPathSlot() {
        return periodicalTrainPathSlot;
    }

    public String getName() {
        return name;
    }

    public PeriodicalTimeFrame getStartTime() {
        return startTime;
    }

    public PeriodicalTimeFrame getEndTime() {
        return endTime;
    }

    public Duration getDuration() {
        return endTime.distanceAfter(startTime);
    }

    public SystemNode getFrom() {
        return from;
    }

    public SystemNode getTo() {
        return to;
    }

    @Override
    public String toString() {
        return "#" + this.name + " [" + getStartTime() + "," + getEndTime() + "]" + ", (" + getFrom() + ", " + getTo() + ")";
    }

    public boolean isStartTimeContainedInclusive(PeriodicalTimeFrame earliest, PeriodicalTimeFrame latest) {
        return getStartTime().isWithinBounds(earliest, latest);
    }


}
