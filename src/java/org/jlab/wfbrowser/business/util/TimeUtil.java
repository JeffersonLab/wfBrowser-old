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
     * DateTime class. Used in SQL queries, etc..
     *
     * @param i The Instant to format
     * @return A date string formatted to match MySQL's DateTime class.
     */
    public static String getDateTimeString(Instant i) {
        if (i == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);
        return formatter.format(i);
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
}
