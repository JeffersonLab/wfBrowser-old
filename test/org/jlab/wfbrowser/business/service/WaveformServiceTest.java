/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.business.service;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.naming.NamingException;
import org.jlab.wfbrowser.business.filter.WaveformFilter;
import org.jlab.wfbrowser.connectionpools.StandaloneConnectionPools;
import org.jlab.wfbrowser.connectionpools.StandaloneJndi;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Waveform;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

/**
 *
 * @author adamc
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class WaveformServiceTest {

    private static StandaloneConnectionPools pools;
    private static long eventId;
    private static Instant now, end;
    private static Event e;
    private static List<Event> eventList;

    public WaveformServiceTest() {
    }

    @BeforeClass
    public static void oneTimeSetUp() throws NamingException, SQLException {
        new StandaloneJndi();
        pools = new StandaloneConnectionPools();

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

        now = Instant.now();
        end = now.plusMillis(5000);

        e = new Event(now, "Bldg87", "rf", false, waveforms);
        eventList = new ArrayList<>();
        eventList.add(new Event(now.plusMillis(1000), "Bldg89", "rf", false, waveforms));
        eventList.add(new Event(now.plusMillis(2000), "Bldg89", "rf", false, waveforms));
        eventList.add(new Event(now.plusMillis(3000), "Bldg89", "rf", false, waveforms));

    }

    @AfterClass
    public static void oneTimeTearDown() throws IOException {
        if (pools.isOpen()) {
            pools.close();
        }
    }

    /**
     * Test of addEvent method, of class WaveformService.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void test1AddEvent() throws Exception {
        System.out.println("addEvent");
        WaveformService instance = new WaveformService();

        // Set the test class parameter for use in other tests
        eventId = instance.addEvent(e);
        e.setEventId(eventId);

        Event expResult = e;
        Event result = instance.getEvent(eventId);

        assertEquals(result, expResult);
        System.out.println("Added event:\n" + result);
    }

    /**
     * Test of getEvent method, of class WaveformService.
     */
    @Test
    public void test2GetEvent() throws Exception {
        System.out.println("getEvent");

        WaveformService instance = new WaveformService();
        Event result = instance.getEvent(eventId);
        Event expResult = e;
        assertEquals(expResult, result);
        System.out.println("Retrieved event:\n" + result);
    }

    /**
     * Test of deleteEvent method, of class WaveformService.
     */
    @Test
    public void test4DeleteEvent() throws Exception {
        System.out.println("deleteEvent");
        WaveformService instance = new WaveformService();
        int expResult = 1;
        int result = instance.deleteEvent(eventId);
        assertEquals(expResult, result);
        System.out.println("Deleted " + result + " event with ID matching\n" + e);

    }

    /**
     * Test of setEventArchiveFlag method, of class WaveformService.
     */
    @Test
    public void test3SetEventArchiveFlag() throws Exception {
        System.out.println("setEventArchiveFlag");
        WaveformService instance = new WaveformService();
        int expResult = 1;
        int result = instance.setEventArchiveFlag(eventId, true);
        assertEquals(expResult, result);
        result = instance.setEventArchiveFlag(eventId, false);
        assertEquals(expResult, result);

    }

    /**
     * Test of the addEventList, getEventList, and deleteEventList method of
     * class WaveformService.
     */
    @Test
    public void test5AddGetDeleteEventList() throws Exception {
        System.out.println("getEventList");
        WaveformService instance = new WaveformService();
        WaveformFilter filter = new WaveformFilter(now, end, "rf", "Bldg89", null);

        System.out.println("  addEventList");
        instance.addEventList(eventList);

        List<Event> result = instance.getEventListWithoutData(filter);
        // Since these numbers will be different with every test its difficult to tell if we're getting the correct value.
        // Set them to null so that the match the expected results / the list we added
        List<Long> eventIds = new ArrayList<>();
        for (Event event : result) {
            eventIds.add(event.getEventId());
            event.setEventId(null);
        }
        List<Event> expResult = eventList;

        System.out.println("  getEventList");
        assertEquals(expResult, result);

        System.out.println("  deleteEventList");
        instance.deleteEventList(eventIds);

        result = instance.getEventListWithoutData(filter);
        assert (result.isEmpty());
    }

}
