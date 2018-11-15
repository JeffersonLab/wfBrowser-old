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
        List<String> locationSelections = locSel == null ? new ArrayList<String>() : Arrays.asList(locSel);
        List<String> seriesSetSelections = serSetSel == null ? new ArrayList<String>() : Arrays.asList(serSetSel);
        String eventId = request.getParameter("eventId");

        Set<String> seriesSelections = new TreeSet<>();
        if (serSel != null) {
            for (String ser : serSel) {
                seriesSelections.add(ser);
            }
        }

        // Process the begin/end parameters
        boolean redirectNeeded = false;
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        Instant begin, end;
        if (beginString != null && !beginString.isEmpty()) {
            TimeUtil.validateDateTimeString(beginString);
            begin = TimeUtil.getInstantFromDateTimeString(beginString);
        } else {
            begin = now.plus(-2, ChronoUnit.DAYS);
            beginString = dtf.format(begin);
            redirectNeeded = true;
        }

        if (endString != null && !endString.isEmpty()) {
            TimeUtil.validateDateTimeString(endString);
            end = TimeUtil.getInstantFromDateTimeString(endString);
        } else {
            end = now;
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

        // Process the series to see what was selected
        Map<String, Boolean> seriesMap = new TreeMap<>();
        boolean firstPass = true;
        for (Series series : seriesOptions) {
            // If the user didn't select a series, pick the first one for them and mark the request for redirection.
            if (firstPass) {
                if (seriesSelections.isEmpty()) {
                    seriesSelections.add(series.getName()); // Add to this list so the redirect can pick it up.
                    redirectNeeded = true;
                }
                firstPass = false;
            }
            seriesMap.put(series.getName(), seriesSelections.contains(series.getName()));
        }

        // Now process the series sets
        Map<String, Boolean> seriesSetMap = new TreeMap<>();
        for (SeriesSet seriesSet : seriesSetOptions) {
            // Don't force a series set to be selected like we did for the series.  We just need at least one series to display.
            seriesSetMap.put(seriesSet.getName(), seriesSetSelections.contains(seriesSet.getName()));
        }

        // Create a map of the location options and whether or not the user selected them.
        EventService es = new EventService();
        List<String> locationOptions;
        try {
            locationOptions = es.getLocationNames();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for location information.", ex);
            throw new ServletException("Error querying database for location information.");
        }
        Map<String, Boolean> locationMap = new HashMap<>();

        // If the user didn't pick a location, then pick them all and mark it for redirect
        // Add the selections while processing the valid options.
        boolean noLocationsSelected = false;
        if (locationSelections.isEmpty()) {
            noLocationsSelected = true;
            redirectNeeded = true;
        }

        for (String location : locationOptions) {
            if (noLocationsSelected) {
                locationSelections.add(location);  // Add to this list so that the redirect will pick it up.
            }
            locationMap.put(location, locationSelections.contains(location));
        }

        Event currentEvent;
        Long id;
        List<Event> eventList;
        EventFilter eFilter = new EventFilter(null, begin, end, "rf", locationSelections, null, null);
        try {
            eventList = es.getEventListWithoutData(eFilter);
            if (eventId == null || eventId.isEmpty()) {
                if (eventId == null) {
                    redirectNeeded = true;
                }
                id = es.getMostRecentEventId(eFilter);
            } else {
                id = Long.parseLong(eventId);
            }

            if (id == null) {
                currentEvent = null;
            } else {
                EventFilter currentFilter = new EventFilter(Arrays.asList(id), null, null, "rf", null, null, null);
                List<Event> currentEventList = es.getEventList(currentFilter);
                if (currentEventList == null || currentEventList.isEmpty()) {
                    currentEvent = null;
                } else {
                    currentEvent = currentEventList.get(0);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database for event information.", ex);
            throw new ServletException("Error querying database for event information.");
        }

        // If a redirect was found to be needed, build the URL based on variables set above and redirect to it.
        if (redirectNeeded) {
            System.out.println("redirectNeeded");
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
