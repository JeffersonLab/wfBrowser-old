package org.jlab.wfbrowser.business.service;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.naming.NamingException;
import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.connectionpools.StandaloneConnectionPools;
import org.jlab.wfbrowser.connectionpools.StandaloneJndi;
import org.jlab.wfbrowser.model.Event;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import java.util.TreeSet;
import java.util.SortedSet;
import org.junit.Assert;

/**
 *
 * @author adamc
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EventServiceTest {

    // The two timstamps used for events.  t1 for unzipped, t2 for zipped.
    private static Instant t1 = null;
    private static Instant t2 = null;

    // Consistent, no class, grouped
    private static Event e1_grp_con_noclass = null;
    private static Event e2_grp_con_noclass = null;
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

    private static StandaloneConnectionPools pools;
    private static List<Event> eventList = new ArrayList<>();

    public EventServiceTest() {
    }

    @BeforeClass
    public static void oneTimeSetUp() throws NamingException, SQLException, IOException {
        System.out.println("Start of setup");

        // Setup the data connection and connection pools
        new StandaloneJndi();
        pools = new StandaloneConnectionPools();

        // Create some events to add to the database - files that match these must exist on the filesystem
        t1 = LocalDateTime.of(2017, 9, 14, 10, 0, 0).atZone(ZoneId.systemDefault()).toInstant().plusMillis(100);  // For the unzipped files
        t2 = LocalDateTime.of(2017, 9, 14, 11, 0, 0).atZone(ZoneId.systemDefault()).toInstant().plusMillis(100);  // For the zipped files

        // Setup the flags for the basic testing.  Make variable names match what they do.
        boolean unarchive = false;
        boolean archive = true;
        boolean delete = true;
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
        e1_grp_con_noclass = new Event(t1, grp_con, "test", unarchive, noDelete, grouped, noClass, nullCF);
        e2_grp_con_noclass = new Event(t2, grp_con, "test", unarchive, noDelete, grouped, noClass, nullCF);  // Should not match e1 since different time        
        e1_grp_con_class1 = new Event(t1, grp_con, "test", unarchive, noDelete, grouped, class1, nullCF);
        e2_grp_con_class1 = new Event(t2, grp_con, "test", unarchive, noDelete, grouped, class1, nullCF);
        eventList.add(e1_grp_con_noclass);
        eventList.add(e2_grp_con_noclass);
        eventList.add(e1_grp_con_class1);
        eventList.add(e2_grp_con_class1);

        // Setup the grouped and incosistent events
        e1_grp_incon_noclass = new Event(t1, grp_incon, "test", unarchive, noDelete, grouped, noClass, nullCF);
        e2_grp_incon_noclass = new Event(t2, grp_incon, "test", unarchive, noDelete, grouped, noClass, nullCF);
        e1_grp_incon_class1 = new Event(t1, grp_incon, "test", unarchive, noDelete, grouped, class1, nullCF);
        e2_grp_incon_class1 = new Event(t2, grp_incon, "test", unarchive, noDelete, grouped, class1, nullCF);
        eventList.add(e1_grp_incon_noclass);
        eventList.add(e2_grp_incon_noclass);
        eventList.add(e1_grp_incon_class1);
        eventList.add(e2_grp_incon_class1);

        // Setup the ungrouped events (consistent by default)
        e1_ungrp_noclass = new Event(t1, ungrp, "test", unarchive, noDelete, ungrouped, noClass, unzipCF);
        e2_ungrp_noclass = new Event(t2, ungrp, "test", unarchive, noDelete, ungrouped, noClass, zipCF);
        e1_ungrp_class1 = new Event(t1, ungrp, "test", unarchive, noDelete, ungrouped, class1, unzipCF);
        e2_ungrp_class1 = new Event(t2, ungrp, "test", unarchive, noDelete, ungrouped, class1, zipCF);
        eventList.add(e1_ungrp_noclass);
        eventList.add(e2_ungrp_noclass);
        eventList.add(e1_ungrp_class1);
        eventList.add(e2_ungrp_class1);
    }

    @AfterClass
    public static void oneTimeTearDown() throws IOException {
        if (pools.isOpen()) {
            pools.close();
        }
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

        EventFilter filter = new EventFilter(eventIdList, null, null, null, null, null, null, null);

        List<Event> result = instance.getEventList(filter);

        assertEquals(eventList.size(), result.size());

        // Add a duplicate event.  This should fail.
        boolean threwException = false;
        try {
            instance.addEvent(e1_ungrp_class1);
        } catch (SQLException ex) {
            threwException = true;
        }
        assertEquals(true, threwException);
    }

    /**
     * Test of getEventList method, of class EventService.
     */
    @Test
    public void test2GetEventList() throws Exception {
        System.out.println("getEvent");

        EventService instance = new EventService();
        // Get all of the events under the test system
        EventFilter filter = new EventFilter(null, null, null, "test", null, null, null, null);
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
        expResultLocations.add(e1_grp_con_noclass);
        expResultLocations.add(e2_grp_con_noclass);
        expResultLocations.add(e1_grp_con_class1);
        expResultLocations.add(e2_grp_con_class1);

        EventFilter filterLocations = new EventFilter(null, null, null, null, locations, null, null, null);
        List<Event> resultLocations = instance.getEventList(filterLocations);

        SortedSet<Long> resultLocationsIds = new TreeSet<>();
        SortedSet<Long> expectedLocationsIds = new TreeSet<>();
        for (Event e : resultLocations) {
            resultIds.add(e.getEventId());
        }
        for (Event e : expResultLocations) {
            expectedIds.add(e.getEventId());
        }
        assertEquals(expectedLocationsIds, resultLocationsIds);

        // Get the e2_grp_incon_class1 via serveral filters
        List<String> locations2 = new ArrayList<>();
        locations2.add("grouped-inconsistent");
        EventFilter filterMulti = new EventFilter(null, t1, t2, "test", locations2, null, false, false);
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
        EventFilter idFilter = new EventFilter(Arrays.asList(e1_grp_con_noclass.getEventId()), null, null, null, null, null, null, null);
        List<Event> resultsId = instance.getEventList(idFilter);
        List <Long> expResultsIdList = Arrays.asList(e1_grp_con_noclass.getEventId());
        List<Long> resultsIdList = new ArrayList<>();
        
        double[][] resultsData = null;
        for(Event e : resultsId) {
            if (resultsData == null) {
                resultsData = e.getWaveformDataAsArray(null);
            }
            resultsIdList.add(e.getEventId());
        }
       assertEquals(expResultsIdList, resultsIdList);
        Assert.assertArrayEquals(e1_grp_con_noclass.getWaveformDataAsArray(null), resultsData);
        
    }

    /**
     * Test of deleteEvent method, of class EventService.
     */
    @Test
    public void test4SetEventDeleteFlag() throws Exception {
        System.out.println("deleteEvent");
        EventService instance = new EventService();
        long id = e1_grp_con_noclass.getEventId();
        int expResult = 1;
        int result = instance.setEventDeleteFlag(id, true);
        assertEquals(expResult, result);

        List<Long> ids = new ArrayList<>();
        ids.add(id);
        EventFilter filter = new EventFilter(ids, null, null, null, null, null, null, null);
        List<Event> eList = instance.getEventList(filter);
        assertEquals(eList.get(0).isDelete(), true);
    }

    /**
     * Test of setEventArchiveFlag method, of class EventService.
     */
    @Test
    public void test3SetEventArchiveFlag() throws Exception {
        System.out.println("setEventArchiveFlag");
        EventService instance = new EventService();

        // Should return 1 for number of updates
        long id = e1_grp_con_noclass.getEventId();
        int expResult = 1;
        int result = instance.setEventArchiveFlag(id, true);
        assertEquals(expResult, result);

        // Verify the flag has been set
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        EventFilter filter = new EventFilter(ids, null, null, null, null, null, null, null);
        List<Event> eList = instance.getEventList(filter);
        assertEquals(eList.get(0).isArchive(), true);
    }

    @Test
    public void test6DeleteEvents() throws Exception {
        System.out.println("Deleting Test Events");
        EventService instance = new EventService();
        EventFilter filter = new EventFilter(null, null, null, null, null, null, null, null);
        List<Event> allEvents = instance.getEventList(filter);
        assertEquals(eventList.size(), allEvents.size());

        for (Event e : allEvents) {
            instance.deleteEvent(e.getEventId(), true);
        }
    }
}
