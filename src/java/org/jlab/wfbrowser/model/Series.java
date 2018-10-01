/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.math.BigDecimal;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * Simple object for representing the lookup information for a single waveform
 * data series
 *
 * @author adamc
 */
public class Series {

    private final String name;
    private final int id;
    private final String pattern;
    private final String system;

    public Series(String name, int id, String pattern, String system) {
        this.name = name;
        this.id = id;
        this.pattern = pattern;
        this.system = system;
    }

    public String getSystem() {
        return system;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public String getPattern() {
        return pattern;
    }

    public JsonObject toJsonObject() {
        return Json.createObjectBuilder()
                .add("name", name)
                .add("seriesId", id)
                .add("pattern", pattern)
                .add("system", system)
                .build();
    }
}
