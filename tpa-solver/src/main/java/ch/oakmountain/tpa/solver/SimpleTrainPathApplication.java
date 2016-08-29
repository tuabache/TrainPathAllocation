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
import org.joda.time.Days;
import org.joda.time.Duration;

import java.lang.reflect.Field;
import java.util.List;

/**
 *
 */

public class SimpleTrainPathApplication {
    private static final Logger LOGGER = LogManager.getLogger();


    private final SystemNode from;
    private final SystemNode to;
    private final PeriodicalTimeFrame startTime;
    private final PeriodicalTimeFrame endTime;
    private final String name;
    private final TrainPathAllocationProblemPruningParameters params;

    private TrainPathApplication parent;
    private int solutionCandidateCounter = 0;

    public SimpleTrainPathApplication(String name, SystemNode from, SystemNode to, PeriodicalTimeFrame startTime, PeriodicalTimeFrame endTime, TrainPathApplication parent, int hardMaximumEarlierDeparture, int hardMinimumDwellTime, int hardMaximumLaterArrival) {
        this.name = name;
        this.from = from;
        this.to = to;
        this.startTime = startTime;
        this.endTime = endTime;
        this.parent = parent;
        if (!endTime.isWithinBounds(startTime, startTime.plus(Days.days(1)))) {
            endTime.isWithinBounds(startTime, startTime.plus(Days.days(1)));
            throw new IllegalArgumentException("End time " + endTime + " is not within 24h from startTime " + startTime + " in request " + name);
        }
        this.params = new TrainPathAllocationProblemPruningParameters(this, hardMaximumEarlierDeparture, hardMinimumDwellTime, hardMaximumLaterArrival);
    }




    public int getNextSolutionCandiateCounte() {
        return solutionCandidateCounter++;
    }

    public TrainPathAllocationProblemPruningParameters getParams() {
        return params;
    }

    public TrainPathApplication getParent() {
        return parent;
    }

    public SystemNode getFrom() {
        return from;
    }

    public SystemNode getTo() {
        return to;
    }

    public PeriodicalTimeFrame getStartTime() {
        return startTime;
    }

    public PeriodicalTimeFrame getEndTime() {
        return endTime;
    }

    public String getName() {
        return name;
    }

    public String getHTMLDescription(MacroscopicTopology macro, TrainPathSlotCatalogue catalogue) throws IllegalAccessException {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        sb.append("<th> Property </th>");
        sb.append("<th> Value </th>");

        for (Field field : this.getClass().getDeclaredFields()) {
            // HACK: JaCoCo adds field $jacocoData; which gives problems in regular expressions at 1 A.M....
            if (field.getName().contains("$") || field.get(this) == null || field.get(this).toString().contains("$")) {
                continue;
            }
            sb.append("<tr>");
            sb.append("<td> " + field.getName() + " </td>");
            sb.append("<td> " + field.get(this) + " </td>");
            sb.append("</tr>");
        }
        for (List<SystemNode> route : macro.getRoutes(from, to)) {
            sb.append("<tr>");
            sb.append("<td> route </td>");
            sb.append("<td>");
            SystemNode previous = null;
            Duration duration = new Duration(0);
            for (SystemNode systemNode : route) {
                sb.append("- " + systemNode.getName() + " -");
                if (previous != null) {
                    List<TrainPathSlot> sortedTrainPathSlots = catalogue.getSortedTrainPathSlots(previous, systemNode, PeriodicalTimeFrame.START_OF_WEEK, PeriodicalTimeFrame.END_OF_WEEK);
                    if (sortedTrainPathSlots.size() == 0) {
                        throw new IllegalStateException("No slot");

                    }
                    TrainPathSlot aSlot = sortedTrainPathSlots.get(0);
                    Duration stepDuration = aSlot.getEndTime().distanceAfter(aSlot.getStartTime());
                    if (stepDuration.isShorterThan(new Duration(0))) {
                        throw new IllegalStateException("Duration of " + aSlot.getFrom() + " - " + aSlot.getTo() + " is negative: " + PeriodicalTimeFrame.formatDuration(stepDuration));
                    }
                    duration = duration.plus(stepDuration);
                }
                previous = systemNode;
            }
            sb.append("----- " + PeriodicalTimeFrame.formatDuration(duration));
            sb.append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    public String getDescription() {
        return "#" + this.name + " [" + getStartTime() + "," + getEndTime() + "]" + ", (" + getFrom() + ", " + getTo() + ")";
    }

}
