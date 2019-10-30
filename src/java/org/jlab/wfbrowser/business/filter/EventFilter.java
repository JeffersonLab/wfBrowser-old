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
public class EventFilter {

    private final List<Long> eventIdList;
    private final List<String> locationList;
    private final List<String> classificationList;
    private final Instant begin, end;
    private final String system;
    private final Boolean archive;
    private final Boolean delete;
    private final Integer minCaptureFiles;

    /**
     * Construct the basic filter object and save the individual filter values.  If minCaptureFiles != null, then query must join capture table with count(*) AS num_cf
     * Supply null if no filter is to be done on that field.
     *
     * @param eventIdList
     * @param begin
     * @param end
     * @param system
     * @param locationList
     * @param classificationList
     * @param archive
     * @param delete
     * @param minCaptureFiles
     */
    public EventFilter(List<Long> eventIdList, Instant begin, Instant end, String system, List<String> locationList, List<String> classificationList, Boolean archive,
                       Boolean delete, Integer minCaptureFiles) {
        this.eventIdList = eventIdList;
        this.begin = begin;
        this.end = end;
        this.system = system;
        this.locationList = locationList;
        this.classificationList = classificationList;
        this.archive = archive;
        this.delete = delete;
        this.minCaptureFiles = minCaptureFiles;
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
            StringBuilder eventIdFilter = new StringBuilder("event_id IN (?");
            for (int i = 1; i < eventIdList.size(); i++) {
                eventIdFilter.append(",?");
            }
            eventIdFilter.append(")");
            filters.add(eventIdFilter.toString());
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
        if (locationList != null && !locationList.isEmpty()) {
            StringBuilder locationFilter = new StringBuilder("location IN (?");
            for (int i = 1; i < locationList.size(); i++) {
                locationFilter.append(",?");
            }
            locationFilter.append(")");
            filters.add(locationFilter.toString());
        }
        if (classificationList != null && !classificationList.isEmpty()) {
            StringBuilder classificationFilter = new StringBuilder("classification IN (?");
            for (int i = 1; i < classificationList.size(); i++) {
                classificationFilter.append(",?");
            }
            classificationFilter.append(")");
            filters.add(classificationFilter.toString());
        }
        if (archive != null) {
            filters.add("archive = ?");
        }
        if (delete != null) {
            filters.add("to_be_deleted = ?");
        }
        if (minCaptureFiles != null) {
            filters.add("num_cf >= ?");
        }

        if (!filters.isEmpty()) {
            filter = " WHERE " + filters.get(0);

            if (filters.size() > 1) {
                for (int i = 1; i < filters.size(); i++) {
                    filter = filter + " AND " + filters.get(i);
                }
            }
        }
        return filter;
    }

    /**
     * Assign the filter parameter values to the prepared statement.
     *
     * @param stmt The prepared statement that the filter should operate on
     * @throws SQLException If issue binding parameters
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
        if (locationList != null && !locationList.isEmpty()) {
            for (String location : locationList) {
                stmt.setString(i++, location);
            }
        }
        if (classificationList != null && !classificationList.isEmpty()) {
            for (String classification : classificationList) {
                stmt.setString(i++, classification);
            }
        }
        if (archive != null) {
            stmt.setInt(i++, archive ? 1 : 0);
        }
        if (delete != null) {
            stmt.setInt(i++, delete ? 1 : 0);
        }
        if (minCaptureFiles != null) {
            stmt.setInt(i++, minCaptureFiles);
        }
    }
}
