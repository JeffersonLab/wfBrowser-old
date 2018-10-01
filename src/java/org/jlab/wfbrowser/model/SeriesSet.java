/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.util.Set;

/**
 * Simple object for representing a named set of waveform data series lookup information.
 * @author adamc
 */
public class SeriesSet {
    private final Set<Series> set;
    private final String name;
    private final int id;

    public SeriesSet(Set<Series> set, String name, int id) {
        this.set = set;
        this.name = name;
        this.id = id;
    }

    public Set<Series> getSet() {
        return set;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }
    
}
