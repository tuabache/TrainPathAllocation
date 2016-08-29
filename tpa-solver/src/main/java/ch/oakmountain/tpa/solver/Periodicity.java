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

import java.util.LinkedList;
import java.util.List;

import static org.joda.time.DateTimeConstants.*;

/**
 * Represents the (requested) days of service.
 *
 *
 */
public class Periodicity {

    public static final String allPattern = "???????";
    // 254 = 2^1 + 2^2 + ... + 2^7
    private static final int alldays = 254;
    private int val = 0;

    public Periodicity() {
    }

    public Periodicity(int val) {
        if (val < 0 || val == 1 || val > alldays) {
            throw new IllegalArgumentException("Not a valid pattern");
        }
        this.val = val;
    }

    public static Periodicity getWholeWeekPeriodicity() {
        return new Periodicity(alldays);
    }

    /**
     * Is {@code valuePeriodicity} a sub-pattern of {@code filterPeriodicity} (inclusively)?
     *
     * @param valuePeriodicity
     * @param filterPeriodicity
     * @return
     */
    public static boolean isSubPeriodicity(Periodicity valuePeriodicity, Periodicity filterPeriodicity) {
        if (filterPeriodicity.val == alldays) {
            return true;
        }
        // Bitwise valuePeriodicity.val implies filterPeriodicity.val <=> not (valuePeriodicity.val) or filterPeriodicity.val
        return ((~(valuePeriodicity.val) | filterPeriodicity.val) & alldays) == alldays;
    }

    /**
     * Parse a string of format [01?]^{7} and return a lower and an upper periodicity
     * Example: 011?110: runs on Tue,Wed,optionally on Thu, Fri and Sat, but not on Monday and Sunday.
     * Returns the pair [0110110,0111110].
     * @param per
     * @return lower and upper periodicity (both inclusive)
     */
    public static Pair<Periodicity, Periodicity> parsePeriodicityBounds(String per) {
        if (per.length() != 7) {
            throw new IllegalArgumentException("Pattern must have length 7.");
        }
        Periodicity lower = new Periodicity();
        Periodicity upper = new Periodicity();
        for (int i = 0; i < 7; i++) {
            String letter = "" + per.charAt(i);
            switch (letter) {
                case "0":
                    break;
                case "?":
                    upper.setWeekDay(i + 1, true);
                    break;
                case "1":
                    lower.setWeekDay(i + 1, true);
                    upper.setWeekDay(i + 1, true);
                    break;
                default:
                    throw new IllegalArgumentException("Expected 0/1/?; found: " + letter);
            }
        }
        return new Pair(lower, upper);
    }

    /**
     * Is periodicity contained within lower and upper periodicity.
     * <p/>
     * An {@code IllegalArgumentException} is thrown if lower is not contained within upper.
     *
     * @param lower
     * @param upper
     * @return
     */
    public boolean containedWithin(Periodicity lower, Periodicity upper) {
        if (!isSubPeriodicity(lower, upper)) {
            throw new IllegalArgumentException("Lower periodicity must be a sub-pattern of upper bound");
        }
        return isSubPeriodicity(lower, this) && isSubPeriodicity(this, upper);
    }

    public int getVal() {
        return val;
    }

    public String getStringRepresentation() {
        String s = "";
        for (int i = 1; i <= 7; i++) {
            if(getWeekDay(i)){
                s += "1";
            }
            else{
                s+= "0";
            }
        }
        return s;
    }

    public void setWeekDay(int day, boolean b) {
        checkWeekDay(day);
        int pattern = (int) Math.pow(2, day);
        if (b) {
            val = val | pattern;
        } else {
            val = val & (~pattern);
        }
    }

    public boolean getWeekDay(int day) {
        checkWeekDay(day);
        int pattern = (int) Math.pow(2, day);
        return (val & pattern) > 0;
    }

    public List<Integer> getWeekDays() {
        List<Integer> l = new LinkedList<>();
        for (int i = 1; i <= 7; i++) {
            if (getWeekDay(i)) {
                l.add(i);
            }
        }
        return l;
    }

    private void checkWeekDay(int day) {
        switch (day) {
            case MONDAY:
                break;
            case TUESDAY:
                break;
            case WEDNESDAY:
                break;
            case THURSDAY:
                break;
            case FRIDAY:
                break;
            case SATURDAY:
                break;
            case SUNDAY:
                break;
            default:
                throw new IllegalArgumentException("Illegal week day " + day);
        }
    }

    @Override
    public int hashCode() {
        return val;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Periodicity that = (Periodicity) o;

        return val == that.val;

    }
}
