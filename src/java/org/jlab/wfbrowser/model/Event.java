package org.jlab.wfbrowser.model;

import org.jlab.wfbrowser.model.CaptureFile.CaptureFile;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.jlab.wfbrowser.business.util.TimeUtil;
import org.jlab.wfbrowser.model.CaptureFile.Metadata;

/**
 * Object for representing and waveform triggering event. The eventId is
 * optional as an Event object needs to be created before it is added to the
 * database, and the eventId is only created in the database once the event is
 * added. While not strictly enforced, an eventId value of null should imply
 * that the Event object was not read from the database. Since the database can
 * only handle microsecond resolution, we truncate event times to that here.
 * This helps with testing.
 *
 * Event objects are responsible for containing all of the information defining
 * an event (location, system, timestamp, whether the event is a set of grouped
 * capture files) and also information that needs to be determined either by
 * inspecting the filesystem (such as when a new object is being added to the
 * database) or by looking it up in the database (as when looking up an event
 * that has already been added to the database). This information is essentially
 * event metadata such as the capture files, waveforms, series or capture file
 * metadata (PV key/value pairs, etc. saved in the file header).
 *
 * @author adamc
 */
public class Event {

    private static final Logger LOGGER = Logger.getLogger(Event.class.getName());

    private final Path dataDir;              // Where does the data live on the filesystem.  This is the base data dir of all events, not the directory containing capture files for this event.
    private Long eventId = null;            // The event id assigned by the database
    private final SortedMap<String, CaptureFile> captureFileMap = new TreeMap<>();
    private final String system;            // The accelerator system from which the event is triggered
    private final String classification;    // The classification of the event as determined by the harvester
    private final String location;           // The location of the event as determined by the harvester
    private final Instant eventTime;       // The time of the event as determined by the harvester
    private final boolean archive;        // Is the event allowed to be deleted
    // TODO: is the delete flag needed?  Probably not.
    private final boolean delete;         // Is the event marked for deletion on next pass
    private final boolean grouped;      // Is the event a group of synchronized waveform files or a single file
    private boolean areWaveformsConsistent = true;  // If all waveforms have the same set of time offsets.  Simplies certain data operaitons.

    private static Path setDataDir() throws IOException {
        Properties props = new Properties();
        try (InputStream is = Event.class.getClassLoader().getResourceAsStream("wfBrowser.properties")) {
            if (is != null) {
                props.load(is);
            }
        }
        return Paths.get(props.getProperty("dataDir", "/usr/opsdata/waveforms/data"));
    }

    /**
     * Add a CaptureFile object to this Event's collection of capture files.
     *
     * @param captureFile The CaptureFile to be added to the Event's collection
     * of capture files
     * @return
     */
    public CaptureFile addCaptureFile(CaptureFile captureFile) {
        return captureFileMap.put(captureFile.getFilename(), captureFile);
    }

    public SortedMap<String, CaptureFile> getCaptureFileMap() {
        return captureFileMap;
    }

    /**
     * Get the captureFiles associated with this event as a List, not a Map
     *
     * @return A List of the Event's CaptureFiles
     */
    public List<CaptureFile> getCaptureFileList() {
        List<CaptureFile> out = new ArrayList<>();
        for (String filename : captureFileMap.keySet()) {
            out.add(captureFileMap.get(filename));
        }
        return out;
    }

    /**
     * A constructor for generating events returned via a database lookup. This
     * should include the event defining information such as the system,
     * classification, location, timestamp, and grouped information AND cached
     * database information like the archive flag, the delete flag, the list of
     * waveforms, and the list of capture files.
     *
     * @param eventId
     * @param eventTime
     * @param location
     * @param system
     * @param archive
     * @param delete
     * @param grouped
     * @param classification capture files
     * @throws IOException
     */
    public Event(long eventId, Instant eventTime, String location, String system, boolean archive, boolean delete,
            boolean grouped, String classification) throws IOException {
        if (eventTime == null) {
            throw new IllegalArgumentException("eventTime is required non-null");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required non-null");
        }
        if (system == null) {
            throw new IllegalArgumentException("system is required non-null");
        }
        if (classification == null) {
            throw new IllegalArgumentException("classification is required non-null");
        }

        this.eventId = eventId;
        this.eventTime = eventTime.truncatedTo(ChronoUnit.MICROS);
        this.location = location;
        this.system = system;
        this.archive = archive;
        this.delete = delete;
        this.grouped = grouped;
        this.classification = classification;

        // Set the waveform data directory based optionally on the value in the config file
        this.dataDir = setDataDir();
    }

