/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.io.BufferedOutputStream;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
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
     * Generate a json object representing an event. Only include a waveforms
     * parameter/array if the waveforms list isn't null. Only use this method if
     * you're returning a Event that came from the data or has an associated
     * database event_id value, since it doesn't make any sense to hand out
     * "unofficial" data through one of our data API end points.
     *
     * @return
     */
    public JsonObject toJsonObject() {
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
                    jab.add(w.toJsonObject());
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);
        return formatter.format(eventTime);
    }

    /**
     * Generate the contents of a CSV file that represents the waveform event
     *
     * @return A string representation of a CSV file representing the waveform
     * event.
     */
    public String toCsv() {

        if (waveforms == null || waveforms.isEmpty()) {
            return null;
        }

        String csvOut;

        if (areWaveformsConsistent) {
            // 2D array for hold csv content - [rows][columns]
            // +1 rows because of the header, +1 columns because of the time_offset column
            String[][] csvData = new String[waveforms.get(0).getTimeOffsets().size() + 1][waveforms.size() + 1];

            // Setup the header row
            csvData[0][0] = "time_offset";
            for (int j = 1, jMax = csvData[0].length; j < jMax; j++) {
                csvData[0][j] = waveforms.get(j - 1).getSeriesName(); // j-1 since j-index includes the "time_offset" series
            }

            // Set up the time offset column
            List<Double> tos = new ArrayList<>(waveforms.get(0).getTimeOffsets());
            for (int i = 1, iMax = csvData.length; i < iMax; i++) {
                csvData[i][0] = tos.get(i - 1).toString();
            }

            // Add in all of the waveform series information
            for (int j = 1, jMax = csvData[0].length; j < jMax; j++) {
                int i = 1;
                Iterator<Double> it = waveforms.get(j - 1).getValues().iterator();
                while (it.hasNext()) {
                    csvData[i][j] = it.next().toString();
                    i++;
                }
            }

            // Generate the string representation of the CSV
            List<String> csvRows = new ArrayList<>();
            for (int i = 0, iMax = csvData.length; i < iMax; i++) {
                csvRows.add(String.join(",", csvData[i]));
            }
            csvOut = String.join("\n", csvRows);
            csvOut += "\n";
        } else {  // The waveforms are not consistent.  Uses a slower general method involving "lookups" for compiling the CSV

            System.out.println("Using generic approach");
            SortedSet<Double> timeOffsetSet = new TreeSet<>();
            for (Waveform w : waveforms) {
                timeOffsetSet.addAll(w.getTimeOffsets());
            }
            List<Double> timeOffsets = new ArrayList<>();
            for (Double t : timeOffsetSet) {
                timeOffsets.add(t);
            }

            // 2D array for hold csv content - [rows][columns]
            // +1 rows because of the header, +1 columns because of the time_offset column
            String[][] csvData = new String[timeOffsets.size() + 1][waveforms.size() + 1];

            // Setup the header row
            csvData[0][0] = "time_offset";
            for (int j = 1, jMax = csvData[0].length; j < jMax; j++) {
                csvData[0][j] = waveforms.get(j - 1).getSeriesName(); // j-1 since j-index includes the "time_offset" series
            }

            // Set up the time offset column
            List<Double> tos = new ArrayList<>(waveforms.get(0).getTimeOffsets());
            for (int i = 1, iMax = csvData.length; i < iMax; i++) {
                csvData[i][0] = tos.get(i - 1).toString();
            }

            // Add in all of the waveform series information
            for (int j = 1, jMax = csvData[0].length; j < jMax; j++) {
                for (int i = 1, iMax = csvData.length; i < iMax; i++) {
                    csvData[i][j] = waveforms.get(j-1).getValueAtOffset(timeOffsets.get(i-1)).toString();
                }
                int i = 1;
                Iterator<Double> it = waveforms.get(j - 1).getValues().iterator();
                while (it.hasNext()) {
                    csvData[i][j] = it.next().toString();
                    i++;
                }
            }

            // Generate the string representation of the CSV
            List<String> csvRows = new ArrayList<>();
            for (int i = 0, iMax = csvData.length; i < iMax; i++) {
                csvRows.add(String.join(",", csvData[i]));
            }
            csvOut = String.join("\n", csvRows);
            csvOut += "\n";
        }
        return csvOut;
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
        String wData = "null";
        String wSize = "null";
        if (waveforms != null) {
            wSize = "" + waveforms.size();
            for (Waveform w : waveforms) {
                wData = wData + w.toString() + "\n";
            }
        }
        return "eventId: " + eventId + "\neventTime: " + getEventTimeString() + "\nlocation: " + location + "\nsystem: " + system
                + "\nnum Waveforms: " + wSize + "\nWaveform Data:\n" + wData;
    }
}
