package org.jlab.wfbrowser.presentation.controller.reports;

import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.filter.LabelFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Label;
import org.jlab.wfbrowser.presentation.util.GraphConfig;
import org.jlab.wfbrowser.presentation.util.SessionUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.time.Instant;
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
        String out = request.getParameter("out");

        List<String> locationSelections = locationStrings == null ? null : Arrays.asList(locationStrings);
        boolean isLabeled = Boolean.parseBoolean(isLabeledString);

        boolean redirectNeeded = false;

        // If someone doesn't supply a confidence value, set it to 0.0.  This works permissively with the default confOp
        // of ">"
        Double confidence;
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

        Instant begin, end;
        Map<String, Boolean> locationSelectionMap;

        String system = "rf";
        GraphConfig defaultGraphConfig = GraphConfig.getDefaultConfig(system);
        GraphConfig requestGraphConfig = new GraphConfig(system, null, null,
                null, null, beginString, endString, null, locationSelections,
                null, null, null, null);


        HttpSession session = request.getSession();
        synchronized (SessionUtils.getSessionLock(request, null)) {
            Map<String, GraphConfig> gcMap = (Map<String, GraphConfig>) session.getAttribute("graphConfigMap");
            if (gcMap == null) {
                gcMap = new HashMap<>();
                session.setAttribute("graphConfigMap", gcMap);
            }
            if (!gcMap.containsKey(system)) {
                gcMap.put(system, defaultGraphConfig);
                redirectNeeded = true;
            }
            GraphConfig sessionGraphConfig = gcMap.get(system);
            boolean updated = sessionGraphConfig.overwriteWith(requestGraphConfig);
            redirectNeeded = redirectNeeded || updated;

            begin = sessionGraphConfig.getBegin();
            end = sessionGraphConfig.getEnd();
            beginString = sessionGraphConfig.getBeginString();
            endString = sessionGraphConfig.getEndString();
            locationSelections = new ArrayList<>(sessionGraphConfig.getLocations());
            locationSelectionMap = sessionGraphConfig.getSelectionMap("location");

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
        }

        EventService es = new EventService();
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
        Collections.sort(eventList, Collections.reverseOrder());

        // If out is csv, then return the table as a CSV.  Otherwise show the report page
        if (out != null && out.equals("csv")) {
            response.setContentType("text/csv");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("timestamp,location,cavity-label,cavity-confidence,fault-type-label,fault-type-confidence,label-model\n");
                for (Event e : eventList) {
                    List<String> output = new ArrayList<>();

                    output.add(e.getEventTime().toString());
                    output.add(e.getLocation());

                    String cLabel = "N/A";
                    String cConf = "N/A";
                    String cModel = "N/A";
                    String fLabel = "N/A";
                    String fConf = "N/A";
                    String fModel = "N/A";
                    if (e.getLabelList() != null) {
                        for (Label l : e.getLabelList()) {
                            if (l.getName().equals("cavity")) {
                                cLabel = l.getValue();
                                cConf = l.getConfidence().toString();
                                cModel = l.getModelName();
                            }
                            if (l.getName().equals("fault-type")) {
                                fLabel = l.getValue();
                                fConf = l.getConfidence().toString();
                                fModel = l.getModelName();
                            }
                        }
                    }
                    output.add(cLabel);
                    output.add(cConf);
                    output.add(fLabel);
                    output.add(fConf);

                    // Model should be the same, but lets do this just in case
                    String model;
                    if (cModel != null && !cModel.equals(fModel)) {
                        model = cModel + "/" + fModel;
                    } else {
                        model = cModel;
                    }
                    output.add(model);

                    // Combine all of the output and write it out
                    pw.write(String.join(",", output) + "\n");
                }
            }
            return;
        }

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
