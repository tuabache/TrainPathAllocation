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

import org.joda.convert.ToString;
import org.joda.time.*;
import org.joda.time.base.BaseLocal;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.field.AbstractReadableInstantFieldProperty;
import org.joda.time.format.DateTimeFormat;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 *
 * Represents a time frame of one week: time is always given modulo one week and durations should not be exeed one week.
 *
 * Based on {@link org.joda.time.LocalDateTime} (joda-time:joda-time:2.8.1)
 *
 *
 */
public class PeriodicalTimeFrame extends BaseLocal implements ReadablePartial, Serializable {

    public static final PeriodicalTimeFrame END_OF_WEEK = new PeriodicalTimeFrame(-62134992000000L, ISOChronology.getInstanceUTC()); // exclusive


    public static final PeriodicalTimeFrame START_OF_WEEK = new PeriodicalTimeFrame(DateTimeConstants.MONDAY, 0, 0, 0, 0); // inclusive
    /**
     * Serialization lock
     */
    private static final long serialVersionUID = -268716875315837168L;
    /**
     * The index of the dayOfMonth field in the field array
     */
    private static final int DAY_OF_WEEK = 1;
    /**
     * The index of the millis field in the field array
     */
    private static final int MILLIS_OF_DAY = 0;
    /**
     * The local millis from 1970-01-01T00:00:00
     */
    private final long iLocalMillis;
    /**
     * The chronology to use in UTC
     */
    private final transient Chronology iChronology;

    /**
     * Constructs an instance set to the specified date and time
     * using <code>ISOChronology</code>.
     *
     * @param dayOfWeek      the day of the week, from 1 to 7
     * @param hourOfDay      the hour of the day, from 0 to 23
     * @param minuteOfHour   the minute of the hour, from 0 to 59
     * @param secondOfMinute the second of the minute, from 0 to 59
     * @param millisOfSecond the millisecond of the second, from 0 to 999
     */
    public PeriodicalTimeFrame(
            int dayOfWeek,
            int hourOfDay,
            int minuteOfHour,
            int secondOfMinute,
            int millisOfSecond) {
        this(dayOfWeek, hourOfDay,
                minuteOfHour, secondOfMinute, millisOfSecond, ISOChronology.getInstanceUTC());
    }

    /**
     * Constructs an instance set to the specified date and time
     * using <code>ISOChronology</code>.
     *
     * @param dayOfWeek    the day of the week, from 1 to 7
     * @param hourOfDay    the hour of the day, from 0 to 23
     * @param minuteOfHour the minute of the hour, from 0 to 59
     */
    public PeriodicalTimeFrame(
            int dayOfWeek,
            int hourOfDay,
            int minuteOfHour) {
        this(dayOfWeek, hourOfDay,
                minuteOfHour, 0, 0, ISOChronology.getInstanceUTC());
    }

    //-----------------------------------------------------------------------

    /**
     * Constructs an instance set to the specified date and time
     * using <code>ISOChronology</code>.
     *
     * @param dayOfWeek      the day of the week, from 1 to 7
     * @param hourOfDay      the hour of the day, from 0 to 23
     * @param minuteOfHour   the minute of the hour, from 0 to 59
     * @param secondOfMinute the second of the minute, from 0 to 59
     */
    public PeriodicalTimeFrame(
            int dayOfWeek,
            int hourOfDay,
            int minuteOfHour,
            int secondOfMinute) {
        this(dayOfWeek, hourOfDay,
                minuteOfHour, secondOfMinute, 0, ISOChronology.getInstanceUTC());
    }

    /**
     * Constructs an instance set to the specified date and time
     * using the specified chronology, whose zone is ignored.
     * <p/>
     * If the chronology is null, <code>ISOChronology</code> is used.
     *
     * @param dayOfWeek      the day of the month, valid values defined by the chronology
     * @param hourOfDay      the hour of the day, valid values defined by the chronology
     * @param minuteOfHour   the minute of the hour, valid values defined by the chronology
     * @param secondOfMinute the second of the minute, valid values defined by the chronology
     * @param millisOfSecond the millisecond of the second, valid values defined by the chronology
     * @param chronology     the chronology, null means ISOChronology in default zone
     */
    public PeriodicalTimeFrame(
            int dayOfWeek,
            int hourOfDay,
            int minuteOfHour,
            int secondOfMinute,
            int millisOfSecond,
            Chronology chronology) {
        super();
        chronology = DateTimeUtils.getChronology(chronology).withUTC();
        // First of January 1971 was a Monday = UTC week day 1
        long instant = chronology.getDateTimeMillis(1, 1, dayOfWeek,
                hourOfDay, minuteOfHour, secondOfMinute, millisOfSecond);
        iChronology = chronology;
        iLocalMillis = instant;
    }

