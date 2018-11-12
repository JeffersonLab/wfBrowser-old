/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import javax.json.Json;
import javax.json.JsonObject;

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
    private final String description;
    private final String units;

    public Series(String name, int id, String pattern, String system, String description, String units) {
        this.name = name;
        this.id = id;
        this.pattern = pattern;
        this.system = system;
        this.description = description;
        this.units = units;
    }

    public String getUnits() {
        return units;
    }

    public String getDescription() {
        return description;
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
                .add("units", units)
                .add("description", description)
                .build();
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Series)) {
            return false;
        }

        Series s = (Series) o;
        return id == s.getId();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + this.id;
        return hash;
    }
}
