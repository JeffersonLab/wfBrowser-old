/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.business.service;

import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.jlab.wfbrowser.business.filter.WaveformFilter;
import org.jlab.wfbrowser.business.util.SqlUtil;
import org.jlab.wfbrowser.business.util.TimeUtil;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Waveform;

/**
 *
 * @author adamc
 */
public class WaveformService {

    private static final Logger LOGGER = Logger.getLogger(WaveformService.class.getName());
    private static Path dataDir;

    public WaveformService() throws FileNotFoundException, IOException {
        Properties props = new Properties();
        try (InputStream is = WaveformService.class.getClassLoader().getResourceAsStream("wfBrowser.properties")) {
            if (is != null) {
                props.load(is);
            }
        }
        dataDir = Paths.get(props.getProperty("dataDir", "/usr/opsdata/waveforms"));
    }

    /**
     * This method uncompresses a compressed waveform event directory and parses
     * it using the same parseWaveformInputStream method as parseWaveformData.
     * The compressed archives should contain a single parent directory with a
     * set of txt files.
     *
     * @param eventArchive
     * @return
     * @throws IOException
     */
    private List<Waveform> parseCompressedWaveformData(Path eventArchive) throws IOException {
        List<Waveform> waveformList = new ArrayList<>();
        boolean foundParentDir = false;
        try (TarArchiveInputStream ais = new TarArchiveInputStream(
                new GzipCompressorInputStream(Files.newInputStream(eventArchive, StandardOpenOption.READ)))) {
            TarArchiveEntry entry;
            while ((entry = ais.getNextTarEntry()) != null) {
                if (entry != null) {
                    if (!ais.canReadEntryData(entry)) {
                        LOGGER.log(Level.WARNING, "Cannot read tar archive entry - {0}", entry.getName());
                        throw new IOException("Cannont read archive entry");
                    }
                    // These shouldn't have nested structures, so just treat the Entry as though it were a file
                    if (entry.isDirectory() && foundParentDir) {
                        LOGGER.log(Level.WARNING, "Unexpected compressed directory structure - {0}", entry.getName());
                        throw new IOException("Unexpected compressed directory structure.");
                    } else if (entry.isDirectory()) {
                        foundParentDir = true;
                    } else {
                        // If these tar files get to be huge, we may have to reconsider this part.  byte arrays can only contain up to 
                        //  2^32 bytes or ~4GB.  For now, the tar files contain ~20MB of data.
                        byte[] content = new byte[(int) entry.getSize()];
                        ais.read(content);
                        waveformList.addAll(parseWaveformInputStream(new ByteArrayInputStream(content)));
                    }
                }
            }
        }
        return waveformList;
    }

    /**
     * The method parses an InputStream representing one of the waveform
     * datafiles. These files are formatted as TSVs, with the first column being
     * the time offset and every other column representing a series of waveform
     * data. This process leads to the time column being stored multiple times
     * as each Waveform object stores its own time/value data.
     *
     * @param wis
     * @return The list of waveforms that were contained in the input stream.
     */
    private List<Waveform> parseWaveformInputStream(InputStream wis) {
        TsvParserSettings settings = new TsvParserSettings();
        settings.getFormat().setLineSeparator("\n");
        TsvParser parser = new TsvParser(settings);

        List<Waveform> waveformList = new ArrayList<>();

        parser.beginParsing(wis);
        String[] row;
        boolean isHeader = true;
        while ((row = parser.parseNext()) != null) {
            if (isHeader) {
                isHeader = false;
                for (int i = 1; i < row.length; i++) {
                    // first entry should be time offset header value
                    waveformList.add(new Waveform(row[i]));
                }
            } else {
                Double timeOffset = Double.valueOf(row[0]);
                for (int i = 1; i < row.length; i++) {
                    // first entry should be the timeoffset, the rest will be waveform values
                    Double value = Double.valueOf(row[i]);
                    // Waveforms index are one less than the row value since we skip time column
                    waveformList.get(i - 1).addPoint(timeOffset, value);
                }
            }
        }
        return waveformList;
    }

    /**
     * Parses all of the data files in the specified event directory
     *
     * @param eventDir
     * @return A List of Waveform objects representing the contents of the data
     * files in the supplied event directory
     * @throws IOException
     */
    private List<Waveform> parseWaveformData(Path eventDir) throws IOException {
        TsvParserSettings settings = new TsvParserSettings();
        settings.getFormat().setLineSeparator("\n");
        TsvParser parser = new TsvParser(settings);

        List<Waveform> waveformList = new ArrayList<>();

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(eventDir)) {
            for (Path path : directoryStream) {
                waveformList.addAll(parseWaveformInputStream(Files.newInputStream(path)));
            }
        }

