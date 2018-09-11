/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.util.ArrayList;
import java.util.List;
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

    public String getSeriesName() {
        return seriesName;
    }
    private final List<Double> timeOffsets = new ArrayList<>();
    private final List<Double> values = new ArrayList<>();

    public Waveform(String seriesName, List<Double> timeOffsets, List<Double> values) {
        if (timeOffsets.size() != values.size()) {
            throw new IllegalArgumentException("time and value arrays are of unequal length");
        }
        this.seriesName = seriesName;
        this.timeOffsets.addAll(timeOffsets);
        this.values.addAll(values);
    }

    public Waveform(String seriesName) {
        this.seriesName = seriesName;
    }

    public List<Double> getTimeOffsets() {
        return timeOffsets;
    }

    public List<Double> getValues() {
        return values;
    }

    public void addPoint(Double timeOffset, Double value) {
        if (timeOffset == null || value == null) {
            throw new IllegalArgumentException("Attempting to add point to waveform with null value");
        }
        timeOffsets.add(timeOffset);
        values.add(value);
    }

    public String toString() {
        String out = "seriesName: " + seriesName + "\npoints: {";
        for (int i = 0; i < timeOffsets.size(); i++) {
            out = out + " [" + timeOffsets.get(i) + "," + values.get(i) + "]";
        }
        out = out + "}";
        return out;
    }

    public JsonObject toJsonObject() {
        JsonObjectBuilder wjob = Json.createObjectBuilder()
                .add("series", seriesName);
        JsonArrayBuilder tjab = Json.createArrayBuilder();
        for (Double t : timeOffsets) {
            tjab.add(t);
        }
        wjob.add("timeOffsets", tjab.build());
        JsonArrayBuilder vjab = Json.createArrayBuilder();
        for (Double v : values){
            vjab.add(v);
        }
        wjob.add("values", vjab.build());
        return wjob.build();
    }
}
