package org.jlab.wfbrowser.business.filter;

import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Label;

import java.util.*;

/**
 * This class is meant to provide support for the EventFilter class in filtering events based on event labels.  This does
 * not provide a full where clause, only it's portion of the content.  Similarly logic applies to the assignParameterValues
 * method.  Filters here will be ANDed together in the SQL WHERE clause
 */
public class LabelFilter {
    private final Boolean isLabeled;            // Controls the level of filter that is done.  If null, do fine grained filtering
    // If not, filter on whether or not the event has a label
    private final List<String> modelNameList;   // Names of the model that produced the label
    private final List<Long> idList;            // IDs of the labels

    // Maps names (e.g., "cavity" or "fault-type") to a list of valid values (e.g. ("1", "2") or ("Microphonics")
    private final Map<String, List<String>> nameValueMap;
    private final Double confidence;            // Label confidence level to compare against (e.g., 0.77 or 0.5)
    private final String confidenceOperator;    // What comparison to make against the confidence.  Must be a valid
    // SQL comparison operator (e.g., "<", ">=", or "!=")

    /**
     * This object allows for filtering out events that do not match all of the specified criteria.  The how each parameter
     * is used in the filter is different so be careful:
     * <ul>
     * <li>modelNameList: Any event containing at least one label that matches ANY of the specified modelNames is returned</li>
     * <li>idList: Any event containing a label that matches ANY of the ids is returned</li>
     * <li>nameValueMap: An event is returned only if it has a label matching each specified name key, and a value matching
     * one of the values associated with that name</li>
     * <li>confidence & confidenceOperator: These must be specified as a pair.  An event is returned if all of it's labels
     * have a confidence that meets the specified criteria.  valid values of confidence parameter are >, >=, <, <=, ==, !=, "null"</li>
     * </ul>
     * <p>
     * Note: A separate constructor is provided if only filtering on label existence is desired.
     * <p>
     * The returned list  is events that pass all of the above tests (i.e., each set of results is AND'ed together).
     *
     * @param modelNameList
     * @param idList
     * @param nameValueMap
     * @param confidence
     * @param confidenceOperator
     */
    public LabelFilter(List<String> modelNameList, List<Long> idList, Map<String, List<String>> nameValueMap, Double confidence, String confidenceOperator) {
        this.isLabeled = null;
        this.modelNameList = modelNameList;
        this.idList = idList;
        this.nameValueMap = nameValueMap;

        if (!(confidence == null && confidenceOperator == null) && !(confidenceOperator != null && confidence != null)) {
            throw new IllegalArgumentException("confidence and confidenceOperator must either both be NULL or both be not NULL");
        }
        this.confidence = confidence;

        List<String> validOps = Arrays.asList("=", "!=", ">", ">=", "<", "<=", "null");
        if (confidenceOperator != null && !validOps.contains(confidenceOperator)) {
            throw new IllegalArgumentException(("Invalid confidenceOperator.  Valid options are " + validOps.toString()));
        }
        this.confidenceOperator = confidenceOperator;
    }

    /**
     * Construct a label filter that will filter events on whether or not they have labels.
     *
     * @param isLabeled
     */
    public LabelFilter(boolean isLabeled) {
        this.isLabeled = isLabeled;
        modelNameList = null;
        idList = null;
        nameValueMap = null;
        confidence = null;
        confidenceOperator = null;
    }


    /**
     * A method for filtering out events that do not meet the criteria specified in the LabelFilter.  Does filtering on
     * results after retrieving them from the database.
     *
     * @param eventList
     * @return
     */
    public List<Event> filterEvents(List<Event> eventList) {

        List<Event> out = new ArrayList<>();

        for (Event e : eventList) {
            // Check which type of filtering we are doing.  If isLabeled != null, then we are just check for label existence
            if (isLabeled == null) {

                // Check that the event has labels.
                if (e.getLabelList() == null || e.getLabelList().isEmpty()) {
                    continue;
                }

                // This section performs a series of checks.  Should a check fail, then we continue on to the next event.
                // Should all checks pass, we add it to the output list.
                if (modelNameList != null) {
                    if (!checkModelNames(e)) {
                        continue;
                    }
                }
                if (idList != null && !idList.isEmpty()) {
                    if (!checkIds(e)) {
                        continue;
                    }
                }
                if (nameValueMap != null) {
                    if (!checkNameValueMap(e)) {
                        continue;
                    }
                }
                if (confidence != null) {
                    if (!checkConfidence(e)) {
                        continue;
                    }
                }
                out.add(e);
            } else if (isLabeled) {
                if (e.getLabelList() != null && !e.getLabelList().isEmpty()) {
                    out.add(e);
                }
            } else {
                if (e.getLabelList() == null || e.getLabelList().isEmpty()) {
                    out.add(e);
                }
            }
        }
        return out;
    }


