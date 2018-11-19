/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.presentation.controller.ajax;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.filter.SeriesSetFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.business.service.SeriesService;
import org.jlab.wfbrowser.business.util.TimeUtil;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.SeriesSet;

/**
 *
 * @author adamc
 */
@WebServlet(name = "event", urlPatterns = {"/ajax/event"})
public class EventAjax extends HttpServlet {

    private final static Logger LOGGER = Logger.getLogger(EventAjax.class.getName());

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method. Used to query for "EventAjax"
     * data as REST API end point.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String[] eArray = request.getParameterValues("id");
        List<Long> eventIdList = null;
        if (eArray != null) {
            eventIdList = new ArrayList<>();
            for (String eventId : eArray) {
                if (eventId != null && (!eventId.isEmpty())) {
                    eventIdList.add(Long.valueOf(eventId));
                }
            }
        }

        String beginString = request.getParameter("begin");
        Instant begin = beginString == null ? null : TimeUtil.getInstantFromDateTimeString(beginString);
        String endString = request.getParameter("end");
        Instant end = endString == null ? null : TimeUtil.getInstantFromDateTimeString(endString);
        String system = request.getParameter("system");
        system = system == null ? "rf" : system;
        String[] locArray = request.getParameterValues("location");
        List<String> locationList = locArray == null ? null : Arrays.asList(locArray);
        String[] serArray = request.getParameterValues("series");
        List<String> seriesList = serArray == null ? null : Arrays.asList(serArray);
        String[] serSetArray = request.getParameterValues("seriesSet");
        List<String> seriesSetList = serSetArray == null ? null : Arrays.asList(serSetArray);
        String requester = request.getParameter("requester");

        String arch = request.getParameter("archive");
        Boolean archive = (arch == null) ? null : arch.equals("true");
        String del = request.getParameter("toDelete");
        Boolean delete = (del == null) ? null : del.equals("true");
        Boolean includeData = Boolean.parseBoolean(request.getParameter("includeData"));  // false if not supplied or not "true"

        String out = request.getParameter("out");
        String output = "json";
        if (out != null) {
            switch (out) {
                case "json":
                    output = "json";
                    break;
                case "csv":
                    output = "csv";
                    break;
                case "dygraph":
                    output = "dygraph";
                    break;
                default:
                    output = "json";
            }
        }

