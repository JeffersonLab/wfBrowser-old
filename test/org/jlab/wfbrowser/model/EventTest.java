/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import javax.naming.NamingException;
import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.connectionpools.StandaloneConnectionPools;
import org.jlab.wfbrowser.connectionpools.StandaloneJndi;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 *
 * @author adamc
 */
public class EventTest {

    private static StandaloneConnectionPools pools;

    // Consistent, no class, grouped, with metadata
    private static Event e1_grp_con_noclass_meta = null;

    // Consistent, no class, grouped
    private static Event e1_grp_con_noclass = null;
    private static Event e1a_grp_con_noclass = null;
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

    public EventTest() {
    }

    @BeforeClass
    public static void oneTimeSetUp() throws IOException, FileNotFoundException, SQLException, NamingException {
        System.out.println("Start of setup");

        // Setup the data connection and connection pools
        new StandaloneJndi();
        pools = new StandaloneConnectionPools();

        // Create some events to add to the database - files that match these must exist on the filesystem
        Instant t1 = LocalDateTime.of(2017, 9, 14, 10, 0, 0).atZone(ZoneId.systemDefault()).toInstant().plusMillis(100);  // For the unzipped files
        Instant t2 = LocalDateTime.of(2017, 9, 14, 11, 0, 0).atZone(ZoneId.systemDefault()).toInstant().plusMillis(100);  // For the zipped files

        // Setup the flags for the basic testing.  Make variable names match what they do.
        boolean unarchive = false;
        boolean archive = true;
        boolean delete = true;
        boolean noDelete = false;
        boolean grouped = true;
        boolean ungrouped = false;
        String grp_con = "grouped-consistent";
        String grp_con_meta = "grouped-consistent-metadata";
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
        e1a_grp_con_noclass = new Event(t1, grp_con, "test", unarchive, noDelete, grouped, noClass, nullCF); // Should match e1 since it is an exact copy
        e2_grp_con_noclass = new Event(t2, grp_con, "test", unarchive, noDelete, grouped, noClass, nullCF);  // Should not match e1 since different time        
        e1_grp_con_noclass.setEventId(1L);
        e1a_grp_con_noclass.setEventId(1L); // Duplicate of e1
        e2_grp_con_noclass.setEventId(2L);
        e1_grp_con_class1 = new Event(t1, grp_con, "test", unarchive, noDelete, grouped, class1, nullCF);
        e2_grp_con_class1 = new Event(t2, grp_con, "test", unarchive, noDelete, grouped, class1, nullCF);

        // Setup the grouped, consistent event with metadata
        e1_grp_con_noclass_meta = new Event(t1, grp_con_meta, "test", unarchive, noDelete, grouped, noClass, nullCF);
        e1_grp_con_noclass_meta.setEventId(3L);

        // Setup the grouped and incosistent events
        e1_grp_incon_noclass = new Event(t1, grp_incon, "test", unarchive, noDelete, grouped, noClass, nullCF);
        e2_grp_incon_noclass = new Event(t2, grp_incon, "test", unarchive, noDelete, grouped, noClass, nullCF);
        e1_grp_incon_class1 = new Event(t1, grp_incon, "test", unarchive, noDelete, grouped, class1, nullCF);
        e2_grp_incon_class1 = new Event(t2, grp_incon, "test", unarchive, noDelete, grouped, class1, nullCF);

        // Setup the ungrouped events (consistent by default)
        e1_ungrp_noclass = new Event(t1, ungrp, "test", unarchive, noDelete, ungrouped, noClass, unzipCF);
        e2_ungrp_noclass = new Event(t2, ungrp, "test", unarchive, noDelete, ungrouped, noClass, zipCF);
        e1_ungrp_class1 = new Event(t1, ungrp, "test", unarchive, noDelete, ungrouped, class1, unzipCF);
        e2_ungrp_class1 = new Event(t2, ungrp, "test", unarchive, noDelete, ungrouped, class1, zipCF);
    }

    @AfterClass
    public static void oneTimeTearDown() throws IOException, SQLException {

        System.out.println("==== Doing one Time Tear Down");

        // Close down the database connections
        if (pools.isOpen()) {
            pools.close();
        }
    }