    /**
     * Return true if the event has at least one label with a modelName that matches one of the names in modelNameLIst.
     *
     * @param e Event to check
     * @return True if the event has a label that matches a model name.  False otherwise.
     */
    private boolean checkModelNames(Event e) {
        if (e == null) {
            return false;
        }
        for (Label l : e.getLabelList()) {
            for (String modelName : modelNameList) {
                if (l.getModelName() != null && l.getModelName().equals(modelName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the event has a label with an ID that matches one of the IDs in idList.
     *
     * @param e Event to check
     * @return True if the event has a label with a matching ID. False, otherwise.
     */
    private boolean checkIds(Event e) {
        if (e == null) {
            return false;
        }
        for (Long id : idList) {
            for (Label l : e.getLabelList()) {
                if (l.getId() != null && l.getId().equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check that the event has labels that match the valid values for each label name.
     *
     * @param e Event to check
     * @return Return true if a label exists that matches each of the specified criteria.  False otherwise.
     */
    private boolean checkNameValueMap(Event e) {
        if (e == null) {
            return false;
        }

        // Setup the checks to be all false
        Map<String, Boolean> hasMatch = new HashMap<>();
        for (String name : nameValueMap.keySet()) {
            hasMatch.put(name, Boolean.FALSE);
        }

        // Go through the labels and update the hasMatch map as appropriate
        for (Label l : e.getLabelList()) {
            List<String> values = nameValueMap.get(l.getName());
            if (hasMatch.containsKey(l.getName())) {
                if (values == null) {
                    hasMatch.put(l.getName(), true); // null value list implies that any value is acceptable
                } else {
                    hasMatch.put(l.getName(), values.contains(l.getValue()));
                }
            }
        }

        // Only return true if all have a match
        Boolean out = null;
        for (Boolean match : hasMatch.values()) {
            if (out == null) {
                out = match;
            } else {
                // One false match will result in out == false
                out = out && match;
            }
        }

        // In case the loop gets skipped
        if (out == null) {
            out = false;
        }
        return out;
    }

    /**
     * Check that every label associated with the Event has a confidence that matches the specified criteria.
     *
     * @param e Event to check
     * @return True if all labels have a confidence
     */
    private boolean checkConfidence(Event e) {
        boolean out = true;
        for (Label l : e.getLabelList()) {
            switch (confidenceOperator) {
                case "null":
                    if (l.getConfidence() != null) {
                        out = false;
                    }
                    break;
                case "=":
                    if (!(l.getConfidence() != null && l.getConfidence().equals(confidence))) {
                        out = false;
                    }
                    break;
                case "!=":
                    if (!(l.getConfidence() != null && !l.getConfidence().equals(confidence))) {
                        out = false;
                    }
                    break;
                case ">":
                    if (!((l.getConfidence() != null) && (l.getConfidence() > confidence))) {
                        out = false;
                    }
                    break;
                case ">=":
                    if (!((l.getConfidence() != null) && (l.getConfidence() >= confidence))) {
                        out = false;
                    }
                    break;
                case "<":
                    if (!((l.getConfidence() != null) && (l.getConfidence() < confidence))) {
                        out = false;
                    }
                    break;
                case "<=":
                    if (!((l.getConfidence() != null) && (l.getConfidence() <= confidence))) {
                        out = false;
                    }
                    break;
                default:
                    out = false;
            }
        }
        return out;
    }
//
//    public String getWhereClauseContent() {
//        // Check if the filter should just filter on the existence of a Label.  Maybe this is a little hokey, but specifying all
//        // null is treated as requesting an existence filter
//        if (modelNameList == null && idList == null && nameList == null && valueList == null && confidence == null &&
//                confidenceOperator == null) {
//            return "(label_id IS NOT NULL)";
//        }
//
//        String sql = "(";
//        // Use label_id first, since it is a primary key we know it must exist for every label, check that it's not NULL
//        // (if parameter is not given) or that it is the specific value given.  Helps with placing the "AND"s.
//        if (idList == null || idList.isEmpty()) {
//            sql += "label_id IS NOT NULL";
//        } else {
//            sql += "label_id IN (?";
//            for (int i = 1; i < idList.size(); i++) {
//                sql += ",?";
//            }
//            sql += ")";
//        }
//
//        if (modelNameList != null && !modelNameList.isEmpty()) {
//            sql += " AND model_name IN (?";
//            for (int i = 1; i < modelNameList.size(); i++) {
//                sql += ",?";
//            }
//            sql += ")";
//        }
//
//        if (nameList != null && !nameList.isEmpty()) {
//            sql += " AND label_name IN (?";
//            for (int i = 1; i < nameList.size(); i++) {
//                sql += ",?";
//            }
//            sql += ")";
//        }
//
//        if (valueList != null && !valueList.isEmpty()) {
//            sql += " AND label_value IN (?";
//            for (int i = 1; i < valueList.size(); i++) {
//                sql += ",?";
//            }
//            sql += ")";
//        }
//
//        if (confidence != null) {
//            sql += " AND label_confidence " + confidenceOperator + " ?";
//        }
//        sql += ")";
//        return sql;
//    }

//    /**
//     * Assign values to the parameters of a PreparedStatement.  Starts at specified parameter index.
//     * @param pstmt
//     * @param index
//     * @return
//     */
//    public int assignParameterValues(PreparedStatement pstmt, int index) throws SQLException {
//        int i = index;
//        if (idList != null && !idList.isEmpty()) {
//            for(Long id : idList) {
//                pstmt.setLong(i++, id);
//            }
//        }
//
//        if (modelNameList != null && !modelNameList.isEmpty()) {
//            for(String modelName : modelNameList) {
//                pstmt.setString(i++, modelName);
//            }
//        }
//
//        if (nameList != null && !nameList.isEmpty()) {
//            for(String name : nameList) {
//                pstmt.setString(i++, name);
//            }
//        }
//
//        if (valueList != null && !valueList.isEmpty()) {
//            for(String value : valueList) {
//                pstmt.setString(i++, value);
//            }
//        }
//
//        if (confidence != null) {
//            pstmt.setDouble(i++, confidence);
//        }
//
//        return i;
//    }

}
