/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author adamc
 */
public class EventTest {
    
    private final Event e1, e1a, e2, e3, e4;
    public EventTest() {
                // Construct an example event for use in tests
        List<Double> time1 = Arrays.asList(1.1, 2.1, 3.1);
        List<Double> vals1 = Arrays.asList(1.5, 2.5, 0.5);
        List<Double> time2 = Arrays.asList(100.1, 200.5, 300.3);
        List<Double> vals2 = Arrays.asList(1.15, 32.5, 10.5);
        Waveform w1 = new Waveform("test1", time1, vals1);
        Waveform w2 = new Waveform("test2", time2, vals2);
        List<Waveform> waveforms = new ArrayList<>();
        waveforms.add(w1);
        waveforms.add(w2);

        Instant t1 = Instant.now();
        Instant t2 = t1.plusMillis(1000);
        Instant t3 = LocalDateTime.of(2018, 01, 01, 5, 0, 0).toInstant(ZoneOffset.of("+05")).plusMillis(500);
        System.out.println(t3.toString());
        e1 = new Event(1, t1, "loc1", "test", false, null);
        e1a = new Event(1, t1, "loc1", "test", false, null); // Should match e1 since it is an exact copy
        e2 = new Event(1, t2, "loc1", "test", false, waveforms);  // Should not match e1 since different time
        e3 = new Event(1, t1, "loc1", "test", false, waveforms);  // Should not match e1 since this has a waveform list
        e4 = new Event(2, t3, "loc1", "test", false, waveforms);  // Used in the toDateTimeString and toJsonObject test
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testGetRelativeFilePath() {
        String expResult = "test\\loc1\\2018_01_01\\050000.5";
        String result = e4.getRelativeFilePath().toString();
        System.out.println(result);
        assertEquals(expResult, result);
    }

    /**
     * Test of toJsonObject method, of class Event.
     */
    @Test
    public void testToJsonObject() {
        System.out.println("toJsonObject");
        String expResult = "{\"id\":2,"
                + "\"datetime\":\"2018-01-01 00:00:00.500000\","
                + "\"location\":\"loc1\","
                + "\"system\":\"test\","
                + "\"archive\":false,"
                + "\"waveforms\":["
                +   "{"
                +     "\"series\":\"test1\","
                +     "\"timeOffsets\":[1.1,2.1,3.1],"
                +     "\"values\":[1.5,2.5,0.5]"
                +   "},"
                +   "{"
                +     "\"series\":\"test2\","
                +     "\"timeOffsets\":[100.1,200.5,300.3],"
                +     "\"values\":[1.15,32.5,10.5]"
                +    "}"
                + "]"
                + "}";
        String result = e4.toJsonObject().toString();
        assertEquals(expResult, result);
    }

    /**
     * Test of getEventTimeString method, of class Event.
     */
    @Test
    public void testGetEventTimeString() {
        System.out.println("getEventTimeString");
        String expResult = "2018-01-01 00:00:00.500000";
        String result = e4.getEventTimeString();
        assertEquals(expResult, result);
    }

    /**
     * Test of equals method, of class Event.
     */
    @Test
    public void testEquals() {
        System.out.println("equals");
        assertEquals(e1, e1a);
        assertNotEquals(e1, e3);
        assertNotEquals(e1, e2);
    }
}
