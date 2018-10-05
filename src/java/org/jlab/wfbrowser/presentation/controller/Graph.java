package org.jlab.wfbrowser.presentation.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.business.util.TimeUtil;
import org.jlab.wfbrowser.model.Event;

/**
 *
 * @author ryans
 */
@WebServlet(name = "Graph", urlPatterns = {"/graph"})
public class Graph extends HttpServlet {

    private final Logger LOGGER = Logger.getLogger(Graph.class.getName());

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     * @throws java.sql.SQLException
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String date = request.getParameter("date");
        String location = request.getParameter("location");
        String eventId = request.getParameter("eventId");
        String[] seriesList = request.getParameterValues("series");

        if (date != null) {
            TimeUtil.validateDateString(date);
        }
        request.setAttribute("date", date);
        request.setAttribute("location", location);
        request.setAttribute("eventId", eventId);
        request.setAttribute("seriesList", seriesList);

//        if (date == null || date.isEmpty() || datetime == null || datetime.isEmpty() || eventId == null || eventId.isEmpty()
//                || series == null || series.length == 0) {
//            String redirectUrl = URLEncoder.encode(request.getContextPath() + "/graph", "UTF-8");
//            response.sendRedirect(redirectUrl);
//        }
        if (eventId != null) {
            long id = Long.parseLong(eventId);
            List<Long> eventIdList = new ArrayList<>();
            eventIdList.add(id);

            EventService es = new EventService();
            EventFilter filter = new EventFilter(eventIdList, null, null, "rf", null, null, null);
            List<Event> eventList;
            try {
                eventList = es.getEventList(filter);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying database", ex);
                throw new ServletException("Error querying database");
            }
            Event event = eventList.get(0);
            request.setAttribute("event_time_utc", event.getEventTimeString());
        }

        request.getRequestDispatcher("/WEB-INF/views/graph.jsp").forward(request, response);
    }
}
