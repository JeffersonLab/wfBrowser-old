/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * This an object just to represent waveform data. The object can be
 * instantiated with or without supplying data, and supports adding
 * timeOffset/value pairs. Should be used in conjunction with a "parent" Event
 * object that contains information system location, and waveform trigger
 * "event" time.
 *
 * @author adamc
 */
public class Waveform {

    private final String waveformName;
    private final List<String> seriesNames = new ArrayList<>();
    private final NavigableMap<Double, Double> points = new TreeMap<>();

    public Waveform(String seriesName, List<Double> timeOffsets, List<Double> values) {
        if (timeOffsets.size() != values.size()) {
            throw new IllegalArgumentException("time and value arrays are of unequal length");
        }
        this.waveformName = seriesName;
        for (int i = 0; i < timeOffsets.size(); i++) {
            points.put(timeOffsets.get(i), values.get(i));
        }
    }

    public void applyWaveformToSeriesMappings(Map<String, List<String>> mapping) {
        if (mapping.get(waveformName) != null) {
            seriesNames.addAll(mapping.get(waveformName));
        }
    }

    public boolean addSeriesName(String seriesName) {
        return seriesNames.add(seriesName);
    }

    public List<String> getSeriesNames() {
        return seriesNames;
    }

    public Waveform(String seriesName) {
        this.waveformName = seriesName;
    }

    public String getWaveformName() {
        return waveformName;
    }

    public Set<Double> getTimeOffsets() {
        return points.keySet();
    }

    public Collection<Double> getValues() {
        return points.values();
    }

    /**
     * This method is returns the value of a waveform at a given offset, and
     * allows for values to be queried for times when the buffer did not sample.
     * This is useful for "filling in" if waveforms were not sampled at exactly
     * the same offset values. If the requested offset is after the last point
     * or before the first point, null is returned.
     *
     * @param timeOffset
     * @return
     */
    public Double getValueAtOffset(Double timeOffset) {
        Double last = points.lastKey();
        if (timeOffset > last) {
            return null;
        }

        // Check if the offset is directly represented.  I believe this is O(1).
        if (points.containsKey(timeOffset)) {
            return points.get(timeOffset);
        }

        // Now we have to go searching for the point if it exists.
        Entry<Double, Double> entry = points.floorEntry(timeOffset);
        return entry == null ? null : entry.getValue();
    }

    public void addPoint(Double timeOffset, Double value) {
        if (timeOffset == null || value == null) {
            throw new IllegalArgumentException("Attempting to add point to waveform with null value");
        }
        points.put(timeOffset, value);
    }

    /**
     * This toString method provides more information about the contents of the
     * waveform
     *
     * @return A JSON-like string describing the waveform
     */
    @Override
    public String toString() {
        String out = "seriesName: " + waveformName + "\npoints: {";
        for (Double t : points.keySet()) {
            out += "[" + t + "," + points.get(t) + "]";
        }
        out += "}";
        return out;
    }

    public JsonObject toJsonObject() {
        JsonObjectBuilder job = Json.createObjectBuilder()
                .add("waveformName", waveformName);
        JsonArrayBuilder sjab = Json.createArrayBuilder();
        for (String name : seriesNames) {
            sjab.add(name);
        }
        job.add("seriesNames", sjab.build());
        JsonArrayBuilder tjab = Json.createArrayBuilder();
        JsonArrayBuilder vjab = Json.createArrayBuilder();
        for (Double t : points.keySet()) {
            tjab.add(t);
            vjab.add(points.get(t));
        }
        job.add("timeOffsets", tjab.build());
        job.add("values", vjab.build());
        return job.build();
    }
}