    /**
     * Constructs an instance set to the local time defined by the specified
     * instant evaluated using the specified chronology.
     * <p/>
     * If the chronology is null, ISO chronology in the default zone is used.
     * Once the constructor is completed, the zone is no longer used.
     *
     * @param instant    the milliseconds from 1970-01-01T00:00:00Z
     * @param chronology the chronology, null means ISOChronology in default zone
     */
    public PeriodicalTimeFrame(long instant, Chronology chronology) {
        chronology = DateTimeUtils.getChronology(chronology);

        long localMillis = chronology.getZone().getMillisKeepLocal(DateTimeZone.UTC, instant);
        iLocalMillis = localMillis;
        iChronology = chronology.withUTC();
    }

    //-----------------------------------------------------------------------

    public static int nextDayOfWeek(int day) {
        if (day < 1 || day > 7) {
            throw new IllegalArgumentException("Illegal day provided: " + day + ". Must be in the range [1,7]");
        }
        if (day == 7) {
            return 1;
        } else {
            return day + 1;
        }
    }

    public static String formatDuration(Duration d) {
        if (d == null) {
            return "";
        }
        long millis = d.getMillis();
        return String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
        //return "" + String.format("%02d", t.getHourOfDay()) + ":" + String.format("%02d", t.getMinuteOfHour()) + ":" + String.format("%02d", t.getSecondOfMinute());
    }

    /**
     * Gets the number of fields in this partial, which is two.
     * The supported fields are  DayOfWeek and MillisOfDay.
     *
     * @return the field count, two
     */
    public int size() {
        return 2;
    }

    //-----------------------------------------------------------------------

    /**
     * Gets the field for a specific index in the chronology specified.
     * <p/>
     * This method must not use any instance variables.
     *
     * @param index  the index to retrieve
     * @param chrono the chronology to use
     * @return the field
     */
    protected DateTimeField getField(int index, Chronology chrono) {
        switch (index) {
            case DAY_OF_WEEK:
                return chrono.dayOfWeek();
            case MILLIS_OF_DAY:
                return chrono.millisOfDay();
            default:
                throw new IndexOutOfBoundsException("Invalid index: " + index);
        }
    }

    /**
     * Gets the value of the field at the specifed index.
     * <p/>
     * This method is required to support the <code>ReadablePartial</code>
     * interface. The supported fields are Year, MonthOfDay, DayOfMonth and MillisOfDay.
     *
     * @param index the index, zero to two
     * @return the value
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public int getValue(int index) {
        switch (index) {
            case DAY_OF_WEEK:
                return getChronology().dayOfMonth().get(getLocalMillis());
            case MILLIS_OF_DAY:
                return getChronology().millisOfDay().get(getLocalMillis());
            default:
                throw new IndexOutOfBoundsException("Invalid index: " + index);
        }
    }

    //-----------------------------------------------------------------------

    /**
     * Get the value of one of the fields of a datetime.
     * <p/>
     * This method gets the value of the specified field.
     * For example:
     * <pre>
     * DateTime dt = new DateTime();
     * int year = dt.get(DateTimeFieldType.year());
     * </pre>
     *
     * @param type a field type, usually obtained from DateTimeFieldType, not null
     * @return the value of that field
     * @throws IllegalArgumentException if the field type is null
     */
    public int get(DateTimeFieldType type) {
        if (type == null) {
            throw new IllegalArgumentException("The DateTimeFieldType must not be null");
        }
        return type.getField(getChronology()).get(getLocalMillis());
    }

    /**
     * Checks if the field type specified is supported by this
     * local datetime and chronology.
     * This can be used to avoid exceptions in {@link #get(DateTimeFieldType)}.
     *
     * @param type a field type, usually obtained from DateTimeFieldType
     * @return true if the field type is supported
     */
    public boolean isSupported(DateTimeFieldType type) {
        if (type == null) {
            return false;
        }

        return type.getField(getChronology()).isSupported();
    }

    /**
     * Checks if the duration type specified is supported by this
     * local datetime and chronology.
     *
     * @param type a duration type, usually obtained from DurationFieldType
     * @return true if the field type is supported
     */
    public boolean isSupported(DurationFieldType type) {
        if (type == null) {
            return false;
        }
        return type.getField(getChronology()).isSupported();
    }


    //-----------------------------------------------------------------------

