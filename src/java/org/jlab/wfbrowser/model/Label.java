package org.jlab.wfbrowser.model;

import org.jlab.wfbrowser.business.util.TimeUtil;

import javax.json.*;
import java.time.Instant;
import java.util.Objects;

public class Label {
    private final Long id;                  // The database ID associated with this label
    private final Instant labelTime;        // When the label was inserted into the database
    private final String modelName;         // Identifier of the of model that generated the label
    private final String name;              // Name of the label (e.g. "cavity" or "fault-type"
    private final String value;             // Value of the label (e.g. "microphonics" or "1"
    private final Double confidence;        // Confidence of the label (0,1)

    /**
     * This constructor should be used for creating Label objects when we have the database's label ID and timestamp
     * (e.g., when the Label is being returned from the database).
     * @param id The database ID of the Label
     * @param labelTime The time the label was added to the database
     * @param modelName The model that generated the label
     * @param name Name of the label
     * @param value Value of the label
     * @param confidence The confidence of the label
     */
    public Label(Long id, Instant labelTime, String modelName, String name, String value, Double confidence) {
        this.id = id;
        this.labelTime = labelTime;
        this.modelName = modelName;
        this.name = name;
        this.value = value;
        this.confidence = confidence;
    }

    /**
     * This constructor should be used when the database label ID is not available (e.g., when a Label is bening created
     * for insertion into the database.
     * @param json A JsonObject containing information needed to construct a Label object.  The Label id and timestamp
     *             are assumed to be null.
     */
    public Label(JsonObject json) {
        id = null;
        labelTime = null;
        modelName =  json.getString("model-name");
        name = json.getString("name");
        value = json.getString("value");

        // Not all models necessarily produce confidence numbers.
        JsonNumber conf = json.getJsonNumber("confidence");
        confidence = conf == null ? null : conf.doubleValue();
    }

    public String getModelName() {
        return modelName;
    }

    public Long getId() {return id;}

    public String getName() {
        return name;
    }

    public String getValue() { return value; }

    public Double getConfidence() { return confidence; }

    public JsonObject toJsonObject() {
        JsonObjectBuilder job = Json.createObjectBuilder();

        if (id == null) {
            job.add("id", JsonValue.NULL);
        } else {
            job.add("id", id);
        }
        if (labelTime == null) {
            job.add("label-time_utc", JsonValue.NULL);
        } else {
            job.add("label-time_utc", TimeUtil.getDateTimeString(labelTime));
        }
        job.add("name", name);
        if ( value == null) {
            job.add("value", JsonValue.NULL);
        } else {
            job.add("value", value);
        }
        if ( confidence == null) {
            job.add("confidence", JsonValue.NULL);
        } else {
            job.add("confidence", confidence);
        }
        job.add("model-name", modelName);
        return job.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Label label = (Label) o;
        // Skipping id and label_time since they are database dependent and make it hard to test
        return modelName.equals(label.modelName) &&
                name.equals(label.name) &&
                value.equals(label.value) &&
                Objects.equals(confidence, label.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, name, value, confidence);
    }

}
