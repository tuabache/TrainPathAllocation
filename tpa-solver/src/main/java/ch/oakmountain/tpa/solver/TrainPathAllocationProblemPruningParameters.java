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
import org.joda.time.Minutes;

import java.util.*;

/**
 *
 */
public class TrainPathAllocationProblemPruningParameters {


    private static final int MAX_TRAINPATHS_PER_REQUEST = Integer.MAX_VALUE;
    private static final int MAX_OUTGOINGCONNECTIONS_PER_SLOT = Integer.MAX_VALUE;

    private final Duration HARD_MAXIMUM_EARLIER_DEPARTURE;
    private final Duration HARD_MINIMUM_DWELL_TIME;
    private final Duration HARD_MAXIMUM_LATER_ARRIVAL;
    private final SimpleTrainPathApplication simpleTrainPathApplication;

    private Duration MAXIMUM_EARLIER_DEPARTURE;
    private Duration MINIMUM_DWELL_TIME;
    private Duration MAXIMUM_LATER_ARRIVAL;

    private Duration MAXIMUM_LATER_DEPARTURE;
    private Duration MAXIMUM_EARLIER_ARRIVAL;
    private Duration MAXIMUM_ADDITIONAL_DWELL_TIME;

    private Map<SystemNode, Duration> nonDefaultAddiontalDwellTimesMap = new HashMap<>();

    public TrainPathAllocationProblemPruningParameters(SimpleTrainPathApplication application) {
        this.simpleTrainPathApplication = application;
        HARD_MAXIMUM_EARLIER_DEPARTURE = Minutes.minutes(0).toStandardDuration();
        HARD_MINIMUM_DWELL_TIME = Minutes.minutes(0).toStandardDuration();
        HARD_MAXIMUM_LATER_ARRIVAL = Minutes.minutes(0).toStandardDuration();
    }

    public TrainPathAllocationProblemPruningParameters(SimpleTrainPathApplication application, int hardMaximumEarlierDeparture, int hardMinimumDwellTime, int hardMaximumLaterArrival) {
        this.simpleTrainPathApplication = application;
        HARD_MAXIMUM_EARLIER_DEPARTURE = Minutes.minutes(hardMaximumEarlierDeparture).toStandardDuration();
        HARD_MINIMUM_DWELL_TIME = Minutes.minutes(hardMinimumDwellTime).toStandardDuration();
        HARD_MAXIMUM_LATER_ARRIVAL = Minutes.minutes(hardMaximumLaterArrival).toStandardDuration();
        setMINIMUM_DWELL_TIME(Minutes.minutes(hardMinimumDwellTime).toStandardDuration());
    }

    public static int getMAX_OUTGOINGCONNECTIONS_PER_SLOT() {
        return MAX_OUTGOINGCONNECTIONS_PER_SLOT;
    }

    public void relaxToMax() {

        setMAXIMUM_ADDITIONAL_DWELL_TIME(getApplicationHardMaxDuration());
        setMAXIMUM_EARLIER_ARRIVAL(getApplicationDuration());
        setMAXIMUM_LATER_DEPARTURE(getApplicationDuration());


        // set to applications hard bounds
        setMINIMUM_DWELL_TIME(getHARD_MINIMUM_DWELL_TIME());
        setMAXIMUM_EARLIER_DEPARTURE(getHARD_MAXIMUM_EARLIER_DEPARTURE());
        setMAXIMUM_LATER_ARRIVAL(getHARD_MAXIMUM_LATER_ARRIVAL());

    }

    public void relax(SimpleTrainPathApplication simpleTrainPathApplication, MacroscopicTopology topology) {
        // TODO not implemented yet
        List<List<SystemNode>> routes = topology.getRoutes(simpleTrainPathApplication.getFrom(), simpleTrainPathApplication.getTo());
        Set<SystemNode> nodes = new HashSet<>();
        for (List<SystemNode> route : routes) {
            for (SystemNode systemNode : route) {
                nodes.add(systemNode);
            }
        }
        for (SystemNode node : nodes) {
            setMAXIMUM_ADDITIONAL_DWELL_TIME(getMAXIMUM_ADDITIONAL_DWELL_TIME(node).plus(Minutes.minutes(10).toStandardDuration()));
        }

    }

