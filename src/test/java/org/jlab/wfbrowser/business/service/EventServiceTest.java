package org.jlab.wfbrowser.business.service;

import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import javax.json.Json;
import javax.json.JsonArray;
import javax.naming.NamingException;

import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.filter.LabelFilter;
import org.jlab.wfbrowser.connectionpools.StandaloneConnectionPools;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Label;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import org.junit.Assert;

/**
 * @author adamc
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EventServiceTest {

    // The two timestamps used for events.  t1 for unzipped, t2 for zipped.
    private static Instant t1 = null;
    private static Instant t2 = null;
    private static Instant t3 = null;
    private static Instant t4 = null;

    // Consistent, no class, grouped
    private static Event e1_grp_con_no_class = null;
    private static Event e2_grp_con_no_class = null;
    private static Event e1_grp_con_class1 = null;
    private static Event e2_grp_con_class1 = null;

    // Inconsistent, no class, grouped
    private static Event e1_grp_incon_noclass = null;
    private static Event e2_grp_incon_noclass = null;
    private static Event e1_grp_incon_class1 = null;
    private static Event e2_grp_incon_class1 = null;

    // Consistent, no class, ungrouped
    private static Event e1_ungrp_noclass = null;
    private static Event e2_ungrp_noclass = null;
    private static Event e1_ungrp_class1 = null;
    private static Event e2_ungrp_class1 = null;

    // Consistent, no class, grouped, with labels
    private static Event e3_grp_con_noclass_label = null;
    private static Event e4_grp_con_noclass_label = null;

    private static List<Event> eventList = new ArrayList<>();

    @BeforeClass
    public static void oneTimeSetUp() throws NamingException, SQLException, IOException {
        System.out.println("EventServiceTest Start of setup");
        StandaloneConnectionPools.setupConnectionPool();

        // Create some events to add to the database - files that match these must exist on the filesystem
        t1 = LocalDateTime.of(2017, 9, 14, 10, 0, 0).atZone(ZoneId.systemDefault()).toInstant().plusMillis(100);  // For the unzipped files
        t2 = LocalDateTime.of(2017, 9, 14, 11, 0, 0).atZone(ZoneId.systemDefault()).toInstant().plusMillis(100);  // For the zipped files
        t3 = LocalDateTime.of(2017, 9, 14, 12, 0, 0).atZone(ZoneId.systemDefault()).toInstant().plusMillis(100);  // For the label tests
        t4 = LocalDateTime.of(2017, 9, 14, 13, 0, 0).atZone(ZoneId.systemDefault()).toInstant().plusMillis(100);  // For the label tests

        // Setup the flags for the basic testing.  Make variable names match what they do.
        boolean unarchive = false;
        boolean noDelete = false;
        boolean grouped = true;
        boolean ungrouped = false;
        String grp_con = "grouped-consistent";
        String grp_incon = "grouped-inconsistent";
        String ungrp = "ungrouped";
        String noClass = "";
        String class1 = "class1";

        // Used for grouped events
        String nullCF = null;

        // Used for ungrouped events
        String zipCF = "test3.2017_09_14_110000.1.txt"; // Must be base file name, since in practice the application won't know whether or not an event has been compressed.
        String unzipCF = "test3.2017_09_14_100000.1.txt";

        // Setup the grouped and consistent events
        // The e1 events mapped to the unzipped files, e2 events map to the zipped files.  e1a, etc. are duplicates of e1 and tested for equality, etc.
        // These will almost certainly not match the IDs in the database, but are used/needed for testing.
        e1_grp_con_no_class = new Event(t1, grp_con, "test", unarchive, noDelete, grouped, noClass, nullCF, null);
        e2_grp_con_no_class = new Event(t2, grp_con, "test", unarchive, noDelete, grouped, noClass, nullCF, null);  // Should not match e1 since different time
        e1_grp_con_class1 = new Event(t1, grp_con, "test", unarchive, noDelete, grouped, class1, nullCF, null);
        e2_grp_con_class1 = new Event(t2, grp_con, "test", unarchive, noDelete, grouped, class1, nullCF, null);
        eventList.add(e1_grp_con_no_class);
        eventList.add(e2_grp_con_no_class);
        eventList.add(e1_grp_con_class1);
        eventList.add(e2_grp_con_class1);

        // Setup the grouped and incosistent events
        e1_grp_incon_noclass = new Event(t1, grp_incon, "test", unarchive, noDelete, grouped, noClass, nullCF, null);
        e2_grp_incon_noclass = new Event(t2, grp_incon, "test", unarchive, noDelete, grouped, noClass, nullCF, null);
        e1_grp_incon_class1 = new Event(t1, grp_incon, "test", unarchive, noDelete, grouped, class1, nullCF, null);
        e2_grp_incon_class1 = new Event(t2, grp_incon, "test", unarchive, noDelete, grouped, class1, nullCF, null);
        eventList.add(e1_grp_incon_noclass);
        eventList.add(e2_grp_incon_noclass);
        eventList.add(e1_grp_incon_class1);
        eventList.add(e2_grp_incon_class1);

        // Setup the ungrouped events (consistent by default)
        e1_ungrp_noclass = new Event(t1, ungrp, "test", unarchive, noDelete, ungrouped, noClass, unzipCF, null);
        e2_ungrp_noclass = new Event(t2, ungrp, "test", unarchive, noDelete, ungrouped, noClass, zipCF, null);
        e1_ungrp_class1 = new Event(t1, ungrp, "test", unarchive, noDelete, ungrouped, class1, unzipCF, null);
        e2_ungrp_class1 = new Event(t2, ungrp, "test", unarchive, noDelete, ungrouped, class1, zipCF, null);
        eventList.add(e1_ungrp_noclass);
        eventList.add(e2_ungrp_noclass);
        eventList.add(e1_ungrp_class1);
        eventList.add(e2_ungrp_class1);

        // Setup some grouped events with RF like labels (cavity, fault-type)
        List<Label> labelList1 = new ArrayList<>();
        labelList1.add(new Label(null, null, "myModel", "cavity", "7", 0.99));
        labelList1.add(new Label(null, null, "myModel", "fault-type", "fault_2", 0.50));
        List<Label> labelList2 = new ArrayList<>();
        labelList2.add(new Label(null, null, "myModel", "cavity", "7", 0.90));
        labelList2.add(new Label(null, null, "myModel", "fault-type", "fault_1", 0.45));
        e3_grp_con_noclass_label = new Event(t3, grp_con, "test", unarchive, noDelete, grouped, noClass, nullCF, labelList1);
        e4_grp_con_noclass_label = new Event(t4, grp_con, "test", unarchive, noDelete, grouped, noClass, nullCF, labelList2);
        eventList.add(e3_grp_con_noclass_label);
        eventList.add(e4_grp_con_noclass_label);
    }


    /**
     * Test of addEvent method, of class EventService.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void test1AddEvent() throws Exception {
        System.out.println("addEvent");
        EventService instance = new EventService();

        // Set the test class parameter for use in other tests
        List<Long> eventIdList = new ArrayList<>();
        for (Event e : eventList) {
            long id = instance.addEvent(e);
            eventIdList.add(id);
            e.setEventId(id);
        }

        EventFilter filter = new EventFilter(eventIdList, null, null, null, null, null, null, null, null);

        List<Event> result = instance.getEventList(filter);

        assertEquals(eventList.size(), result.size());

        // Add a duplicate event.  This should fail.
        boolean threwException = false;
        try {
            instance.addEvent(e1_ungrp_class1);
        } catch (SQLException ex) {
            threwException = true;
        }
        assertTrue(threwException);
    }

    /**
     * Test of getEventList method, of class EventService.
     */
    @Test
    public void test2GetEventList() throws Exception {
        System.out.println("getEvent");

        EventService instance = new EventService();
        // Get all of the events under the test system
        EventFilter filter = new EventFilter(null, null, null, "test", null, null, null, null, null);
        List<Event> result = instance.getEventList(filter);
        assertEquals(eventList.size(), result.size());
        SortedSet<Long> resultIds = new TreeSet<>();
        SortedSet<Long> expectedIds = new TreeSet<>();
        for (Event e : result) {
            resultIds.add(e.getEventId());
        }
        for (Event e : eventList) {
            expectedIds.add(e.getEventId());
        }
        assertEquals(expectedIds, resultIds);

        // Get all of the grouped_consistent events
        List<String> locations = new ArrayList<>();
        locations.add("grouped-consistent");
        List<Event> expResultLocations = new ArrayList<>();
        expResultLocations.add(e1_grp_con_no_class);
        expResultLocations.add(e2_grp_con_no_class);
        expResultLocations.add(e3_grp_con_noclass_label);
        expResultLocations.add(e4_grp_con_noclass_label);
        expResultLocations.add(e1_grp_con_class1);
        expResultLocations.add(e2_grp_con_class1);

        EventFilter filterLocations = new EventFilter(null, null, null, null, locations, null, null, null, null);
        List<Event> resultLocations = instance.getEventList(filterLocations);
        SortedSet<Long> resultLocationsIds = new TreeSet<>();
        SortedSet<Long> expectedLocationsIds = new TreeSet<>();
        for (Event e : resultLocations) {
            resultLocationsIds.add(e.getEventId());
        }
        for (Event e : expResultLocations) {
            expectedLocationsIds.add(e.getEventId());
        }
        assertEquals(expectedLocationsIds, resultLocationsIds);

        // Get the e2_grp_incon_class1 via several filters
        List<String> locations2 = new ArrayList<>();
        locations2.add("grouped-inconsistent");
        EventFilter filterMulti = new EventFilter(null, t1, t2, "test", locations2, null, false, false, null);
        List<Event> resultsMulti = instance.getEventList(filterMulti);
        SortedSet<Long> resultsMultiIds = new TreeSet<>();
        SortedSet<Long> expMultiIds = new TreeSet<>();

        for (Event e : resultsMulti) {
            resultsMultiIds.add(e.getEventId());
        }
        expMultiIds.add(e1_grp_incon_noclass.getEventId());
        expMultiIds.add(e2_grp_incon_noclass.getEventId());
        expMultiIds.add(e1_grp_incon_class1.getEventId());
        expMultiIds.add(e2_grp_incon_class1.getEventId());
        assertEquals(expMultiIds, resultsMultiIds);

        // Check that the we get waveform data back and that it matches
        EventFilter idFilter = new EventFilter(Arrays.asList(e1_grp_con_no_class.getEventId()), null, null, null, null, null, null, null, null);
        List<Event> resultsId = instance.getEventList(idFilter);
        List<Long> expResultsIdList = Arrays.asList(e1_grp_con_no_class.getEventId());
        List<Long> resultsIdList = new ArrayList<>();

        double[][] resultsData = null;
        for (Event e : resultsId) {
            if (resultsData == null) {
                resultsData = e.getWaveformDataAsArray(null);
            }
            resultsIdList.add(e.getEventId());
        }
        assertEquals(expResultsIdList, resultsIdList);
        Assert.assertArrayEquals(e1_grp_con_no_class.getWaveformDataAsArray(null), resultsData);

    }

    /**
     * Test of the use of EventFilters that use LabelFilters.
     */
    @Test
    public void test2aGetEventListWithLabelFilters() throws Exception {
        System.out.println("test2aGetEventListWithLabelFilters");

        EventService es = new EventService();

        // This should filter out all of the events without a label and return just the two that have labels
        EventFilter ef1 = new EventFilter(null, null, null, null, null, null, null, null, null);
        LabelFilter lf = new LabelFilter(true);

        List<Event> res1 = lf.filterEvents(es.getEventList(ef1));
        List<Event> exp1 = new ArrayList<>();
        exp1.add(e3_grp_con_noclass_label);
        exp1.add(e4_grp_con_noclass_label);
        assertEquals(exp1.size(), res1.size());
        for (int i = 0; i < exp1.size(); i++) {
            assertEquals(exp1.get(i), res1.get(i));
        }
    }

    /**
     * Test of the use of EventFilters that use LabelFilters.
     */
    @Test
    public void test2bGetEventListWithLabelFilters() throws Exception {
        System.out.println("test2bGetEventListWithLabelFilters");

        EventService es = new EventService();

        // This should return one event with high confidence cavity label and a low confidence fault-type label. E.g.,
        // {"id":null,"label-time_utc":null,"name":"cavity","value":"7","confidence":0.99,"model-name":"myModel"}
        // {"id":null,"label-time_utc":null,"name":"fault-type","value":"fault_2","confidence":0.5,"model-name":"myModel"}

        Map<String, List<String>> nameValueMap = new HashMap<>();
        nameValueMap.put("cavity", null);
        nameValueMap.put("fault-type", null);
        EventFilter ef = new EventFilter(null, null, null, null, null, null , null, null, null);

        LabelFilter lf = new LabelFilter(Collections.singletonList("myModel"), null, nameValueMap, 0.499, ">");
        List<Event> res = lf.filterEvents(es.getEventList(ef));
        List<Event> exp = Collections.singletonList(e3_grp_con_noclass_label);

        assertEquals(exp.size(), res.size());
        for (int i = 0; i < exp.size(); i++) {
            assertEquals(exp.get(i), res.get(i));
        }
    }

    /**
     * Test of the use of EventFilters that use LabelFilters.
     */
    @Test
    public void test2cGetEventListWithLabelFilters() throws Exception {
        System.out.println("test2bGetEventListWithLabelFilters");

        EventService es = new EventService();

        // This label filter should return all labeled events, and the other event filters should select the earlier one.
        // TODO: The filters are returning the appropriate event list, but without all of the labels.  Figure it out.
        EventFilter ef = new EventFilter(null, t3.plusMillis(-100), t3.plusMillis(100), null, null, null, null, null, null);
        LabelFilter lf = new LabelFilter(true);

        List<Event> res = lf.filterEvents(es.getEventList(ef));
        List<Event> exp = Collections.singletonList(e3_grp_con_noclass_label);

        assertEquals(exp.size(), res.size());
        for (int i = 0; i < exp.size(); i++) {
            assertEquals(exp.get(i), res.get(i));
        }
    }

    /**
     * Test of setEventArchiveFlag method, of class EventService.
     */
    @Test
    public void test3SetEventArchiveFlag() throws Exception {
        System.out.println("setEventArchiveFlag");
        EventService instance = new EventService();

        // Should return 1 for number of updates
        long id = e1_grp_con_no_class.getEventId();
        int expResult = 1;
        int result = instance.setEventArchiveFlag(id, true);
        assertEquals(expResult, result);

        // Verify the flag has been set
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        EventFilter filter = new EventFilter(ids, null, null, null, null, null, null, null, null);
        List<Event> eList = instance.getEventList(filter);
        assertTrue(eList.get(0).isArchive());
    }

    /**
     * Test of deleteEvent method, of class EventService.
     */
    @Test
    public void test4SetEventDeleteFlag() throws Exception {
        System.out.println("deleteEvent");
        EventService instance = new EventService();
        long id = e1_grp_con_no_class.getEventId();
        int expResult = 1;
        int result = instance.setEventDeleteFlag(id, true);
        assertEquals(expResult, result);

        List<Long> ids = new ArrayList<>();
        ids.add(id);
        EventFilter filter = new EventFilter(ids, null, null, null, null, null, null, null, null);
        List<Event> eList = instance.getEventList(filter);
        assertTrue(eList.get(0).isDelete());
    }

    @Test
    public void test5GetLabelTallyAsJson() throws Exception {
        System.out.println("Testing Tallying Label Method");

        EventService es = new EventService();
        JsonArray exp = Json.createReader(new StringReader("[" +
                "{\"location\":\"grouped-consistent\",\"label-combo\":\"NULL\",\"count\":4}," +
                "{\"location\":\"grouped-consistent\",\"label-combo\":\"fault_1,7\",\"count\":1}," +
                "{\"location\":\"grouped-consistent\",\"label-combo\":\"fault_2,7\",\"count\":1}," +
                "{\"location\":\"grouped-inconsistent\",\"label-combo\":\"NULL\",\"count\":4}," +
                "{\"location\":\"ungrouped\",\"label-combo\":\"NULL\",\"count\":4}" +
                "]")).readArray();

        JsonArray result = es.getLabelTallyAsJson(null, null, true);
        assertEquals(exp.toString(), result.toString());
    }


    @Test
    public void test6DeleteEvents() throws Exception {
        System.out.println("Deleting Test Events");
        EventService instance = new EventService();
        EventFilter filter = new EventFilter(null, null, null, null, null, null, null, null, null);
        List<Event> allEvents = instance.getEventList(filter);
        assertEquals(eventList.size(), allEvents.size());

        for (Event e : allEvents) {
            instance.deleteEvent(e.getEventId(), true);
        }
    }
}