    /**
     * Gets the milliseconds of the datetime instant from the Java epoch
     * of 1970-01-01T00:00:00 (not fixed to any specific time zone).
     *
     * @return the number of milliseconds since 1970-01-01T00:00:00
     * @since 1.5 (previously private)
     */
    protected long getLocalMillis() {
        return iLocalMillis;
    }

    /**
     * Gets the chronology of the datetime.
     *
     * @return the Chronology that the datetime is using
     */
    public Chronology getChronology() {
        return iChronology;
    }

    //-----------------------------------------------------------------------

    /**
     * Compares this ReadablePartial with another returning true if the chronology,
     * field types and values are equal.
     *
     * @param partial an object to check against
     * @return true if fields and values are equal
     */
    public boolean equals(Object partial) {
        // override to perform faster
        if (this == partial) {
            return true;
        }
        if (partial instanceof PeriodicalTimeFrame) {
            PeriodicalTimeFrame other = (PeriodicalTimeFrame) partial;
            if (iChronology.equals(other.iChronology)) {
                return iLocalMillis == other.iLocalMillis;
            }
        }
        return super.equals(partial);
    }

    /**
     * Gets a hash code for the ReadablePartial that is compatible with the
     * equals method.
     *
     * @return a suitable hash code
     */
    public int hashCode() {
        int total = 157;
        for (int i = 0, isize = size(); i < isize; i++) {
            total = 23 * total + getValue(i);
            total = 23 * total + getFieldType(i).hashCode();
        }
        total += getChronology().hashCode();
        return total;
    }

    /**
     * Compares this partial with another returning an integer
     * indicating the order.
     * <p/>
     * The fields are compared in order, from largest to smallest.
     * The first field that is non-equal is used to determine the result.
     * <p/>
     * The specified object must be a partial instance whose field types
     * match those of this partial.
     *
     * @param partial an object to check against
     * @return negative if this is less, zero if equal, positive if greater
     * @throws ClassCastException   if the partial is the wrong class
     *                              or if it has field types that don't match
     * @throws NullPointerException if the partial is null
     */
    public int compareTo(ReadablePartial partial) {
        // override to perform faster
        if (this == partial) {
            return 0;
        }
        if (partial instanceof PeriodicalTimeFrame) {
            PeriodicalTimeFrame other = (PeriodicalTimeFrame) partial;
            if (iChronology.equals(other.iChronology)) {
                return (iLocalMillis < other.iLocalMillis ? -1 :
                        (iLocalMillis == other.iLocalMillis ? 0 : 1));

            }
        }
        return super.compareTo(partial);
    }

    /**
     * Gets the property object for the specified type, which contains many
     * useful methods.
     *
     * @param fieldType the field type to get the chronology for
     * @return the property object
     * @throws IllegalArgumentException if the field is null or unsupported
     */
    public Property property(DateTimeFieldType fieldType) {
        if (fieldType == null) {
            throw new IllegalArgumentException("The DateTimeFieldType must not be null");
        }
        if (isSupported(fieldType) == false) {
            throw new IllegalArgumentException("Field '" + fieldType + "' is not supported");
        }
        return new Property(this, fieldType.getField(getChronology()));
    }

    /**
     * Get the day of week field value.
     * <p/>
     * The values for the day of week are defined in {@link org.joda.time.DateTimeConstants}.
     *
     * @return the day of week
     */
    public int getDayOfWeek() {
        return getChronology().dayOfWeek().get(getLocalMillis());
    }

    /**
     * Get the hour of day field value.
     *
     * @return the hour of day
     */
    public int getHourOfDay() {
        return getChronology().hourOfDay().get(getLocalMillis());
    }

    //-----------------------------------------------------------------------

    /**
     * Get the minute of hour field value.
     *
     * @return the minute of hour
     */
    public int getMinuteOfHour() {
        return getChronology().minuteOfHour().get(getLocalMillis());
    }

    /**
     * Get the second of minute field value.
     *
     * @return the second of minute
     */
    public int getSecondOfMinute() {
        return getChronology().secondOfMinute().get(getLocalMillis());
    }

    /**
     * Get the millis of second field value.
     *
     * @return the millis of second
     */
    public int getMillisOfSecond() {
        return getChronology().millisOfSecond().get(getLocalMillis());
    }

    /**
     * Get the millis of day field value.
     *
     * @return the millis of day
     */
    public int getMillisOfDay() {
        return getChronology().millisOfDay().get(getLocalMillis());
    }

    /**
     * Get the day of week property which provides access to advanced functionality.
     *
     * @return the day of week property
     */
    public Property dayOfWeek() {
        return new Property(this, getChronology().dayOfWeek());
    }