    @Test
    public void testAddRemoveGoodEvents() throws SQLException, IOException {
        System.out.println("Test Adding Good Events");

        // Don't add e1a since it is a duplicate and should fail 
        EventService es = new EventService();
        es.addEvent(e1_grp_con_noclass);
        es.addEvent(e2_grp_con_noclass);

        // Delete every event in the test database.  It should just be the two added above
        EventFilter filter = new EventFilter(null, null, null, null, null, null, null);
        List<Event> all = es.getEventList(filter);
        for (Event e : all) {
            es.deleteEvent(e.getEventId(), true);
        }

        // Will pass as long as there is no exception thrown and only two events are found in the database
        assertEquals(all.size(), 2);
    }

//    @Test
//    public void testGetRelativeFilePath() {
//        String expResult = "test\\loc1\\2018_01_01\\050000.5";
//        String result = e4.getRelativeFilePath().toString();
//        System.out.println(result);
//        assertEquals(expResult, result);
//    }
    /**
     * Test of toJsonObject method, of class Event.
     */
    @Test
    public void testToJsonObject() {
        System.out.println("toJsonObject");
        String expResult = "{\"id\":2,"
                + "\"datetime_utc\":\"2017-09-14 15:00:00.1\","
                + "\"location\":\"grouped-consistent\","
                + "\"system\":\"test\","
                + "\"archive\":false,"
                + "\"captureFiles\":["
                + "{\"filename\":\"test.2017_09_14_110000.1.txt\",\"sample_start\":1.1,\"sample_end\":3.1,\"sample_step\":1.0,\"metadata\":[],\"waveforms\":[{\"waveformName\":\"test1\",\"series\":[],\"timeOffsets\":[1.1,2.1,3.1],\"values\":[1.5,2.5,0.5]}]},"
                + "{\"filename\":\"test2.2017_09_14_110000.3.txt\",\"sample_start\":1.1,\"sample_end\":3.1,\"sample_step\":1.0,\"metadata\":[],\"waveforms\":[{\"waveformName\":\"test2\",\"series\":[],\"timeOffsets\":[1.1,2.1,3.1],\"values\":[1.15,32.5,10.5]}]}"
                + "]"
                + "}";
        String result = e2_grp_con_noclass.toJsonObject().toString();
        assertEquals(expResult, result);

        String expResult1 = "{\"id\":3,"
                + "\"datetime_utc\":\"2017-09-14 14:00:00.1\","
                + "\"location\":\"grouped-consistent-metadata\","
                + "\"system\":\"test\","
                + "\"archive\":false,"
                + "\"captureFiles\":["
                + "{\"filename\":\"test.2017_09_14_100000.1.txt\",\"sample_start\":1.1,\"sample_end\":3.1,\"sample_step\":1.0,"
                + "\"metadata\":[{\"name\":\"PV1\",\"type\":\"NUMBER\",\"value\":\"5.6\",\"offset\":-0.5,\"start\":-45.9},{\"name\":\"PV2\",\"type\":\"STRING\",\"value\":\"ABC\",\"offset\":0.0,\"start\":-0.4},{\"name\":\"PV3\",\"type\":\"UNAVAILABLE\",\"value\":null,\"offset\":0.0,\"start\":null},{\"name\":\"PV4\",\"type\":\"UNARCHIVED\",\"value\":null,\"offset\":null,\"start\":null}],"
                + "\"waveforms\":[{\"waveformName\":\"test1\",\"series\":[],\"timeOffsets\":[1.1,2.1,3.1],\"values\":[1.5,2.5,0.5]}]},"
                + "{\"filename\":\"test2.2017_09_14_100000.3.txt\",\"sample_start\":1.1,\"sample_end\":3.1,\"sample_step\":1.0,"
                + "\"metadata\":[{\"name\":\"PV1:hb\",\"type\":\"NUMBER\",\"value\":\"0.056\",\"offset\":-0.5,\"start\":-45.9},{\"name\":\"PV2.VAL\",\"type\":\"STRING\",\"value\":\"ABC\",\"offset\":0.0,\"start\":-0.4},{\"name\":\"PV3_1\",\"type\":\"UNAVAILABLE\",\"value\":null,\"offset\":0.0,\"start\":null},{\"name\":\"PV4-1\",\"type\":\"UNARCHIVED\",\"value\":null,\"offset\":null,\"start\":null}]"
                + ",\"waveforms\":[{\"waveformName\":\"test2\",\"series\":[],\"timeOffsets\":[1.1,2.1,3.1],\"values\":[1.15,32.5,10.5]}]}"
                + "]"
                + "}";
        String result1 = e1_grp_con_noclass_meta.toJsonObject().toString();
        assertEquals(expResult1, result1);

    }

