package org.jlab.wfbrowser.presentation.util;

import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.filter.SeriesFilter;
import org.jlab.wfbrowser.business.filter.SeriesSetFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.business.service.SeriesService;
import org.jlab.wfbrowser.business.util.TimeUtil;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.SeriesSet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class for containing and working with the configuration information of the main "graph" page.
 */
public class GraphConfig {
    private static final Logger LOGGER = Logger.getLogger(GraphConfig.class.getName());
    private String system;
    private Set<String> locations;
    private List<String> locationOptions;
    private Set<String> classifications;
    private List<String> classificationOptions;
    private Set<Series> series;
    private List<Series> seriesOptions;
    private Set<SeriesSet> seriesSets;
    private List<SeriesSet> seriesSetOptions;
    private Set<Series> seriesMasterSet;
    private Integer minCaptureFiles;
    private Long eventId;
    private Instant begin;
    private Instant end;
    private DateTimeFormatter dtf;

    /**
     * A factory method for producing the default graph page configuration for the specified system.
     *
     * @param systemName The name of the system to produce a default configuration (e.g., rf)
     * @return A GraphConfig object with the default configuration
     */
    public static GraphConfig getDefaultConfig(String systemName) throws IOException {

        String system = systemName == null ? "rf" : systemName;
        Set<Series> series = new HashSet<>();
        List<Series> seriesOptions;
        Set<SeriesSet> seriesSets = new HashSet<>();
        List<SeriesSet> seriesSetOptions;
        Set<String> locations;
        List<String> locationOptions;
        Set<String> classifications;
        List<String> classificationOptions;
        Integer minCaptureFiles = null;
        Event currentEvent;
        Instant end = Instant.now();
        Instant begin = end.plus(-2, ChronoUnit.DAYS);

        // Use these for database access
        SeriesService ss = new SeriesService();
        EventService es = new EventService();

        // Look up what the options are for the series and seriesSets.
        SeriesFilter sFilter = new SeriesFilter(null, system, null);
        SeriesSetFilter ssFilter = new SeriesSetFilter(null, system, null);
        try {
            seriesOptions = ss.getSeries(sFilter);
            seriesOptions.sort(Comparator.comparing(Series::getName));
            seriesSetOptions = ss.getSeriesSets(ssFilter);
            seriesSetOptions.sort(Comparator.comparing(SeriesSet::getName));
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for series information.", ex);
            throw new RuntimeException("Error querying database for series information.");
        }

        // By default for RF,  we want the GDR Trip view.  Otherwise, do something acceptable if it's not there.
        for (SeriesSet sSet : seriesSetOptions) {
            if (sSet.getName().equals("GDR Trip")) {
                seriesSets.add(sSet);
            }
        }
        if (seriesSets.isEmpty()) {
            if (seriesOptions.isEmpty()) {
                throw new RuntimeException("Database returned no named series.  Please contact admin.");
            }
            Iterator<Series> it = seriesOptions.iterator();
            Series next = it.next();
            series.add(next);
        }

        // Get all of the classification options and set them as selected
        classificationOptions = queryClassificationOptions(system);
        classifications = new HashSet<>(classificationOptions);

        // Get all of the location options and set them as selected
        locationOptions = queryLocationOptions(system);
        locations = new HashSet<>(locationOptions);


        // Figure out the most recent event within the time range and other options.
        try {
            currentEvent = es.getMostRecentEvent(new EventFilter(null, begin, end, system, locationOptions,
                    classificationOptions, null, null, minCaptureFiles), false);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for latest event", ex);
            throw new RuntimeException("Error querying database for latest event", ex);
        }

        Long eventId = (currentEvent == null) ? null : currentEvent.getEventId();
        return new GraphConfig(system, locations, classifications, minCaptureFiles, eventId, begin, end,
                classificationOptions, locationOptions, series, seriesOptions, seriesSets, seriesSetOptions);
    }