    //-----------------------------------------------------------------------

    /**
     * Get the hour of day field property which provides access to advanced functionality.
     *
     * @return the hour of day property
     */
    public Property hourOfDay() {
        return new Property(this, getChronology().hourOfDay());
    }

    /**
     * Get the minute of hour field property which provides access to advanced functionality.
     *
     * @return the minute of hour property
     */
    public Property minuteOfHour() {
        return new Property(this, getChronology().minuteOfHour());
    }

    /**
     * Get the second of minute field property which provides access to advanced functionality.
     *
     * @return the second of minute property
     */
    public Property secondOfMinute() {
        return new Property(this, getChronology().secondOfMinute());
    }

    //-----------------------------------------------------------------------

    /**
     * Get the millis of second property which provides access to advanced functionality.
     *
     * @return the millis of second property
     */
    public Property millisOfSecond() {
        return new Property(this, getChronology().millisOfSecond());
    }

    //-----------------------------------------------------------------------

    /**
     * Get the millis of day property which provides access to advanced functionality.
     *
     * @return the millis of day property
     */
    public Property millisOfDay() {
        return new Property(this, getChronology().millisOfDay());
    }

    /**
     * Output the date time in ISO8601 format (yyyy-MM-ddTHH:mm:ss.SSS).
     *
     * @return ISO8601 time formatted string.
     */
    @ToString
    public String toString() {

        /*if (iLocalMillis < 0) {
            return ISODateTimeFormat.basicWeekDateTimeNoMillis().print(this.withLocalMillis(7 * 24 * 3600 * 1000 + iLocalMillis));

        }
        return ISODateTimeFormat.basicWeekDateTimeNoMillis().print(this);*/
        return toString("E HH:mm:ss");

    }

    //-----------------------------------------------------------------------

    /**
     * Output the date using the specified format pattern.
     *
     * @param pattern the pattern specification, null means use <code>toString</code>
     * @see org.joda.time.format.DateTimeFormat
     */
    public String toString(String pattern) {
        if (pattern == null) {
            return toString();
        }
        return DateTimeFormat.forPattern(pattern).print(this);
    }

    /**
     * Output the date using the specified format pattern.
     *
     * @param pattern the pattern specification, null means use <code>toString</code>
     * @param locale  Locale to use, null means default
     * @see org.joda.time.format.DateTimeFormat
     */
    public String toString(String pattern, Locale locale) throws IllegalArgumentException {
        if (pattern == null) {
            return toString();
        }
        return DateTimeFormat.forPattern(pattern).withLocale(locale).print(this);
    }

    /**
     * Returns a copy of this datetime with different local millis.
     * <p/>
     * The returned object will be a new instance of the same type.
     * Only the millis will change, the chronology is kept.
     * The returned object will be either be a new instance or <code>this</code>.
     *
     * @param newMillis the new millis, from 1970-01-01T00:00:00
     * @return a copy of this datetime with different millis
     */
    PeriodicalTimeFrame withLocalMillis(long newMillis) {
        return shiftToStandardWeek(newMillis == getLocalMillis() ? this : new PeriodicalTimeFrame(newMillis, getChronology()));
    }

    /**
     * Returns a copy of this datetime with the specified duration added.
     * <p/>
     * If the amount is zero or null, then <code>this</code> is returned.
     *
     * @param duration the duration to add to this one, null means zero
     * @return a copy of this datetime with the duration added
     * @throws ArithmeticException if the result exceeds the internal capacity
     */
    public PeriodicalTimeFrame plus(ReadableDuration duration) {
        return withDurationAdded(duration, 1);
    }

    //-----------------------------------------------------------------------

    private PeriodicalTimeFrame shiftToStandardWeek(PeriodicalTimeFrame periodicalTimeFrame) {
        int i = 0;
        while (periodicalTimeFrame.isAfterOrEqual(END_OF_WEEK)) {
            periodicalTimeFrame.isAfterOrEqual(END_OF_WEEK);
            periodicalTimeFrame = periodicalTimeFrame.minus(Days.days(7));
        }
        while (periodicalTimeFrame.isBefore(START_OF_WEEK)) {
            periodicalTimeFrame.isBefore(START_OF_WEEK);
            periodicalTimeFrame = periodicalTimeFrame.plus(Days.days(7));
        }
        return periodicalTimeFrame;
    }