    /**
     * Event constructor for creating an event object that has not been added to
     * the database. This requires only information that is needed for defining
     * an event (system, classification, location, timestamp, grouped). Other
     * database and filesystem information may be added later if needed. Some
     * "optional" parameter must also be specified, since the intended use of
     * this is for adding new events to the database, which contains some flags
     * that are set only by admin users.
     *
     * If the event is ungrouped, then captureFiles must contain at least one
     * capture file. If the event is grouped, then the capture files will be
     * looked up from the filesystem and the supplied arguement will be ignored.
     *
     * @param eventTime
     * @param location
     * @param system
     * @param archive
     * @param delete
     * @param grouped
     * @param classification
     * @param captureFile
     * @throws IOException
     */
    public Event(Instant eventTime, String location, String system, boolean archive, boolean delete, boolean grouped,
            String classification, String captureFile) throws IOException {
        if (eventTime == null) {
            throw new IllegalArgumentException("eventTime is required non-null");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required non-null");
        }
        if (system == null) {
            throw new IllegalArgumentException("system is required non-null");
        }
        if (classification == null) {
            throw new IllegalArgumentException("classification is required non-null");
        }

        this.eventTime = eventTime.truncatedTo(ChronoUnit.MICROS);
        this.location = location;
        this.system = system;
        this.archive = archive;
        this.delete = delete;
        this.grouped = grouped;
        this.classification = classification;

        // This sets the base data dir based on the an optional config parameter.
        this.dataDir = setDataDir();

        List<String> filesToProcess = new ArrayList<>();
        if (!grouped) {
            // Ungrouped events must have a capture file specified since different ungrouped event capture files can have names
            // with the same timestamps but different harvester PVs.
            if (captureFile == null || captureFile.isEmpty()) {
                throw new IllegalArgumentException("Ungrouped events must include exactly one capture file");
            }
            filesToProcess.add(captureFile);
        } else {
            // Grouped events have a directory that contains only capture files for that event, so we can go look at the filesystem 
            // to determine which files are associated with the event.  The directory can be determined by attributes of the event.
            filesToProcess.addAll(getCaptureFileNamesFromFileSystem());
            if (filesToProcess.isEmpty()) {
                throw new IllegalArgumentException("Could not find any capture files on disk associated with event");
            }
        }
        // Process the capture files to get waveforms, data, etc.
        loadCaptureFilesFromDisk(filesToProcess, true);  // includeData = true

        updateWaveformsConsistency();
    }

