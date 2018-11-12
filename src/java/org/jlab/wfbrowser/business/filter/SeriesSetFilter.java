/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.business.filter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author adamc
 */
public class SeriesSetFilter {
    private final List<Long> idList;
    private final String systemName;
    private final String setName;
    
    public SeriesSetFilter(List<Long> idList, String systemName, String setName) {
        this.idList = idList;
        this.systemName = systemName;
        this.setName = setName;
    }
    
    public String getWhereClause() {
        String filter = "";
        List<String> filterList = new ArrayList<>();
        
        if (idList != null && !idList.isEmpty()) {
            String idFilter = "set_id IN (?";
            for (int i = 1; i < idList.size(); i++) {
                idFilter += ",?";
            }
            idFilter += ")";
            filterList.add(idFilter);
        }
        
        if (systemName != null) {
            filterList.add("system_name = ?");
        }
        
        if (setName != null) {
            filterList.add("set_name = ?");
        }
        
        if (!filterList.isEmpty()) {
            filter = " WHERE " + filterList.get(0);
            for(int i = 1; i < filterList.size(); i++) {
                filter += " AND " + filterList.get(i);
            }
        }
        
        return filter;
    }
    
    public void assignParameterValues(PreparedStatement stmt) throws SQLException {
        int i = 1;
        
        if (idList != null && !idList.isEmpty()) {
            for(Long id : idList) {
                stmt.setLong(i++, id);
            }
        }
        if (systemName != null) {
            stmt.setString(i++, systemName);
        }
        
        if (setName != null) {
            stmt.setString(i++, setName);
        }        
    }
}
