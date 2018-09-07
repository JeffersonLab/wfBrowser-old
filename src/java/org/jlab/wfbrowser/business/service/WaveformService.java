/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.business.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.wfbrowser.business.util.SqlUtil;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Waveform;

/**
 *
 * @author adamc
 */
public class WaveformService {

    private static final Logger LOGGER = Logger.getLogger(WaveformService.class.getName());

    public long addEvent(Event e) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        String systemIdSql = "SELECT system_id,count(*) "
                + "FROM waveforms.system_type"
                + " WHERE system_name = ?";

        int systemId;
        long eventId;
        try {
            conn = SqlUtil.getConnection();

            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(systemIdSql);
            pstmt.setString(1, e.getSystem());

            rs = pstmt.executeQuery();

            // system_id column should have a unique constraint.  If only one name, the name and count will be returned.
            // if multiple names, only the first name will be returned, but it won't matter since we'll throw an error over the
            // count.
            if (rs.next()) {
                systemId = rs.getInt("system_id");
                int n = rs.getInt("count(*)");
                if (n == 0) {
                    LOGGER.log(Level.WARNING, "User attempted to add event for unsupported system ''{0}''", e.getSystem());
                    throw new IllegalArgumentException("Waveform system '" + e.getSystem() + "' is not supported at this time");
                } else if (n > 1) {
                    LOGGER.log(Level.SEVERE, "Waveform system, ''{0}', maps multiple system IDs in database'", e.getSystem());
                    throw new IllegalArgumentException("Error: Waveform system name lookup returned multiple system IDs.  Contact the software"
                            + "maintainer about this error");
                }
            } else {
                throw new RuntimeException("Error querying database for system ID");
            }

            String insertEventSql = "INSERT INTO waveforms.event (event_time_utc, location, system_id) VALUES (?, ?, ?)";

            pstmt = conn.prepareStatement(insertEventSql, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, e.getEventTimeString());
            pstmt.setString(2, e.getLocation());
            pstmt.setInt(3, systemId);

            int n = pstmt.executeUpdate();
            if (n != 1) {
                conn.rollback();
                throw new RuntimeException("Inserting new event did not update more exactly one row in the database");
            }

            ResultSet rse = pstmt.getGeneratedKeys();
            if (rse != null && rse.next()) {
                eventId = rse.getLong(1);
            } else {
                conn.rollback();
                throw new RuntimeException("Error querying database for last inserted event_id");
            }

            List<Waveform> waveforms = e.getWaveforms();
            String insertPointSql = "INSERT INTO waveforms.data (event_id, series_name, time_offset, val) VALUES (?, ?, ? ,?)";
            for (Waveform w : waveforms) {
                pstmt = conn.prepareStatement(insertPointSql);
                for (int i = 0; i < w.getTimeOffsets().size(); i++) {
                    pstmt.setLong(1, eventId);
                    pstmt.setString(2, w.getSeriesName());
                    pstmt.setDouble(3, w.getTimeOffsets().get(i));
                    pstmt.setDouble(4, w.getValues().get(i));
                    int numUpdates = pstmt.executeUpdate();

                    if (numUpdates != 1) {
                        conn.rollback();
                        throw new RuntimeException("Error inserting waveform into database.");
                    }

                    // Maybe not necessary, but seems safer
                    pstmt.clearParameters();
                }
            }

            conn.commit();
        } finally {
            SqlUtil.close(pstmt, conn);
        }
        return eventId;
    }

    public Event getEvent(long eventId) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        Event event = null;
        Instant eventTime = null;
        String location = null;
        String system = null;
        Boolean archive = null;
        List<Waveform> waveforms = new ArrayList<>();

        try {
            conn = SqlUtil.getConnection();

            String getEventSql = "SELECT event_time_utc,location,system_type.system_name,archive FROM waveforms.event, waveforms.system_type "
                    + "WHERE waveforms.system_type.system_id = waveforms.event.system_id AND event_id = ?";
            pstmt = conn.prepareStatement(getEventSql);
            pstmt.setLong(1, eventId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
                Timestamp ts = rs.getTimestamp("event_time_utc", cal);
                if (ts != null) {
                    eventTime = ts.toInstant();
                } else {
                    throw new RuntimeException("Event date time field mssing in database");
                }
                location = rs.getString("location");
                system = rs.getString("system_name");
                archive = rs.getBoolean("archive");
            }
            if (location == null || system == null || archive == null) {
                throw new RuntimeException("Error querying event information from database");
            }
            rs.close();
            pstmt.close();

            String getDataSql = "SELECT series_name,time_offset,val FROM data WHERE event_id = ?";
            pstmt = conn.prepareStatement(getDataSql);
            pstmt.setLong(1, eventId);
            rs = pstmt.executeQuery();

            String seriesName;
            Double timeOffset, value;
            Waveform wf = null;
            while (rs.next()) {
                seriesName = rs.getString("series_name");
                if (wf == null) {
                    // FIrst run through the loop.  Create a waveform to hold data.
                    wf = new Waveform(seriesName);
                } else if (!wf.getSeriesName().equals(seriesName)) {
                    // Since the query is sorted on series_name, if the name changes we're done with current waveform.  Add it to the list
                    // and make a new waveform object for the next set of data.
                    waveforms.add(wf);
                    wf = new Waveform(seriesName);
                }
                // Now the we have managed the waveform "lifecycle" events, we only need to add more data points to the current
                // waveform
                timeOffset = rs.getDouble("time_offset");
                value = rs.getDouble("val");
                wf.addPoint(timeOffset, value);
            }

            // Add the last waveform
            waveforms.add(wf);
            event = new Event(eventId, eventTime, location, system, archive, waveforms);

        } finally {
            SqlUtil.close(pstmt, conn, rs);
        }

        return event;
    }

    public int deleteEvent(long eventId) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        int rowsAffected;
        String deleteSql = "DELETE FROM waveforms.event WHERE event_id = ?";

        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(deleteSql);
            pstmt.setLong(1, eventId);
            rowsAffected = pstmt.executeUpdate();
        } finally {
            SqlUtil.close(pstmt, conn);
        }

        return rowsAffected;
    }

    public int setEventArchiveFlag(long eventId, boolean archive) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        int rowsAffected;
        String updateSql = "UPDATE waveforms.event SET archive = ? WHERE event_id = ?";

        try {
            conn = SqlUtil.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(updateSql);
            pstmt.setLong(1, archive ? 1 : 0);
            pstmt.setLong(2, eventId);
            rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 1) {
                conn.rollback();
                throw new RuntimeException("Updating event archive flag affected more than one row.");
            }
            conn.commit();
        } finally {
            SqlUtil.close(pstmt, conn);
        }

        return rowsAffected;
    }
}
