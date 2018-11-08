/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.math.BigDecimal;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.json.JsonNumber;
import org.jlab.wfbrowser.business.util.TimeUtil;

/**
 * Object for representing and waveform triggering event. The eventId is
 * optional as an Event object needs to be created before it is added to the
 * database, and the eventId is only created in the database once the event is
 * added. While not strictly enforced, an eventId value of null should imply
 * that the Event object was not read from the database. Since the database can
 * only handle microsecond resolution, we truncate event times to that here.
 * This helps with testing.
 *
 * @author adamc
 */
public class Event {

    private Long eventId = null;
    private final Instant eventTime;
    private final String location;
    private final String system;
    private final boolean archive;
    private final boolean delete;
    private final List<Waveform> waveforms;
    private boolean areWaveformsConsistent = true;  // If all waveforms have the same set of time offsets.  Simplies certain data operaitons.

    public Event(long eventId, Instant eventTime, String location, String system, boolean archive, boolean delete, List<Waveform> waveforms) {
        this.eventId = eventId;
        this.eventTime = eventTime.truncatedTo(ChronoUnit.MICROS);
        this.location = location;
        this.system = system;
        this.archive = archive;
        this.delete = delete;
        this.waveforms = waveforms;

        updateWaveformsConsistency();
    }

    public Event(Instant eventTime, String location, String system, boolean archive, boolean delete, List<Waveform> waveforms) {
        this.eventTime = eventTime.truncatedTo(ChronoUnit.MICROS);
        this.location = location;
        this.system = system;
        this.archive = archive;
        this.delete = delete;
        this.waveforms = waveforms;

        updateWaveformsConsistency();
    }

    /**
     * Check if the waveforms are consistent
     *
     * @return Whether the booleans
     */
    private void updateWaveformsConsistency() {
        boolean consistent = true;
        if (waveforms != null && !waveforms.isEmpty()) {
            Set<Double> timeOffsets = new HashSet<>();
            for (Waveform w : waveforms) {
                if (timeOffsets.isEmpty()) {
                    timeOffsets.addAll(w.getTimeOffsets());
                } else if (!timeOffsets.equals(w.getTimeOffsets())) {
                    consistent = false;
                    break;
                }
            }
        }
        areWaveformsConsistent = consistent;
    }

    public boolean isDelete() {
        return delete;
    }

    public Path getRelativeFilePath() {
        DateTimeFormatter dFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd").withZone(ZoneId.systemDefault());
        DateTimeFormatter tFormatter = DateTimeFormatter.ofPattern("HHmmss.S").withZone(ZoneId.systemDefault());
        String day = dFormatter.format(eventTime);
        String time = tFormatter.format(eventTime);

        return Paths.get(system, location, day, time);
    }

    public Path getRelativeArchivePath() {
        DateTimeFormatter dFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd").withZone(ZoneId.systemDefault());
        DateTimeFormatter tFormatter = DateTimeFormatter.ofPattern("HHmmss.S").withZone(ZoneId.systemDefault());
        String day = dFormatter.format(eventTime);
        String time = tFormatter.format(eventTime);

        return Paths.get(Paths.get(system, location, day, time).toString() + ".tar.gz");
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

    public List<Waveform> getWaveforms() {
        return waveforms;
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
     * @param seriesList If not null, only include waveforms who's listed
     * seriesNames includes at least of the series in the list.
     * @return
     */
    public JsonObject toJsonObject(List<String> seriesList) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        if (eventId != null) {
            job.add("id", eventId)
                    .add("datetime_utc", TimeUtil.getDateTimeString(eventTime))
                    .add("location", location)
                    .add("system", system)
                    .add("archive", archive);
            if (waveforms != null) {
                // Don't add a waveforms parameter if it's null.  That indicates that the waveforms were requested
                JsonArrayBuilder jab = Json.createArrayBuilder();
                for (Waveform w : waveforms) {
                    if (seriesList != null) {
                        for (String seriesName : seriesList) {
                            for (Series series : w.getSeries()) {
                                if (series.getName().equals(seriesName)) {
                                    jab.add(w.toJsonObject());
                                    break;
                                }
                            }
                        }
                    } else {
                        jab.add(w.toJsonObject());
                    }
                }
                job.add("waveforms", jab.build());
            }
        } else {
            // Should never try to send out a response on an "Event" that didn't come from the database.  Full stop if we try.
            throw new RuntimeException("Cannot return event without database event ID");
        }
        return job.build();
    }

    public String getEventTimeString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S").withZone(ZoneOffset.UTC);
        return formatter.format(eventTime);
    }

