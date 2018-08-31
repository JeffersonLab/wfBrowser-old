/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.time.Instant;
import java.util.List;

/**
 *
 * @author adamc
 */
public class Event {
    private final Instant eventTime;
    private final String location;
    private final String system;
    private final boolean archive;
    private final List<Waveform> waveforms;

    public Event(Instant eventTime, String location, String system, boolean archive, List<Waveform> waveforms) {
        this.eventTime = eventTime;
        this.location = location;
        this.system = system;
        this.archive = archive;
        this.waveforms = waveforms;
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
    
}