    /**
     * Returns a copy of this datetime with the specified period added.
     * <p/>
     * If the amount is zero or null, then <code>this</code> is returned.
     * <p/>
     * This method is typically used to add complex period instances.
     *
     * @param period the period to add to this one, null means zero
     * @return a copy of this datetime with the period added
     * @throws ArithmeticException if the result exceeds the internal capacity
     */
    public PeriodicalTimeFrame plus(ReadablePeriod period) {
        return withPeriodAdded(period, 1);
    }

    /**
     * Returns a copy of this datetime plus the specified number of days.
     * <p/>
     * This LocalWeekTime instance is immutable and unaffected by this method call.
     * <p/>
     * The following three lines are identical in effect:
     * <pre>
     * LocalWeekTime added = dt.plusDays(6);
     * LocalWeekTime added = dt.plus(Period.days(6));
     * LocalWeekTime added = dt.withFieldAdded(DurationFieldType.days(), 6);
     * </pre>
     *
     * @param days the amount of days to add, may be negative
     * @return the new LocalWeekTime plus the increased days
     */
    public PeriodicalTimeFrame plusDays(int days) {
        if (days == 0) {
            return this;
        }
        long instant = getChronology().days().add(getLocalMillis(), days);
        return withLocalMillis(instant);
    }

    /**
     * Returns a copy of this datetime plus the specified number of hours.
     * <p/>
     * This LocalWeekTime instance is immutable and unaffected by this method call.
     * <p/>
     * The following three lines are identical in effect:
     * <pre>
     * LocalWeekTime added = dt.plusHours(6);
     * LocalWeekTime added = dt.plus(Period.hours(6));
     * LocalWeekTime added = dt.withFieldAdded(DurationFieldType.hours(), 6);
     * </pre>
     *
     * @param hours the amount of hours to add, may be negative
     * @return the new LocalWeekTime plus the increased hours
     */
    public PeriodicalTimeFrame plusHours(int hours) {
        if (hours == 0) {
            return this;
        }
        long instant = getChronology().hours().add(getLocalMillis(), hours);
        return withLocalMillis(instant);
    }

    //-----------------------------------------------------------------------

    /**
     * Returns a copy of this datetime plus the specified number of minutes.
     * <p/>
     * This LocalWeekTime instance is immutable and unaffected by this method call.
     * <p/>
     * The following three lines are identical in effect:
     * <pre>
     * LocalWeekTime added = dt.plusMinutes(6);
     * LocalWeekTime added = dt.plus(Period.minutes(6));
     * LocalWeekTime added = dt.withFieldAdded(DurationFieldType.minutes(), 6);
     * </pre>
     *
     * @param minutes the amount of minutes to add, may be negative
     * @return the new LocalWeekTime plus the increased minutes
     */
    public PeriodicalTimeFrame plusMinutes(int minutes) {
        if (minutes == 0) {
            return this;
        }
        long instant = getChronology().minutes().add(getLocalMillis(), minutes);
        return withLocalMillis(instant);
    }

    /**
     * Returns a copy of this datetime plus the specified number of seconds.
     * <p/>
     * This LocalWeekTime instance is immutable and unaffected by this method call.
     * <p/>
     * The following three lines are identical in effect:
     * <pre>
     * LocalWeekTime added = dt.plusSeconds(6);
     * LocalWeekTime added = dt.plus(Period.seconds(6));
     * LocalWeekTime added = dt.withFieldAdded(DurationFieldType.seconds(), 6);
     * </pre>
     *
     * @param seconds the amount of seconds to add, may be negative
     * @return the new LocalWeekTime plus the increased seconds
     */
    public PeriodicalTimeFrame plusSeconds(int seconds) {
        if (seconds == 0) {
            return this;
        }
        long instant = getChronology().seconds().add(getLocalMillis(), seconds);
        return withLocalMillis(instant);
    }

    /**
     * Returns a copy of this datetime plus the specified number of millis.
     * <p/>
     * This LocalWeekTime instance is immutable and unaffected by this method call.
     * <p/>
     * The following three lines are identical in effect:
     * <pre>
     * LocalWeekTime added = dt.plusMillis(6);
     * LocalWeekTime added = dt.plus(Period.millis(6));
     * LocalWeekTime added = dt.withFieldAdded(DurationFieldType.millis(), 6);
     * </pre>
     *
     * @param millis the amount of millis to add, may be negative
     * @return the new LocalWeekTime plus the increased millis
     */
    public PeriodicalTimeFrame plusMillis(int millis) {
        if (millis == 0) {
            return this;
        }
        long instant = getChronology().millis().add(getLocalMillis(), millis);
        return withLocalMillis(instant);
    }

    //-----------------------------------------------------------------------