    public void setDefaultPruning() {
        // set to applications hard bounds
        setMINIMUM_DWELL_TIME(getHARD_MINIMUM_DWELL_TIME());
        setMAXIMUM_EARLIER_DEPARTURE(getHARD_MAXIMUM_EARLIER_DEPARTURE());
        setMAXIMUM_LATER_ARRIVAL(getHARD_MAXIMUM_LATER_ARRIVAL());

        // set to arbitrary initial values
        // TODO command-line options for default pruning?
        setMAXIMUM_ADDITIONAL_DWELL_TIME(Minutes.minutes(15).toStandardDuration());
        Duration maxDuration = getApplicationHardMaxDuration();
        Duration anHour = Minutes.minutes(60).toStandardDuration();
        if (maxDuration.isShorterThan(anHour)) {
            setMAXIMUM_LATER_DEPARTURE(maxDuration);
            setMAXIMUM_EARLIER_ARRIVAL(maxDuration);
        } else {
            setMAXIMUM_LATER_DEPARTURE(Minutes.minutes(60).toStandardDuration());
            setMAXIMUM_EARLIER_ARRIVAL(Minutes.minutes(60).toStandardDuration());
        }


        nonDefaultAddiontalDwellTimesMap.clear();
    }

    public Duration getHARD_MAXIMUM_EARLIER_DEPARTURE() {
        return HARD_MAXIMUM_EARLIER_DEPARTURE;
    }

    public Duration getHARD_MINIMUM_DWELL_TIME() {
        return HARD_MINIMUM_DWELL_TIME;
    }

    public Duration getHARD_MAXIMUM_LATER_ARRIVAL() {
        return HARD_MAXIMUM_LATER_ARRIVAL;
    }


    public int getMAX_TRAINPATHS_PER_REQUEST() {
        return MAX_TRAINPATHS_PER_REQUEST;
    }

    public Duration getMAXIMUM_LATER_DEPARTURE() {
        return MAXIMUM_LATER_DEPARTURE;
    }

    public void setMAXIMUM_LATER_DEPARTURE(Duration MAXIMUM_LATER_DEPARTURE) {
        this.MAXIMUM_LATER_DEPARTURE = MAXIMUM_LATER_DEPARTURE;
    }

    public Duration getMAXIMUM_EARLIER_DEPARTURE() {
        return MAXIMUM_EARLIER_DEPARTURE;
    }

    public void setMAXIMUM_EARLIER_DEPARTURE(Duration MAXIMUM_EARLIER_DEPARTURE) {
        if (MAXIMUM_EARLIER_DEPARTURE.toPeriod().toStandardDuration().isLongerThan(HARD_MAXIMUM_EARLIER_DEPARTURE.toPeriod().toStandardDuration())) {
            throw new IllegalArgumentException("Must not be longer than hard maximum.");
        }
        this.MAXIMUM_EARLIER_DEPARTURE = MAXIMUM_EARLIER_DEPARTURE;
    }

    public Duration getMINIMUM_DWELL_TIME() {
        return MINIMUM_DWELL_TIME;
    }

    public void setMINIMUM_DWELL_TIME(Duration MINIMUM_DWELL_TIME) {
        if (MINIMUM_DWELL_TIME.isShorterThan(HARD_MINIMUM_DWELL_TIME)) {
            throw new IllegalArgumentException("Must not be shorter than hard minimum.");
        }
        this.MINIMUM_DWELL_TIME = MINIMUM_DWELL_TIME;
    }

    public Duration getMAXIMUM_LATER_ARRIVAL() {
        return MAXIMUM_LATER_ARRIVAL;
    }

    public void setMAXIMUM_LATER_ARRIVAL(Duration MAXIMUM_LATER_ARRIVAL) {
        if (MAXIMUM_LATER_ARRIVAL.toPeriod().toStandardDuration().isLongerThan(HARD_MAXIMUM_LATER_ARRIVAL.toPeriod().toStandardDuration())) {
            throw new IllegalArgumentException("Must not be longer than hard maximum.");
        }
        this.MAXIMUM_LATER_ARRIVAL = MAXIMUM_LATER_ARRIVAL;
    }