    /**
     * Query the database for the known classifications of events for the given system.
     *
     * @param system The name of the system
     * @return An sorted list of the classification options
     */
    private static List<String> queryClassificationOptions(String system) {
        // Lookup the options for event classification.  Select them all by default, but some systems will have none.
        List<String> classificationOptions;
        EventService es = new EventService();
        try {
            classificationOptions = es.getClassifications(Collections.singletonList(system));
            classificationOptions.sort(String::compareTo);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for classification information.", ex);
            throw new RuntimeException("Error querying database for classification information.");
        }
        return classificationOptions;
    }


    /**
     * Query the database for the known locations of events for the given system.
     *
     * @param system The name of the system
     * @return An sorted list of the location options
     */
    private static List<String> queryLocationOptions(String system) {
        // Lookup the location options for this system.
        EventService es = new EventService();
        List<String> locationOptions;
        try {
            locationOptions = es.getLocationNames(Collections.singletonList(system));
            locationOptions.sort(String::compareTo);
            if (locationOptions.isEmpty()) {
                LOGGER.log(Level.SEVERE, "Error. No location options found.  Consider adding events.");
                throw new RuntimeException("Error. No location options found.  Consider adding events.");
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for location information.", ex);
            throw new RuntimeException("Error querying database for location information.");
        }

        return locationOptions;
    }


    public GraphConfig(String system, Set<String> locations, Set<String> classifications, Integer minCaptureFiles,
                       Long eventId, Instant begin, Instant end, List<String> classificationOptions,
                       List<String> locationOptions, Set<Series> series, List<Series> seriesOptions,
                       Set<SeriesSet> seriesSets, List<SeriesSet> seriesSetOptions) {
        if (system == null) {
            this.system = "rf";
        } else {
            this.system = system;
        }

        // The users of this will need to do a query to determine the options to display and which are selected.
        // No point in two sets of queries, one here and in the controller.
        this.locations = locations;
        this.locationOptions = locationOptions;
        this.classifications = classifications;
        this.classificationOptions = classificationOptions;
        this.minCaptureFiles = minCaptureFiles;
        this.eventId = eventId;

        this.series = series;
        this.seriesSets = seriesSets;
        this.seriesOptions = seriesOptions;
        this.seriesSetOptions = seriesSetOptions;
        updateSeriesMasterSet();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        this.end = (end == null) ? now : end;
        this.begin = (begin == null) ? this.end.plus(-2, ChronoUnit.DAYS) : begin;
        this.dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    }

    public Set<String> getLocations() {
        return locations;
    }

    public Set<String> getClassifications() {
        return classifications;
    }

    public Instant getBegin() {
        return begin;
    }

    public Instant getEnd() {
        return end;
    }

    public Set<Series> getSeries() {
        return series;
    }

    public String getSystem() {
        return system;
    }

    public Set<Series> getSeriesMasterSet() {
        return seriesMasterSet;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Set<SeriesSet> getSeriesSets() {
        return seriesSets;
    }

    public List<String> getLocationOptions() {
        return locationOptions;
    }

    public List<String> getClassificationOptions() {
        return classificationOptions;
    }

    public List<Series> getSeriesOptions() {
        return seriesOptions;
    }

    public List<SeriesSet> getSeriesSetOptions() {
        return seriesSetOptions;
    }

    public Integer getMinCaptureFiles() {
        return minCaptureFiles;
    }

    public void setLocationOptions(List<String> locationOptions) {
        this.locationOptions = locationOptions;
    }

    public void setClassificationOptions(List<String> classificationOptions) {
        this.classificationOptions = classificationOptions;
    }

    public void setSeriesOptions(List<Series> seriesOptions) {
        this.seriesOptions = seriesOptions;
    }

    public void setSeriesSetOptions(List<SeriesSet> seriesSetOptions) {
        this.seriesSetOptions = seriesSetOptions;
    }

    /**
     * Create an object for containing the configuration related to a graph page.  If an option is null, appropriate defaults
     * may be set in some cases.
     *
     * @param system          The name of the system to display on the graph page.  If null, set to "rf"
     * @param locations       The list of locations to display on the timeline.  null OK
     * @param classifications The list of classifications to display.  null OK
     * @param minCaptureFiles The minimum number of capture files/  null OK
     * @param eventId         The database ID of the event currently selected to be displayed
     * @param begin           The earliest event time to display in the timeline in "yyyy-MM-dd HH:mm:ss" format.  Assumes current CEBAF timezone
     * @param end             The latest event time to display in the timeline in "yyyy-MM-dd HH:mm:ss" format.  Assumes current CEBAF timezone
     */
    public GraphConfig(String system, Set<String> locations, Set<String> classifications, Integer minCaptureFiles,
                       Long eventId, String begin, String end, List<String> classificationOptions,
                       List<String> locationOptions, Set<Series> series, List<Series> seriesOptions,
                       Set<SeriesSet> seriesSets, List<SeriesSet> seriesSetOptions) {
        if (system == null) {
            this.system = "rf";
        } else {
            this.system = system;
        }

        // The users of this will need to do a query to determine the options to display and which are selected.
        // No point in two sets of queries, one here and in the controller.
        this.locations = locations;
        this.locationOptions = locationOptions;
        this.classifications = classifications;
        this.classificationOptions = classificationOptions;
        this.minCaptureFiles = minCaptureFiles;
        this.eventId = eventId;

        this.series = series;
        this.seriesSets = seriesSets;
        this.seriesOptions = seriesOptions;
        this.seriesSetOptions = seriesSetOptions;
        updateSeriesMasterSet();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        this.end = (end == null || end.isEmpty()) ? now : TimeUtil.getInstantFromDateTimeString(end);
        this.begin = (begin == null || begin.isEmpty()) ? this.end.plus(-2, ChronoUnit.DAYS) :
                TimeUtil.getInstantFromDateTimeString(begin);
        this.dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    }

    /**
     * Convenience method for updating the series "Master Set" to reflect to current contents of the series and series
     * sets.
     */
    private void updateSeriesMasterSet() {
        if (this.seriesMasterSet != null) {
            this.seriesMasterSet.clear();
        } else {
            this.seriesMasterSet = new HashSet<>();
        }
        if (this.series != null) {
            this.seriesMasterSet.addAll(this.series);
        }
        if (this.seriesSets != null) {
            for (SeriesSet ss : this.seriesSets) {
                this.seriesMasterSet.addAll(ss.getSet());
            }
        }
    }

    /**
     * Convenience function for producing maps from options to whether that option was selected.
     * @param key A case-insensitive keyword used to specify which selection map is needed. (series, seriesSet,
     *            location, classification)
     * @return A map with the display name of the option matched to a boolean for whether it was selected
     */
    public Map<String, Boolean> getSelectionMap(String key) {
        Map<String, Boolean> out = new TreeMap<>();
        switch (key.toLowerCase()) {
            case "series":
                if (seriesOptions != null) {
                    for (Series s : seriesOptions) {
                        out.put(s.getName(), false);
                    }
                    if (series != null) {
                        for(Series s : series) {
                            out.put(s.getName(), true);
                        }
                    }
                }
                break;
            case "seriesset":
                if (seriesSetOptions != null) {
                    for (SeriesSet s : seriesSetOptions) {
                        out.put(s.getName(), false);
                    }
                    if (seriesSets != null) {
                        for(SeriesSet s : seriesSets) {
                            out.put(s.getName(), true);
                        }
                    }
                }
                break;
            case "location":
                if (locationOptions != null) {
                    for (String l : locationOptions) {
                        out.put(l, false);
                    }
                    if (locations != null) {
                        for (String l : locations) {
                            out.put(l, true);
                        }
                    }
                }
                break;
            case "classification":
                if (classificationOptions != null) {
                    for (String c : classificationOptions) {
                        out.put(c, false);
                    }
                    if (classifications != null) {
                        for (String c : classifications) {
                            out.put(c, true);
                        }
                    }
                }
                break;
            default:
                throw new RuntimeException("Unsupported key: " + key);
        }
        return out;
    }

    public String getBeginString() {
        return dtf.format(begin);
    }

    public String getEndString() {
        return dtf.format(end);
    }

    public void setBegin(Instant begin) {
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new RuntimeException("begin cannot be after end");
        }

        this.begin = (begin == null) ? (end.plus(-2, ChronoUnit.DAYS)) : begin;
    }

    public void setEnd(Instant end) {
        if (begin != null && end != null && end.isBefore(begin)) {
            throw new RuntimeException(("end cannot be before begin"));
        }
        this.end = (end == null) ? Instant.now() : end;
    }

    /**
     * Set both the begin and end values for the time range.  Having this simplifies the logic around updating both
     * values while trying to enforce that begin is never later than end.
     *
     * @param begin The earliest time to be displayed on the timeline
     * @param end   The latest time to be displayed on the timeline
     */
    public void setTimelineRange(Instant begin, Instant end) {
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new RuntimeException("begin cannot be after end");
        }
        // Set to null to avoid order check
        this.begin = null;
        this.end = null;
        setEnd(end);
        setBegin(begin);
    }

