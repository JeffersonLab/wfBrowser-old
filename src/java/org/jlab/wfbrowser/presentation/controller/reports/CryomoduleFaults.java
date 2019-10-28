package org.jlab.wfbrowser.presentation.controller.reports;

import org.jlab.wfbrowser.business.service.EventService;

import javax.json.JsonArray;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "CryomoduleFaults", urlPatterns = {"/reports/cryomodule-faults"})
public class CryomoduleFaults extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(CryomoduleFaults.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        EventService es = new EventService();
        Map<String, Map<String, Long>> tallyMap;
        JsonArray tallyArray;
        try {
            tallyMap = es.getLabelTally(null);
            tallyArray = es.getLabelTallyAsJson(null);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error querying database for label tally");
            throw new ServletException(ex);
        }

        request.setAttribute("tallyMap", tallyMap);
        request.setAttribute("tallyArray", tallyArray);
        request.getRequestDispatcher("/WEB-INF/views/reports/cryomodule-faults.jsp").forward(request, response);
    }
}
