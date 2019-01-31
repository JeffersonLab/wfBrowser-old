/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 *
 * @author adamc
 */
public class CaptureFile {
    
    private Long captureId = null;
    private final String filename;
    private final SortedMap<String, Waveform> waveformMap = new TreeMap<>();
    private final double sampleStart;
    private final double sampleEnd;
    private final double sampleStep;
    
    public CaptureFile(String filename, List<Waveform> waveforms, double sampleStart, double sampleEnd, double sampleStep) {
        this.filename = filename;
        for (Waveform w : waveforms) {
            waveformMap.put(w.getWaveformName(), w);
        }
        this.sampleStart = sampleStart;
        this.sampleEnd = sampleEnd;
        this.sampleStep = sampleStep;
    }

    /**
     * Constructor for situation where we have a database ID, and will add
     * waveforms after the object has been constructed.
     *
     * @param captureId
     * @param filename
     * @param sampleStart
     * @param sampleEnd
     * @param sampleStep
     */
    public CaptureFile(Long captureId, String filename, Double sampleStart, Double sampleEnd, Double sampleStep) {
        this.captureId = captureId;
        this.filename = filename;
        this.sampleStart = sampleStart;
        this.sampleEnd = sampleEnd;
        this.sampleStep = sampleStep;
    }
    
    public Long getCaptureId() {
        return captureId;
    }
    
    public String getFilename() {
        return filename;
    }

    /**
     * Returns a copy of the internal waveform list
     *
     * @return a copy of the internal waveform list
     */
    public List<Waveform> getWaveforms() {
        List<Waveform> out = new ArrayList<>();
        for (String name : waveformMap.keySet()) {
            out.add(waveformMap.get(name));
        }
        return out;
    }

    /**
     * Add a single waveform
     *
     * @param waveform A waveform object to be associated with the capture file
     */
    public void addWaveform(Waveform waveform) {
        waveformMap.put(waveform.getWaveformName(), waveform);
    }

    /**
     * Does this CaptureFile contain a waveform that matches the name of the
     * supplied waveform.
     *
     * @param waveformName
     * @return
     */
    public boolean hasWaveform(String waveformName) {
        return waveformMap.containsKey(waveformName);
    }

    /**
     * Update the data on the specified waveform. Should check that this
     * waveform exists in this CaptureFile prior.
     *
     * @param waveformName
     * @param timeOffsets
     * @param values
     */
    public void updateWaveformData(String waveformName, double[] timeOffsets, double[] values) {
        waveformMap.get(waveformName).updateData(timeOffsets, values);
    }
    
    public void applySeriesMapping(Map<String, List<Series>> seriesMapping) {
        for (String name : waveformMap.keySet()) {
            if (seriesMapping.containsKey(name)) {
                waveformMap.get(name).addSeries(seriesMapping.get(name));
            }
        }
    }
    
    public Double getSampleStart() {
        return sampleStart;
    }
    
    public Double getSampleEnd() {
        return sampleEnd;
    }
    
    public Double getSampleStep() {
        return sampleStep;
    }
    
    public JsonObject toJsonObject() {
        JsonObjectBuilder job = Json.createObjectBuilder()
                .add("filename", filename)
                .add("sample_start", sampleStart)
                .add("sample_end", sampleEnd)
                .add("sample_step", sampleStep);
        JsonArrayBuilder jab = Json.createArrayBuilder();
        for (String name : waveformMap.keySet()) {
            Waveform w = waveformMap.get(name);
            jab.add(w.toJsonObject());
        }
        return job.add("waveforms", jab.build()).build();
    }
    
    @Override
    public String toString() {
        return toJsonObject().toString();
    }
}