        if (eventIdList != null && eventIdList.isEmpty()) {
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                pw.write("{\"error\": \"No event IDs specified\"}");
            }
            return;
        }

        // The Event objects can filter the data the return based on a Set of series names.  Since we take Series and SeriesSet
        // names as parameters to this endpoint, we need to combine them into a single set of Series names, i.e., a master set
        Set<String> seriesMasterSet = null;

        // Add any series from the seriesSets.
        if (seriesSetList != null) {
            if (seriesMasterSet == null) {
                seriesMasterSet = new TreeSet<>();
            }

            // Setup the query by the SeriesSets by names
            SeriesService ss = new SeriesService();
            SeriesSetFilter sfilter = new SeriesSetFilter(null, null, seriesSetList);
            try {
                // For each SeriesSet return, add the names of it's contained series to the master set
                List<SeriesSet> seriesSets = ss.getSeriesSets(sfilter);
                for (SeriesSet set : seriesSets) {
                    for (Series series : set.getSet()) {
                        System.out.println(series.getName());
                        seriesMasterSet.add(series.getName()); // Not null
                    }
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying database for series sets information");
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                try (PrintWriter pw = response.getWriter()) {
                    pw.print("{\"error\": \"error querying database - " + ex.getMessage() + "\"}");
                }
            }
        }
        if (seriesList != null) {
            if (seriesMasterSet == null) {
                seriesMasterSet = new TreeSet<>();
            }
            seriesMasterSet.addAll(seriesList);
        }

        EventService wfs = new EventService();
        // Enforce an rf system filter since this is likely to be an interface for only RF systems for some time
        EventFilter filter = new EventFilter(eventIdList, begin, end, system, locationList, archive, delete);

        // Output data in the request format.  CSV probably only makes sense if you wanted the data, but not reason to not support
        // the no data case.
        List<Event> eventList;
        try {
            if (includeData) {
                eventList = wfs.getEventList(filter);
            } else {
                eventList = wfs.getEventListWithoutData(filter);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database");
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter pw = response.getWriter()) {
                pw.print("{\"error\": \"error querying database - " + ex.getMessage() + "\"}");
            }
            return;
        } catch (FileNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "Error querying data - {0}", ex.getMessage());
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter pw = response.getWriter()) {
                pw.print("{\"error\": \"error querying data - " + ex.getMessage() + "\"}");
            }
            return;
        }

        // The graph page uses AJAX calls to get data when a user clicks on the timeline or "next"/"prev" control buttons
        if (requester != null && requester.equals("graph")) {
            if (!eventList.isEmpty()) {
                HttpSession session = request.getSession();
                session.setAttribute("graphCurrentEvent",eventList.get(0));
            }
        }

        if (output.equals("json") || output.equals("dygraph")) {
            JsonObjectBuilder job = Json.createObjectBuilder();
            JsonArrayBuilder jab = Json.createArrayBuilder();
            for (Event e : eventList) {
                if (output.equals("json")) {
                    jab.add(e.toJsonObject(seriesMasterSet));
                } else if (output.equals("dygraph")) {
                    jab.add(e.toDyGraphJsonObject(seriesMasterSet));
                }
            }
            job.add("events", jab.build());

            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.print(job.build().toString());
            }
            return;
        } else if (output.equals("csv")) {
            response.setContentType("text/csv");
            // This only returns the first event in a csv.  Update so that multiple CSVs are tar.gz'ed and sent, but not needed
            // for now.  Only used to send over a single event to a dygraph chart widget.
            try (PrintWriter pw = response.getWriter()) {
                for (Event e : eventList) {
                    if (e.getWaveforms() != null && (!e.getWaveforms().isEmpty())) {
                        pw.write(e.toCsv(seriesMasterSet));
                    } else {
                        pw.write("No data requested");
                    }
                    break;
                }
            }
            return;
        }
    }

    /**
     * Handle logic for events to be added to waveform database. Part of REST
     * API.
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String datetime = request.getParameter("datetime");
        String location = request.getParameter("location");
        String system = request.getParameter("system");
        String archive = request.getParameter("archive");
        String delete = request.getParameter("delete");
        response.setContentType("application/json");

        if (datetime == null || location == null || system == null) {
            try (PrintWriter pw = response.getWriter()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                pw.write("{\"error\": \"Missing required argument.  Requires datetime, location, system\"}");
            }
            return;
        }

        Instant t = TimeUtil.getInstantFromDateTimeString(datetime);
        EventService wfs = new EventService();
        try {

            Boolean arch = archive != null;
            Boolean del = delete != null;
            Event event = new Event(t, location, system, arch, del, null);
            long id = wfs.addEvent(event, false);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"id\": \"" + id + "\", \"message\": \"Waveform event successfully added to database\"}");
            }
        } catch (SQLException e) {
            try (PrintWriter pw = response.getWriter()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                pw.write("{\"error\": \"Problem updating database - " + e.toString() + "\"}");
            }
        } catch (IOException e) {
            try (PrintWriter pw = response.getWriter()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                pw.write("{\"error\": \"Problem adding event - " + e.toString() + "\"}");
            }
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        String id = request.getParameter("id");
        String arch = request.getParameter("archive");
        String del = request.getParameter("delete");
        Long eventId;

        if (id == null || id.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\": \"id must be specified and a valid long integer\"}");
            }
            return;
        }

        try {
            eventId = Long.parseLong(id);
        } catch (NumberFormatException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\": \"id must be a valid long integer\"}");
            }
            return;
        }

        if (arch == null && del == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\": \"either archive or delete parameter must be specified\"}");
            }
            return;
        } else if ((arch != null && del != null) && (Boolean.getBoolean(arch) && Boolean.getBoolean(del))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\": \"only archive or delete flag can be set\"}");
            }
            return;
        }

        Boolean archive = Boolean.parseBoolean(arch);
        Boolean delete = Boolean.parseBoolean(del);

        EventService wfs = new EventService();
        try {
            // Cannot set an event to both be deleted and archived
            if (arch != null) {
                wfs.setEventArchiveFlag(eventId, archive);
                if (archive == true) {
                    wfs.setEventDeleteFlag(eventId, false);
                }
            }
            // Cannot set an event to both be deleted and archived
            if (del != null) {
                wfs.setEventDeleteFlag(eventId, delete);
                if (delete == true) {
                    wfs.setEventArchiveFlag(eventId, false);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error updating database - {0}", ex.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Error updating the database - " + ex.getMessage() + "\"}");
            }
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        try (PrintWriter pw = response.getWriter()) {
            pw.write("{\"message\":\"Update successful\"}");
        }
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
