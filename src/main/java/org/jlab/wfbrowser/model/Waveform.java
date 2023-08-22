package org.jlab.wfbrowser.model;

import java.util.ArrayList;
import java.util.Arrays;
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

    private Long waveformId = null;
    private final String waveformName;
    private final List<Series> seriesList = new ArrayList<>();
    private double[] timeOffsets;
    private double[] values;

    /**
     * Create a waveform object from arrays of primitive doubles. Preferred
     * since there is no boxing or copying costs. The array references used
     * directly.
     *
     * @param waveformName The waveform name
     * @param timeOffsets An a array of time offsets
     * @param values An array of values
     */
    public Waveform(String waveformName, double[] timeOffsets, double[] values) {
        if (timeOffsets.length != values.length) {
            throw new IllegalArgumentException("time and value arrays are of unequal length");
        }
        this.waveformName = waveformName;
        this.timeOffsets = timeOffsets;
        this.values = values;
    }

    /**
     * Create a waveform object where the data will be added later.
     *
     * @param waveformId Database ID of the waveform.
     * @param waveformName The name of the waveform.
     */
    public Waveform(Long waveformId, String waveformName) {
        this.waveformId = waveformId;
        this.waveformName = waveformName;

        this.timeOffsets = new double[0];
        this.values = new double[0];
    }

    /**
     * Replaces the existing data arrays with new copies. Useful when adding the
     * data after constructing the waveform.
     *
     * @param timeOffsets
     * @param values
     */
    public void updateData(double[] timeOffsets, double[] values) {
        if (timeOffsets.length != values.length) {
            throw new IllegalArgumentException("time and value arrays are of unequal length");
        }
        this.timeOffsets = timeOffsets;
        this.values = values;
    }

    /**
     * This adds multiple of series to the waveform's series list.
     *
     * @param seriesList A list of series to add the to waveform
     */
    public void addSeries(List<Series> seriesList) {
        if (seriesList != null) {
            this.seriesList.addAll(seriesList);
        }
    }

    public boolean addSeries(Series series) {
        return seriesList.add(series);
    }

    public List<Series> getSeries() {
        return seriesList;
    }

    public Long getWaveformId() {
        return waveformId;
    }
    
    public String getWaveformName() {
        return waveformName;
    }

    public double[] getTimeOffsets() { return timeOffsets; }

    public double[] getValues() { return values; }

    /**
     * This method is returns the value of a waveform at a given offset, and
     * allows for values to be queried for times when the buffer did not sample.
     * This is useful for "filling in" if waveforms were not sampled at exactly
     * the same offset values. If the requested offset is after the last point
     * or before the first point, null is returned.
     *
     * @param timeOffset
     * @return The waveform value of the nearest preceding point prior to
     * timeOffset. If timeOffset is after the last point in the waveform or if
     * the waveform has no value defined there, then NaN is returned.
     */
    public double getValueAtOffset(double timeOffset) {
        int floorIndex = floorIndexSearch(timeOffsets, 0, timeOffsets.length - 1, timeOffset);
        if (floorIndex == -1) {
            return Double.NaN;
        }
        return values[floorIndex];
    }

    /**
     * Find the index of arr that is the floor of the requested value x (index
     * of arr where arr[index] is largest value in arr that is still less than
     * or equal to x) using a recursive binary search. If value is outside of
     * specified low/high range, return -1;
     *
     * @param arr array of doubles
     * @param low low point of this iteration of the binary search
     * @param high high point of this iteration the binary search
     * @param x the value for which we want the floor index
     * @return The floor index or -1 if it is outside the bounds of the array
     */
    private int floorIndexSearch(double arr[], int low, int high, double x) {
        // Check boundary conditions
        int out = -1;
        boolean terminate = false;
        if (x > arr[high]) {
            // If it is after the waveform timeOffsets, return -1
            out = -1;
            terminate = true;
        } else if (Double.compare(x, arr[high]) == 0) {
            // If it is the last point, return it
            out = high;
            terminate = true;
        } else if (x < arr[low]) {
            // If it is before the waveform timeOffsets, return -1
            out = -1;
            terminate = true;
        } else if (Double.compare(x, arr[low]) == 0) {
            // If it is the first point return it
            out = low;
            terminate = true;
        } else if (low + 1 == high){
            // We have narrowed this down completely and found the floor
            out = low;
            terminate = true;
        }

        // We haven't hit a short circuit if we've hit a boundary condition.  Do the binary search thing.
        if (!terminate) {
            int mid = (low + high) / 2;
            if (Double.compare(x, arr[mid]) == 0) {
                out = mid;
            } else if (x > arr[mid]) {
                // do the search again setting low = mid;
                out = floorIndexSearch(arr, mid, high, x);
            } else {
                // Since x != arr[mid] and ! x > arr[mid], then x < arr[mid]
                // do the search again setting low = mid;
                out = floorIndexSearch(arr, low, mid, x);
            }
        }
        return out;
    }

    /**
     * This toString method provides more information about the contents of the
     * waveform
     *
     * @return A JSON-like string describing the waveform
     */
    @Override
    public String toString() {
        List<String> seriesJson = new ArrayList<>();
        for (Series series : seriesList) {
            seriesJson.add(series.toJsonObject().toString());
        }

        String out = "waveformName: " + waveformName
                + "\nseries: [" + String.join(",", seriesJson) + "]\n";
        out += "points: {";
        for (int i = 0; i < values.length; i++) {
            out += "[" + timeOffsets[i] + "," + values[i] + "]";
        }
        out += "}";
        return out;
    }

    /**
     * Create a generic json object representing the Event object
     *
     * @return A JsonObject representing the Event object
     */
    public JsonObject toJsonObject() {
        JsonObjectBuilder job = Json.createObjectBuilder()
                .add("waveformName", waveformName);
        JsonArrayBuilder sjab = Json.createArrayBuilder();
        for (Series series : seriesList) {
            sjab.add(series.toJsonObject());
        }
        job.add("series", sjab.build());
        JsonArrayBuilder tjab = Json.createArrayBuilder();
        JsonArrayBuilder vjab = Json.createArrayBuilder();
        for (int i = 0; i < values.length; i++) {
            tjab.add(timeOffsets[i]);
            vjab.add(values[i]);
        }
        job.add("timeOffsets", tjab.build());
        job.add("values", vjab.build());
        return job.build();
    }
}