    /**
     * This method allows for another GraphConfig object to overwriteWith values of this one.
     *
     * @param other The GraphConfig object whose values should override the calling object's values.
     * @return true if an update was made
     */
    public boolean overwriteWith(GraphConfig other) {
        boolean update = false;
        if (!other.system.equals(system)) {
            throw new RuntimeException("New system '" + other.system + "' must match this system '"
                    + system + "'");
        }

        if (other.locations != null && !Objects.equals(locations, other.locations)) {
            locations = other.locations;
            update = true;
        }
        if (other.classifications != null && !Objects.equals(classifications, other.classifications)) {
            classifications = other.classifications;
            update = true;
        }
        if (!Objects.equals(minCaptureFiles, other.minCaptureFiles)) {
            minCaptureFiles = other.minCaptureFiles;
            update = true;
        }
        if (other.eventId != null && !Objects.equals(eventId, other.eventId)) {
            eventId = other.eventId;
            update = true;
        }
        if (!Objects.equals(begin, other.begin) || !Objects.equals(end, other.end)) {
            setTimelineRange(other.begin, other.end);
            update = true;
        }
        if (other.classificationOptions != null &&
                !Objects.equals(classificationOptions, other.classificationOptions)) {
            classificationOptions = other.classificationOptions;
            update = true;
        }
        if (other.locationOptions != null && !Objects.equals(locationOptions, other.locationOptions)) {
            locationOptions = other.locationOptions;
            update = true;
        }

        // If we change anything about the series, we need to update the series master sets.
        boolean updateSeries = false;
        // Only make a change if other has something for either series or seriesSets.  Both null means no change.
        if (other.series != null || other.seriesSets != null) {
            if (!Objects.equals(series, other.series)) {
                series = other.series;
                update = true;
                updateSeries = true;
            }
            if (!Objects.equals(seriesSets, other.seriesSets)) {
                seriesSets = other.seriesSets;
                update = true;
                updateSeries = true;
            }
            if (updateSeries) {
                updateSeriesMasterSet();
            }
        }

        if (other.seriesOptions != null && !Objects.equals(seriesOptions, other.seriesOptions)) {
            seriesOptions = other.seriesOptions;
            update = true;
        }
        if (other.seriesSetOptions != null && !Objects.equals(seriesSetOptions, other.seriesSetOptions)) {
            seriesSetOptions = other.seriesSetOptions;
            update = true;
        }

        return update;
    }

}

