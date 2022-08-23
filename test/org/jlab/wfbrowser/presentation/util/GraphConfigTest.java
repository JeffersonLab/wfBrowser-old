package org.jlab.wfbrowser.presentation.util;

import org.jlab.wfbrowser.model.Series;
import org.junit.Test;
import static org.junit.Assert.*;


import java.util.HashSet;
import java.util.Set;

public class GraphConfigTest {
    @Test
    public void testOverwriteWith() throws Exception {
        Set<String> loc1 = new HashSet<>();
        loc1.add("a");
        loc1.add("b");
        Set<Series> s1 = new HashSet<>();
        s1.add(new Series("ser1", 1, "", "system1", "", "NA", null, null));
        GraphConfig gc1 = new GraphConfig("system1", loc1, null, 3, null,
                (String) null, null, null, null, s1, null,
                null, null);

        Set<String> c2 = new HashSet<>();
        Set<Series> s2 = new HashSet<>();
        s2.add(new Series("ser2", 2, "", "system1", "asdfasdfasdfasdfasdf", "NA", null, null));
        c2.add("cc1");
        c2.add("cc2");
        GraphConfig gc2 = new GraphConfig("system1", null, c2, 5, null,
                (String) null, null, null, null, s2, null,
                null, null);

        gc1.overwriteWith(gc2);

        // Check that series, classificaitons, and minCaptureFiles are overwritten, while locations are not.
        assertEquals(gc1.getSeries(), s2);
        assertEquals((int) gc1.getMinCaptureFiles(), 5);
        assertEquals(gc1.getLocations(), loc1);
    }

    @Test(expected = RuntimeException.class)
    public void testOverwriteWith_DifferentSystems() throws Exception {
        // Should throw since they have different systems
        GraphConfig gc1 = new GraphConfig("system1", null, null, 3, null,(String) null, null, null, null, null, null, null, null);
        GraphConfig gc2 = new GraphConfig("system2", null, null, 3, null,(String) null, null, null, null, null, null, null, null);
        gc1.overwriteWith(gc2);
    }

}
