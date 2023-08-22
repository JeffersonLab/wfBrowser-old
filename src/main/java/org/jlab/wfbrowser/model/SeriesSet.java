/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.util.Objects;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

/**
 * ;
 * Simple object for representing a named set of waveform data series lookup
 * information.
 *
 * @author adamc
 */
public class SeriesSet {

    private final Set<Series> set;
    private final String name;
    private final int id;
    private final String systemName;
    private final String description;

    public SeriesSet(Set<Series> set, String name, int id, String systemName, String description) {
        if (set == null) {
            throw new RuntimeException("set cannot be null");
        }
        this.set = set;
        this.name = name;
        this.id = id;
        this.systemName = systemName;
        this.description = description;
    }

    public Set<Series> getSet() {
        return set;
    }

    public String getName() {
        return name;
    }

    public String getSystemName() {
        return systemName;
    }

    public int getId() {
        return id;
    }

    public boolean addSeries(Series series) {
        return set.add(series);
    }

    public String getDescription() {
        return description;
    }

    public JsonObject toJsonObject() {
        JsonArrayBuilder jab = Json.createArrayBuilder();
        for (Series s : set) {
            jab.add(s.toJsonObject());
        }
        return Json.createObjectBuilder()
                .add("name", name)
                .add("setId", id)
                .add("description", description)
                .add("system", systemName)
                .add("series", jab.build())
                .build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {return true;}
        if (!(obj instanceof SeriesSet)) { return false;}
        SeriesSet s = (SeriesSet) obj;

        // Compare the simple attributes
        if (id != s.id) { return false; }
        if (!name.equals(s.name)) { return false; }
        if (!systemName.equals(s.systemName)) { return false; }
        if (!description.equals(s.description)) { return false; }

        // Compare the series each one contains.
        if (set.size() != s.set.size()) { return false; }
        for (Series series : set) {
            boolean foundIt = false;
            for (Series oSeries : s.set) {
                if (series.equals(oSeries)) {
                    foundIt = true;
                    break;
                }
            }
            if (!foundIt) { return false; }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(set, name, id, systemName, description);
    }
}
