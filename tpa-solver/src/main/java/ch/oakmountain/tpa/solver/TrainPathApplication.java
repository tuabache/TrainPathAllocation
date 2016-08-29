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

import org.joda.time.LocalTime;

import java.util.LinkedList;
import java.util.List;

/**
 * Train Path Application with Periodicity.
 *
 *
 */
public class TrainPathApplication implements IPeriodical {
    private final Periodicity periodicity;
    private final SystemNode from;
    private final SystemNode to;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final SimpleTrainPathApplication[] simpleTrainPathApplications = new SimpleTrainPathApplication[8];
    private final String name;


    public TrainPathApplication(String name, SystemNode from, SystemNode to, LocalTime startTime, LocalTime endTime, Periodicity periodicity, int hardMaximumEarlierDeparture, int hardMinimumDwellTime, int hardMaximumLaterArrival) {
        this.name = name;
        this.periodicity = periodicity;
        this.from = from;
        this.to = to;
        this.startTime = startTime;
        this.endTime = endTime;
        for (Integer day : periodicity.getWeekDays()) {
            PeriodicalTimeFrame start = new PeriodicalTimeFrame(day, startTime.getHourOfDay(), startTime.getMinuteOfHour());
            PeriodicalTimeFrame end = new PeriodicalTimeFrame(day, endTime.getHourOfDay(), endTime.getMinuteOfHour());
            if (end.isBefore(start)) {
                end = new PeriodicalTimeFrame(PeriodicalTimeFrame.nextDayOfWeek(day), endTime.getHourOfDay(), endTime.getMinuteOfHour());
            }
            SimpleTrainPathApplication r = new SimpleTrainPathApplication(name + "_" + day, from, to, start, end, this, hardMaximumEarlierDeparture, hardMinimumDwellTime, hardMaximumLaterArrival);
            simpleTrainPathApplications[day] = r;
        }
    }

    public Periodicity getPeriodicity() {
        return periodicity;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean getWeekDay(int day) {
        return periodicity.getWeekDay(day);
    }

    public SimpleTrainPathApplication getRequestOnWeekDay(int day) {
        return simpleTrainPathApplications[day];
    }

    public int getNbDays() {
        int nb = 0;
        for (SimpleTrainPathApplication simpleTrainPathApplication : simpleTrainPathApplications) {
            if (simpleTrainPathApplication != null) {
                nb++;
            }
        }
        return nb;
    }

    public List<SimpleTrainPathApplication> getChildren() {
        List<SimpleTrainPathApplication> simpleTrainPathApplicationList = new LinkedList<>();
        for (SimpleTrainPathApplication simpleTrainPathApplication : simpleTrainPathApplications) {
            if (simpleTrainPathApplication != null) {
                simpleTrainPathApplicationList.add(simpleTrainPathApplication);
            }
        }
        return simpleTrainPathApplicationList;
    }

}
