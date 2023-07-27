package org.jlab.wfbrowser.business.service;

import org.jlab.wfbrowser.business.util.SqlUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class manage all methods directly relating to label information not tied to events
 */
public class LabelService {

    public Map<String, List<String>> getDistinctLabels(List<String> labelNames, String system) throws SQLException {

        Map<String, List<String>> out = new HashMap<>();

        StringBuilder sql = new StringBuilder("SELECT DISTINCT system_name, label_name, label_value\n" +
                "FROM label\n" +
                "         JOIN event e ON label.event_id = e.event_id\n" +
                "         JOIN system_type st ON e.system_id = st.system_id");

        // Build up the collection of WHERE statement clauses if needed
        List<String> filters = new ArrayList<>();
        if (system != null) {
            filters.add("system_name = ?");
        }
        if (labelNames != null && !labelNames.isEmpty()) {
            StringBuilder filter = new StringBuilder("label_name IN (?");
            for (int i = 1; i < labelNames.size(); i++) {
                filter.append(",?");
            }
            filter.append(")");
            filters.add(filter.toString());
        }

        if (!filters.isEmpty()) {
            sql.append(" WHERE ").append(filters.get(0));
            for (int i = 1; i < filters.size(); i++) {
                sql.append(" AND ").append(filters.get(i));
            }
        }

        // Get the output back in a reasonable order
        sql.append(" ORDER BY system_name, label_name, label_value");

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql.toString());
            int i = 1;
            if (system != null) {
                pstmt.setString(i++, system);
            }
            if (labelNames != null && !labelNames.isEmpty()) {
                for (String name : labelNames) {
                    pstmt.setString(i++, name);
                }
            }

            rs = pstmt.executeQuery();
            while (rs.next()) {
                String labelName = rs.getString("label_name");
                String labelValue = rs.getString("label_value");
                if(!out.containsKey(labelName)) {
                    out.put(labelName, new ArrayList<>());
                }
                out.get(labelName).add(labelValue);
            }
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }

        return out;
    }
}
