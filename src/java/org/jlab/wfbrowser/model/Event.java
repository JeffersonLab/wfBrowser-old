/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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

    public Event(long eventId, Instant eventTime, String location, String system, boolean archive, boolean delete, List<Waveform> waveforms) {
        this.eventId = eventId;
        this.eventTime = eventTime.truncatedTo(ChronoUnit.MICROS);
        this.location = location;
        this.system = system;
        this.archive = archive;
        this.delete = delete;
        this.waveforms = waveforms;
    }

    public Event(Instant eventTime, String location, String system, boolean archive, boolean delete, List<Waveform> waveforms) {
        this.eventTime = eventTime.truncatedTo(ChronoUnit.MICROS);
        this.location = location;
        this.system = system;
        this.archive = archive;
        this.delete = delete;
        this.waveforms = waveforms;
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
                    .add("datetime", TimeUtil.getDateTimeString(eventTime))
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
