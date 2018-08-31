/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

/**
 *
 * @author adamc
 */
public class Waveform {
    private double[] timeOffsets;
    private double[] values;
    
    public Waveform(double[] timeOffsets, double[] values) {
        if (timeOffsets.length != values.length) {
            throw new IllegalArgumentException("time and value arrays are of unequal length");
        }
        this.timeOffsets = timeOffsets;
        this.values = values;
    }

    public double[] getTimeOffsets() {
        return timeOffsets;
    }

    public double[] getValues() {
        return values;
    }
    
}