    public Duration getMAXIMUM_EARLIER_ARRIVAL() {
        return MAXIMUM_EARLIER_ARRIVAL;
    }

    public void setMAXIMUM_EARLIER_ARRIVAL(Duration MAXIMUM_EARLIER_ARRIVAL) {
        this.MAXIMUM_EARLIER_ARRIVAL = MAXIMUM_EARLIER_ARRIVAL;
    }

    public Duration getMAXIMUM_ADDITIONAL_DWELL_TIME(SystemNode node) {
        if (nonDefaultAddiontalDwellTimesMap.containsKey(node)) {
            return nonDefaultAddiontalDwellTimesMap.get(node);
        } else {
            return MAXIMUM_ADDITIONAL_DWELL_TIME;
        }
    }

    public void setMAXIMUM_ADDITIONAL_DWELL_TIME(Duration MAXIMUM_ADDITIONAL_DWELL_TIME) {
        if (MAXIMUM_ADDITIONAL_DWELL_TIME.getMillis() < 0) {
            throw new IllegalArgumentException("Duration must not be < 0");
        }
        this.MAXIMUM_ADDITIONAL_DWELL_TIME = MAXIMUM_ADDITIONAL_DWELL_TIME;
    }

    public void setMAXIMUM_ADDITIONAL_DWELL_TIME(SystemNode node, Duration MAXIMUM_ADDITIONAL_DWELL_TIME) {
        if (MAXIMUM_ADDITIONAL_DWELL_TIME.getMillis() < 0) {
            throw new IllegalArgumentException("Duration must not be < 0");
        }
        nonDefaultAddiontalDwellTimesMap.put(node, MAXIMUM_ADDITIONAL_DWELL_TIME);
    }

    public PeriodicalTimeFrame getArrivalLowerBound() {
        PeriodicalTimeFrame lowerInclusiveBoundRequest = simpleTrainPathApplication.getEndTime().minus(MAXIMUM_EARLIER_ARRIVAL);
        return lowerInclusiveBoundRequest;
    }

    public PeriodicalTimeFrame getArrivalUpperBound() {
        PeriodicalTimeFrame upperInclusiveBoundRequest = simpleTrainPathApplication.getEndTime().plus(MAXIMUM_LATER_ARRIVAL);
        return upperInclusiveBoundRequest;
    }

    public PeriodicalTimeFrame getArrivalHardUpperBound() {
        PeriodicalTimeFrame upperInclusiveBoundRequest = simpleTrainPathApplication.getEndTime().plus(HARD_MAXIMUM_LATER_ARRIVAL);
        return upperInclusiveBoundRequest;
    }

    public PeriodicalTimeFrame getDepartureHardLowerBound() {
        PeriodicalTimeFrame lowerInclusiveBoundRequest = simpleTrainPathApplication.getStartTime().minus(HARD_MAXIMUM_EARLIER_DEPARTURE);
        return lowerInclusiveBoundRequest;
    }

    public PeriodicalTimeFrame getDepartureLowerBound() {
        PeriodicalTimeFrame lowerInclusiveBoundRequest = simpleTrainPathApplication.getStartTime().minus(MAXIMUM_EARLIER_DEPARTURE);
        return lowerInclusiveBoundRequest;
    }

    public PeriodicalTimeFrame getDepartureUpperBound() {
        PeriodicalTimeFrame upperInclusiveBoundRequest = simpleTrainPathApplication.getStartTime().plus(MAXIMUM_LATER_DEPARTURE);
        return upperInclusiveBoundRequest;
    }

    public boolean isWithinHardBounds(PeriodicalTimeFrame moment) {
        return moment.isWithinBounds(getDepartureHardLowerBound(), getArrivalHardUpperBound());
    }

    public boolean isWithinBounds(PeriodicalTimeFrame moment) {
        return moment.isWithinBounds(getDepartureLowerBound(), getArrivalUpperBound());
    }

    public Duration getApplicationHardMaxDuration() {
        Duration duration = getArrivalHardUpperBound().distanceAfter(getDepartureHardLowerBound());
        return duration;
    }

    public Duration getApplicationDuration() {
        Duration duration = simpleTrainPathApplication.getEndTime().distanceAfter(simpleTrainPathApplication.getStartTime());
        return duration;
    }


}
