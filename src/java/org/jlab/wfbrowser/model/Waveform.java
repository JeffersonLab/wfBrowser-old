/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
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

    private final String seriesName;

    private final List<Double> timeOffsets = new ArrayList<>();
    private final List<Double> values = new ArrayList<>();
    private final SortedMap<Double, Double> points = new TreeMap<>();

    public Waveform(String seriesName, List<Double> timeOffsets, List<Double> values) {
        if (timeOffsets.size() != values.size()) {
            throw new IllegalArgumentException("time and value arrays are of unequal length");
        }
        this.seriesName = seriesName;
        for (int i = 0; i < timeOffsets.size(); i++) {
            points.put(timeOffsets.get(i), values.get(i));
        }
    }

    public Waveform(String seriesName) {
        this.seriesName = seriesName;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public Set<Double> getTimeOffsets() {
        return points.keySet();
    }

    public Collection<Double> getValues() {
        return points.values();
    }

    public void addPoint(Double timeOffset, Double value) {
        if (timeOffset == null || value == null) {
            throw new IllegalArgumentException("Attempting to add point to waveform with null value");
        }
        points.put(timeOffset, value);
    }

    /**
     * This toString method provides more information about the contents of the waveform
     * @return A JSON-like string describing the waveform
     */
    @Override
    public String toString() {
        String out = "seriesName: " + seriesName + "\npoints: {";
        for(Double t : points.keySet()) {
            out += "[" + t + "," + points.get(t) + "]";
        }
        out += "}";
        return out;
    }

    public JsonObject toJsonObject() {
        JsonObjectBuilder job = Json.createObjectBuilder()
                .add("series", seriesName);
        JsonArrayBuilder tjab = Json.createArrayBuilder();
        JsonArrayBuilder vjab = Json.createArrayBuilder();
        for(Double t : points.keySet()) {
            tjab.add(t);
            vjab.add(points.get(t));
        }
        job.add("timeOffsets", tjab.build());
        job.add("values", vjab.build());
        return job.build();
    }
}