    @Test
    public void testToDyGraphJsonObject() {
        System.out.println("toDyGraphJsonObject");
        String expResult = "{\"id\":2,"
                + "\"datetime_utc\":\"2017-09-14 15:00:00.1\","
                + "\"location\":\"grouped-consistent\","
                + "\"system\":\"test\","
                + "\"archive\":false,"
                + "\"timeOffsets\":[1.1,2.1,3.1],"
                + "\"waveforms\":["
                + "{\"waveformName\":\"test1\","
                + "\"dygraphLabel\":\"test\","
                + "\"dygraphId\":\"t\","
                + "\"series\":[],"
                + "\"dataPoints\":[1.5,2.5,0.5]"
                + "},"
                + "{\"waveformName\":\"test2\","
                + "\"dygraphLabel\":\"test\","
                + "\"dygraphId\":\"t\","
                + "\"series\":[],"
                + "\"dataPoints\":[1.15,32.5,10.5]"
                + "}"
                + "]"
                + "}";
        String result = e2_grp_con_noclass.toDyGraphJsonObject(null).toString();
        assertEquals(expResult, result);
    }

    @Test
    public void testGetWaveformDataAsArray() {
        System.out.println("getWaveformDataAsArray");

        // Consistent, no class, grouped
        double[][] expResult1 = new double[][]{
            {1.1, 1.5, 1.15},
            {2.1, 2.5, 32.5},
            {3.1, 0.5, 10.5}
        };
        double[][] result1 = e1_grp_con_noclass.getWaveformDataAsArray(null);
        double[][] result1Zip = e2_grp_con_noclass.getWaveformDataAsArray(null);
        double[][] result1Class1 = e1_grp_con_class1.getWaveformDataAsArray(null);
        double[][] result1ZipClass1 = e2_grp_con_class1.getWaveformDataAsArray(null);
        assertArrayEquals(expResult1, result1);
        assertArrayEquals(expResult1, result1Zip);
        assertArrayEquals(expResult1, result1Class1);
        assertArrayEquals(expResult1, result1ZipClass1);

        // Inconsistent, no class, grouped
        double[][] expResult2 = new double[][]{
            {1.1, 1.5, Double.NaN, Double.NaN, Double.NaN},
            {2.1, 2.5, Double.NaN, Double.NaN, Double.NaN},
            {3.1, 0.5, Double.NaN, Double.NaN, Double.NaN},
            {100.1, Double.NaN, 1.15, 31.15, 17},
            {200.5, Double.NaN, 32.5, 332.5, 1},
            {300.3, Double.NaN, 10.5, 310.5, -11.103}
        };
        double[][] result2 = e1_grp_incon_noclass.getWaveformDataAsArray(null);
        double[][] result2Zip = e2_grp_incon_noclass.getWaveformDataAsArray(null);
        double[][] result2Class1 = e1_grp_incon_class1.getWaveformDataAsArray(null);
        double[][] result2ZipClass1 = e2_grp_incon_class1.getWaveformDataAsArray(null);
        assertArrayEquals(expResult2, result2);
        assertArrayEquals(expResult2, result2Zip);
        assertArrayEquals(expResult2, result2Class1);
        assertArrayEquals(expResult2, result2ZipClass1);

        // Consistent, no class, ungrouped
        double[][] expResult3 = new double[][]{
            {100.1, 31.15, 17},
            {200.5, 332.5, 1},
            {300.3, 310.5, -11.103}
        };
        double[][] result3 = e1_ungrp_noclass.getWaveformDataAsArray(null);
        double[][] result3Zip = e2_ungrp_noclass.getWaveformDataAsArray(null);
        double[][] result3Class1 = e1_ungrp_class1.getWaveformDataAsArray(null);
        double[][] result3ZipClass1 = e2_ungrp_class1.getWaveformDataAsArray(null);
        assertArrayEquals(expResult3, result3);
        assertArrayEquals(expResult3, result3Zip);
        assertArrayEquals(expResult3, result3Class1);
        assertArrayEquals(expResult3, result3ZipClass1);
    }

    @Test
    public void testToCsv() {
        System.out.println("toCsv");
        String expResult = "time_offset,test1,test2\n"
                + "1.1,1.5,1.15\n"
                + "2.1,2.5,32.5\n"
                + "3.1,0.5,10.5\n";
        String result = e2_grp_con_noclass.toCsv(null);
        assertEquals(expResult, result);
    }

    /**
     * Test of getEventTimeString method, of class Event.
     */
    @Test
    public void testGetEventTimeString() {
        System.out.println("getEventTimeString");
        String expResult = "2017-09-14 15:00:00.1";  // UTC zone, so +4 or + 5 over the time we specified above
        String result = e2_grp_con_noclass.getEventTimeString();
        assertEquals(expResult, result);
    }

    /**
     * Test of equals method, of class Event.
     */
    @Test
    public void testEquals() {
        System.out.println("equals");
        assertEquals(e1_grp_con_noclass, e1a_grp_con_noclass);
        assertNotEquals(e1_grp_con_noclass, e2_grp_con_noclass);
    }
}