    /**
     * Look on disk and inspect either the event directory or the compressed
     * archive file to determine what capture files are associated with the
     * event. Should duplicate capture files exist for an IOC, we keep the first
     * file created.
     *
     * @return
     * @throws IOException
     */
    private List<String> getCaptureFileNamesFromFileSystem() throws IOException {
        // Sort the file names.  They should be of the format <HARVESTER_PV>.<IOC_timestamp>.txt
        // with timestamps sorting nicely (i.e., formatted as yyyy_mm_dd HH:MM:ss.S)
        SortedSet<String> fileSet = new TreeSet<>();
        Path eventDir = getEventDirectoryPath();
        Path archivePath = getArchivePath();
        if (Files.exists(eventDir)) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(getEventDirectoryPath())) {
                for (Path path : directoryStream) {
                    // Actual harvester files end with .txt extension.  Do some basic filtering
                    if (path.getFileName().toString().contains(".txt")) {
                        fileSet.add(path.getFileName().toString());
                    }
                }
            }
        } else if (Files.exists(archivePath)) {
            // We have to uncompress these tar.gz file and look at what's inside it.  For grouped events, there will be a directory
            // contain capture files.  For ungrouped files, there will be only a single file in the tar.gz.
            boolean foundParentDir = false;
            if (!grouped) {
                foundParentDir = true;  // Ungrouped have no parent directory
            }

            try (TarArchiveInputStream ais = new TarArchiveInputStream(
                    new GzipCompressorInputStream(Files.newInputStream(getArchivePath(), StandardOpenOption.READ)))) {
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
                            if (entry.getName().contains(".txt")) {
                                // If this is a grouped event, we need to strip off the "parent" directory.  No effect if ungrouped.
                                fileSet.add(Paths.get(entry.getName()).getFileName().toString());
                            }
                        }
                    }
                }
            }
        }

        // Go through and find duplicates.  Since the files are sorted nicely, you only need to look at the last kept filename and
        // the current one to see if they are duplicate PVs.  If you find a duplicate, remove it from the list of files.
        // Filenames should be of the format <HARVESTER_PV>.<IOC_timestamp>.txt
        String prev = null;
        String[] prevParts;
        String pv = null;
        List<String> fileList = new ArrayList<>();
        for (String filename : fileSet) {
            if (prev == null) {
                fileList.add(filename);
                prev = filename;
                prevParts = prev.split("\\.");
                pv = prevParts[0];
            } else {
                String[] parts = filename.split("\\.");
                if (parts[0].equals(pv)) {
                    LOGGER.log(Level.WARNING, "Ignoring duplicate harvester file {0}", filename);
                } else {
                    fileList.add(filename);
                    prev = filename;
                    prevParts = prev.split("\\.");
                    pv = prevParts[0];
                }
            }
        }
        return fileList;
    }

    /**
     * Check if the waveforms have equivalent timeOffsets and update the
     * areWaveformsConsistent parameter
     *
     * @return void
     */
    private void updateWaveformsConsistency() {
        boolean consistent = true;
        Double sampleStart = null;
        Double sampleEnd = null;
        Double sampleStep = null;
        if (captureFileMap != null && !captureFileMap.isEmpty()) {
            for (String file : captureFileMap.keySet()) {
                CaptureFile cf = captureFileMap.get(file);
                if (sampleStart == null) {
                    sampleStart = cf.getSampleStart();
                    sampleEnd = cf.getSampleEnd();
                    sampleStep = cf.getSampleStep();
                } else {
                    if (Double.compare(sampleStart, cf.getSampleStart()) != 0) {
                        consistent = false;
                        break;
                    }
                    if (Double.compare(sampleEnd, cf.getSampleEnd()) != 0) {
                        consistent = false;
                        break;
                    }
                    if (Double.compare(sampleStep, cf.getSampleStep()) != 0) {
                        consistent = false;
                        break;
                    }
                }
            }
        }
        areWaveformsConsistent = consistent;
    }

    public boolean isDelete() {
        return delete;
    }

    /**
     * Determine the location where the compressed archive file would exist (if
     * it did), using information available from the event (and not an explicit
     * base file path).
     *
     * @return The Path of the compressed archive file
     */
    public Path getArchivePath() {
        return getArchivePath(null);
    }

    /**
     * Determine the location where the compressed archive file would exists (if
     * it did). If the event is grouped, then archive path will be based on the
     * event directory path and will always ignore the contents of captureFiles.
     * If the event is ungrouped, captureFile will be referenced only when not
     * null or empty, but if null or empty, then the event''s captureFileMap
     * will be referenced.
     *
     * @param captureFile The filename used to create the archived path. If null
     * or empty, reference the captureFileMap instead. NOTE: Likely needed in
     * case where ungrouped event hasn't updated it's captureFileMap.
     * @return
     */
    public Path getArchivePath(String captureFile) {
        Path archivePath;

        // The arhcive file path needs to be determined differently depending on if the event is grouped or not.  If grouped, you
        // always use the event information (system, location, class, time) to determine the archive folder.  If ungrouped, you 
        // either must be explicitly told the file to check, or, if the event has already processed the capture file, reference the
        // event's captureFileMap.  If supplied with the capture file, use that first, and then default to captureFileMap
        if (grouped) {
            archivePath = Paths.get(getEventDirectoryPath().toString() + ".tar.gz");
        } else {
            if (captureFile == null || captureFile.isEmpty()) {
                if (captureFileMap.size() != 1) {
                    throw new RuntimeException("An ungrouped event does not have a single capture file associated with it.  Can't determine tar.gz path.");
                }
                archivePath = getEventDirectoryPath().resolve(captureFileMap.firstKey() + ".tar.gz");
            } else {
                archivePath = Paths.get(getEventDirectoryPath().toString(), captureFile + ".tar.gz");
            }
        }

        return archivePath;
    }

    /**
     * Determine the location where the uncompressed event directory would be.
     * In the case of a grouped event, this is a directory containing only the
     * capture files for the event. In the case of ungrouped events, it is a
     * directory potentially containing capture files from lots of ungrouped
     * events.
     *
     * @return
     */
    public Path getEventDirectoryPath() {
        DateTimeFormatter dFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd").withZone(ZoneId.systemDefault());
        DateTimeFormatter tFormatter = DateTimeFormatter.ofPattern("HHmmss.S").withZone(ZoneId.systemDefault());
        Path dir;

        String day = dFormatter.format(eventTime);
        String time = tFormatter.format(eventTime);

        if (grouped) {
            dir = dataDir.resolve(Paths.get(system, location, classification, day, time));
        } else {
            dir = dataDir.resolve(Paths.get(system, location, classification, day));
        }

        return dir;
    }

    /**
     * Check if the data is actually on disk. If the event directory is found,
     * verify that the expected capture files exist within it. If the event
     * directory doesn't exist, check for a compressed "archive" file, but don't
     * uncompress it to verify that it contains the expected capture files. This
     * seems like an unnecessary performance hit, and shouldn't be done unless
     * later proven necessary. If neither the directory or archive file are
     * found, return false.
     *
     * NOTE: this method checks that the files specified by the Event's
     * CaptureFiles exist.
     *
     * @return True if the event directory with capture files or the archive
     * file is found. False otherwise.
     */
    public boolean isDataOnDisk() {
        List<String> files = new ArrayList<>();
        files.addAll(captureFileMap.keySet());
        return isDataOnDisk(files);
    }

    /**
     * Check if the data is actually on disk. If the event directory is found,
     * verify that the expected capture files exist within it. If the event
     * directory doesn't exist, check for a compressed "archive" file, but don't
     * uncompress it to verify that it contains the expected capture files. This
     * seems like an unnecessary performance hit, and shouldn't be done unless
     * later proven necessary. If neither the directory or archive file are
     * found, return false.
     *
     * This method checks that the supplied files names exists, and NOT that the
     * Event's CaptureFile's exists.
     *
     * @param captureFiles
     * @return True if the event directory with capture files or the archive
     * file is found. False otherwise.
     */
    public boolean isDataOnDisk(List<String> captureFiles) {
        boolean exists = false;

        Path eventDir = getEventDirectoryPath();
        Path archiveFile;
        if (grouped) {
            archiveFile = getArchivePath();
        } else {
            // Ungrouped so it should be a single file
            if (captureFiles.size() != 1) {
                throw new IllegalArgumentException("Ungrouped event must have only one capture file associated with it");
            }
            // Since we checking for a set of explicit files, use that information to get the archive file path
            archiveFile = getArchivePath(captureFiles.get(0));
        }

        // For ungrouped events, the event directory is the parent directory of the capture file, which may exist even if the 
        // capture file has been compressed.  Check for the compressed version first to avoid a short circuit.
        if (Files.exists(archiveFile)) {
            exists = true;
        } else if (Files.exists(eventDir)) {
            exists = true;
            for (String file : captureFiles) {

                if (!Files.exists(eventDir.resolve(file))) {
                    LOGGER.log(Level.WARNING, "Could not find file on disk - {0}", eventDir.resolve(file));
                    exists = false;
                    break;
                }
            }
        } else {
            LOGGER.log(Level.WARNING, "Could not find archive file ''{0}'' or event directory ''{1}''", new Object[]{archiveFile, eventDir});
        }

        return exists;
    }

    // TODO: add javadocs and make sure this is needed.
    public void loadWaveformDataFromDisk() throws IOException {
        List<String> filenames = new ArrayList<>();
        filenames.addAll(captureFileMap.keySet());
        loadCaptureFilesFromDisk(filenames, true); // includeData = true
    }

    /**
     * Method for parsing capture files on disk. This updates the event's
     * Waveforms and CaptureFiles.
     *
     * @param captureFiles The list of capture files that should be parsed.
     * These should be only the file names that will be found within the event
     * directory or compressed archive file.
     * @param includeData Whether or not to include the waveform data or just
     * header information
     * @throws java.io.FileNotFoundException
     */
    private void loadCaptureFilesFromDisk(List<String> captureFiles, boolean includeData) throws FileNotFoundException, IOException {
        if (!isDataOnDisk(captureFiles)) {
            LOGGER.log(Level.SEVERE, "Could not locate data on disk");
            throw new FileNotFoundException("Could not locate data on disk");
        }

        // event is grouped, so we can use the event data to determine the directory or tgz file containing the waveform files to be parsed
        Path eventDir = getEventDirectoryPath();
        Path eventArchive;

        // For logging purposes
        String eventName = (eventId == null) ? system + "--" + location + "--" + classification + "--" + eventTime : eventId.toString();

        if (grouped) {
            // For grouped, event directory is the directory containing the capture files for the event
            eventArchive = getArchivePath();
            if (Files.exists(eventDir)) {
                LOGGER.log(Level.FINEST, "Looking for data in {0} for event {1}", new Object[]{eventDir.toString(), eventName});
                parseWaveformData(captureFiles, includeData);
            } else if (Files.exists(eventArchive)) {
                LOGGER.log(Level.FINEST, "Looking for data in {0} for event {1}", new Object[]{eventArchive.toString(), eventName});
                parseCompressedWaveformData(captureFiles, includeData);
            }
        } else {
            // For ungrouped, event directory is the directory contain the capture file or the compressed capture file.
            String filename = captureFiles.get(0);
            eventArchive = getArchivePath(filename);
            if (Files.exists(Paths.get(eventDir.toString(), filename))) {
                LOGGER.log(Level.FINEST, "Looking for data in {0} for event {1}", new Object[]{Paths.get(eventDir.toString(), filename).toString(), eventName});
                parseWaveformData(captureFiles, includeData);
            } else if (Files.exists(eventArchive)) {
                LOGGER.log(Level.FINEST, "Looking for data in {0} for event {1}", new Object[]{eventArchive.toString(), eventName});
                parseCompressedWaveformData(captureFiles, includeData);
            }
        }
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public String getLocation() {
        return location;
    }

    public String getSystem() {
        return system;
    }

    public boolean isArchive() {
        return archive;
    }

    public boolean isGrouped() {
        return grouped;
    }

    public List<Waveform> getWaveforms() {
        List<Waveform> out = new ArrayList<>();
        for (String file : captureFileMap.keySet()) {
            out.addAll(captureFileMap.get(file).getWaveforms());
        }
        return out;
    }

    public String getClassification() {
        return classification;
    }

    public void applySeriesMapping(Map<String, List<Series>> seriesMapping) {
        for (String file : captureFileMap.keySet()) {
            CaptureFile cf = captureFileMap.get(file);
            cf.applySeriesMapping(seriesMapping);
        }
    }

    /**
     * Generate a json object representing an event. Simple wrapper on
     * toJsonObject(List &;lt seriesList &;gt) that does no series filtering.
     *
     * @return
     */
    public JsonObject toJsonObject() {
        return toJsonObject(null);
    }

    /**
     * Generate a json object representing an event. Only include a waveforms
     * parameter/array if the waveforms list isn't null. Only use this method if
     * you're returning a Event that came from the data or has an associated
     * database event_id value, since it doesn't make any sense to hand out
     * "unofficial" data through one of our data API end points.
     *
     * @param seriesSet If not null, only include waveforms who's listed
     * seriesNames includes at least of the series in the list.
     * @return
     */
    public JsonObject toJsonObject(Set<String> seriesSet) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        if (eventId != null) {
            job.add("id", eventId)
                    .add("datetime_utc", TimeUtil.getDateTimeString(eventTime))
                    .add("location", location)
                    .add("system", system)
                    .add("archive", archive)
                    .add("classification", classification);
            JsonArrayBuilder jab = Json.createArrayBuilder();
            for (String cfName : captureFileMap.keySet()) {
                jab.add(captureFileMap.get(cfName).toJsonObject(seriesSet));
            }
            job.add("captureFiles", jab.build());
        } else {
            // Should never try to send out a response on an "Event" that didn't come from the database.  Full stop if we try.
            throw new RuntimeException("Cannot return event without database event ID");
        }
        return job.build();
    }

    /**
     * Get the event's timestamp in UTC as a string.
     *
     * @return The event timestamp string.
     */
    public String getEventTimeString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S").withZone(ZoneOffset.UTC);
        return formatter.format(eventTime);
    }

    /**
     * This method processes the list of individual waveforms in a 2D array that
     * makes additional manipulations much easier. The resulting array does not
     * contain the header information - only time offsets and data values. The
     * rows of the arrays represent the lines of the
     *
     * If the waveforms are "consistent", i.e., they have the same set of time
     * offsets, then the first set of time offsets are used and all "data"
     * values are added in order. If the waveforms are not "consistent", then a
     * new "master" list of time offsets are constructed and the "blanks" filled
     * in for each waveform via step-wise interpolation. Optionally, a list of
     * specific series can be requested by supplying a non-null list of strings.
     *
     * @param seriesSet A set of series to include. Include all if null.
     * @return
     */
    public double[][] getWaveformDataAsArray(Set<String> seriesSet) {
        double[][] data;

        List<Waveform> wfList = getWaveforms(seriesSet);
        if (areWaveformsConsistent) {
            // 2D array for hold csv content - [rows][columns]
            //  number of points only since we aren't including headers, +1 columns because of the time_offset column
            data = new double[wfList.get(0).getTimeOffsets().length][wfList.size() + 1];

            // Set up the time offset column
            double[] tos = wfList.get(0).getTimeOffsets();
            for (int i = 0, iMax = data.length; i < iMax; i++) {
                data[i][0] = tos[i];
            }

            // Add in all of the waveform series information
            int j = 1;
            for (Waveform w : wfList) {
                double[] values = w.getValues();
                for (int i = 0, iMax = values.length; i < iMax; i++) {
                    data[i][j] = values[i];
                }
                j++;
            }
        } else {
            // The waveforms are not consistent in that they do not all have the same set of time offsets.  Instead of just grabbing
            // the timeoffsets from one waveform and pulling the values from all in order, we need to compile the full set of time
            // offsets from all of the waveforms.  Then for each offset, see what the value would have been for a waveform at that
            // point using a method that interpolates, but not extrapolates.

            // Get all of the time offset value from all of the waveforms and put them in a sorted set.  Manually convert the set to
            // a list to ensure the order is maintained (SortedSet returns value in sorted order, and ArrayList maintains insertion
            // order.
            SortedSet<Double> timeOffsetSet = new TreeSet<>();
            for (Waveform w : wfList) {
                List<Double> tos = convertArrayToList(w.getTimeOffsets());
                timeOffsetSet.addAll(tos);
            }
            List<Double> timeOffsets = new ArrayList<>();
            for (Double t : timeOffsetSet) {
                timeOffsets.add(t);
            }

            // 2D array for hold csv content - [rows][columns]
            // rows for data only because no headers, +1 columns because of the time_offset column
            data = new double[timeOffsets.size()][wfList.size() + 1];

            // Set up the time offset column
            for (int i = 0, iMax = data.length; i < iMax; i++) {
                data[i][0] = timeOffsets.get(i);
            }

            // Add in all of the waveform series data points
            for (int j = 1, jMax = data[0].length; j < jMax; j++) {
                for (int i = 0, iMax = data.length; i < iMax; i++) {
                    Double value = wfList.get(j - 1).getValueAtOffset(timeOffsets.get(i));
                    data[i][j] = value;  // Should be a valid double or NaN if no value found
                }
            }
        }
        return data;
    }

    /**
     * Return the list of waveforms that match a set of series names. Order
     * should be consistent with the ordering of waveforms member
     *
     * @param seriesSet
     * @return
     */
    public List<Waveform> getWaveforms(Set<String> seriesSet) {
        List<Waveform> wfList = new ArrayList<>();
        List<Waveform> waveforms = getWaveforms();
        for (Waveform waveform : waveforms) {
            if (seriesSet != null) {
                for (String seriesName : seriesSet) {
                    for (Series series : waveform.getSeries()) {
                        if (series.getName().equals(seriesName)) {
                            wfList.add(waveform);
                            break;
                        }
                    }
                }
            } else {
                wfList = waveforms;
                break;
            }
        }
        return wfList;
    }

    /**
     * Generate the contents of a CSV file that represents the waveform event
     *
     * @param seriesSet A set of the named series that should be included
     * @return A string representation of a CSV file representing the waveform
     * event.
     */
    public String toCsv(Set<String> seriesSet) {
        double[][] csvData = getWaveformDataAsArray(seriesSet);
        List<String> headers = new ArrayList<>();
        headers.add("time_offset");
        for (Waveform w : getWaveforms(seriesSet)) {
            if (w != null) {
                headers.add(w.getWaveformName());
            }
        }

        String csvOut;

        // Generate the string representation of the CSV
        StringBuilder builder = new StringBuilder(csvData.length * csvData[0].length);
        builder.append(String.join(",", headers));
        builder.append('\n');
        for (int i = 0, iMax = csvData.length; i < iMax; i++) {
            builder.append(csvData[i][0]);
            for (int j = 1, jMax = csvData[0].length; j < jMax; j++) {
                builder.append(',');
                builder.append(csvData[i][j]);
            }
            builder.append('\n');
        }

        return builder.toString();
    }

    /**
     * Generate a json object in a way that makes it easy to pass to the dygraph
     * widgets
     *
     * @param seriesSet A set of series names that should be included in the
     * output
     * @return
     */
    public JsonObject toDyGraphJsonObject(Set<String> seriesSet) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        List<Waveform> waveforms = getWaveforms();
        if (eventId != null) {
            job.add("id", eventId)
                    .add("datetime_utc", TimeUtil.getDateTimeString(eventTime))
                    .add("location", location)
                    .add("system", system)
                    .add("archive", archive);
            if (waveforms != null) {
                double[][] data = getWaveformDataAsArray(seriesSet);

                List<String> headerNames = new ArrayList<>();
                headerNames.add("time_offset");

                for (Waveform w : getWaveforms(seriesSet)) {
                    headerNames.add(w.getWaveformName());
                }

                // Get the timeOffsets
                JsonArrayBuilder tjab = Json.createArrayBuilder();
                if (data != null) {
                    for (int i = 0; i < data.length; i++) {
                        tjab.add(data[i][0]);
                    }
                }
                job.add("timeOffsets", tjab.build());

                // Don't add a waveforms parameter if it's null.  That indicates that the waveforms were requested
                JsonArrayBuilder wjab = Json.createArrayBuilder();
                JsonArrayBuilder sjab, djab;
                JsonObjectBuilder wjob;
                for (int i = 1; i < headerNames.size(); i++) {
                    for (Waveform w : waveforms) {
                        if (w.getWaveformName().equals(headerNames.get(i))) {
                            wjob = Json.createObjectBuilder().add("waveformName", headerNames.get(i));

                            // Add some information that the client side can cue off of for consitent colors and names.
                            wjob.add("dygraphLabel", getDygraphLabel(headerNames.get(i)));
                            wjob.add("dygraphId", getDygraphId(headerNames.get(i)));

                            // Get the series names for the waveform and add them
                            sjab = Json.createArrayBuilder();
                            for (Series series : w.getSeries()) {
                                sjab.add(series.toJsonObject());
                            }
                            wjob.add("series", sjab.build());

                            // Add the data points for the series.  Can't query the waveform directly in case the waveforms aren't consistent
                            djab = Json.createArrayBuilder();
                            if (data != null) {
                                for (int j = 0; j < data.length; j++) {
                                    // Since waveformNames is the first row, it's index matches up with the columns of data;
                                    djab.add(data[j][i]);
                                }
                            }
                            wjob.add("dataPoints", djab.build());
                            wjab.add(wjob.build());
                        }
                    }
                }
                job.add("waveforms", wjab.build());
            }
        } else {
            // Should never try to send out a response on an "Event" that didn't come from the database.  Full stop if we try.
            throw new RuntimeException("Cannot return event without database event ID");
        }
        return job.build();
    }

    /**
     * Generate a string label used by dygraph clients
     *
     * @param waveformName The name of the waveform for which we are producting
     * a dygraph label
     * @return
     */
    private String getDygraphLabel(String waveformName) {
        String label;
        switch (system) {
            case "rf":
                label = waveformName.substring(0, 4);
                break;
            case "acclrm":
                label = waveformName.substring(7, 10);
                break;
            default:
                throw new IllegalStateException("Unrecognized system - " + system);
        }
        return label;
    }

    /**
     * Generate a numeric ID that can be used by dygraph clients to group
     * related waveforms
     *
     */
    private long getDygraphId(String waveformName) {
        long id;
        switch (system) {
            case "rf":
                id = Long.parseLong(waveformName.substring(3, 4));
                break;
            case "acclrm":
                Map<String, Long> idMap = new HashMap<>();
                idMap.put("CRY", 1L);
                idMap.put("FLR", 2L);
                idMap.put("LCW", 3L);
                id = idMap.get(waveformName.substring(7, 10));
                break;
            default:
                throw new IllegalStateException("Unrecognized system - " + system);
        }
        return id;
    }

    /**
     * Events are considered equal if all of the metadata about the event are
     * equal. We currently do not enforce equality of waveform data, only that
     * an event has the same number of waveforms. The database should maintain
     * uniqueness of event IDs and it's related data.
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Event)) {
            return false;
        }

        Event e = (Event) o;
        boolean isEqual = true;
        // For events to be equal, either both IDs are null or both are the same number.  Mixing null and non-null IDs could be
        // problematic since one probably came from the database and the other didn't.  In the database, the IDs serve as primary keys, so are GUIDs.
        isEqual = isEqual && (eventId == null ? e.getEventId() == null : eventId.equals(e.getEventId()));

        // An event is defined by the time it occurred and its system, location, and classification.
        isEqual = isEqual && (eventTime.equals(e.getEventTime()));
        isEqual = isEqual && (location.equals(e.getLocation()));
        isEqual = isEqual && (system.equals(e.getSystem()));
        isEqual = isEqual && (classification.equals(e.getClassification()));
        isEqual = isEqual && (archive == e.isArchive());
        return isEqual;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 11 * hash + Objects.hashCode(this.eventId);
        hash = 11 * hash + Objects.hashCode(this.eventTime);
        hash = 11 * hash + Objects.hashCode(this.location);
        hash = 11 * hash + Objects.hashCode(this.system);
        hash = 11 * hash + Objects.hashCode(this.classification);
        hash = 11 * hash + (this.archive ? 1 : 0);
        return hash;
    }

    @Override
    public String toString() {
        String wData = null;
        String wSize = "null";
        List<Waveform> waveforms = getWaveforms();
        if (waveforms != null) {
            wData = "";
            wSize = "" + waveforms.size();
            for (Waveform w : waveforms) {
                wData = wData + w.toString() + "\n";
            }
        }
        String cfMap = "captureFiles: {";
        if (captureFileMap != null) {
            for (String filename : captureFileMap.keySet()) {
                cfMap += "'" + filename + "': " + captureFileMap.get(filename).toString();
            }
            cfMap += "}";
        }
        return "eventId: " + eventId + "\neventTime: " + getEventTimeString() + "\nlocation: " + location + "\nsystem: " + system
                + "\nClassification: " + classification + "\nnum Waveforms: " + wSize + "\nWaveform Data:\n" + wData + "\n" + cfMap;
    }

    /**
     * Convenience function for converting a primitive double array to a List of
     * Doubles. Maintains order
     *
     * @param a An array of double to be converted
     * @return The resultant list
     */
    private List<Double> convertArrayToList(double[] a) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < a.length; i++) {
            out.add(a[i]);
        }
        return out;
    }

    /**
     * This method uncompresses a compressed waveform event directory and parses
     * it using the same parseWaveformInputStream method as parseWaveformData.
     * The compressed archives should contain a single parent directory with a
     * set of txt files. This method uses the Event's List of CaptureFile
     * objects to know which files to parse.
     *
     * @param eventArchive
     * @param includeData boolean for whether or not the waveforms should
     * include their data
     * @return
     * @throws IOException
     */
    private void parseCompressedWaveformData(List<String> captureFiles, boolean includeData) throws IOException {
        boolean foundParentDir = false;
        String captureFile = null; // If grouped event, this is unnecessary.
        if (!grouped) {
            // Ungrouped events will not have a parent directory, so in essence, we've already found it.
            foundParentDir = true;
            // Since this is ungrouped, we have to pass along the file name.  We identify which file to use based on event info otherwise.
            captureFile = captureFiles.get(0);
        }

        // Track which files we found.  Throw an exception if any are missing
        Map<String, Boolean> fileFound = new HashMap<>();
        for (String file : captureFiles) {
            fileFound.put(file, false);
        }

        try (TarArchiveInputStream ais = new TarArchiveInputStream(
                new GzipCompressorInputStream(Files.newInputStream(getArchivePath(captureFile), StandardOpenOption.READ)));
                BufferedReader br = new BufferedReader(new InputStreamReader(ais))) {
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
                        String filename = Paths.get(entry.getName()).getFileName().toString();
                        if (captureFiles.contains(filename)) {
                            fileFound.put(filename, true);
                            // If this is a grouped event, the entry name will contain the parent directory.  We need only the filename.
                            parseWaveformInputStream(br, filename, includeData);
                        }
                    }
                }
            }
        }

        // Verify that all files were found
        boolean allFound = true;
        List<String> missing = new ArrayList<>();
        for (String file : fileFound.keySet()) {
            if (!fileFound.get(file)) {
                allFound = false;
                missing.add(file);
                LOGGER.log(Level.SEVERE, "Expected Capture File {0} not found in archive {1}", new Object[]{getArchivePath(), file});
            }
        }
        if (!allFound) {
            throw new FileNotFoundException("Files not found in compressed archvie - " + String.join(",", missing));
        }
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
    private void parseWaveformInputStream(BufferedReader br, String filename, boolean includeData) throws IOException {
        String[] headers;
        double[][] out;
        Double sampleStart = null;
        Double sampleStop = null;
        Double sampleStep = null;

        String line;

        List<Metadata> metadataList = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            if (line.matches("#.*")) {
                metadataList.add(Metadata.getMetadataFromCaptureFileLine(line));
            } else {
                break;
            }
        }

        // Check that there is data to process
        if (line == null) {
            return;
        }
        headers = line.split("\\s+");

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
                String[] nums = line.split("\\s+");
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
                System.arraycopy(data[j], 0, out[j], 0, out[j].length);
            }
            sampleStart = out[0][0];
            sampleStop = out[0][out[0].length - 1];
            sampleStep = out[0][1] - out[0][0];
        }

        // Create the capture file if it doesn't exist.  If it doesn't exist, then this event wasn't made with data from the database,
        // so we don't have a capture ID to put here.  If the capture did exist, we just need to add the waveforms if they don't exist
        // and the waveform data if requested
        if (!captureFileMap.containsKey(filename)) {
            CaptureFile cf = new CaptureFile(null, filename, sampleStart, sampleStop, sampleStep);
            cf.addMetadata(metadataList);
            captureFileMap.put(filename, cf);
            updateWaveformsConsistency();
        }

        // Add the waveforms to the captureFile or update the waveforms data if they already exist.
        for (int j = 0; j < out.length; j++) {
            if (j > 0) {
                if (captureFileMap.get(filename).hasWaveform(headers[j])) {
                    captureFileMap.get(filename).updateWaveformData(headers[j], out[0], out[j]);
                } else {
                    captureFileMap.get(filename).addWaveform(new Waveform(headers[j], out[0], out[j]));
                }
            }
        }
    }

    /**
     * Parses all of the data files in the specified event directory. Uses the
     * provided list of filenames to know which files to process
     *
     * @param eventDir
     * @param includeData Should the waveform objects include the data points or
     * only the header information
     * @return A List of Waveform objects representing the contents of the data
     * files in the supplied event directory
     * @throws IOException
     */
    private void parseWaveformData(List<String> captureFiles, boolean includeData) throws IOException {
        // NOTE: We don't need to check that all of these files are found since an exception will be generated if the path
        // doesn't exists when we try to open an new FileInputStream
        // Go through the set of Path objects representing valid data files and parse them.
        Path path;
        for (String filename : captureFiles) {
            path = getEventDirectoryPath().resolve(filename);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(path)))) {
                parseWaveformInputStream(br, filename, includeData);
            }
        }
    }

    /**
     * Add a waveform to both the named captureFile and the Event's waveforms
     * List. Throws if the capture file isn't found in the Event's CaptureFile
     * Map.
     *
     * @param captureFileName The name of the capture file to add
     * @param waveform The waveform object to add to the capture file
     */
    public void addWaveform(String captureFileName, Waveform waveform) {
        if (!captureFileMap.containsKey(captureFileName)) {
            LOGGER.log(Level.SEVERE, "Adding waveform for a capture file unrelated to this event.");
            throw new IllegalArgumentException("Adding waveform for a capture file unrelated to this event");
        }
        captureFileMap.get(captureFileName).addWaveform(waveform);
        updateWaveformsConsistency();
    }

    /**
     * This method writes out the Event's capture file(s) in a tar.gz format. If
     * grouped, the event directory is tar/gziped. If ungrouped, just the
     * singular capture file is tar/gziped.
     *
     * @param os OutputStream to which the data should be written
     * @throws java.io.FileNotFoundException
     */
    public void streamCaptureFiles(OutputStream os) throws FileNotFoundException, IOException {
        if (!isDataOnDisk()) {
            LOGGER.log(Level.SEVERE, "Could not locate data on disk");
            throw new FileNotFoundException("Could not locate data on disk");
        }

        if (Files.exists(getArchivePath())) {
            // The event is archived.  These are tar.gz files of either the single capture file or of the event directory
            InputStream is = new FileInputStream(getArchivePath().toString());
            int numRead;
            byte[] b = new byte[8192];
            while ((numRead = is.read(b)) != -1) {
                os.write(b, 0, numRead);
            }
        } else {
            // The event is not archived, i.e., it's not already in a tar.gz format.
            try (TarArchiveOutputStream taos = new TarArchiveOutputStream(new GzipCompressorOutputStream(os))) {
                File eventDir = this.getEventDirectoryPath().toFile();
                String dirName = eventDir.getName();

                // Add the event dir
                TarArchiveEntry entry = new TarArchiveEntry(eventDir, dirName);
                taos.putArchiveEntry(entry);
                taos.closeArchiveEntry();

                // Add the capture files
                for (CaptureFile cf : captureFileMap.values()) {
                    File file = Paths.get(eventDir.toString(), cf.getFilename()).toFile();

                    // TarArchiveEntry wants a File object representing the file, plus a name field that is the relative path
                    // of the entry in the archive.  This means the name field has to include the name of the event dir.
                    entry = new TarArchiveEntry(file, Paths.get(eventDir.getName(), file.getName()).toString());
                    taos.putArchiveEntry(entry);
                    try (InputStream is = new FileInputStream(file)) {
                        IOUtils.copy(is, taos);
                    }
                    taos.closeArchiveEntry();
                }
                taos.finish();
            }
        }
    }

}
