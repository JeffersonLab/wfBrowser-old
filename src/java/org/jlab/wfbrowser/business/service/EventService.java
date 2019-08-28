package org.jlab.wfbrowser.business.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.*;
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
import org.jlab.wfbrowser.model.Label;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.Waveform;

/**
 * A class for querying Events from the database.
 *
 * @author adamc
 */
public class EventService {
    // Note: Suppressing SqlUnused IDE warnings for this class the filter classes hide possible usages.

    private static final Logger LOGGER = Logger.getLogger(EventService.class.getName());

    /**
     * Adds an event's meta data to the database. Verify that an event directory
     * exists in the proper location on the filesystem prior to updating
     * database.
     *
     * @param e The Event to add to the database
     * @return The eventId of the new entry in the database corresponding to the
     * row in the event table.
     * @throws IOException If problems arise while checking or accessing data on disk
     * @throws SQLException If problems arise while accessing data from database
     */
    public long addEvent(Event e) throws SQLException, IOException {
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

            String insertEventSql = "INSERT INTO event " +
                    "(event_time_utc, location, system_id, archive, to_be_deleted, grouped, classification) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

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

            // Insert label information
            if (e.getLabel() != null) {
                Label l = e.getLabel();
                String labelSql = "INSERT INTO label " +
                        "(event_id, model_name, cavity_label, cavity_confidence, fault_label, fault_confidence) " +
                        "VALUES(?, ?, ?, ?, ?, ?)";
                pstmt = conn.prepareStatement(labelSql);
                pstmt.setLong(1, eventId);
                pstmt.setString(2, l.getModelName());
                pstmt.setString(3,l.getCavityLabel());
                pstmt.setDouble(4,l.getCavityConfidence());
                pstmt.setString(5, l.getFaultLabel());
                pstmt.setDouble(6,l.getFaultConfidence());

                // Insert the label record and rollback if we didn't add one row.
                n = pstmt.executeUpdate();
                if (n != 1) {
                    conn.rollback();
                    LOGGER.log(Level.SEVERE, "Error inserting label information.  Rolling back.");
                    throw new SQLException("Error inserting label information.  Rolling back.");
                }
            }

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
                                pstmt.setString(4, m.getValue().toString());
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
     * @param filter An EventFilter parameter providing a finer selection of the data returned
     * @return The most recent event ID that matches the filter requirements
     * @throws SQLException If problems arise while accessing data in database
     */
    public Long getMostRecentEventId(EventFilter filter) throws SQLException {
        Long out = null;
        String sql = "SELECT event_id FROM ("
                + " SELECT *, count(*) AS num_cf FROM event"
                + " JOIN system_type ON event.system_id = system_type.system_id"
                + " JOIN capture ON event.event_id = capture.event_id"
                + " GROUP BY event_id"
                + " ) AS t";
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
     * @param filter An event filter for narrowing down the acceptable Event responses
     * @return The most recent event passing the filter
     * @throws SQLException If problems arise accessing database
     * @throws IOException If problems arise accessing waveform data on disk
     */
    public Event getMostRecentEvent(EventFilter filter) throws SQLException, IOException {
        List<Event> eventList = getEventList(filter, 1L, true);
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
     * @throws SQLException If problems arise accessing data on disk
     */
    public List<String> getLocationNames(List<String> systemList) throws SQLException {
        List<String> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT location"
                + " FROM event"
                + " JOIN system_type ON event.system_id = system_type.system_id");
        if (systemList != null && !systemList.isEmpty()) {
            sql.append(" WHERE system_name IN (?");
            for (int i = 1; i < systemList.size(); i++) {
                sql.append(",?");
            }
            sql.append(")");
        }
        sql.append(" ORDER BY location");

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql.toString());
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
     * @throws SQLException If problems arise accessing the database
     */
    public List<String> getClassifications(List<String> systemList) throws SQLException {
        List<String> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT classification"
                + " FROM event"
                + " JOIN system_type ON event.system_id = system_type.system_id");
        if (systemList != null && !systemList.isEmpty()) {
            sql.append(" WHERE system_name IN (?");
            for (int i = 1; i < systemList.size(); i++) {
                sql.append(",?");
            }
            sql.append(")");
        }
        sql.append(" ORDER BY classification");

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql.toString());
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
     * @throws SQLException If problems arise while accessing the database
     */
    public List<Series> getSeries(List<Long> eventIdList) throws SQLException {
        List<Series> out = new ArrayList<>();
        if (eventIdList == null || eventIdList.isEmpty()) {
            return out;
        }

        // This is a little complex.  Perform a subquery / derived table on the event IDs to cut down on the amount of data we process.
        // Then join on the series and event_waveforms tables where the waveform name (PV) matches the specified pattern.  This should
        // only return rowns where we had a non-zero number of matches.
        StringBuilder sql = new StringBuilder("SELECT series_name, series_id, system_name, pattern, description, units, COUNT(*) FROM"
                + " (SELECT * FROM event_waveforms WHERE event_id IN (?");
        for (int i = 1; i < eventIdList.size(); i++) {
            sql.append(",?");
        }
        sql.append(")) derived_table"
                + " JOIN series ON derived_table.waveform_name LIKE series.pattern"
                + " JOIN system_type ON series.system_id = system_type.system_id"
                + " GROUP BY series_name"
                + " ORDER BY series_name ");

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql.toString());
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
     * @param filter EventFilter for narrowing down the acceptable rannge of Events
     * @return The List of Events that meet the filter requirements
     * @throws SQLException If problems arise while accessing the database
     * @throws IOException If problems arise while accessing data on disk
     */
    public List<Event> getEventList(EventFilter filter) throws SQLException, IOException {
        return getEventList(filter, null, true);
    }

    /**
     * Returns a map of waveform names to common series names for a given event.
     *
     * @param eventId The ID of the event for which to return the waveform to series mapping.
     * @return A map of waveform names to their corresponding series names
     * @throws SQLException If trouble arises while accessing the database
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
     * @param filter EventFilter for narrowing down which Events are returned
     * @param limit How many events to return. Null for unlimited
     * @param includeData Whether the events should include waveform data read
     * from disk
     * @return The list of Events that match the filter criteria ordered by event time.
     * @throws SQLException If problems arise accessing the database
     * @throws IOException If problems arise accessing waveform data on disk
     */
    public List<Event> getEventList(EventFilter filter, Long limit, boolean includeData) throws SQLException, IOException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        List<Event> eventList = new ArrayList<>();
        long eventId;
        Instant eventTime, labelTime;
        String location, system, classification, cavityLabel, faultLabel, modelName;
        Double cavityConfidence, faultConfidence;
        boolean archive, delete, grouped;
        Long labelId;

        try {
            conn = SqlUtil.getConnection();

            String getEventSql = "SELECT event_id,event_time_utc,location,system_name,archive,to_be_deleted,grouped,classification," +
                    "label_id,model_name,label_time_utc,cavity_label,cavity_confidence,fault_label,fault_confidence"
                    + " FROM (SELECT *, count(*) AS num_cf FROM event"
                    + "   JOIN system_type USING(system_id)"
                    + "   JOIN capture USING(event_id)"
                    + "   LEFT JOIN label USING(event_id)"
                    + "   GROUP BY event_id"
                    + " ) AS t ";
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
                eventTime = TimeUtil.getInstantFromSQLDateTime(rs, "event_time_utc");
                location = rs.getString("location");
                system = rs.getString("system_name");
                archive = rs.getBoolean("archive");
                delete = rs.getBoolean("to_be_deleted");
                grouped = rs.getBoolean("grouped");
                classification = rs.getString("classification");
                labelId = rs.getLong("label_id");
                modelName = rs.getString("model_name");
                labelTime = TimeUtil.getInstantFromSQLDateTime(rs,"label_time_utc");
                cavityLabel = rs.getString("cavity_label");
                cavityConfidence = rs.getDouble("cavity_confidence");
                faultLabel = rs.getString("fault_label");
                faultConfidence = rs.getDouble("fault_confidence");

                if ( location == null || system == null ) {
                    // All of these should have NOT NULL constraints on them.  Verify that something hasn't gone wrong
                    throw new SQLException("Error querying event information from database");
                } else {

                    // An event may or may not have an associated label.  If it does, there will be a label ID.  If not,
                    // the LEFT JOIN will return null for all of the label table fields.  IDE warnings are wrong here.
                    Label label = null;
                    if (labelId != null) {
                        label = new Label(labelId, labelTime, modelName, cavityLabel, faultLabel,
                                cavityConfidence, faultConfidence);
                    }
                    // Add the event to the list
                    eventList.add(new Event(eventId, eventTime, location, system, archive, delete, grouped,
                            classification, label));
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
                            case UNAVAILABLE:
                            case UNARCHIVED:
                                // Should be null
                                value = rs.getString("value");
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
                        waveformToSeries.put(waveformName, new ArrayList<>());
                    }
                    waveformToSeries.get(waveformName).add(new Series(seriesName, seriesId, pattern, systemName, description, units));
                }
                rs.close();

                // Have the event apply the series mapping
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
     * @param eventId ID of the event to modify
     * @param delete The logical value of the to_be_deleted database flag
     * @return The number of rows affected. Should only ever be one since
     * eventId should be the primary key.
     * @throws SQLException If problems arise while accessing the database
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
     * @param eventId The ID of the event to be deleted
     * @param force Delete the event even if the to_be_deleted flag is not set
     * @return The number of rows modified
     * @throws SQLException If problems arise while accessing the database
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

    public void setEventLabel(long eventId, Label label, boolean force) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        String deleteSql = "DELETE FROM label WHERE event_id = ?";

        String insertSql = "INSERT INTO label " +
                "(event_id, model_name, cavity_label, cavity_confidence, fault_label, fault_confidence) " +
                "VALUES(?, ?, ?, ?, ?, ?)";

        try {
            conn = SqlUtil.getConnection();

            // If we may delete the older data, make sure we can roll back if the new label fails to insert.
            if (force) {
                conn.setAutoCommit(false);
            }

            // Delete the existing label for the given ID
            if (force) {
                pstmt = conn.prepareStatement(deleteSql);
                pstmt.setLong(1, eventId);
                pstmt.executeUpdate();
                pstmt.close();
            }

            // Add the new label
            try {
                pstmt = conn.prepareStatement(insertSql);
                pstmt.setLong(1, eventId);
                pstmt.setString(2, label.getModelName());
                pstmt.setString(3, label.getCavityLabel());
                pstmt.setDouble(4, label.getCavityConfidence());
                pstmt.setString(5, label.getFaultLabel());
                pstmt.setDouble(6, label.getFaultConfidence());
                pstmt.executeUpdate();
            } catch (SQLException ex) {
                // If the insert failed, then rollback.  If nothing was deleted, then there is no harm, and if something
                // was deleted earlier, we get it back.
                if (force) {
                    conn.rollback();
                }
                throw ex;
            }

            // Commit the updates since we are not in autocomit mode.
            if (force) {
                conn.commit();
            }
        } finally {
            SqlUtil.close(pstmt,conn);
        }
    }

