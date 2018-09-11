/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.business.filter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jlab.wfbrowser.business.util.TimeUtil;

/**
 * An object to make filtering SQL requests for Waveforms/Events easier. Create
 * a filter object, generate the where clause string, externally create a
 * PreparedStatement using the generated sql string, then use the
 * bindParameterValues method to apply the filter
 *
 * @author adamc
 */
public class WaveformFilter {

    private final List<Long> eventIdList;
    private final Instant begin, end;
    private final String system, location;
    private final Boolean archive;

    /**
     * Construct the basic filter object and save the individual filter values.
     * Supply null if no filter is to be done on that field.
     *
     * @param eventIdList
     * @param begin
     * @param end
     * @param system
     * @param location
     * @param archive
     */
    public WaveformFilter(List<Long> eventIdList, Instant begin, Instant end, String system, String location, Boolean archive) {
        this.eventIdList = eventIdList;
        this.begin = begin;
        this.end = end;
        this.system = system;
        this.location = location;
        this.archive = archive;
    }

    /**
     * Generate a WHERE SQL clause based on the supplied filter parameters
     *
     * @return A string containing the WHERE clause based on the filter
     * parameters
     */
    public String getWhereClause() {
        String filter = "";
        List<String> filters = new ArrayList<>();

        if (eventIdList != null && !eventIdList.isEmpty()) {
            String eventIdFilter = "event_id IN (?";
            for (int i = 1; i < eventIdList.size(); i++) {
                eventIdFilter += ",?";
            }
            eventIdFilter += ")";
            filters.add(eventIdFilter);
        }
        if (begin != null) {
            filters.add("event_time_utc >= ?");
        }
        if (end != null) {
            filters.add("event_time_utc <= ?");
        }
        if (system != null) {
            filters.add("system_name = ?");
        }
        if (location != null) {
            filters.add("location = ?");
        }
        if (archive != null) {
            filters.add("archive = ?");
        }

        if (!filters.isEmpty()) {
            filter = "WHERE " + filters.get(0);

            if (filters.size() > 1) {
                for (int i = 1; i < filters.size(); i++) {
                    filter = filter + "AND " + filters.get(i);
                }
            }
        }

        return filter;
    }

    /**
     * Assign the filter parameter values to the prepared statement.
     * @param stmt
     * @throws SQLException
     */
    public void assignParameterValues(PreparedStatement stmt) throws SQLException {
        int i = 1;

        if (eventIdList != null && !eventIdList.isEmpty()) {
            for (Long eventId : eventIdList) {
                stmt.setLong(i++, eventId);
            }
        }
        if (begin != null) {
            stmt.setString(i++, TimeUtil.getDateTimeString(begin));
        }
        if (end != null) {
            stmt.setString(i++, TimeUtil.getDateTimeString(end));
        }
        if (system != null) {
            stmt.setString(i++, system);
        }
        if (location != null) {
            stmt.setString(i++, location);
        }
        if (archive != null) {
            stmt.setInt(i++, archive ? 1 : 0);
        }
    }
}