    /**
     * Returns a copy of this datetime with the specified duration taken away.
     * <p/>
     * If the amount is zero or null, then <code>this</code> is returned.
     *
     * @param duration the
     *                 duration to reduce this instant by
     * @return a copy of this dfatetime with the duration taken away
     * @throws ArithmeticException if the result exceeds the internal capacity
     */
    public PeriodicalTimeFrame minus(ReadableDuration duration) {
        return withDurationAdded(duration, -1);
    }

    /**
     * Returns a copy of this datetime with the specified period taken away.
     * <p/>
     * If the amount is zero or null, then <code>this</code> is returned.
     * <p/>
     * This method is typically used to subtract complex period instances.
     *
     * @param period the period to reduce this instant by
     * @return a copy of this datetime with the period taken away
     * @throws ArithmeticException if the result exceeds the internal capacity
     */
    public PeriodicalTimeFrame minus(ReadablePeriod period) {
        return withPeriodAdded(period, -1);
    }

    /**
     * Returns a copy of this datetime minus the specified number of days.
     * <p/>
     * This LocalWeekTime instance is immutable and unaffected by this method call.
     * <p/>
     * The following three lines are identical in effect:
     * <pre>
     * LocalWeekTime subtracted = dt.minusDays(6);
     * LocalWeekTime subtracted = dt.minus(Period.days(6));
     * LocalWeekTime subtracted = dt.withFieldAdded(DurationFieldType.days(), -6);
     * </pre>
     *
     * @param days the amount of days to subtract, may be negative
     * @return the new LocalWeekTime minus the increased days
     */
    public PeriodicalTimeFrame minusDays(int days) {
        if (days == 0) {
            return this;
        }
        long instant = getChronology().days().subtract(getLocalMillis(), days);
        return withLocalMillis(instant);
    }

    /**
     * Returns a copy of this datetime minus the specified number of hours.
     * <p/>
     * This LocalWeekTime instance is immutable and unaffected by this method call.
     * <p/>
     * The following three lines are identical in effect:
     * <pre>
     * LocalWeekTime subtracted = dt.minusHours(6);
     * LocalWeekTime subtracted = dt.minus(Period.hours(6));
     * LocalWeekTime subtracted = dt.withFieldAdded(DurationFieldType.hours(), -6);
     * </pre>
     *
     * @param hours the amount of hours to subtract, may be negative
     * @return the new LocalWeekTime minus the increased hours
     */
    public PeriodicalTimeFrame minusHours(int hours) {
        if (hours == 0) {
            return this;
        }
        long instant = getChronology().hours().subtract(getLocalMillis(), hours);
        return withLocalMillis(instant);
    }

    //-----------------------------------------------------------------------

    /**
     * Returns a copy of this datetime minus the specified number of minutes.
     * <p/>
     * This LocalWeekTime instance is immutable and unaffected by this method call.
     * <p/>
     * The following three lines are identical in effect:
     * <pre>
     * LocalWeekTime subtracted = dt.minusMinutes(6);
     * LocalWeekTime subtracted = dt.minus(Period.minutes(6));
     * LocalWeekTime subtracted = dt.withFieldAdded(DurationFieldType.minutes(), -6);
     * </pre>
     *
     * @param minutes the amount of minutes to subtract, may be negative
     * @return the new LocalWeekTime minus the increased minutes
     */
    public PeriodicalTimeFrame minusMinutes(int minutes) {
        if (minutes == 0) {
            return this;
        }
        long instant = getChronology().minutes().subtract(getLocalMillis(), minutes);
        return withLocalMillis(instant);
    }

    /**
     * Returns a copy of this datetime minus the specified number of seconds.
     * <p/>
     * This LocalWeekTime instance is immutable and unaffected by this method call.
     * <p/>
     * The following three lines are identical in effect:
     * <pre>
     * LocalWeekTime subtracted = dt.minusSeconds(6);
     * LocalWeekTime subtracted = dt.minus(Period.seconds(6));
     * LocalWeekTime subtracted = dt.withFieldAdded(DurationFieldType.seconds(), -6);
     * </pre>
     *
     * @param seconds the amount of seconds to subtract, may be negative
     * @return the new LocalWeekTime minus the increased seconds
     */
    public PeriodicalTimeFrame minusSeconds(int seconds) {
        if (seconds == 0) {
            return this;
        }
        long instant = getChronology().seconds().subtract(getLocalMillis(), seconds);
        return withLocalMillis(instant);
    }