    /**
     * Set the archive flag on an event in the database.
     *
     * @param eventId The id of the event that is to be modified
     * @param archive The value of the archive flag (true is set, false is
     * unset)
     * @return The number of rows affected (should always be 1/0)
     * @throws SQLException If problems arise while accessing the database
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
     * @param eventList A list of Events to be added to the database
     * @return The number of events added to the database
     * @throws SQLException If problems arise while accessing the database
     * @throws IOException If problems arise while accessing the waveform data on disk
     */
    public int addEventList(List<Event> eventList) throws SQLException, IOException {
        int numAdded = 0;
        for (Event e : eventList) {
            if (e != null) {
                // eventId is an auto-incremented primary key starting at 1.  It should never be < 0
                addEvent(e);
                numAdded++;
            }
        }
        return numAdded;
    }

    /**
     * Set the to_be_deleted flag on the specified events in the database.
     *
     * @param eventIds List of IDs of events to modify
     * @param delete Logical value of the to_be_deleted flag set in the database
     * @return The number of affected events in the database
     * @throws SQLException If problems arise while accessing the database
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
     * Method for deleting the label associated an event
     * @param eventId The database ID of the event to delete
     * @return The number of rows updated
     * @throws SQLException If a problem arises while accessing the database
     */
    public int deleteEventLabel(long eventId) throws SQLException {
        String sql = "DELETE FROM label where event_id = ?";

        Connection conn = null;
        PreparedStatement pstmt = null;
        int n;
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            pstmt.setLong(1, eventId);
            n = pstmt.executeUpdate();
        } finally {
            SqlUtil.close(conn, pstmt);
        }

