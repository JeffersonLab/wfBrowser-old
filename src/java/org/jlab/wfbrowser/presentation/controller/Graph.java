package org.jlab.wfbrowser.presentation.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.SeriesSet;
import org.jlab.wfbrowser.presentation.util.GraphConfig;
import org.jlab.wfbrowser.presentation.util.SessionUtils;

/**
 * @author adamc
 */
@WebServlet(name = "Graph", urlPatterns = {"/graph"})
public class Graph extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(Graph.class.getName());

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String beginString = request.getParameter("begin");
        String endString = request.getParameter("end");
        String[] locSel = request.getParameterValues("location");
        String[] serSel = request.getParameterValues("series");
        String[] serSetSel = request.getParameterValues("seriesSet");
        String[] classSel = request.getParameterValues("classification");

        String eventId = request.getParameter("eventId");
        String system = request.getParameter("system");
        String minCF = request.getParameter("minCF");

        Integer minCaptureFiles = null;
        if (minCF != null && !minCF.isEmpty()) {
            minCaptureFiles = Integer.parseInt(minCF);
        }

        /* Basic strategy with these session attributes - if we get explicit request parameters, use them and update the session
         * copies.  If we don't get request params, but we have the needed session attributes, use them and redirect.  If we don't
         * have request or session values, then use defaults, update the session, and redirect.
         */
        HttpSession session = request.getSession();
        boolean redirectNeeded = false;

        // Get the config for this system
        GraphConfig defaultGraphConfig = GraphConfig.getDefaultConfig(system);
        synchronized (SessionUtils.getSessionLock(request, null)) {
            // We only want to work with the sessionGraphConfig object within this lock.
            GraphConfig sessionGraphConfig;

            // Make sure we have a default system to query against
            if (system == null) {
                redirectNeeded = true;
                if (session.getAttribute("graphSystem") == null) {
                    system = "rf";
                } else {
                    system = (String) session.getAttribute("graphSystem");
                }
            }
            session.setAttribute("graphSystem", system);
            Map<String, GraphConfig> gcMap = (Map<String, GraphConfig>) session.getAttribute("graphConfigMap");
            if (gcMap == null) {
                gcMap = new HashMap<>();
                session.setAttribute("graphConfigMap", gcMap);
            }
            if (gcMap.get(system) == null) {
                // Use the default as the starting point for the session config if none exists.
                gcMap.put(system, defaultGraphConfig);
                // Since there was no session, it is likely the first time to hit this page and a redirect is needed
                redirectNeeded = true;
            } else {
                // Update the different options in case they may have changed since last use.
                sessionGraphConfig = gcMap.get(system);
                sessionGraphConfig.setLocationOptions(defaultGraphConfig.getLocationOptions());
                sessionGraphConfig.setClassificationOptions(defaultGraphConfig.getClassificationOptions());
                sessionGraphConfig.setSeriesOptions(defaultGraphConfig.getSeriesOptions());
                sessionGraphConfig.setSeriesSetOptions(defaultGraphConfig.getSeriesSetOptions());
            }
        }

        List<Series> seriesOptions = defaultGraphConfig.getSeriesOptions();
        List<SeriesSet> seriesSetOptions = defaultGraphConfig.getSeriesSetOptions();

        // Get the series selections if any were made.  Keep only the valid ones.
        Set<Series> seriesSelections = null;
        if (serSel != null && serSel.length > 0) {
            for (String seriesName : serSel) {
                for (Series s : seriesOptions) {
                    if (seriesName.equals(s.getName())) {
                        if (seriesSelections == null) {
                            seriesSelections = new HashSet<>();
                        }
                        seriesSelections.add(s);
                    }
                }
            }
        }

        // Get the series set selections if any were made.  Keep only the valid ones.
        Set<SeriesSet> seriesSetSelections = null;
        if (serSetSel != null && serSetSel.length > 0) {
            for (String setName : serSetSel) {
                for (SeriesSet s : seriesSetOptions) {
                    if (setName.equals(s.getName())) {
                        if (seriesSetSelections == null) {
                            seriesSetSelections = new HashSet<>();
                        }
                        seriesSetSelections.add(s);
                    }
                }
            }
        }

        List<String> locationOptions = defaultGraphConfig.getLocationOptions();
        Set<String> locationSelections = keepOnlyMatches(locSel, locationOptions);

        List<String> classificationOptions = defaultGraphConfig.getClassificationOptions();
        Set<String> classificationSelections = keepOnlyMatches(classSel, classificationOptions);

        Long eId = (eventId == null || eventId.isEmpty()) ? null : Long.parseLong(eventId);

        GraphConfig requestGraphConfig = new GraphConfig(system, locationSelections, classificationSelections, minCaptureFiles,
                eId, beginString, endString, classificationOptions, locationOptions, seriesSelections, seriesOptions,
                seriesSetSelections, seriesSetOptions);

        // Used in UI
        Map<String, Boolean> locationMap = new TreeMap<>();
        Map<String, Boolean> classificationMap = new TreeMap<>();
        Map<String, Boolean> seriesMap = new TreeMap<>();
        Map<String, Boolean> seriesSetMap = new TreeMap<>();
        Set<String> seriesMasterSet = new HashSet<>();
        List<Event> eventList;
        Event currentEvent = null;

        synchronized (SessionUtils.getSessionLock(request, null)) {
            Map<String, GraphConfig> gcMap = (Map<String, GraphConfig>) session.getAttribute("graphConfigMap");
            GraphConfig sessionGraphConfig = gcMap.get(system);
            boolean updated = sessionGraphConfig.overwriteWith(requestGraphConfig);
            redirectNeeded = redirectNeeded || updated;


            // Overwrite the graph configuration variables with the finals values after mergeing session and request
            // versions.
            eId = sessionGraphConfig.getEventId();
            system = sessionGraphConfig.getSystem();
            beginString = sessionGraphConfig.getBeginString();
            endString = sessionGraphConfig.getEndString();
            seriesSelections = sessionGraphConfig.getSeries();
            seriesSetSelections = sessionGraphConfig.getSeriesSets();
            classificationSelections = sessionGraphConfig.getClassifications();
            locationSelections = sessionGraphConfig.getLocations();
            Instant begin = sessionGraphConfig.getBegin();
            Instant end = sessionGraphConfig.getEnd();

            List<String> locationSelectionsList = new ArrayList<>(locationSelections);
            List<String> classificationSelectionsList = new ArrayList<>(classificationSelections);

            // Check that the current event is within the requested time.  If it's not, select a new current event
            // from within that time range
            try {
                EventService es = new EventService();
                // We have a requested ID.  Grab it, but enforce the requested time range and other filters.  If we get
                // nothing back, it's because either the ID is invalid or it's out of the time range.
                if (eId != null) {
                    EventFilter filter = new EventFilter(Collections.singletonList(eId), begin, end, system,
                            locationSelectionsList, classificationSelectionsList, null, null, minCaptureFiles);
                    currentEvent = es.getMostRecentEvent(filter, true);
                    eId = (currentEvent == null) ? null : currentEvent.getEventId();
                    sessionGraphConfig.setEventId(eId);
                    if (eId == null) {
                        redirectNeeded = true;
                    }
                }
                // If we don't have an event ID to display, let's see if we can find one to use with our time range.
                if (eId == null) {
                    EventFilter filter = new EventFilter(null, begin, end, system,
                            locationSelectionsList, classificationSelectionsList, null, null, minCaptureFiles);
                    currentEvent = es.getMostRecentEvent(filter, true);
                    sessionGraphConfig.setEventId((currentEvent == null) ? null : currentEvent.getEventId());
                    if (eId != null) {
                        redirectNeeded = true;
                    }
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying database for event information.", ex);
                throw new ServletException("Error querying database for event information.");
            } catch (FileNotFoundException ex) {
                LOGGER.log(Level.SEVERE, "File not found:  Error locating event data on disk.", ex);
                throw new ServletException("File not found:  Error locating event data on disk.", ex);
            }

            // If a redirect was found to be needed, build the URL based on variables set above and redirect to it.
            if (redirectNeeded) {
                StringBuilder redirectUrl = new StringBuilder(request.getContextPath() + "/graph?"
                        + "eventId=" + URLEncoder.encode((eId == null ? "" : "" + eId), "UTF-8")
                        + "&system=" + URLEncoder.encode(system, "UTF-8")
                        + "&begin=" + URLEncoder.encode(beginString, "UTF-8")
                        + "&end=" + URLEncoder.encode(endString, "UTF-8"));
                for (String location : locationSelections) {
                    redirectUrl.append("&location=").append(URLEncoder.encode(location, "UTF-8"));
                }
                for (String classification : classificationSelections) {
                    redirectUrl.append("&classification=").append(URLEncoder.encode(classification, "UTF-8"));
                }
                if (seriesSelections != null) {
                    for (Series series : seriesSelections) {
                        redirectUrl.append("&series=").append(URLEncoder.encode(series.getName(), "UTF-8"));
                    }
                }
                if (seriesSetSelections != null) {
                    for (SeriesSet seriesSet : seriesSetSelections) {
                        redirectUrl.append("&seriesSet=").append(URLEncoder.encode(seriesSet.getName(), "UTF-8"));
                    }
                }
                if (minCaptureFiles != null) {
                    redirectUrl.append("&minCF=").append(URLEncoder.encode(minCaptureFiles.toString(), "UTF-8"));
                }

                response.sendRedirect(response.encodeRedirectURL(redirectUrl.toString()));
            }

            // Process more configuration info since we know we aren't redirecting and will use it.
            for (Series s : sessionGraphConfig.getSeriesMasterSet()) {
                seriesMasterSet.add(s.getName());
            }

            // Process the option maps for convenient use in the UI
            if (locationOptions != null) {
                for (String location : locationOptions) {
                    locationMap.put(location, false);
                }
            }
            if (locationSelections != null) {
                for (String location : locationSelections) {
                    locationMap.put(location, true);
                }
            }
            if (classificationOptions != null) {
                for (String classification : classificationOptions) {
                    classificationMap.put(classification, false);
                }
            }
            if (classificationSelections != null) {
                for (String classification : classificationSelections) {
                    classificationMap.put(classification, true);
                }
            }
            if (seriesOptions != null) {
                for (Series s : seriesOptions) {
                    seriesMap.put(s.getName(), false);
                }
            }
            if (seriesSelections != null) {
                for (Series s : seriesSelections) {
                    seriesMap.put(s.getName(), true);
                }
            }
            if (seriesSetOptions != null) {
                for (SeriesSet s : seriesSetOptions) {
                    seriesSetMap.put(s.getName(), false);
                }
            }
            if (seriesSetSelections != null) {
                for (SeriesSet s : seriesSetSelections) {
                    seriesSetMap.put(s.getName(), true);
                }
            }


            // Get a list of events that are to be displayed in the timeline - should not be in session since this might change
            EventService es = new EventService();
            try {
                EventFilter eFilter = new EventFilter(null, begin, end, system,
                        new ArrayList<>(locationSelections), new ArrayList<>(classificationSelections),
                        null, null, minCaptureFiles);
                eventList = es.getEventListWithoutCaptureFiles(eFilter);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying database for event information.", ex);
                throw new ServletException("Error querying database for event information.");
            }

        }

        JsonArrayBuilder jab = Json.createArrayBuilder();
        JsonObjectBuilder job = Json.createObjectBuilder();
        for (Event event : eventList) {
            jab.add(event.toJsonObject());
        }
        JsonObject eventListJson = job.add("events", jab.build()).build();

        // Create a system name meant for user consumption
        String systemDisplay;
        switch (system) {
            case "rf":
                systemDisplay = "RF";
                break;
            case "acclrm":
                systemDisplay = "Accelerometer";
                break;
            default:
                throw new IllegalArgumentException("No display name defined for system -" + system);
        }

        // It's easier to work with a list of names in the JSP
        List<String> classificationSelectionStrings = new ArrayList<>(classificationSelections);
        Collections.sort(classificationSelectionStrings);
        List<String> locationSelectionStrings = new ArrayList<>(locationSelections);
        Collections.sort(locationSelectionStrings);
        List<String> seriesSelectionStrings = new ArrayList<>();
        if (seriesSelections != null) {
            for (Series s : seriesSelections) {
                seriesSelectionStrings.add(s.getName());
            }
        }
        List<String> seriesSetSelectionStrings = new ArrayList<>();
        if (seriesSetSelections != null) {
            for (SeriesSet s : seriesSetSelections) {
                seriesSetSelectionStrings.add(s.getName());
            }
        }

        request.setAttribute("begin", beginString);
        request.setAttribute("end", endString);
        request.setAttribute("locationSelections", locationSelectionStrings);
        request.setAttribute("locationMap", locationMap);
        request.setAttribute("classificationSelections", classificationSelectionStrings);
        request.setAttribute("classificationMap", classificationMap);
        request.setAttribute("seriesSelections", seriesSelectionStrings);
        request.setAttribute("seriesSetSelections", seriesSetSelectionStrings);
        request.setAttribute("seriesMasterSet", seriesMasterSet);
        request.setAttribute("seriesMap", seriesMap);
        request.setAttribute("seriesSetMap", seriesSetMap);
        request.setAttribute("eventId", eId);
        request.setAttribute("minCF", minCaptureFiles);
        request.setAttribute("system", system);
        request.setAttribute("systemDisplay", systemDisplay);
        request.setAttribute("eventListJson", eventListJson.toString());
        request.setAttribute("currentEvent", currentEvent == null ? "null" : currentEvent.toDyGraphJsonObject(seriesMasterSet).toString());

        request.getRequestDispatcher("/WEB-INF/views/graph.jsp").forward(request, response);
    }

    /**
     * Convenience method for filtering out invalid choices.
     *
     * @param source An array of choices that may contain duplicates or invalid entries
     * @param valid  An array of valid choices
     * @param <T>    The common type of the source, valid selections, and returned Set (if not null)
     * @return The Set of valid entries in source or null if no source or empty source was provided.
     */
    private static Set<String> keepOnlyMatches(String[] source, List<String> valid) {
        // Create a list of the selections.  Only keep names that match valid options.
        Set<String> keep = null;
        if (source != null && source.length > 0) {
            keep = new TreeSet<>();
            for (String s : source) {
                for (String v : valid) {
                    if (s.equals((v))) {
                        keep.add(s);
                    }
                }
            }
        }
        return keep;
    }
}