    /**
     * Returns a copy of this datetime minus the specified number of millis.
     * <p/>
     * This LocalWeekTime instance is immutable and unaffected by this method call.
     * <p/>
     * The following three lines are identical in effect:
     * <pre>
     * LocalWeekTime subtracted = dt.minusMillis(6);
     * LocalWeekTime subtracted = dt.minus(Period.millis(6));
     * LocalWeekTime subtracted = dt.withFieldAdded(DurationFieldType.millis(), -6);
     * </pre>
     *
     * @param millis the amount of millis to subtract, may be negative
     * @return the new LocalWeekTime minus the increased millis
     */
    public PeriodicalTimeFrame minusMillis(int millis) {
        if (millis == 0) {
            return this;
        }
        long instant = getChronology().millis().subtract(getLocalMillis(), millis);
        return withLocalMillis(instant);
    }

    /**
     * Returns a copy of this datetime with the specified duration added.
     * <p/>
     * If the addition is zero, then <code>this</code> is returned.
     *
     * @param durationToAdd the duration to add to this one, null means zero
     * @param scalar        the amount of times to add, such as -1 to subtract once
     * @return a copy of this datetime with the duration added
     * @throws ArithmeticException if the result exceeds the internal capacity
     */
    public PeriodicalTimeFrame withDurationAdded(ReadableDuration durationToAdd, int scalar) {
        if (durationToAdd == null || scalar == 0) {
            return this;
        }
        long instant = getChronology().add(getLocalMillis(), durationToAdd.getMillis(), scalar);
        return withLocalMillis(instant);
    }

    /**
     * Returns a copy of this datetime with the specified period added.
     * <p/>
     * If the addition is zero, then <code>this</code> is returned.
     * <p/>
     * This method is typically used to add multiple copies of complex
     * period instances.
     *
     * @param period the period to add to this one, null means zero
     * @param scalar the amount of times to add, such as -1 to subtract once
     * @return a copy of this datetime with the period added
     * @throws ArithmeticException if the result exceeds the internal capacity
     */
    public PeriodicalTimeFrame withPeriodAdded(ReadablePeriod period, int scalar) {
        if (period == null || scalar == 0) {
            return this;
        }
        long instant = getChronology().add(period, getLocalMillis(), scalar);
        return withLocalMillis(instant);
    }

    /**
     * Is this partial later than or equal to the the specified partial.
     * <p/>
     * The fields are compared in order, from largest to smallest.
     * The first field that is non-equal is used to determine the result.
     * <p/>
     * You may not pass null into this method. This is because you need
     * a time zone to accurately determine the current date.
     *
     * @param partial a partial to check against, must not be null
     * @return true if this date is after the date passed in
     * @throws IllegalArgumentException if the specified partial is null
     * @throws ClassCastException       if the partial has field types that don't match
     * @since 1.1
     */
    public boolean isAfterOrEqual(ReadablePartial partial) {
        if (partial == null) {
            throw new IllegalArgumentException("Partial cannot be null");
        }
        return compareTo(partial) >= 0;
    }

    /**
     * Is this partial earlier than or equal to the specified partial.
     * <p/>
     * The fields are compared in order, from largest to smallest.
     * The first field that is non-equal is used to determine the result.
     * <p/>
     * You may not pass null into this method. This is because you need
     * a time zone to accurately determine the current date.
     *
     * @param partial a partial to check against, must not be null
     * @return true if this date is before the date passed in
     * @throws IllegalArgumentException if the specified partial is null
     * @throws ClassCastException       if the partial has field types that don't match
     * @since 1.1
     */
    public boolean isBeforeOrEqual(ReadablePartial partial) {
        if (partial == null) {
            throw new IllegalArgumentException("Partial cannot be null");
        }
        return compareTo(partial) <= 0;
    }

    /**
     * Returns if within inclusive bounds:
     * if earliest <= latest: earliest <= this <= latest
     * if earliest > latest: (this >= earliest) or (latest >= this)
     *
     * @param earliest
     * @param latest
     * @return
     */
    public boolean isWithinBounds(PeriodicalTimeFrame earliest, PeriodicalTimeFrame latest) {
        if (earliest.isBeforeOrEqual(latest)) {
            return earliest.isBeforeOrEqual(this) && this.isBeforeOrEqual(latest);
        } else {
            return this.isAfterOrEqual(earliest) || this.isBeforeOrEqual(latest);
        }
    }

    /**
     * Positive distance ub->this and 0 if this in_T [lb,ub]
     * @param lb
     * @param ub
     * @return
     */
    public Duration distanceAfterInterval(PeriodicalTimeFrame lb, PeriodicalTimeFrame ub) {
        if (isWithinBounds(lb, ub)){
            return new Duration(0);
        } else {
            return this.distanceAfter(ub);
        }
    }

