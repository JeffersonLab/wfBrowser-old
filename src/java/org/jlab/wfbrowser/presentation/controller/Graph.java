package org.jlab.wfbrowser.presentation.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
import org.jlab.wfbrowser.business.filter.SeriesFilter;
import org.jlab.wfbrowser.business.filter.SeriesSetFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.business.service.SeriesService;
import org.jlab.wfbrowser.business.util.TimeUtil;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.SeriesSet;

/**
 *
 * @author ryans
 */
@WebServlet(name = "Graph", urlPatterns = {"/graph"})
public class Graph extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(Graph.class.getName());

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
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

        /* Basic strategy with these session attributes - if we get explicit request parameters, use them and update the session
         * copies.  If we don't get reuqest params, but we have the needed session attributes, use them and redirect.  If we don't
         * have request or session values, then use defaults, update the session, and redirect.
         */
        HttpSession session = request.getSession();
        boolean redirectNeeded = false;

        // Make sure we have a default system to query against
        if (system == null) {
            redirectNeeded = true;
            system = "rf";
        }

        // Process the begin/end parameters
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        Instant begin, end;
        if (beginString != null && !beginString.isEmpty()) {
            TimeUtil.validateDateTimeString(beginString);
            begin = TimeUtil.getInstantFromDateTimeString(beginString);
            session.setAttribute("graphBegin", begin);
        } else if (session.getAttribute("graphBegin") != null) {
            begin = (Instant) session.getAttribute("graphBegin");
            beginString = dtf.format(begin);
            redirectNeeded = true;
        } else {
            begin = now.plus(-2, ChronoUnit.DAYS);
            session.setAttribute("graphBegin", begin);
            beginString = dtf.format(begin);
            redirectNeeded = true;
        }

        if (endString != null && !endString.isEmpty()) {
            TimeUtil.validateDateTimeString(endString);
            end = TimeUtil.getInstantFromDateTimeString(endString);
            session.setAttribute("graphEnd", end);
        } else if (session.getAttribute("graphEnd") != null) {
            end = (Instant) session.getAttribute("graphEnd");
            endString = dtf.format(end);
            redirectNeeded = true;
        } else {
            end = now;
            session.setAttribute("graphEnd", end);
            endString = dtf.format(end);
            redirectNeeded = true;
        }

        // Create a map of the series and seriesSet options and whether or not the user selected them.
        SeriesService ss = new SeriesService();
        SeriesFilter sFilter = new SeriesFilter(null, system, null);
        SeriesSetFilter ssFilter = new SeriesSetFilter(null, system, null);
        List<Series> seriesOptions = new ArrayList<>();
        List<SeriesSet> seriesSetOptions = new ArrayList<>();
        try {
            seriesOptions = ss.getSeries(sFilter);
            seriesSetOptions = ss.getSeriesSets(ssFilter);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for series information.", ex);
            throw new ServletException("Error querying database for series information.");
        }

        // Process the requested series and series set selections.  This is a little more complicated than usual.  We have to have
        // at least one series to graph, so that means either a series or series set must be specified at the end.  If the request
        // doesn't have either specified, then use the session.  If the session doesn't have either specified, then set a single series
        // as the default.
        //
        // Figure out what we're using - request, session, or default
        String seriesCase;
        if ((serSel != null && serSel.length != 0) || (serSetSel != null && serSetSel.length != 0)) {
            seriesCase = "request";
        } else if (session.getAttribute("graphSeriesSelections") != null || session.getAttribute("graphSeriesSetSelections") != null) {
            seriesCase = "session";
            redirectNeeded = true;
        } else {
            seriesCase = "default"; // maps to switch default
            redirectNeeded = true;
        }

        Set<String> seriesSelections;
        switch (seriesCase) {
            case "request":
                seriesSelections = new TreeSet<>();
                // Only keep valid series
                if (serSel != null) {
                    for (String ser : serSel) {
                        boolean found = false;
                        for (Series s : seriesOptions) {
                            if (ser.equals(s.getName())) {
                                seriesSelections.add(ser);
                                found = true;
                            }
                        }
                        if (!found) {
                            redirectNeeded = true;
                        }
                    }

                    // We may have removed all of their choices - add one back
                    if (seriesSelections.isEmpty()) {
                        redirectNeeded = true;
                        if (!seriesOptions.isEmpty()) {
                            seriesSelections.add(seriesOptions.get(0).getName());
                        } else {
                            LOGGER.log(Level.WARNING, "No series returned from database.  Cannot determine what data to display.");
                            throw new RuntimeException("No series returned from database.  Cannot determine what data to display.");
                        }
                    }
                }

                break;
            case "session":
                if (session.getAttribute("graphSeriesSelections") == null) {
                    seriesSelections = new TreeSet<>();
                } else {
                    redirectNeeded = true;
                    seriesSelections = (Set<String>) session.getAttribute("graphSeriesSelections");
                    // Go through the specified series and keep only valid options
                    Iterator it = seriesSelections.iterator();
                    boolean anyRemoved = false;
                    while (it.hasNext()) {
                        String next = (String) it.next();
                        boolean valid = false;
                        for (Series s : seriesOptions) {
                            if (next.equals(s.getName())) {
                                valid = true;
                            }
                        }
                        if (!valid) {
                            it.remove();
                            redirectNeeded = true;
                            anyRemoved = true;
                        }
                    }

                    // If the session had only invalid selections that we removed, put back a single valid series to make sure we've viewable situation.
                    if (anyRemoved && seriesSelections.isEmpty()) {
                        if (!seriesOptions.isEmpty()) {
                            seriesSelections.add(seriesOptions.get(0).getName());
                        } else {
                            LOGGER.log(Level.WARNING, "No series returned from database.  Cannot determine what data to display.");
                            throw new RuntimeException("No series returned from database.  Cannot determine what data to display.");
                        }
                    }
                    session.setAttribute("graphSeriesSelections", seriesSelections);
                }
                break;
            default:
                // No request params and no session objects - so we pick the first series available
                seriesSelections = new TreeSet<>();
                redirectNeeded = true;
                if (!seriesOptions.isEmpty()) {
                    seriesSelections.add(seriesOptions.get(0).getName());
                } else {
                    LOGGER.log(Level.WARNING, "No series returned from database.  Cannot determine what data to display.");
                    throw new RuntimeException("No series returned from database.  Cannot determine what data to display.");
                }
                break;
        }
        session.setAttribute("graphSeriesSelections", seriesSelections);

        // Process the series to see what was selected and what was not.  Save this for easy look up in the view.
        Map<String, Boolean> seriesMap = new TreeMap<>();
        for (Series series : seriesOptions) {
            seriesMap.put(series.getName(), seriesSelections.contains(series.getName()));
        }

        // Now process the series sets
        List<String> seriesSetSelections;
        switch (seriesCase) {
            case "request":
                System.out.println("request case");
                seriesSetSelections = new ArrayList<>();
                if (serSetSel != null) {
                    // Only keep valid seriesSet options
                    for (String serSet : serSetSel) {
                        boolean found = false;
                        for (SeriesSet sSet : seriesSetOptions) {
                            if (serSet.equals(sSet.getName())) {
                                seriesSetSelections.add(serSet);
                                found = true;
                            }
                        }
                        if (!found) {
                            redirectNeeded = true;
                        }
                    }
                }
                break;
            case "session":
                if (session.getAttribute("graphSeriesSetSelections") == null) {
                    seriesSetSelections = new ArrayList<>();
                } else {
                    seriesSetSelections = (List<String>) session.getAttribute("graphSeriesSetSelections");
                    Iterator it = seriesSetSelections.iterator();
                    boolean anyRemoved = false;
                    while (it.hasNext()) {
                        String next = (String) it.next();
                        boolean found = false;
                        for (SeriesSet sSet : seriesSetOptions) {
                            if (next.equals(sSet.getName())) {
                                found = true;
                            }
                        }
                        if (!found) {
                            it.remove();
                            redirectNeeded = true;
                            anyRemoved = true;
                        }
                    }

                    // If the session specified a set, and we removed all of them, put a valid one back.
                    if (seriesSetSelections.isEmpty() && anyRemoved) {
                        if (!seriesSetOptions.isEmpty()) {
                            seriesSetSelections.add(seriesSetOptions.get(0).getName());
                        } else {
                            LOGGER.log(Level.WARNING, "No series sets returned from database.  Cannot determine what data to display.");
                            throw new RuntimeException("No series sets returned from database.  Cannot determine what data to display.");
                        }
                    }
                }
                break;
            default:
                seriesSetSelections = new ArrayList<>();
                redirectNeeded = true;
                break;
        }
        session.setAttribute("graphSeriesSetSelections", seriesSetSelections);

        // Figure out which series sets were selected and which were not.  Save in a map for easy lookup in the view
        Map<String, Boolean> seriesSetMap = new TreeMap<>();
        for (SeriesSet seriesSet : seriesSetOptions) {
            // Don't force a series set to be selected like we did for the series.  We just need at least one series to display.
            seriesSetMap.put(seriesSet.getName(), seriesSetSelections.contains(seriesSet.getName()));
        }

        // There is the possibility that we somehow stumble into the case where the session contains two empty lists
        // for series and series sets.  If so, add a series.
        if (seriesSetSelections.isEmpty() && seriesSelections.isEmpty()) {
            if (seriesOptions == null || seriesOptions.isEmpty()) {
                LOGGER.log(Level.WARNING, "No series sets returned from database.  Cannot determine what data to display.");
                throw new RuntimeException("No series sets returned from database.  Cannot determine what data to display.");
            } else {
                seriesSelections.add(seriesOptions.get(0).getName());
                seriesMap.put(seriesOptions.get(0).getName(), Boolean.TRUE);
            }
        }

        // Process the location selections.  Use the request version if given, the session if not, and all available options if we have nothing
        // Get a list of the location options
        EventService es = new EventService();
        List<String> locationOptions;
        try {
            locationOptions = es.getLocationNames(Arrays.asList(system));
            Collections.sort(locationOptions);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for location information.", ex);
            throw new ServletException("Error querying database for location information.");
        }
        Map<String, Boolean> locationMap = new TreeMap<>();

        // Figure out the locations.  Since the user could be bouncing between systems, we need to make sure we only present
        // locations for the currently specified system.
        List<String> locationSelections = null;
        if (locSel != null && locSel.length != 0) {
            // The user made a request so use it - but constrain selections to valid options
            locationSelections = new ArrayList<>();
            for (String loc : locSel) {
                if (locationOptions.contains(loc)) {
                    locationSelections.add(loc);
                }
            }
        } else if (session.getAttribute("graphLocationSelections") != null) {
            // The user did not make a request, but we have usable session info
            locationSelections = (List<String>) session.getAttribute("graphLocationSelections");
            Iterator it = locationSelections.iterator();
            while (it.hasNext()) {
                if (locationOptions.contains((String) it.next())) {
                    it.remove();
                }
            }
        }
        if (locationSelections == null || locationSelections.isEmpty()) {
            // We end up with not locations after checking the request and session for valid location choices.  Default to all valid options.
            locationSelections = new ArrayList<>(locationOptions);
            redirectNeeded = true;
        }
        session.setAttribute("graphLocationSelections", locationSelections);

        // See which options were selected and which were not.  Save for easy lookup in the view.
        for (String location : locationOptions) {
            locationMap.put(location, locationSelections.contains(location));
        }

        // TODO: Add getClassification section for options, selections, and the map
        List<String> classificationOptions;
        try {
            classificationOptions = es.getClassifications(Arrays.asList(system));
            Collections.sort(classificationOptions);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for classification information.", ex);
            throw new ServletException("Error querying database for classification information.");
        }

        Map<String, Boolean> classificationMap = new TreeMap<>();
        // Figure out the locations.  Since the user could be bouncing between systems, we need to make sure we only present
        // locations for the currently specified system.
        List<String> classificationSelections = null;
        if (classSel != null && classSel.length != 0) {
            // The user made a request so use it - but constrain selections to valid options
            classificationSelections = new ArrayList<>();
            for (String classification : classSel) {
                if (classificationOptions.contains(classification)) {
                    classificationSelections.add(classification);
                }
            }
        } else if (session.getAttribute("graphClassificationSelections") != null) {
            // The user did not make a request, but we have usable session info
            classificationSelections = (List<String>) session.getAttribute("graphClassificationSelections");
            Iterator it = classificationSelections.iterator();
            while (it.hasNext()) {
                if (classificationOptions.contains((String) it.next())) {
                    it.remove();
                }
            }
        }
        if (classificationSelections == null || classificationSelections.isEmpty()) {
            // We end up with not locations after checking the request and session for valid location choices.  Default to all valid options.
            classificationSelections = new ArrayList<>(classificationOptions);
            redirectNeeded = true;
        }
        session.setAttribute("graphClassificationSelections", classificationSelections);

        // See which options were selected and which were not.  Save for easy lookup in the view.
        for (String classification : classificationOptions) {
            classificationMap.put(classification, classificationSelections.contains(classification));
        }

        // Process the eventId request parameter.  Use the id if in the request or use the most recent event in time window 
        // specified as a default.  DON'T save the event object in the session since this will cause the application server to hold
        // on to hundreds of megabytes of data per session.  It's simple enough to go look it up since we're saving the eventId.
        Event currentEvent = null;
        Long id;
        if (eventId != null && !eventId.isEmpty()) {
            // We have a real request
            try {
                // Query the event id with the other constraints that were determined so far (location, start/end, etc.).  If we don't get
                // anything, then get the default entry for that set of constrains minus the event Id
                id = Long.parseLong(eventId);
                EventFilter currentFilter = new EventFilter(Arrays.asList(id), begin, end, system, locationSelections, classificationSelections, null, null);
                List<Event> currentEventList = es.getEventList(currentFilter);
                if (currentEventList == null || currentEventList.isEmpty()) {
                    currentEvent = null;
                } else {
                    currentEvent = currentEventList.get(0);  // Will be null if there were no events for the given EventFilter
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying database for event information.", ex);
                throw new ServletException("Error querying database for event information.");
            }
        }

        // If the eventId in the request was not in location or date range specified OR was not specified at all
        if (currentEvent == null) {
            // Use a default value of the most recent event within the specified time window
            try {
                EventFilter eFilter = new EventFilter(null, begin, end, system, locationSelections, classificationSelections, null, null);
                currentEvent = es.getMostRecentEvent(eFilter);

                // Still possible the user specified parameters with no events.  Only redirect if we have something to redirect to.
                if (currentEvent != null) {
                    redirectNeeded = true;
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying database for event information.", ex);
                throw new ServletException("Error querying database for event information.");
            }
        }

        if (currentEvent == null) {
            id = null;
        } else {
            id = currentEvent.getEventId();
        }
        session.setAttribute("graphEventId", id);

        // Get a list of events that are to be displayed in the timeline - should not be in session since this might change
        List<Event> eventList;
        try {
            EventFilter eFilter = new EventFilter(null, begin, end, system, locationSelections, classificationSelections, null, null);
            eventList = es.getEventListWithoutCaptureFiles(eFilter);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for event information.", ex);
            throw new ServletException("Error querying database for event information.");
        }

        // If a redirect was found to be needed, build the URL based on variables set above and redirect to it.
        if (redirectNeeded) {
            String redirectUrl = request.getContextPath() + "/graph?"
                    + "eventId=" + URLEncoder.encode((id == null ? "" : "" + id), "UTF-8")
                    + "&system=" + URLEncoder.encode(system, "UTF-8")
                    + "&begin=" + URLEncoder.encode(beginString, "UTF-8")
                    + "&end=" + URLEncoder.encode(endString, "UTF-8");
            for (String location : locationSelections) {
                redirectUrl += "&location=" + URLEncoder.encode(location, "UTF-8");
            }
            for (String classification : classificationSelections) {
                redirectUrl += "&classification=" + URLEncoder.encode(classification, "UTF-8");
            }
            for (String series : seriesSelections) {
                redirectUrl += "&series=" + URLEncoder.encode(series, "UTF-8");
            }
            for (String seriesSet : seriesSetSelections) {
                redirectUrl += "&seriesSet=" + URLEncoder.encode(seriesSet, "UTF-8");
            }

            response.sendRedirect(response.encodeRedirectURL(redirectUrl));
            return;
        }

        // Put together a simple set of all of the series names that are to be shown in the graphs.  The series parameters are specified
        // by name so that is  straightforward.  We have to loookup the seriesSet and process the objects to get the series names
        // they include.  We already have a full list of all SeriesSet objects in the series Set objects so just use that.
        Set<String> seriesMasterSet = new TreeSet<>();
        seriesMasterSet.addAll(seriesSelections);
        for (String sName : seriesSetSelections) {
            for (SeriesSet seriesSet : seriesSetOptions) {
                if (seriesSet.getName().equals(sName)) {
                    for (Series series : seriesSet.getSet()) {
                        seriesMasterSet.add(series.getName());
                    }
                    break;
                }
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

        request.setAttribute("begin", beginString);
        request.setAttribute("end", endString);
        request.setAttribute("locationSelections", locationSelections);
        request.setAttribute("locationMap", locationMap);
        request.setAttribute("classificationSelections", classificationSelections);
        request.setAttribute("classificationMap", classificationMap);
        request.setAttribute("seriesSelections", seriesSelections);
        request.setAttribute("seriesSetSelections", seriesSetSelections);
        request.setAttribute("seriesMasterSet", seriesMasterSet);
        request.setAttribute("seriesMap", seriesMap);
        request.setAttribute("seriesSetMap", seriesSetMap);
        request.setAttribute("eventId", id);
        request.setAttribute("system", system);
        request.setAttribute("systemDisplay", systemDisplay);
        request.setAttribute("eventListJson", eventListJson.toString());
        request.setAttribute("currentEvent", currentEvent == null ? "null" : currentEvent.toDyGraphJsonObject(seriesMasterSet).toString());

        request.getRequestDispatcher("/WEB-INF/views/graph.jsp").forward(request, response);
    }
}
