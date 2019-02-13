/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model.CaptureFile;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * Metadata values MYA data points gathered by the harvester at a specified
 * offset from the time of the event trigger. There are four types of metadata -
 * strings, numbers, unarchived (meaning the PV does not exist in the archived
 * configuration), and unavailable (meaning the PV is archived, but the PV is in
 * a non-update state like network disconnect). Given this, metadata values are
 * saved as Objects and it is the responsibility of the users of Metadata
 * objects to query the MetadataType and cast the value Object to the proper
 * type.
 *
 * @author adamc
 */
public class Metadata {

    private final MetadataType type;
    private final String name;
    private final Object value;
    private final Double offset; // Relative time offset from trigger (in seconds, specified in Harvester config)
    private final Double start;  // When did the PV take this value relative to trigger time (in seconds)

    /**
     * Basic constructor for the Metadata class
     *
     * @param type
     * @param name
     * @param value
     * @param offset
     * @param start
     */
    public Metadata(MetadataType type, String name, Object value, Double offset, Double start) {
        this.type = type;
        this.name = name;
        this.value = value;
        this.offset = offset;
        this.start = start;
    }

    /**
     * Construct a Metadata object from a String representing the a metadata
     * line in the harvester waveform file.
     *
     * Four possible formats are acceptable for numeric values, string values,
     * unavailable values, and unarchived values. Examples:
     * <pre>
     * # PV1=5.6 @ -.5(-45.9)
     * # PV2='ABC' @ 0(-000.4)
     * # PV3=unavailable @ 0
     * # PV4=not archived
     * </pre>
     *
     *
     * @param line A line read from a capture file containing a metadata entry
     * @return A Metadata object
     */
    public static Metadata getMetadataFromCaptureFileLine(String line) {
        String unarchivedRegex = "# ([^=]+)=not archived";
        String unavailableRegex = "# ([^=]+)=unavailable @ ([\\-\\d\\.e]+)";
        String numberRegex = "# ([^=]+)=([\\-\\d\\.e]+) @ ([\\-\\d\\.e]+)\\(([\\-\\d\\.e]+)\\)";
        String stringRegex = "# ([^=]+)='(.*)' @ ([\\-\\d\\.e]+)\\(([\\-\\d\\.e]+)\\)";

        String testRegex = "# ([^=]+)=unavailable @ (\\d+)";

        Matcher m;
        MetadataType t;
        String n;
        Object v;
        Double o, s;
        if (line.matches(unarchivedRegex)) {
            m = Pattern.compile(unarchivedRegex).matcher(line);
            if (!m.matches()) {
                throw new IllegalArgumentException("Error processing metadata.  Pattern:" + m.pattern().pattern() + " Line:" + line);
            } else {
                t = MetadataType.UNARCHIVED;
                n = m.group(1);
                v = null;
                o = null;
                s = null;
            }
        } else if (line.matches(unavailableRegex)) {
            m = Pattern.compile(unavailableRegex).matcher(line);
            if (!m.matches()) {
                throw new IllegalArgumentException("Error processing metadata.  Pattern:" + m.pattern().pattern() + " Line:" + line);
            } else {
                t = MetadataType.UNAVAILABLE;
                n = m.group(1);
                v = null;
                o = Double.valueOf(m.group(2));
                s = null;
            }
        } else if (line.matches(numberRegex)) {
            m = Pattern.compile(numberRegex).matcher(line);
            if (!m.matches()) {
                throw new IllegalArgumentException("Error processing metadata.  Pattern:" + m.pattern().pattern() + " Line:" + line);
            } else {
                t = MetadataType.NUMBER;
                n = m.group(1);
                v = Double.valueOf(m.group(2));
                o = Double.valueOf(m.group(3));
                s = Double.valueOf(m.group(4));
            }
        } else if (line.matches(stringRegex)) {
            m = Pattern.compile(stringRegex).matcher(line);
            if (!m.matches()) {
                throw new IllegalArgumentException("Error processing metadata.  Pattern:" + m.pattern().pattern() + " Line:" + line);
            } else {
                t = MetadataType.STRING;
                n = m.group(1);
                v = m.group(2);
                o = Double.valueOf(m.group(3));
                s = Double.valueOf(m.group(4));
            }
        } else {
            throw new IllegalArgumentException("Metadata line has unrecognized format - " + line);
        }

        return new Metadata(t, n, v, o, s);
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public MetadataType getType() {
        return type;
    }

    public Double getOffset() {
        return offset;
    }

    public Double getStart() {
        return start;
    }

    /**
     * Determines equality based on the name and offset parameters.
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }
        if (!(o instanceof Metadata)) {
            return false;
        }

        Metadata m = (Metadata) o;
        if (name.equals(m.getName())) {
            if (offset == null) {
                if (m.getOffset() == null) {
                    return true;
                } else {
                    return false;
                }
            } else {
                if (m.getOffset() == null) {
                    return false;
                }

                if (offset.compareTo(m.getOffset()) == 0) {
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    /**
     * Generate a hashcode based on the name and offset of the metadata
     *
     * @return
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.name);
        hash = 47 * hash + Objects.hashCode(this.offset);
        return hash;
    }
    
    /**
     * Create a JSON representation of the object
     * @return A JSON representation of the object
     */
    public JsonObject toJsonObject() {
        JsonObjectBuilder job = Json.createObjectBuilder()
                .add("name", name)
                .add("type", type.toString());
        if (null == type) {
            job.add("value", (String) null);
        } else switch (type) {
            case NUMBER:
                job.add("value", ((Double) value).toString())
                .add("offset", offset)
                .add("start", start);
                break;
            case STRING:
                job.add("value", (String) value)
                .add("offset", offset)
                .add("start", start);
                break;
            case UNAVAILABLE:
                job.add("value", JsonObject.NULL)
                .add("offset", offset)
                .add("start", JsonObject.NULL);
                break;
            case UNARCHIVED:
                job.add("value", JsonObject.NULL)
                .add("offset", JsonObject.NULL)
                .add("start", JsonObject.NULL);
                break;
            default:
                throw new IllegalArgumentException("Unrecognized MetadataType " + type);
        }
        return job.build();
    }
}
