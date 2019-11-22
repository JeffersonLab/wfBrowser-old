package org.jlab.wfbrowser.presentation.controller.reports;

import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.filter.LabelFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.business.util.TimeUtil;
import org.jlab.wfbrowser.model.Event;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "RFFaultTable", urlPatterns = {"/reports/rf-fault-table"})
public class RFFaultTable extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(RFFaultTable.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String beginString = request.getParameter("begin");
        String endString = request.getParameter("end");
        String confString = request.getParameter("conf");
        String confOpString = request.getParameter("confOp");
        String[] locationStrings = request.getParameterValues("location");
        String isLabeledString = request.getParameter("isLabeled");

        Instant begin = beginString == null ? null : TimeUtil.getInstantFromDateTimeString(beginString);
        Instant end = endString == null ? null : TimeUtil.getInstantFromDateTimeString(endString);
        List<String> locationSelections = locationStrings == null ? null : Arrays.asList(locationStrings);
        boolean isLabeled = Boolean.parseBoolean(isLabeledString);

        boolean redirectNeeded = false;
        Double confidence;

        // If someone doesn't supply a confidence value, set it to 0.0.  This works permissively with the default confOp
        // of ">"
        if (confString == null || confString.isEmpty()) {
            redirectNeeded = true;
            confString = "0.0";
            confidence = 0.0;
        } else {
            confidence = Double.valueOf(confString);
        }
        // If someone supplied a confidence, but not an operator, assume a default operator of ">"
        if (confOpString == null) {
            redirectNeeded = true;
            confOpString = ">";
        }

        EventService es = new EventService();

        // Check that we have some basic values for our query.  If not, set reasonable defaults and redirect to this
        // end point with the added parameters.
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        if (end == null) {
            redirectNeeded = true;
            end = Instant.now();
            endString = dtf.format(end);
        }
        if (begin == null) {
            redirectNeeded = true;
            begin = end.plus(-7, ChronoUnit.DAYS);
            beginString = dtf.format(begin);
        }

        // Get valid location options and check that we have location selections
        List<String> locationOptions;
        try {
            locationOptions = es.getLocationNames(Collections.singletonList("rf"));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error querying database for known location names - " + e);
            throw new ServletException(e);
        }
        if (locationSelections == null || locationSelections.isEmpty()) {
            redirectNeeded = true;
            locationSelections = locationOptions;
        }

        // The map gets used by the view for tracking valid options and which options have been selected
        Map<String, Boolean> locationSelectionMap = new TreeMap<>();
        for (String loc : locationOptions) {
            locationSelectionMap.put(loc, locationSelections.contains(loc));
        }

        // Redirect if needed.  Make sure we grab all of our user selections to make this bookmark-able
        if (redirectNeeded) {
            StringBuilder redirectUrl = new StringBuilder(request.getContextPath() + "/reports/rf-fault-table?" +
                    "begin=" + URLEncoder.encode(beginString, "UTF-8") +
                    "&end=" + URLEncoder.encode(endString, "UTF-8"));
            redirectUrl.append("&conf=").append(URLEncoder.encode(confString, "UTF-8")).append("&confOp=").append(URLEncoder.encode(confOpString, "UTF-8"));
            for (String location : locationSelections) {
                redirectUrl.append("&location=").append(URLEncoder.encode(location, "UTF-8"));
            }
            response.sendRedirect(response.encodeRedirectURL(redirectUrl.toString()));
            return;
        }

        List<Event> eventList = new ArrayList<>();
        try {
            // Get the tally of labeled events
            EventFilter ef = new EventFilter(null, begin, end, "rf", locationSelections, null, null, null, null);

            // Get the list of events that match both the event filters and the label confidence filter
            // Note: that filtering on label confidence implies that only labeled events will be returned
            LabelFilter lf = new LabelFilter(null, null, null, confidence, confOpString);
            eventList = es.getEventListWithoutCaptureFiles(ef);
            List<Event> filteredList = lf.filterEvents(eventList);

            // If the user does not want only labeled events, add back the unlabeled events that were filtered out
            if (!isLabeled) {
                LabelFilter unlabFilter = new LabelFilter(false);
                filteredList.addAll(unlabFilter.filterEvents(eventList));
            }
            eventList = filteredList;
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error querying database for event data");
            throw new ServletException(ex);
        }

        // Sort the list here so that it is displayed in a reasonable order on the client
        Collections.sort(eventList);

        request.setAttribute("locationSelectionMap", locationSelectionMap);
        request.setAttribute("locationSelections", locationSelections);
        request.setAttribute("confString", confString);
        request.setAttribute("confOpString", confOpString);
        request.setAttribute("beginString", beginString);
        request.setAttribute("endString", endString);
        request.setAttribute("isLabeled", isLabeled);
        request.setAttribute("eventList", eventList);
        request.getRequestDispatcher("/WEB-INF/views/reports/rf-fault-table.jsp").forward(request, response);
    }
}
