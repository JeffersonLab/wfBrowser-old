package org.jlab.wfbrowser.business.service;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.util.SqlUtil;
import org.jlab.wfbrowser.business.util.TimeUtil;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.Waveform;

/**
 *
 * @author adamc
 */
public class EventService {

    private static final Logger LOGGER = Logger.getLogger(EventService.class.getName());
    private static Path dataDir;

    public EventService() throws FileNotFoundException, IOException {
        Properties props = new Properties();
        try (InputStream is = EventService.class.getClassLoader().getResourceAsStream("wfBrowser.properties")) {
            if (is != null) {
                props.load(is);
            }
        }
        dataDir = Paths.get(props.getProperty("dataDir", "/usr/opsdata/waveforms/data"));
    }

    /**
     * Simple wrapper on parseCompressedWaveformData(Path eventArchive, boolean
     * includeData) that always sets includeData to true.
     *
     * @param eventArchive
     * @return
     * @throws IOException
     */
    private List<Waveform> parseCompressedWaveformData(Path eventArchive) throws IOException {
        return parseCompressedWaveformData(eventArchive, true);
    }

    /**
     * This method uncompresses a compressed waveform event directory and parses
     * it using the same parseWaveformInputStream method as parseWaveformData.
     * The compressed archives should contain a single parent directory with a
     * set of txt files.
     *
     * @param eventArchive
     * @param includeData boolean for whether or not the waveforms should
     * include their data
     * @return
     * @throws IOException
     */
    private List<Waveform> parseCompressedWaveformData(Path eventArchive, boolean includeData) throws IOException {
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
                        waveformList.addAll(parseWaveformInputStream(ais, includeData));
                    }
                }
            }
        }
        return waveformList;
    }

    /**
     * Wrapper on parseWaveformInputStream(InputStream, boolean) that always
     * sets includeData to true)
     *
     * @param wis
     * @return
     */
    private List<Waveform> parseWaveformInputStream(InputStream wis) throws IOException {
        return parseWaveformInputStream(wis, true);
    }

    /**
     * The method parses an InputStream representing one of the waveform
     * datafiles. These files are formatted as TSVs, with the first column being
     * the time offset and every other column representing a series of waveform
     * data. This process leads to the time column being stored multiple times
     * as each Waveform object stores its own time/value data.
     *
     * @param wis An input stream providing the waveform data in TSV format
     * @param includeData flag for whether or not the data and not just headers
     * should be parsed
     * @return The list of waveforms that were contained in the input stream.
     */
    private List<Waveform> parseWaveformInputStream(InputStream wis, boolean includeData) throws IOException {
        List<Waveform> waveforms = new ArrayList<>();
        String[] headers;
        double[][] out;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(wis))) {
            String line = br.readLine();
            if (line == null) {
                return waveforms;
            }
            headers = line.split("\t");

            // Define this as an array of empty arrays.  This will get redefined if the includeData flag is set.
            out = new double[headers.length][0];
            if (includeData) {
                // The data array structure is the transpose of the file.  it goes columns, by rows, since we know the number of headers
                // but not the number of rows of data.  Plus this makes it easier to access each waveform.
                double[][] data = new double[headers.length][8192];
                int i = 0;
                while ((line = br.readLine()) != null) {

                    // If our index is about to move out of bounds, copy the data to a larger 2D array
                    if (i >= data[0].length) {
                        for (int j = 0; j < data.length; j++) {
                            data[j] = Arrays.copyOf(data[j], 2 * data[j].length);
                        }
                    }

                    // Split the string on tabs.  The order of the tokens should match the headers
                    String[] nums = line.split("\t");
                    for (int j = 0; j < headers.length; j++) {
                        if (nums[j].isEmpty()) {
                            data[j][i] = Double.NaN;
                        } else {
                            data[j][i] = Double.parseDouble(nums[j]);
                        }
                    }
                    i++;
                }

                // Copy the data array structure to one that is properly sized for it.
                out = new double[headers.length][i];
                for (int j = 0; j < out.length; j++) {
                    for (int k = 0; k < out[j].length; k++) {
                        out[j][k] = data[j][k];
                    }
                }
            }
        }

        // Create the waveform list
        for (int j = 0; j < out.length; j++) {
            if (j > 0) {
                waveforms.add(new Waveform(headers[j], out[0], out[j]));
            }
        }

        return waveforms;
    }

    /**
     * Parses all of the data files in the specified event directory
     *
     * @param eventDir
     * @param includeData Should the waveform objects include the data points or
     * only the header information
     * @return A List of Waveform objects representing the contents of the data
     * files in the supplied event directory
     * @throws IOException
     */
    private List<Waveform> parseWaveformData(Path eventDir, boolean includeData) throws IOException {
        List<Waveform> waveformList = new ArrayList<>();

        // Sort the file names.  They should be of the format <HARVESTER_PV>.<IOC_timestamp>.txt
        // with timestamps sorting nicely (i.e., formatted as ####_##_## ##:##:##.#)
        SortedSet<Path> fileSet = new TreeSet<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(eventDir)) {
            for (Path path : directoryStream) {
                // Actual harvester files end with .txt extension.  Do some basic filtering
                if (path.getFileName().toString().contains(".txt")) {
                    fileSet.add(path);
                }
            }
        }

        // Go through and find duplicates.  Since the files are sorted nicely, you only need to look at the last kept filename and
        // the current one to see if they are duplicate PVs.  If you find a duplicate, remove it from the list of files.
        String prev = null;
        String[] prevParts;
        String pv = null;
        List<Path> fileList = new ArrayList<>();
        for (Path path : fileSet) {
            if (prev == null) {
                fileList.add(path);
                prev = path.getFileName().toString();
                prevParts = prev.split("\\.");
                pv = prevParts[0];
            } else {
                String[] parts = path.getFileName().toString().split("\\.");
                if (parts[0].equals(pv)) {
                    LOGGER.log(Level.WARNING, "Ignoring duplicate harvester file {0}", path.toString());
                } else {
                    fileList.add(path);
                    prev = path.getFileName().toString();
                    prevParts = prev.split("\\.");
                    pv = prevParts[0];
                }
            }
        }

        // Go through the set of Path objects representing valid data files and parse them.
        for (Path path : fileList) {
            waveformList.addAll(parseWaveformInputStream(Files.newInputStream(path), includeData));
        }

        return waveformList;
    }

    /**
     * Adds an event's meta data to the database. Verify that an event directory
     * exists in the proper location on the filesystem prior to updating
     * database.
     *
     * @param e The Event to add to the database
     * @param force Add to the database even if the data cannot be found on
     * disk. Mostly to make testing easier.
     * @return The eventId of the new entry in the database corresponding to the
     * row in the event table.
     * @throws FileNotFoundException
     * @throws SQLException
     */
    public long addEvent(Event e, boolean force) throws FileNotFoundException, SQLException, IOException {
        if (force == false) {
            Path eventDir = dataDir.resolve(e.getRelativeFilePath());
            Path eventArchive = dataDir.resolve(e.getRelativeArchivePath());
            if (!Files.exists(eventDir) && !Files.exists(eventArchive)) {
                throw new FileNotFoundException("Cannot add event to database if data is missing from disk.  Directory '"
                        + eventDir.toString() + "' or '" + eventArchive.toString() + "' not found.");
            }
        }

        // We need to read the waveform data files from disk, but we don't need the actual data.  Parsing the 20MB of data
        // takes a while when adding en masse.
        List<Waveform> waveformList = getWaveformsFromDisk(e, force, false);

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

            // In testing mode (force/ignoreErrors == true), waveformList could be null.
            if (waveformList != null) {
                String seriesSql = "INSERT INTO event_waveforms (event_id, waveform_name) VALUES(?,?)";
                pstmt = conn.prepareStatement(seriesSql);
                for (Waveform w : waveformList) {
                    pstmt.setLong(1, eventId);
                    pstmt.setString(2, w.getWaveformName());
                    int numUpdated = pstmt.executeUpdate();
                    if (numUpdated != 1) {
                        conn.rollback();
                        throw new SQLException("Error adding waveform metadata to database.");
                    }
                    pstmt.clearParameters();
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
        String sql = "SELECT event_id FROM waveforms.event JOIN system_type ON event.system_id = system_type.system_id";
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
     * Get the most recent event in the database given the applied filter
     *
     * @param filter
     * @return
     * @throws SQLException
     * @throws java.io.IOException
     */
    public Event getMostRecentEvent(EventFilter filter) throws SQLException, IOException {
        List<Event> eventList = getEventList(filter, false, 1l);
        Event out = null;
        if (!eventList.isEmpty()) {
            out = eventList.get(0);
        }
        return out;
    }

    /**
     * Query the database for the List of unique location names
     *
     * @return A list of the unique location names
     * @throws SQLException
     */
    public List<String> getLocationNames() throws SQLException {
        List<String> out = new ArrayList<>();
        String sql = "SELECT DISTINCT location FROM waveforms.event ORDER BY location";

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                out.add(rs.getString("location"));
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
     * Method for reading parsing on disk event data into a list of event
     * waveform objects
     *
     * @param event The event for which we are parsing data
     * @param ignoreErrors Whether or not to throw exception and log data if the
     * files cannot be found.
     * @param includeData Whether or not to include the waveform data or just
     * header information
     * @return The event data as a list of waveforms
     * @throws IOException
     */
    private List<Waveform> getWaveformsFromDisk(Event event, boolean ignoreErrors, boolean includeData) throws IOException {
        List<Waveform> waveformList = null;

        if (event == null) {
            return null;
        }

        Path eventDir = dataDir.resolve(event.getRelativeFilePath());
        Path eventArchive = dataDir.resolve(event.getRelativeArchivePath());
        LOGGER.log(Level.FINEST, "Looking for data at {0}, {1} for event {2}", new Object[]{eventDir.toString(), eventArchive.toString(), event.getEventId()});
        if (Files.exists(eventDir)) {
            waveformList = parseWaveformData(eventDir, includeData);
        } else if (Files.exists(eventArchive)) {
            waveformList = parseCompressedWaveformData(eventArchive, includeData);
        } else if (!ignoreErrors) {
            LOGGER.log(Level.SEVERE, "Could not locate data files at {0} or {1}", new Object[]{eventDir.toString(), eventArchive.toString()});
            throw new FileNotFoundException("Could not locate data files for requested event");
        }

        return waveformList;
    }

    /**
     * Returns the event object mapping to the event records with eventId from
     * the database. Simple wrapper that does not ignore errors.
     *
     * @param filter
     * @return
     * @throws SQLException
     * @throws java.io.IOException
     */
    public List<Event> getEventList(EventFilter filter) throws SQLException, IOException {
        return getEventList(filter, false, null);
    }

    /**
     * Returns a map of waveform names to common series names for a given event.
     *
     * @param eventId
     * @return
     */
    public Map<String, String> getWaveformToSeriesMap(long eventId) throws SQLException {
        Map<String, String> waveformToSeries = new HashMap<>();
        String sql = "SELECT waveform_name, series_name"
                + " FROM event_waveforms"
                + " JOIN series ON event_waveforms.waveform_name LIKE series.pattern"
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
     * @param ignoreErrors Whether or not to ignore errors when parsing waveform
     * data on disk. Used for testing only.
     * @param limit How many events to return. Null for unlimited
     * @return
     * @throws SQLException
     * @throws java.io.IOException
     */
    public List<Event> getEventList(EventFilter filter, boolean ignoreErrors, Long limit) throws SQLException, IOException {
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
                    + " JOIN waveforms.system_type USING(system_id) ";
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

                if (eventId == null || location == null || system == null || archive == null) {
                    // All of these should have NOT NULL constraints on them.  Verify that something hasn't gone wrong
                    throw new SQLException("Error querying event information from database");
                } else {
                    eventList.add(new Event(eventId, eventTime, location, system, archive, delete, new ArrayList<Waveform>()));
                }
            }

            String mapSql = "SELECT series_name, series_id, pattern, system_type.system_name, description, units, waveform_name "
                    + " FROM event_waveforms"
                    + " JOIN series ON waveform_name LIKE series.pattern"
                    + " JOIN system_type ON series.system_id = system_type.system_id"
                    + " WHERE event_id = ?"
                    + " GROUP BY waveform_name"
                    + " ORDER BY waveform_name";

            pstmt = conn.prepareStatement(mapSql);

            for (Event e : eventList) {
                e.getWaveforms().addAll(getWaveformsFromDisk(e, ignoreErrors, true)); // We want the actual waveform data
                
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
                for (Waveform w : e.getWaveforms()) {
                    w.addSeries(waveformToSeries.get(w.getWaveformName()));
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
        String deleteSql = "UPDATE waveforms.event SET to_be_deleted = ? WHERE event_id = ?";

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
    public int addEventList(List<Event> eventList, boolean force) throws SQLException, FileNotFoundException, IOException {
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
     * transferring all of the actual waveform data around.
     *
     * @param filter
     * @return A list of events
     * @throws SQLException
     */
    public List<Event> getEventListWithoutData(EventFilter filter) throws SQLException {
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
