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
import java.util.Enumeration;
import java.util.HashMap;
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
        String eventId = request.getParameter("eventId");

        /* Basic strategy with these session attributes - if we get explicit request parameters, use them and update the session
         * copies.  If we don't get reuqest params, but we have the needed session attributes, use them and redirect.  If we don't
         * have request or session values, then use defaults, update the session, and redirect.
         */
        HttpSession session = request.getSession();

        // Process the begin/end parameters
        boolean redirectNeeded = false;
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
        SeriesFilter sFilter = new SeriesFilter(null, "rf", null);
        SeriesSetFilter ssFilter = new SeriesSetFilter(null, "rf", null);
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
                if (serSel != null) {
                    for (String ser : serSel) {
                        seriesSelections.add(ser);
                    }
                }
                session.setAttribute("graphSeriesSelections", seriesSelections);
                break;
            case "session":
                if (session.getAttribute("graphSeriesSelections") != null) {
                    seriesSelections = (Set<String>) session.getAttribute("graphSeriesSelections");
                } else {
                    seriesSelections = new TreeSet<>();
                    session.setAttribute("graphSeriesSelections", seriesSelections);
                }
                break;
            default:
                seriesSelections = new TreeSet<>();
                seriesSelections.add(seriesOptions.get(0).getName());
                session.setAttribute("graphSeriesSelections", seriesSelections);
                break;
        }

        // Process the series to see what was selected and what was not.  Save this for easy look up in the view.
        Map<String, Boolean> seriesMap = new TreeMap<>();
        for (Series series : seriesOptions) {
            seriesMap.put(series.getName(), seriesSelections.contains(series.getName()));
        }

        // Now process the series sets
        List<String> seriesSetSelections;
        switch (seriesCase) {
            case "request":
                seriesSetSelections = (serSetSel == null) ? new ArrayList<String>() : Arrays.asList(serSetSel);
                session.setAttribute("graphSeriesSetSelections", seriesSetSelections);
                break;
            case "session":
                if (session.getAttribute("graphSeriesSetSelections") == null) {
                    seriesSetSelections = new ArrayList<>();
                    session.setAttribute("graphSeriesSetSelections", seriesSetSelections);
                } else {
                    seriesSetSelections = (List<String>) session.getAttribute("graphSeriesSetSelections");
                }
                break;
            default:
                seriesSetSelections = new ArrayList<>();
                break;
        }

        // Figure out which series sets were selected and which were not.  Save in a map for easy lookup in the view
        Map<String, Boolean> seriesSetMap = new TreeMap<>();
        for (SeriesSet seriesSet : seriesSetOptions) {
            // Don't force a series set to be selected like we did for the series.  We just need at least one series to display.
            seriesSetMap.put(seriesSet.getName(), seriesSetSelections.contains(seriesSet.getName()));
        }

        // Process the location selections.  Use the request version if given, the session if not, and all available options if we have nothing
        // Get a list of the location options
        EventService es = new EventService();
        List<String> locationOptions;
        try {
            locationOptions = es.getLocationNames();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for location information.", ex);
            throw new ServletException("Error querying database for location information.");
        }
        Map<String, Boolean> locationMap = new HashMap<>();

        List<String> locationSelections;
        if (locSel != null && locSel.length != 0) {
            // The user made a request so use it
            locationSelections = Arrays.asList(locSel);
            session.setAttribute("graphLocationSelections", locationSelections);
        } else if (session.getAttribute("graphLocationSelections") != null) {
            // The user did not make a request, but we have usable session info
            locationSelections = (List<String>) session.getAttribute("graphLocationSelections");
            redirectNeeded = true;
        } else {
            // The user did not make a request, and we do not have useable session info.  Select them all.
            locationSelections = new ArrayList<>(locationOptions);
            redirectNeeded = true;
        }

        // See which options were selected and which were not.  Save for easy lookup in the view.
        for (String location : locationOptions) {
            locationMap.put(location, locationSelections.contains(location));
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
                EventFilter currentFilter = new EventFilter(Arrays.asList(id), begin, end, "rf", locationSelections, null, null);
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
                EventFilter eFilter = new EventFilter(null, begin, end, "rf", locationSelections, null, null);
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
            EventFilter eFilter = new EventFilter(null, begin, end, "rf", locationSelections, null, null);
            eventList = es.getEventListWithoutData(eFilter);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for event information.", ex);
            throw new ServletException("Error querying database for event information.");
        }

        // If a redirect was found to be needed, build the URL based on variables set above and redirect to it.
        if (redirectNeeded) {
            String redirectUrl = request.getContextPath() + "/graph?"
                    + "eventId=" + URLEncoder.encode((id == null ? "" : "" + id), "UTF-8")
                    + "&begin=" + URLEncoder.encode(beginString, "UTF-8")
                    + "&end=" + URLEncoder.encode(endString, "UTF-8");
            for (String location : locationSelections) {
                redirectUrl += "&location=" + URLEncoder.encode(location, "UTF-8");
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

        request.setAttribute("begin", beginString);
        request.setAttribute("end", endString);
        request.setAttribute("locationSelections", locationSelections);
        request.setAttribute("seriesSelections", seriesSelections);
        request.setAttribute("seriesSetSelections", seriesSetSelections);
        request.setAttribute("seriesMasterSet", seriesMasterSet);
        request.setAttribute("locationMap", locationMap);
        request.setAttribute("seriesMap", seriesMap);
        request.setAttribute("seriesSetMap", seriesSetMap);
        request.setAttribute("eventId", id);
        request.setAttribute("eventListJson", eventListJson.toString());
        request.setAttribute("currentEvent", currentEvent == null ? "null" : currentEvent.toDyGraphJsonObject(seriesMasterSet).toString());

        request.getRequestDispatcher("/WEB-INF/views/graph.jsp").forward(request, response);
    }
}
