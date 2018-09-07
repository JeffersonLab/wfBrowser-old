/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

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
    private final List<Waveform> waveforms;

    public Event(long eventId, Instant eventTime, String location, String system, boolean archive, List<Waveform> waveforms) {
        this.eventId = eventId;
        this.eventTime = eventTime.truncatedTo(ChronoUnit.MICROS);
        this.location = location;
        this.system = system;
        this.archive = archive;
        this.waveforms = waveforms;
    }

    public Event(Instant eventTime, String location, String system, boolean archive, List<Waveform> waveforms) {
        this.eventTime = eventTime.truncatedTo(ChronoUnit.MICROS);
        this.location = location;
        this.system = system;
        this.archive = archive;
        this.waveforms = waveforms;
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

    public String getEventTimeString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);
        return formatter.format(eventTime);
    }

    /**
     * Events are considered equal if all of the metadata about the event are
     * equal. We currently do not enforce equality of waveform data as the
     * database should maintain uniqueness of event IDs and it's related data.
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
        return eventId == null ? null == e.getEventId() : eventId.equals(e.getEventId()) && eventTime.equals(e.getEventTime()) && location.equals(e.getLocation()) && system.equals(e.getSystem())
                && archive == e.isArchive() && waveforms.size() == e.getWaveforms().size();
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
        String wData = "";
        for(Waveform w: waveforms) {
            wData = wData + w.toString() + "\n";
        }
        return "eventId: " + eventId + "\neventTime: " + getEventTimeString() + "\nlocation: " + location + "\nsystem: " + system
                + "\nnum Waveforms: " + waveforms.size() + "\nWaveform Data:\n" + wData;
    }
}
