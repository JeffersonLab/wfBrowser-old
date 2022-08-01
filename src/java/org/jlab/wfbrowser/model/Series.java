/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

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
    private final Double yMin;
    private final Double yMax;

    public Series(String name, int id, String pattern, String system, String description, String units, Double yMin,
                  Double yMax) {
        this.name = name;
        this.id = id;
        this.pattern = pattern;
        this.system = system;
        this.description = description;
        this.units = units;
        this.yMin = yMin;
        this.yMax = yMax;
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
        JsonObjectBuilder job = Json.createObjectBuilder()
                .add("name", name)
                .add("seriesId", id)
                .add("pattern", pattern)
                .add("system", system)
                .add("units", (units == null) ? "" : units)
                .add("description", (description == null) ? "" : description);
        if (yMin == null) {
            job.add("y-min", JsonValue.NULL);
        } else {
            job.add("y-min", yMin);
        }
        if (yMax == null) {
            job.add("y-max", JsonValue.NULL);
        } else {
            job.add("y-max", yMax);
        }

        return job.build();
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
