package org.jlab.wfbrowser.business.filter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * This class is meant to provide support for the EventFilter class in filtering events based on event labels.  This does
 * not provide a full where clause, only it's portion of the content.  Similarly logic applies to the assignParameterValues
 * method.  Filters here will be ANDed together in the SQL WHERE clause
 */
public class LabelFilter {
    // TODO: Figure out how to make this label filter stuff actually exclude an event and not just one of it's labels.
    private final List<String> modelNameList;   // Names of the model that produced the label
    private final List<Long> idList;            // IDs of the labels
    private final List<String> nameList;        // Label names to match (e.g., "cavity" or "fault-type")
    private final List<String> valueList;       // Label values to match (e.g., "1" or "Microphonics")
    private final Double confidence;            // Label confidence level to compare against (e.g., 0.77 or 0.5)
    private final String confidenceOperator;    // What comparison to make against the confidence.  Must be a valid
    // SQL comparison operator (e.g., "<", ">=", or "!="

    /**
     * This filter matches each parameter when they are not NULL.  If an individual parameter is NULL, then no filtering
     * is performed.  If all parameters are NULL, then the filter returns Events which are labeled.
     * <p>
     * The confidence parameter is slightly more complicated.  The confidence and confidenceOperators must both be
     * supplied as not NULL or both NULL.  The confidenceOperator must be a valid SQL operator (=, <, >, <=, >=, <>, !=).
     * on equality.  If an opera
     *
     * @param modelNameList
     * @param idList
     * @param nameList
     * @param valueList
     * @param confidence
     * @param confidenceOperator
     */
    public LabelFilter(List<String> modelNameList, List<Long> idList, List<String> nameList, List<String> valueList,
                       Double confidence, String confidenceOperator) {
        this.modelNameList = modelNameList;
        this.idList = idList;
        this.nameList = nameList;
        this.valueList = valueList;

        if (!(confidence == null && confidenceOperator == null) && !(confidenceOperator != null && confidence != null)) {
            throw new IllegalArgumentException("confidence and confidenceOperator must either both be NULL or both be not NULL");
        }
        this.confidence = confidence;

        List<String> validOps = Arrays.asList("=", "<>", "!=", ">", ">=", "<", "<=");
        if (confidenceOperator != null && !validOps.contains(confidenceOperator)) {
            throw new IllegalArgumentException(("Invalid confidenceOperator.  Valid options are " + validOps.toString()));
        }
        this.confidenceOperator = confidenceOperator;
    }

    public String getWhereClauseContent() {
        // Check if the filter should just filter on the existence of a Label.  Maybe this is a little hokey, but specifying all
        // null is treated as requesting an existence filter
        if (modelNameList == null && idList == null && nameList == null && valueList == null && confidence == null &&
                confidenceOperator == null) {
            return "(label_id IS NOT NULL)";
        }

        String sql = "(";
        // Use label_id first, since it is a primary key we know it must exist for every label, check that it's not NULL
        // (if parameter is not given) or that it is the specific value given.  Helps with placing the "AND"s.
        if (idList == null || idList.isEmpty()) {
            sql += "label_id IS NOT NULL";
        } else {
            sql += "label_id IN (?";
            for (int i = 1; i < idList.size(); i++) {
                sql += ",?";
            }
            sql += ")";
        }

        if (modelNameList != null && !modelNameList.isEmpty()) {
            sql += " AND model_name IN (?";
            for (int i = 1; i < modelNameList.size(); i++) {
                sql += ",?";
            }
            sql += ")";
        }

        if (nameList != null && !nameList.isEmpty()) {
            sql += " AND label_name IN (?";
            for (int i = 1; i < nameList.size(); i++) {
                sql += ",?";
            }
            sql += ")";
        }

        if (valueList != null && !valueList.isEmpty()) {
            sql += " AND label_value IN (?";
            for (int i = 1; i < valueList.size(); i++) {
                sql += ",?";
            }
            sql += ")";
        }

        if (confidence != null) {
            sql += " AND label_confidence " + confidenceOperator + " ?";
        }
        sql += ")";
        return sql;
    }

    /**
     * Assign values to the parameters of a PreparedStatement.  Starts at specified parameter index.
     * @param pstmt
     * @param index
     * @return
     */
    public int assignParameterValues(PreparedStatement pstmt, int index) throws SQLException {
        int i = index;
        if (idList != null && !idList.isEmpty()) {
            for(Long id : idList) {
                pstmt.setLong(i++, id);
            }
        }

        if (modelNameList != null && !modelNameList.isEmpty()) {
            for(String modelName : modelNameList) {
                pstmt.setString(i++, modelName);
            }
        }

        if (nameList != null && !nameList.isEmpty()) {
            for(String name : nameList) {
                pstmt.setString(i++, name);
            }
        }

        if (valueList != null && !valueList.isEmpty()) {
            for(String value : valueList) {
                pstmt.setString(i++, value);
            }
        }

        if (confidence != null) {
            pstmt.setDouble(i++, confidence);
        }

        return i;
    }

}