    /**
     * This method processes the list of individual waveforms in a 2D array that
     * makes additional manipulations much easier. The first row contains the
     * waveform names and the first column contains the time_offset values. If
     * the waveforms are "consistent", i.e., they have the same set of time
     * offsets, then the first set of time offsets are used and all "data"
     * values are added in order. If the waveforms are not "consistent", then a
     * new "master" list of time offsets are constructed and the "blanks" filled
     * in for each waveform via step-wise interpolation. Optionally, a list of
     * specific series can be requested by supplying a non-null list of strings.
     *
     * @param seriesList A list of series to include. Include all if null.
     * @return
     */
    private String[][] getWaveformDataAsArray(List<String> seriesList) {
        String[][] data;
        if (waveforms == null || waveforms.isEmpty()) {
            return null;
        }

        List<Waveform> wfList = new ArrayList<>();
        for (Waveform waveform : waveforms) {
            if (seriesList != null) {
                for (String seriesName : seriesList) {
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
        if (wfList.isEmpty()) {
            return null;
        }

        if (areWaveformsConsistent) {
            // 2D array for hold csv content - [rows][columns]
            // +1 rows because of the header, +1 columns because of the time_offset column
            data = new String[wfList.get(0).getTimeOffsets().size() + 1][wfList.size() + 1];

            // Setup the header row
            data[0][0] = "time_offset";
            for (int j = 1, jMax = data[0].length; j < jMax; j++) {
                data[0][j] = wfList.get(j - 1).getWaveformName(); // j-1 since j-index includes the "time_offset" series
            }

            // Set up the time offset column
            List<Double> tos = new ArrayList<>(wfList.get(0).getTimeOffsets());
            for (int i = 1, iMax = data.length; i < iMax; i++) {
                data[i][0] = tos.get(i - 1).toString();
            }

            // Add in all of the waveform series information
            for (int j = 1, jMax = data[0].length; j < jMax; j++) {
                int i = 1;
                Iterator<Double> it = wfList.get(j - 1).getValues().iterator();
                while (it.hasNext()) {
                    data[i][j] = it.next().toString();
                    i++;
                }
            }

        } else {
            // The waveforms are not consistent in that they do not all have the same set of time offsets.  Instead of just grabbing
            // the timeoffsets from one waveform and pulling the values from all in order, we need to compile the full set of time
            // offsets from all of the waveforms.  Then for each offset, see what the value would have been for a waveform at that
            // point using a slightly smart method based on NavigableMap's floorKey method that interpolates, but not extrapolates.

            // Get all of the time offset value from all of the waveforms and put them in a sorted set.  Manually convert the set to
            // a list to ensure the order is maintained (SortedSet returns value in sorted order, and ArrayList maintains insertion
            // order.
            SortedSet<Double> timeOffsetSet = new TreeSet<>();
            for (Waveform w : wfList) {
                timeOffsetSet.addAll(w.getTimeOffsets());
            }
            List<Double> timeOffsets = new ArrayList<>();
            for (Double t : timeOffsetSet) {
                timeOffsets.add(t);
            }

            // 2D array for hold csv content - [rows][columns]
            // +1 rows because of the header, +1 columns because of the time_offset column
            data = new String[timeOffsets.size() + 1][wfList.size() + 1];

            // Setup the header row
            data[0][0] = "time_offset";
            for (int j = 1, jMax = data[0].length; j < jMax; j++) {
                data[0][j] = wfList.get(j - 1).getWaveformName(); // j-1 since j-index includes the "time_offset" series
            }

            // Set up the time offset column
            for (int i = 1, iMax = data.length; i < iMax; i++) {
                data[i][0] = timeOffsets.get(i - 1).toString();
            }

            // Add in all of the waveform series information
            for (int j = 1, jMax = data[0].length; j < jMax; j++) {
                for (int i = 1, iMax = data.length; i < iMax; i++) {
                    Double value = wfList.get(j - 1).getValueAtOffset(timeOffsets.get(i - 1));
                    data[i][j] = value == null ? "" : value.toString();
                }
            }
        }
        return data;
    }

    /**
     * Generate the contents of a CSV file that represents the waveform event
     *
     * @param seriesList A list of the named series that should be included
     * @return A string representation of a CSV file representing the waveform
     * event.
     */
    public String toCsv(List<String> seriesList) {
        String[][] csvData = getWaveformDataAsArray(seriesList);

        String csvOut;

        // Generate the string representation of the CSV
        List<String> csvRows = new ArrayList<>();
        for (int i = 0, iMax = csvData.length; i < iMax; i++) {
            csvRows.add(String.join(",", csvData[i]));
        }
        csvOut = String.join("\n", csvRows);
        csvOut += "\n";

        return csvOut;
    }

    public JsonObject toDyGraphJsonObject(List<String> seriesList) {

        JsonObjectBuilder job = Json.createObjectBuilder();
        if (eventId != null) {
            job.add("id", eventId)
                    .add("datetime_utc", TimeUtil.getDateTimeString(eventTime))
                    .add("location", location)
                    .add("system", system)
                    .add("archive", archive);
            if (waveforms != null) {
                String[][] data = getWaveformDataAsArray(seriesList);

                String[] waveformNames = data[0];

                // Get the timeOffsets
                JsonArrayBuilder tjab = Json.createArrayBuilder();
                for (int i = 1; i < data.length; i++) {
                    if (data[i][0] == null || data[i][0].isEmpty()) {
                        tjab.add(Double.NaN);
                    } else {
                        tjab.add(Double.parseDouble(data[i][0]));
                    }
                }
                job.add("timeOffsets", tjab.build());

                // Don't add a waveforms parameter if it's null.  That indicates that the waveforms were requested
                JsonArrayBuilder wjab = Json.createArrayBuilder();
                JsonArrayBuilder sjab, djab;
                JsonObjectBuilder wjob;
                for (int i = 1; i < waveformNames.length; i++) {
                    for (Waveform w : waveforms) {
                        if (w.getWaveformName().equals(waveformNames[i])) {
                            wjob = Json.createObjectBuilder().add("waveformName", waveformNames[i]);

                            // Add some information that the client side can cue off of for consitent colors and names.
                            wjob.add("dygraphLabel", waveformNames[i].substring(0, 4));
                            wjob.add("dygraphId", waveformNames[i].substring(3, 4));

                            // Get the series names for the waveform and add them
                            sjab = Json.createArrayBuilder();
                            for (Series series : w.getSeries()) {
                                sjab.add(series.toJsonObject());
//                                sjab.add(series.getName());
                            }
//                            wjob.add("seriesNames", sjab.build());
                            wjob.add("series", sjab.build());

                            // Add the data points for the series.  Can't query the waveform directly in case the waveforms aren't consistent
                            djab = Json.createArrayBuilder();
                            for (int j = 1; j < data.length; j++) {
                                // Since waveformNames is the first row, it's index matches up with the columns of data;
                                if (data[j][i] == null || data[j][i].isEmpty()) {
                                    djab.add(JsonNumber.NULL);
                                } else {
                                    djab.add(Double.parseDouble(data[j][i]));
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
     * Events are considered equal if all of the metadata about the event are
     * equal. We currently do not enforce equality of waveform data, only that
     * an event has the same number of waveforms. The database should maintain
     * uniqueness of event IDs and it's related data.
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o
    ) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Event)) {
            return false;
        }

        Event e = (Event) o;
        boolean isEqual = true;
        isEqual = isEqual && (eventId == null ? e.getEventId() == null : eventId.equals(e.getEventId()));
        if (e.getWaveforms() != null || waveforms != null) {
            if (e.getWaveforms() == null || waveforms == null) {
                isEqual = false;
            } else {
                isEqual = isEqual && (waveforms.size() == e.getWaveforms().size());
            }
        }
        isEqual = isEqual && (eventTime.equals(e.getEventTime()));
        isEqual = isEqual && (location.equals(e.getLocation()));
        isEqual = isEqual && (system.equals(e.getSystem()));
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
        hash = 11 * hash + (this.archive ? 1 : 0);
        return hash;
    }

    @Override
    public String toString() {
        String wData = null;
        String wSize = "null";
        if (waveforms != null) {
            wData = "";
            wSize = "" + waveforms.size();
            for (Waveform w : waveforms) {
                wData = wData + w.toString() + "\n";
            }
        }
        return "eventId: " + eventId + "\neventTime: " + getEventTimeString() + "\nlocation: " + location + "\nsystem: " + system
                + "\nnum Waveforms: " + wSize + "\nWaveform Data:\n" + wData;
    }
}