        return n;
    }

    /**
     * Get a list of events from the database matching the specified filter.
     * Useful for querying what events exist without the overhead of
     * transferring all of the actual waveform data around. Only includes high
     * level information about the event which can be useful for determining
     * timelines, etc.
     *
     * @param filter EventFilter for narrowing down the which events are returned
     * @return A list of events that do not contain waveform data
     * @throws SQLException If problems arise while accessing the database
     * @throws IOException If problems arise while accessing waveform data from disk
     */
    public List<Event> getEventListWithoutCaptureFiles(EventFilter filter) throws SQLException, IOException {
        List<Event> events = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        String sql = "SELECT event_id,event_time_utc,location,archive,to_be_deleted,system_name,classification,grouped," +
                "label_id,model_name,label_time_utc,cavity_label,cavity_confidence,fault_label,fault_confidence"
                + " FROM (SELECT *, COUNT(*) as num_cf"
                + " FROM event"
                + " JOIN system_type USING(system_id)"
                + " JOIN capture USING(event_id)"
                + " LEFT JOIN label USING(event_id)"
                + " GROUP BY event_id"
                + " ) AS t ";
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
            Instant eventTime, labelTime;
            String location, system, classification, modelName, cavityLabel, faultLabel;
            boolean archive, delete, grouped;
            Long labelId;
            Double cavityConfidence, faultConfidence;
            while (rs.next()) {
                eventId = rs.getLong("event_id");
                eventTime = TimeUtil.getInstantFromSQLDateTime(rs, "event_time_utc");
                system = rs.getString("system_name");
                location = rs.getString("location");
                archive = rs.getBoolean("archive");
                delete = rs.getBoolean("to_be_deleted");
                grouped = rs.getBoolean("grouped");
                classification = rs.getString("classification");
                labelId = rs.getLong("label_id");

                // The Java SQL API tries to hide SQL nulls.  We key some behavior on this field, so make sure we
                // get a null value if one was there.
                if (rs.wasNull()) {
                    labelId = null;
                }
                modelName = rs.getString("model_name");
                labelTime = TimeUtil.getInstantFromSQLDateTime(rs,"label_time_utc");
                cavityLabel = rs.getString("cavity_label");
                cavityConfidence = rs.getDouble("cavity_confidence");
                faultLabel = rs.getString("fault_label");
                faultConfidence = rs.getDouble("fault_confidence");

                // An event may or may not have an associated label.  If it does, there will be a label ID.  If not,
                // the LEFT JOIN will return null for all of the label table fields.  IDE warnings are wrong here.
                Label label = null;
                if (labelId != null) {
                    label = new Label(labelId, labelTime, modelName, cavityLabel, faultLabel,
                            cavityConfidence, faultConfidence);
                }
                // Add the event to the list
                events.add(new Event(eventId, eventTime, location, system, archive, delete, grouped,
                        classification, label));
            }
            rs.close();
            pstmt.close();
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }

        return events;
    }
}
