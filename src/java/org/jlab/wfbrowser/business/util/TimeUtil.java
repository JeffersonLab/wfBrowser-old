/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.business.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.TimeZone;

/**
 *
 * @author adamc
 */
public class TimeUtil {

    private TimeUtil() {
        // private so no instances can be made
    }

    /**
     * Utility function for generating a date string formatted to match MySQL's
     * DateTime class. Used in SQL queries, etc..  Same as getDateTimeString with ZoneOffset.UTC supplied.
     *
     * @param i The Instant to format
     * @return A date string formatted to match MySQL's DateTime class.
     */
    public static String getDateTimeString(Instant i) {
        return getDateTimeString(i, ZoneId.of("Z"));
    }

    /**
     * Utility function for getting the accurate date from a MySQL DateTime
     * class. I think this is necessary since the MySQL DateTime class does not
     * include a timezone, but the MySQL Timestamp class has one. The java.sql
     * package doesn't contain a get DateTime, only getTimestamp which treats
     * the database value as though is was UTC and converts it to the system
     * default timezone. Since Instants use UTC, all we need to do is tell the
     * java.sql call to use a calendar with the UTC timezone and everything will
     * match up.
     *
     * @param rs
     * @return
     * @throws SQLException
     */
    public static Instant getInstantFromDateTime(ResultSet rs) throws SQLException {
        Instant out = null;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
        Timestamp ts = rs.getTimestamp("event_time_utc", cal);
        if (ts != null) {
            out = ts.toInstant();
        } else {
            throw new RuntimeException("Event date time field mssing in database");
        }
        return out;
    }

    /**
     * Return an instant from a datetime string. Expects format of "yyyy-MM-dd
     * HH:mm:ss.S"
     *
     * @param datetime The string containing the date and time to convert.
     * @return The Instant based on interpreting the string using the system
     * default ZoneId.
     */
    public static Instant getInstantFromDateTimeString(String datetime) {
        if (datetime == null) {
            return null;
        }
        Instant t = LocalDateTime.parse(datetime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S")).atZone(ZoneId.systemDefault()).toInstant();
        return t;
    }

    /**
     * Return an instant from a datetime string. Expects format of "yyyy-MM-dd".
     *
     * @param date The string containing the date to convert.
     * @return The Instant based on interpreting the string using the system
     * default ZoneId.
     */
    public static Instant getInstantFromDateString(String date) {
        if (date == null) {
            return null;
        }
        Instant t = LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atZone(ZoneId.systemDefault()).toInstant();
        return t;
    }

    /**
     * Performs a simple validation of a date string of format "yyyy-MM-dd" by
     * converting it to a LocalDateTime and back. If the two strings are equal
     * and no exceptions where thrown then all is probably good. If not, an
     * exception will be thrown.
     *
     * @param date The date string to validate
     */
    public static void validateDateString(String date) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate t = LocalDate.parse(date, dtf);
        String d = dtf.format(t);
        if (!d.equals(date)) {
            throw new RuntimeException("Unexpected date format");
        }
    }

    /**
     * Convenience function for converting an Instant to a string representing
     * the system's default date time. Note: format is "yyyy-MM-dd HH:mm:ss.S".
     *
     * @param i The instant to format
     * @param zone The ZoneId to for which the string applies
     * @return The string representation
     */
    public static String getDateTimeString(Instant i, ZoneId zone) {
        if (i == null ) {
            return null;
        }
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S").withZone(zone);
        return dtf.format(i);
    }
}