        return waveformList;
    }

    /**
     * Adds an event's meta data to the database. Verify that an event directory
     * exists in the proper location on the filesystem prior to updating
     * database.
     *
     * @param e The Event to add to the database
     * @param force Add to the database even if the data cannot be found on disk
     * @return The eventId of the new entry in the database corresponding to the
     * row in the event table.
     * @throws FileNotFoundException
     * @throws SQLException
     */
    public long addEvent(Event e, boolean force) throws FileNotFoundException, SQLException {
        if (force == false) {
            Path eventDir = dataDir.resolve(e.getRelativeFilePath());
            Path eventArchive = dataDir.resolve(e.getRelativeArchivePath());
            if (!Files.exists(eventDir) && !Files.exists(eventArchive)) {
                throw new FileNotFoundException("Cannot add event to database if data is missing from disk.  Directory '"
                        + eventDir.toString() + "' or '" + eventArchive.toString() + "' not found.");
            }
        }
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

            String insertEventSql = "INSERT INTO waveforms.event (event_time_utc, location, system_id, archive) VALUES (?, ?, ?, ?)";

            pstmt = conn.prepareStatement(insertEventSql, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, e.getEventTimeString());
            pstmt.setString(2, e.getLocation());
            pstmt.setInt(3, systemId);
            pstmt.setInt(4, e.isArchive() ? 1 : 0);

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

            conn.commit();
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }
        return eventId;
    }

    /**
     * Returns the event object mapping to the event records with eventId from
     * the database.
     *
     * @param filter
     * @return
     * @throws SQLException
     * @throws java.io.IOException
     */
    public List<Event> getEventList(WaveformFilter filter) throws SQLException, IOException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        List<Event> eventList = new ArrayList<>();
        Long eventId;
        Instant eventTime;
        String location, system;
        Boolean archive, delete;

        try {
            conn = SqlUtil.getConnection();

            String getEventSql = "SELECT event_id,event_time_utc,location,system_type.system_name,archive,to_be_deleted FROM waveforms.event"
                    + " JOIN waveforms.system_type USING(system_id) " + filter.getWhereClause();
            pstmt = conn.prepareStatement(getEventSql);
            filter.assignParameterValues(pstmt);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                eventId = rs.getLong("event_id");
                eventTime = TimeUtil.getInstantFromDateTime(rs);
                location = rs.getString("location");
                system = rs.getString("system_name");
                archive = rs.getBoolean("archive");
                delete = rs.getBoolean("to_be_deleted");
                
                if (eventId == null || location == null || system == null || archive == null) {
                    // All of these should have NOT NULL constraints on them.  Verify that something hasn't gone wrong
                    throw new SQLException("Error querying event information from database");
                } else {
                    eventList.add(new Event(eventId, eventTime, location, system, archive, delete, new ArrayList<>()));
                }
            }

            for (Event e : eventList) {
                Path eventDir = dataDir.resolve(e.getRelativeFilePath());
                Path eventArchive = dataDir.resolve(e.getRelativeArchivePath());
                LOGGER.log(Level.FINEST, "Looking for data at {0}, {1}", new Object[]{eventDir.toString(), eventArchive.toString()});
                List<Waveform> waveformList;
                if (Files.exists(eventDir)) {
                    waveformList = parseWaveformData(eventDir);
                } else if (Files.exists(eventArchive)) {
                    waveformList = parseCompressedWaveformData(eventArchive);
                } else {
                    LOGGER.log(Level.SEVERE, "Could not locate data files at {0} or {1}", new Object[]{eventDir.toString(), eventArchive.toString()});
                    throw new FileNotFoundException("Could not locate data files for requested event");
                }
                e.getWaveforms().addAll(waveformList);
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
     * @return The number of rows affected. Should only ever be one since
     * eventId should be the primary key.
     * @throws SQLException
     */
    public int setEventDeleteFlag(long eventId) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        int rowsAffected;
        String deleteSql = "UPDATE waveforms.event SET to_be_deleted = 1 WHERE event_id = ?";

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
        String deleteSql = "DELETE waveforms.event WHERE to_be_deleted = 1 AND event_id = ?";
        if (force) {
            deleteSql = "DELETE FROM waveforms.event WHERE event_id = ?";
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

    /**
     * Add a list of events to the database.
     *
     * @param eventList
     * @param force Add to the database even if the data cannot be found on
     * disk. Should really only be used for testing.
     * @return
     * @throws SQLException
     * @throws java.io.FileNotFoundException
     */
    public int addEventList(List<Event> eventList, boolean force) throws SQLException, FileNotFoundException {
        long eventId;
        int numAdded = 0;
        for (Event e : eventList) {
            if (e != null) {
                // eventId is an autoincremented primary key starting at 1.  It should never be < 0
                eventId = addEvent(e, force);
                numAdded++;
            }
        }
        return numAdded;
    }

    /**
     * Set the to_be_deleted flag on the specified events in the database.
     *
     * @param eventIds
     * @return The number of affected events in the database
     * @throws SQLException
     */
    public int setEventDeleteFlag(List<Long> eventIds) throws SQLException {
        int numDeleted = 0;
        if (eventIds != null) {
            for (Long eventId : eventIds) {
                if (eventId != null) {
                    numDeleted += setEventDeleteFlag(eventId);
                }
            }
        }
        return numDeleted;
    }

    /**
     * Get a list of events from the database matching the specified filter.
     * Useful for querying what events exist without the overhead of
     * transferring all of the actual waveform data around.
     *
     * @param filter
     * @return A list of events
     * @throws SQLException
     */
    public List<Event> getEventListWithoutData(WaveformFilter filter) throws SQLException {
        List<Event> events = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        String sql = "SELECT event_id,event_time_utc,location,archive,to_be_deleted,system_type.system_name"
                + " FROM waveforms.event"
                + " JOIN waveforms.system_type USING(system_id)";
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
            String location, system;
            Boolean archive, delete;
            while (rs.next()) {
                eventId = rs.getLong("event_id");
                eventTime = TimeUtil.getInstantFromDateTime(rs);
                system = rs.getString("system_name");
                location = rs.getString("location");
                archive = rs.getBoolean("archive");
                delete = rs.getBoolean("to_be_deleted");
                events.add(new Event(eventId, eventTime, location, system, archive, delete, null));
            }
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }

        return events;
    }
}