    /**
     * Positive distance this->lb and 0 if this in_T [lb,ub]
     *
     * @param lb
     * @param ub
     * @return
     */
    public Duration distanceBeforeInterval(PeriodicalTimeFrame lb, PeriodicalTimeFrame ub) {
        if (isWithinBounds(lb, ub)) {
            return new Duration(0);
        } else {
            return lb.distanceAfter(this);
        }
    }

    /**
     * Returns
     *  latest-earliest if earliest<= latest and
     *  T - (earliest-latest) if earliest > latest.
     * Always non-negative.
     *
     * @param reference
     * @return
     */
    public Duration distanceAfter(PeriodicalTimeFrame reference) {
        PeriodicalTimeFrame moment = this;

        Duration durationAfter;
        if (moment.equals(reference)) {
            durationAfter = new Duration(0);
        } else if (moment.isBeforeOrEqual(reference)) {
            // 0 = start of week
            // T = end of week
            // X = moment
            // R = reference
            // 0__X___R____T
            durationAfter = END_OF_WEEK.toDuration().minus(reference.toDuration()).plus(moment.toDuration().minus(START_OF_WEEK.toDuration()));
        } else {
            // 0__R___X____T
            durationAfter = moment.toDuration().minus(reference.toDuration());
        }
        return durationAfter;
    }

    public Duration toDuration() {
        return new Duration(iLocalMillis);
    }

    /**
     * LocalWeekTime.Property binds a LocalWeekTime to a DateTimeField allowing
     * powerful datetime functionality to be easily accessed.
     * <p/>
     * The simplest use of this class is as an alternative get method, here used to
     * get the year '1972' (as an int) and the month 'December' (as a String).
     * <pre>
     * LocalWeekTime dt = new LocalWeekTime(1972, 12, 3, 0, 0);
     * int year = dt.year().get();
     * String monthStr = dt.month().getAsText();
     * </pre>
     * <p/>
     * Methods are also provided that allow date modification. These return
     * new instances of LocalWeekTime - they do not modify the original.
     * The example below yields two independent immutable date objects
     * 20 years apart.
     * <pre>
     * LocalWeekTime dt = new LocalWeekTime(1972, 12, 3, 0, 0);
     * LocalWeekTime dt1920 = dt.year().setCopy(1920);
     * </pre>
     * <p/>
     * LocalWeekTime.Property itself is thread-safe and immutable, as well as the
     * LocalWeekTime being operated on.
     *
     * @author Stephen Colebourne
     * @author Brian S O'Neill
     * @since 1.3
     */
    public static final class Property extends AbstractReadableInstantFieldProperty {

        /**
         * Serialization version
         */
        private static final long serialVersionUID = -358138762846288L;

        /**
         * The instant this property is working against
         */
        private transient PeriodicalTimeFrame iInstant;
        /**
         * The field this property is working against
         */
        private transient DateTimeField iField;

        /**
         * Constructor.
         *
         * @param instant the instant to set
         * @param field   the field to use
         */
        Property(PeriodicalTimeFrame instant, DateTimeField field) {
            super();
            iInstant = instant;
            iField = field;
        }

        /**
         * Writes the property in a safe serialization format.
         */
        private void writeObject(ObjectOutputStream oos) throws IOException {
            oos.writeObject(iInstant);
            oos.writeObject(iField.getType());
        }

        /**
         * Reads the property from a safe serialization format.
         */
        private void readObject(ObjectInputStream oos) throws IOException, ClassNotFoundException {
            iInstant = (PeriodicalTimeFrame) oos.readObject();
            DateTimeFieldType type = (DateTimeFieldType) oos.readObject();
            iField = type.getField(iInstant.getChronology());
        }

        //-----------------------------------------------------------------------

        /**
         * Gets the field being used.
         *
         * @return the field
         */
        public DateTimeField getField() {
            return iField;
        }

        /**
         * Gets the milliseconds of the datetime that this property is linked to.
         *
         * @return the milliseconds
         */
        protected long getMillis() {
            return iInstant.getLocalMillis();
        }

        /**
         * Gets the chronology of the datetime that this property is linked to.
         *
         * @return the chronology
         * @since 1.4
         */
        protected Chronology getChronology() {
            return iInstant.getChronology();
        }

        /**
         * Gets the LocalWeekTime object linked to this property.
         *
         * @return the linked LocalWeekTime
         */
        public PeriodicalTimeFrame getLocalWeekTime() {
            return iInstant;
        }


    }

}
