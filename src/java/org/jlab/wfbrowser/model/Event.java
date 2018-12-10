package org.jlab.wfbrowser.model;

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
import java.util.Arrays;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
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
     * Check if the waveforms have equivalent timeOffsets and update the
     * areWaveformsConsistent parameter
     *
     * @return void
     */
    private void updateWaveformsConsistency() {
        boolean consistent = true;
        if (waveforms != null && !waveforms.isEmpty()) {
            double[] timeOffsets = null;
            for (Waveform w : waveforms) {
                if (timeOffsets == null) {
                    timeOffsets = w.getTimeOffsets();
                } else if (!Arrays.equals(timeOffsets, w.getTimeOffsets())) {
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
                    .add("archive", archive);
            if (waveforms != null) {
                // Don't add a waveforms parameter if it's null.  That indicates that the waveforms were requested
                JsonArrayBuilder jab = Json.createArrayBuilder();
                for (Waveform w : waveforms) {
                    if (seriesSet != null) {
                        for (String seriesName : seriesSet) {
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
     * @param seriesSet A set of series to include. Include all if null.
     * @return
     */
    private double[][] getWaveformDataAsArray(Set<String> seriesSet) {
        double[][] data;
        if (waveforms == null || waveforms.isEmpty()) {
            return null;
        }

        List<Waveform> wfList = getWaveformList(seriesSet);
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
            // point using a slightly smart method based on NavigableMap's floorKey method that interpolates, but not extrapolates.

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
            data = new double[timeOffsets.size() + 1][wfList.size() + 1];

            // Set up the time offset column
            for (int i = 0, iMax = data.length; i < iMax; i++) {
                data[i][0] = timeOffsets.get(i);
            }

            // Add in all of the waveform series data points
            for (int j = 1, jMax = data[0].length; j < jMax; j++) {
                for (int i = 1, iMax = data.length; i < iMax; i++) {
                    Double value = wfList.get(j - 1).getValueAtOffset(timeOffsets.get(i - 1));
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
    private List<Waveform> getWaveformList(Set<String> seriesSet) {
        List<Waveform> wfList = new ArrayList<>();
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
        for (Waveform w : getWaveformList(seriesSet)) {
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
     * Generate a json object in a way that makes it easy to pass to the dygraph widgets
     * @param seriesSet A set of series names that should be included in the output
     * @return 
     */
    public JsonObject toDyGraphJsonObject(Set<String> seriesSet) {

        JsonObjectBuilder job = Json.createObjectBuilder();
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
                
                for(Waveform w: getWaveformList(seriesSet)) {
                    System.out.println(w.getWaveformName());
                    headerNames.add(w.getWaveformName());
                }

                // Get the timeOffsets
                JsonArrayBuilder tjab = Json.createArrayBuilder();
                for (int i = 0; i < data.length; i++) {
                    tjab.add(data[i][0]);
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
                            wjob.add("dygraphLabel", headerNames.get(i).substring(0, 4));
                            wjob.add("dygraphId", headerNames.get(i).substring(3, 4));

                            // Get the series names for the waveform and add them
                            sjab = Json.createArrayBuilder();
                            for (Series series : w.getSeries()) {
                                sjab.add(series.toJsonObject());
                            }
                            wjob.add("series", sjab.build());

                            // Add the data points for the series.  Can't query the waveform directly in case the waveforms aren't consistent
                            djab = Json.createArrayBuilder();
                            for (int j = 1; j < data.length; j++) {
                                // Since waveformNames is the first row, it's index matches up with the columns of data;
                                djab.add(data[j][i]);
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
    public boolean equals(Object o) {
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
}
