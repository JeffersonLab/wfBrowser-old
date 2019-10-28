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
    private final List<LabelFilter> labelFilterList;

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
     * @param labelFilterList
     */
    public EventFilter(List<Long> eventIdList, Instant begin, Instant end, String system, List<String> locationList, List<String> classificationList, Boolean archive,
                       Boolean delete, Integer minCaptureFiles, List<LabelFilter> labelFilterList) {
        this.eventIdList = eventIdList;
        this.begin = begin;
        this.end = end;
        this.system = system;
        this.locationList = locationList;
        this.classificationList = classificationList;
        this.archive = archive;
        this.delete = delete;
        this.minCaptureFiles = minCaptureFiles;
        this.labelFilterList = labelFilterList;
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
        if (locationList != null && !locationList.isEmpty()) {
            String locationFilter = "location IN (?";
            for (int i = 1; i < locationList.size(); i++) {
                locationFilter += ",?";
            }
            locationFilter += ")";
            filters.add(locationFilter);
        }
        if (classificationList != null && !classificationList.isEmpty()) {
            String classificationFilter = "classification IN (?";
            for (int i = 1; i < classificationList.size(); i++) {
                classificationFilter += ",?";
            }
            classificationFilter += ")";
            filters.add(classificationFilter);
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

        // Working with the LabelFilters is a little different since these will be ORed together.  Internally they are
        // AND'ed so this provides a little extra flexibility in what can be specified.
        if (labelFilterList != null && !labelFilterList.isEmpty()) {
            // Setup the WHERE clause as needed.  Needed since this may be the only filter applied.
            if (filters.isEmpty()) {
                filter = " WHERE (";
            } else {
                filter += " AND (";
            }

            filter += labelFilterList.get(0).getWhereClauseContent();

            if (labelFilterList.size() > 1) {
                for (int i = 1; i < labelFilterList.size(); i++) {
                    filter += " OR " + labelFilterList.get(i).getWhereClauseContent();
                }
            }
            filter += ")";
        }

        return filter;
    }

    /**
     * Assign the filter parameter values to the prepared statement.
     *
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
        if (labelFilterList != null && !labelFilterList.isEmpty()) {
            for (LabelFilter labelFilter : labelFilterList) {
                i = labelFilter.assignParameterValues(stmt, i);
            }
        }
    }
}
