package org.jlab.wfbrowser.business.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.util.SqlUtil;
import org.jlab.wfbrowser.business.util.TimeUtil;
import org.jlab.wfbrowser.model.CaptureFile.CaptureFile;
import org.jlab.wfbrowser.model.CaptureFile.Metadata;
import org.jlab.wfbrowser.model.CaptureFile.MetadataType;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.Waveform;

/**
 *
 * @author adamc
 */
public class EventService {

    private static final Logger LOGGER = Logger.getLogger(EventService.class.getName());

    /**
     * Adds an event's meta data to the database. Verify that an event directory
     * exists in the proper location on the filesystem prior to updating
     * database.
     *
     * @param e The Event to add to the database
     * @return The eventId of the new entry in the database corresponding to the
     * row in the event table.
     * @throws FileNotFoundException
     * @throws SQLException
     */
    public long addEvent(Event e) throws FileNotFoundException, SQLException, IOException {
        if (!e.isDataOnDisk()) {
            throw new FileNotFoundException("Cannot add event to database if data is missing from disk.  Directory '"
                    + e.getEventDirectoryPath().toString() + "' or '" + e.getArchivePath().toString() + "' not found.");
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        String systemIdSql = "SELECT system_id,count(*) "
                + "FROM system_type"
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

            String insertEventSql = "INSERT INTO event (event_time_utc, location, system_id, archive, to_be_deleted, grouped, classification) VALUES (?, ?, ?, ?, ?, ?, ?)";

            pstmt = conn.prepareStatement(insertEventSql, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, e.getEventTimeString());
            pstmt.setString(2, e.getLocation());
            pstmt.setInt(3, systemId);
            pstmt.setInt(4, e.isArchive() ? 1 : 0);
            pstmt.setInt(5, e.isDelete() ? 1 : 0);
            pstmt.setInt(6, e.isGrouped() ? 1 : 0);
            pstmt.setString(7, e.getClassification());

            int n = pstmt.executeUpdate();
            if (n != 1) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Inserting new event did not update exactly one row in the database");
                throw new SQLException("Inserting new event did not update exactly one row in the database");
            }

            ResultSet rse = pstmt.getGeneratedKeys();
            if (rse != null && rse.next()) {
                eventId = rse.getLong(1);
            } else {
                conn.rollback();
                throw new RuntimeException("Error querying database for last inserted event_id");
            }
            pstmt.close();

            // Make sure we the event has capture files to add
            Map<String, CaptureFile> captureFileMap = e.getCaptureFileMap();
            if (captureFileMap == null || captureFileMap.isEmpty()) {
                conn.rollback();
                throw new RuntimeException("Attempting to add event with no associated capture files");
            }

            // Add the capture files to the database.  For each capture file, we need to add the list of waveforms associated with it.
            String captureSql = "INSERT INTO capture (event_id, filename, sample_start, sample_end, sample_step)"
                    + " VALUES(?,?,?,?,?)";
            for (String filename : captureFileMap.keySet()) {
                pstmt = conn.prepareStatement(captureSql, Statement.RETURN_GENERATED_KEYS);
                CaptureFile cf = captureFileMap.get(filename);
                pstmt.setLong(1, eventId);
                pstmt.setString(2, filename);
                pstmt.setDouble(3, cf.getSampleStart());
                pstmt.setDouble(4, cf.getSampleEnd());
                pstmt.setDouble(5, cf.getSampleStep());
                int numUpdated = pstmt.executeUpdate();
                if (numUpdated != 1) {
                    conn.rollback();
                    throw new SQLException("Error adding capture file to database");
                }

                // Get the capture_id of the capture file we just added to the database
                long captureId;
                rse = pstmt.getGeneratedKeys();
                if (rse != null && rse.next()) {
                    captureId = rse.getLong(1);
                } else {
                    conn.rollback();
                    throw new RuntimeException("Error querying database for last inserted event_id");
                }
                pstmt.close();

                List<Waveform> waveformList = cf.getWaveforms();
                if (waveformList != null) {
                    String waveformSql = "INSERT INTO capture_wf (capture_id, waveform_name) VALUES(?,?)";
                    pstmt = conn.prepareStatement(waveformSql);
                    for (Waveform w : waveformList) {
                        pstmt.setLong(1, captureId);
                        pstmt.setString(2, w.getWaveformName());
                        numUpdated = pstmt.executeUpdate();
                        if (numUpdated != 1) {
                            conn.rollback();
                            throw new SQLException("Error adding waveform metadata to database.");
                        }
                        pstmt.clearParameters();
                    }
                }
                pstmt.close();

                List<Metadata> metadataList = cf.getMetadataList();
                if (metadataList != null) {
                    String metaSql = "INSERT INTO capture_meta (capture_id, meta_name, type, value, start, offset)"
                            + " VALUES(?,?,?,?,?,?)";
                    pstmt = conn.prepareStatement(metaSql);
                    for (Metadata m : metadataList) {
                        pstmt.setLong(1, captureId);
                        pstmt.setString(2, m.getName());
                        pstmt.setString(3, m.getType().toString());
                        switch (m.getType()) {
                            case NUMBER:
                                pstmt.setString(4, ((Double) m.getValue()).toString());
                                pstmt.setDouble(5, m.getStart());
                                pstmt.setDouble(6, m.getOffset());
                                break;
                            case STRING:
                                pstmt.setString(4, (String) m.getValue());
                                pstmt.setDouble(5, m.getStart());
                                pstmt.setDouble(6, m.getOffset());
                                break;
                            case UNAVAILABLE:
                                pstmt.setString(4, null);
                                pstmt.setNull(5, java.sql.Types.NULL);
                                pstmt.setDouble(6, m.getOffset());
                                break;
                            case UNARCHIVED:
                                pstmt.setString(4, null);
                                pstmt.setNull(5, java.sql.Types.NULL);
                                pstmt.setNull(6, java.sql.Types.NULL);
                                break;
                            default:
                                throw new RuntimeException("Unrecognized MetadataType - " + m.getType().toString());
                        }
                        numUpdated = pstmt.executeUpdate();
                        if (numUpdated != 1) {
                            conn.rollback();
                            throw new SQLException("Error adding capture file metadata to database.");
                        }
                        pstmt.clearParameters();
                    }
                }
            }
            conn.commit();
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }
        return eventId;
    }

    /**
     * Get the most recent event ID in the database given the applied filter
     *
     * @param filter
     * @return
     * @throws SQLException
     */
    public Long getMostRecentEventId(EventFilter filter) throws SQLException {
        Long out = null;
        String sql = "SELECT event_id FROM event JOIN system_type ON event.system_id = system_type.system_id";
        if (filter != null) {
            sql += filter.getWhereClause();
        }
        sql += " ORDER BY event_time_utc DESC LIMIT 1";

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            if (filter != null) {
                filter.assignParameterValues(pstmt);
            }
            rs = pstmt.executeQuery();
            if (rs.next()) {
                out = rs.getLong("event_id");
            }
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }
        return out;
    }

    /**
     * Get the most recent event in the database given the applied filter.
     * Includes data
     *
     * @param filter
     * @return
     * @throws SQLException
     * @throws java.io.IOException
     */
    public Event getMostRecentEvent(EventFilter filter) throws SQLException, IOException {
        List<Event> eventList = getEventList(filter, 1l, true);
        Event out = null;
        if (!eventList.isEmpty()) {
            out = eventList.get(0);
        }
        return out;
    }

    /**
     * Query the database for the List of unique location names
     *
     * @param systemList A list of systems to filter on. Null or empty list
     * means do no filtering
     * @return A list of the unique location names
     * @throws SQLException
     */
    public List<String> getLocationNames(List<String> systemList) throws SQLException {
        List<String> out = new ArrayList<>();
        String sql = "SELECT DISTINCT location"
                + " FROM event"
                + " JOIN system_type ON event.system_id = system_type.system_id";
        if (systemList != null && !systemList.isEmpty()) {
            sql += " WHERE system_name IN (?";
            for (int i = 1; i < systemList.size(); i++) {
                sql += ",?";
            }
            sql += ")";
        }
        sql += " ORDER BY location";

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            if (systemList != null && !systemList.isEmpty()) {
                for (int i = 1; i <= systemList.size(); i++) {
                    pstmt.setString(i, systemList.get(i - 1));
                }
            }

            rs = pstmt.executeQuery();
            while (rs.next()) {
                out.add(rs.getString("location"));
            }
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }

        return out;
    }

    // TODO: Add a test routine
    /**
     * Get a list of classifications associated with a system or list of systems
     * @param systemList A list of system names
     * @return A list of classifications
     * @throws SQLException 
     */
    public List<String> getClassifications(List<String> systemList) throws SQLException {
        List<String> out = new ArrayList<>();
        String sql = "SELECT DISTINCT classification"
                + " FROM event"
                + " JOIN system_type ON event.system_id = system_type.system_id";
        if (systemList != null && !systemList.isEmpty()) {
            sql += " WHERE system_name IN (?";
            for (int i = 1; i < systemList.size(); i++) {
                sql += ",?";
            }
            sql += ")";
        }
        sql += " ORDER BY classification";

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            if (systemList != null && !systemList.isEmpty()) {
                for (int i = 1; i <= systemList.size(); i++) {
                    pstmt.setString(i, systemList.get(i - 1));
                }
            }

            rs = pstmt.executeQuery();
            while (rs.next()) {
                out.add(rs.getString("classification"));
            }
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }

        return out;
    }

    /**
     * This method returns a List of named series that were recorded for the
     * specified List of events
     *
     * @param eventIdList A list of event IDs for which we want the recorded
     * named series
     * @return A list of the names of the series that are available for the
     * specified events
     * @throws SQLException
     */
    public List<Series> getSeries(List<Long> eventIdList) throws SQLException {
        List<Series> out = new ArrayList<>();
        if (eventIdList == null || eventIdList.isEmpty()) {
            return out;
        }

        // This is a little complex.  Perform a subquery / derived table on the event IDs to cut down on the amount of data we process.
        // Then join on the series and event_waveforms tables where the waveform name (PV) matches the specified pattern.  This should
        // only return rowns where we had a non-zero number of matches.
        String sql = "SELECT series_name, series_id, system_name, pattern, description, units, COUNT(*) FROM"
                + " (SELECT * FROM event_waveforms WHERE event_id IN (?";
        for (int i = 1; i < eventIdList.size(); i++) {
            sql += ",?";
        }
        sql += ")) derived_table"
                + " JOIN series ON derived_table.waveform_name LIKE series.pattern"
                + " JOIN system_type ON series.system_id = system_type.system_id"
                + " GROUP BY series_name"
                + " ORDER BY series_name ";

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            for (int i = 1; i <= eventIdList.size(); i++) {
                // prepared statement parameters are 1-indexed, but lists are 0-indexed
                pstmt.setLong(i, eventIdList.get(i - 1));
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String seriesName = rs.getString("series_name");
                int id = rs.getInt("series_id");
                String pattern = rs.getString("pattern");
                String systemName = rs.getString("system_name");
                String description = rs.getString("description");
                String units = rs.getString("units");
                out.add(new Series(seriesName, id, pattern, systemName, description, units));
            }
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }

        return out;
    }

    /**
     * Returns the event object mapping to the event records with eventId from
     * the database. Simple wrapper that has no limit and includes data.
     *
     * @param filter
     * @return
     * @throws SQLException
     * @throws java.io.IOException
     */
    public List<Event> getEventList(EventFilter filter) throws SQLException, IOException {
        return getEventList(filter, null, true);
    }

    /**
     * Returns a map of waveform names to common series names for a given event.
     *
     * @param eventId
     * @return
     * @throws java.sql.SQLException
     */
    public Map<String, String> getWaveformToSeriesMap(long eventId) throws SQLException {
        Map<String, String> waveformToSeries = new HashMap<>();
        String sql = "SELECT waveform_name, series_name"
                + " FROM event_waveforms"
                + " JOIN series ON event_waveform_name LIKE series.pattern"
                + " WHERE event_id = ?";

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            pstmt.setLong(1, eventId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String waveformName = rs.getString("waveform_name");
                String seriesName = rs.getString("series_name");
                waveformToSeries.put(waveformName, seriesName);
            }
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }
        return waveformToSeries;
    }

    /**
     * Returns the event object mapping to the event records with eventId from
     * the database.
     *
     * @param filter
     * @param limit How many events to return. Null for unlimited
     * @param includeData Whether the events should include waveform data read
     * from disk
     * @return
     * @throws SQLException
     * @throws java.io.IOException
     */
    public List<Event> getEventList(EventFilter filter, Long limit, boolean includeData) throws SQLException, IOException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        List<Event> eventList = new ArrayList<>();
        Long eventId;
        Instant eventTime;
        String location, system, classification;
        Boolean archive, delete, grouped;

        try {
            conn = SqlUtil.getConnection();

            String getEventSql = "SELECT event_id,event_time_utc,location,system_type.system_name,archive,to_be_deleted,grouped,classification"
                    + " FROM event"
                    + " JOIN system_type USING(system_id) ";
            if (filter != null) {
                getEventSql += filter.getWhereClause();
            }
            getEventSql += " ORDER BY event_time_utc DESC";
            if (limit != null) {
                getEventSql += " LIMIT " + limit;
            }
            pstmt = conn.prepareStatement(getEventSql);

            if (filter != null) {
                filter.assignParameterValues(pstmt);
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                eventId = rs.getLong("event_id");
                eventTime = TimeUtil.getInstantFromDateTime(rs);
                location = rs.getString("location");
                system = rs.getString("system_name");
                archive = rs.getBoolean("archive");
                delete = rs.getBoolean("to_be_deleted");
                grouped = rs.getBoolean("grouped");
                classification = rs.getString("classification");

                if (eventId == null || location == null || system == null || archive == null || grouped == null) {
                    // All of these should have NOT NULL constraints on them.  Verify that something hasn't gone wrong
                    throw new SQLException("Error querying event information from database");
                } else {
                    eventList.add(new Event(eventId, eventTime, location, system, archive, delete, grouped, classification));
                }
            }
            rs.close();
            pstmt.close();

            // For each event, go through the database and load up the capture file information.  We''ll come back and do a
            // second pass to add the waveform data to the CaptureFiles.
            String captureSql = "SELECT capture_id, filename, sample_start, sample_end, sample_step "
                    + " FROM capture"
                    + " WHERE event_id = ?";
            pstmt = conn.prepareStatement(captureSql, Statement.RETURN_GENERATED_KEYS);
            for (Event e : eventList) {
                pstmt.setLong(1, e.getEventId());
                rs = pstmt.executeQuery();
                long captureId;
                String filename;
                double sampleStart, sampleEnd, sampleStep;
                while (rs.next()) {
                    captureId = rs.getLong("capture_id");
                    filename = rs.getString("filename");
                    sampleStart = rs.getDouble("sample_start");
                    sampleEnd = rs.getDouble("sample_end");
                    sampleStep = rs.getDouble("sample_step");
                    e.addCaptureFile(new CaptureFile(captureId, filename, sampleStart, sampleEnd, sampleStep));
                }
                rs.close();
            }
            pstmt.close();

            // For each event, get the recently constructed CaptureFiles, then add waveforms without data to them.  We'll add data later if it was requested.
            String waveformSql = "SELECT cwf_id, waveform_name FROM capture_wf WHERE capture_id = ?";
            pstmt = conn.prepareStatement(waveformSql);
            for (Event e : eventList) {
                String waveformName;
                Long cwfId;
                for (CaptureFile cf : e.getCaptureFileList()) {
                    pstmt.setLong(1, cf.getCaptureId());
                    rs = pstmt.executeQuery();
                    while (rs.next()) {
                        cwfId = rs.getLong("cwf_id");
                        waveformName = rs.getString("waveform_name");
                        e.addWaveform(cf.getFilename(), new Waveform(cwfId, waveformName));
                    }
                    rs.close();
                }
            }
            pstmt.close();

            // Load up the capture file metadata
            String metaSql = "SELECT meta_id, meta_name, type, value, start, offset FROM capture_meta WHERE capture_id = ?";
            pstmt = conn.prepareStatement(metaSql);
            for (Event e : eventList) {
                Long metaId;
                String metaName;
                Object value;
                Double start, offset;
                MetadataType type;
                for (CaptureFile cf : e.getCaptureFileList()) {
                    pstmt.setLong(1, cf.getCaptureId());
                    rs = pstmt.executeQuery();
                    while (rs.next()) {
                        metaId = rs.getLong("meta_id");
                        metaName = rs.getString("meta_name");
                        type = MetadataType.valueOf(rs.getString("type"));
                        switch (type) {
                            case NUMBER:
                                value = Double.valueOf(rs.getString("value"));
                                break;
                            case STRING:
                                value = rs.getString("value");
                                break;
                            case UNAVAILABLE:
                                value = rs.getString("value"); // Should be null
                                break;
                            case UNARCHIVED:
                                value = rs.getString("value"); // Should be null
                                break;
                            default:
                                throw new SQLException("Error getting capture file metadata from database- unexpected MetadataType");
                        }
                        start = rs.getDouble("start");
                        offset = rs.getDouble("offset");
                        Metadata m = new Metadata(type, metaName, value, offset, start);
                        m.setId(metaId);
                        cf.addMetadata(m);
                    }
                }
            }

            // Determine the rules for labeling waveform series (GMES vs DETA2, not Cav1, Cav2, ...)
            String mapSql = "SELECT series_name, series_id, pattern, system_type.system_name, description, units, waveform_name "
                    + " FROM capture_wf"
                    + " JOIN series ON waveform_name LIKE series.pattern"
                    + " JOIN system_type ON series.system_id = system_type.system_id"
                    + " JOIN capture ON capture.capture_id = capture_wf.capture_id"
                    + " WHERE event_id = ?"
                    + " GROUP BY waveform_name"
                    + " ORDER BY waveform_name";
            pstmt = conn.prepareStatement(mapSql);
            // Get the waveform data for each event's CaptureFile, then figure out the waveform to series mapping and apply it.
            for (Event e : eventList) {
                // Get the mapping
                Map<String, List<Series>> waveformToSeries = new HashMap<>();
                pstmt.setLong(1, e.getEventId());
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    String waveformName = rs.getString("waveform_name");
                    String seriesName = rs.getString("series_name");
                    int seriesId = rs.getInt("series_id");
                    String pattern = rs.getString("pattern");
                    String systemName = rs.getString("system_name");
                    String description = rs.getString("description");
                    String units = rs.getString("units");
                    if (waveformToSeries.get(waveformName) == null) {
                        waveformToSeries.put(waveformName, new ArrayList<Series>());
                    }
                    waveformToSeries.get(waveformName).add(new Series(seriesName, seriesId, pattern, systemName, description, units));
                }
                rs.close();

                // Have the event apply the serires mapping
                e.applySeriesMapping(waveformToSeries);
            }
            pstmt.close();

            // Now get the data if requested
            if (includeData) {
                for (Event e : eventList) {
                    e.loadWaveformDataFromDisk();
                }
            }
        } finally {
            SqlUtil.close(pstmt, conn, rs);
        }

        return eventList;
    }

    /**
     * Updates the to_be_deleted flag on the specified event in the waveform
     * database
     *
     * @param eventId
     * @param delete The logical value of the to_be_deleted database flag
     * @return The number of rows affected. Should only ever be one since
     * eventId should be the primary key.
     * @throws SQLException
     */
    public int setEventDeleteFlag(long eventId, boolean delete) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        int rowsAffected;
        String deleteSql = "UPDATE event SET to_be_deleted = ? WHERE event_id = ?";

        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(deleteSql);
            pstmt.setInt(1, delete ? 1 : 0);
            pstmt.setLong(2, eventId);
            rowsAffected = pstmt.executeUpdate();
        } finally {
            SqlUtil.close(pstmt, conn);
        }

        return rowsAffected;
    }

    /**
     * This method deletes an entry from the waveforms events table. By default
     * it only searches for events that have the to_be_deleted flag set, but
     * there is an optional force setting that just searches for the event_id.
     *
     * @param eventId
     * @param force Delete the event even if the to_be_deleted flag is not set
     * @return
     * @throws SQLException
     */
    public int deleteEvent(long eventId, boolean force) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        int rowsAffected;
        String deleteSql = "DELETE FROM event WHERE to_be_deleted = 1 AND event_id = ?";
        if (force) {
            deleteSql = "DELETE FROM event WHERE event_id = ?";
        }

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

    /**
     * Set the archive flag on an event in the database.
     *
     * @param eventId The id of the event that is to be modified
     * @param archive The value of the archive flag (true is set, false is
     * unset)
     * @return The number of rows affected (should always be 1/0)
     * @throws SQLException
     */
    public int setEventArchiveFlag(long eventId, boolean archive) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        int rowsAffected;
        String updateSql = "UPDATE event SET archive = ? WHERE event_id = ?";

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

    /**
     * Add a list of events to the database.
     *
     * @param eventList
     * @return
     * @throws SQLException
     * @throws java.io.FileNotFoundException
     */
    public int addEventList(List<Event> eventList) throws SQLException, FileNotFoundException, IOException {
        long eventId;
        int numAdded = 0;
        for (Event e : eventList) {
            if (e != null) {
                // eventId is an autoincremented primary key starting at 1.  It should never be < 0
                eventId = addEvent(e);
                numAdded++;
            }
        }
        return numAdded;
    }

    /**
     * Set the to_be_deleted flag on the specified events in the database.
     *
     * @param eventIds
     * @param delete Logical value of the to_be_deleted flag set in the database
     * @return The number of affected events in the database
     * @throws SQLException
     */
    public int setEventDeleteFlag(List<Long> eventIds, boolean delete) throws SQLException {
        int numDeleted = 0;
        if (eventIds != null) {
            for (Long eventId : eventIds) {
                if (eventId != null) {
                    numDeleted += setEventDeleteFlag(eventId, delete);
                }
            }
        }
        return numDeleted;
    }

    /**
     * Get a list of events from the database matching the specified filter.
     * Useful for querying what events exist without the overhead of
     * transferring all of the actual waveform data around. Only includes high
     * level information about the event which can be useful for determining
     * timelines, etc.
     *
     * @param filter
     * @return A list of events
     * @throws SQLException
     * @throws java.io.IOException
     */
    public List<Event> getEventListWithoutCaptureFiles(EventFilter filter) throws SQLException, IOException {
        List<Event> events = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        String sql = "SELECT event_id,event_time_utc,location,archive,to_be_deleted,system_type.system_name,classification,grouped"
                + " FROM event"
                + " JOIN system_type USING(system_id)";
        try {
            conn = SqlUtil.getConnection();
            if (filter != null) {
                sql += " " + filter.getWhereClause();
            }
            pstmt = conn.prepareStatement(sql);
            if (filter != null) {
                filter.assignParameterValues(pstmt);
            }
            rs = pstmt.executeQuery();
            long eventId;
            Instant eventTime;
            String location, system, classification;
            Boolean archive, delete, grouped;
            while (rs.next()) {
                eventId = rs.getLong("event_id");
                eventTime = TimeUtil.getInstantFromDateTime(rs);
                system = rs.getString("system_name");
                location = rs.getString("location");
                archive = rs.getBoolean("archive");
                delete = rs.getBoolean("to_be_deleted");
                grouped = rs.getBoolean("grouped");
                classification = rs.getString("classification");
                // false for includeData, false for ignoreErrors
                events.add(new Event(eventId, eventTime, location, system, archive, delete, grouped, classification));
            }
            rs.close();
            pstmt.close();
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }

        return events;
    }
}
