/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model.CaptureFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.Waveform;

/**
 * The waveform harvester saves waveform data in per IOC "capture" files. These
 * capture files contain an optional metadata header section, and a mandatory
 * waveform data section. The first row of the waveform data section contains
 * the names of data columns (time or waveform name). There is additional
 * metadata about the waveforms that is calculated from the data section, and
 * can be used in presenting the data by higher level objects. This includes the
 * initial timestamp, ending timestamp, and the step size between samples.
 *
 * @author adamc
 */
public class CaptureFile {

    private Long captureId = null;
    private final String filename;
    private final SortedMap<String, Waveform> waveformMap = new TreeMap<>();
    private final List<Metadata> metadataList = new ArrayList<>();
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

    public List<Metadata> getMetadataList() {
        return metadataList;
    }

    public boolean addMetadata(Metadata m) {
        return metadataList.add(m);
    }

    public boolean addMetadata(List<Metadata> mList) {
        return metadataList.addAll(mList);
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

    /**
     * Create a JSON object representation of this capture file. Include only
     * waveforms who match at least one of the specified series names. Don't
     * filter on series if the set is null.
     *
     * @param seriesSet The names of series to include in the waveform output.  Null if all should be included.
     * @return
     */
    public JsonObject toJsonObject(Set<String> seriesSet) {
        JsonObjectBuilder job = Json.createObjectBuilder()
                .add("filename", filename)
                .add("sample_start", sampleStart)
                .add("sample_end", sampleEnd)
                .add("sample_step", sampleStep);
        JsonArrayBuilder jab = Json.createArrayBuilder();
        for (String name : waveformMap.keySet()) {
            Waveform w = waveformMap.get(name);
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
        JsonArrayBuilder mJab = Json.createArrayBuilder();
        for (Metadata m : metadataList) {
            mJab.add(m.toJsonObject());
        }
        job.add("metadata", mJab.build());
        job.add("waveforms", jab.build());
        return job.build();
    }

    @Override
    public String toString() {
        return toJsonObject(null).toString();
    }
}
