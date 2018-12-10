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

    private final String waveformName;
    private final List<Series> seriesList = new ArrayList<>();
    private final double[] timeOffsets;
    private final double[] values;

    public Waveform(String waveformName, List<Double> timeOffsets, List<Double> values) {
        if (timeOffsets.size() != values.size()) {
            throw new IllegalArgumentException("time and value arrays are of unequal length");
        }
        this.waveformName = waveformName;

        this.timeOffsets = new double[timeOffsets.size()];
        this.values = new double[values.size()];
        for (int i = 0; i < timeOffsets.size(); i++) {
            this.timeOffsets[i] = timeOffsets.get(i);
            this.values[i] = values.get(i);
        }
    }

    public Waveform(String waveformName, double[] timeOffsets, double[] values) {
        if (timeOffsets.length != values.length) {
            throw new IllegalArgumentException("time and value arrays are of unequal length");
        }
        this.waveformName = waveformName;

        this.timeOffsets = new double[timeOffsets.length];
        this.values = new double[values.length];
        for (int i = 0; i < timeOffsets.length; i++) {
            this.timeOffsets[i] = timeOffsets[i];
            this.values[i] = values[i];
        }
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

    public String getWaveformName() {
        return waveformName;
    }

    public double[] getTimeOffsets() {
        return Arrays.copyOf(timeOffsets, timeOffsets.length);
    }

    public double[] getValues() {
        return Arrays.copyOf(values, values.length);
    }

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
        if (x > arr[high]) {
            // If it is after the waveform timeOffsets, return -1
            return -1;
        } else if (Double.compare(x, arr[high]) == 0) {
            // If it is the last point, return it
            return high;
        } else if (x < arr[low]) {
            // If it is before the waveform timeOffsets, return -1
            return -1;
        } else if (Double.compare(x, arr[low]) == 0) {
            // If it is the first point return it
            return low;
        }

        int mid = (low + high) / 2;
        if (Double.compare(x, arr[mid]) == 0) {
            return mid;
        } else if (x > arr[mid]) {
            // do the search again setting low = mid;
            return floorIndexSearch(arr, mid, high, x);
        } else {
            // Since x != arr[mid] and ! x > arr[mid], then x < arr[mid]
            // do the search again setting low = mid;
            return floorIndexSearch(arr, low, mid, x);
        }
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
