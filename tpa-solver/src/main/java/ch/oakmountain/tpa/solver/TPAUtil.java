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

import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public class TPAUtil {
    static final Logger LOGGER = LogManager.getLogger();

    private static final double EPSILON = 1E-14;

    public static boolean doubleEquals(double d1, double d2) {
        return Math.abs(d2 - d1) <= EPSILON;
    }

    public static Stopwatch startStopWatch() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        return stopwatch;
    }

    public static void stopStopWatch(Stopwatch stopwatch, String message) {
        stopwatch.stop();
        long secsBuildModel = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        LOGGER.info("TIME TO " + message + ": " + stopwatch);
    }
}
