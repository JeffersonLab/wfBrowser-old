/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model.CaptureFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author adamc
 */
public class MetadataTest {

    public MetadataTest() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of getMetadataFromCaptureFileLine method, of class Metadata.
     */
    @Test
    public void testGetMetadataFromCaptureFileLine() {
        System.out.println("getMetadataFromCaptureFileLine");
        String numberLine1 = "# R123GMES=-1.27 @ -3.2(-40.1)";
        String numberLine2 = "# R123GMES=-1.2e7 @ -3.2(-40.1)";
        String stringLine = "# R123GMES='enabled' @ 3(1)";
        String unavailableLine = "# R123GMES=unavailable @ -3";
        String unarchivedLine = "# R123GMES=not archived";

        // equals() checks for equality of name and trigger offset - must check value, and start, and type manually
        Metadata expNumberResult = new Metadata(MetadataType.NUMBER, "R123GMES", -1.27, -3.2, -40.1);
        Metadata numberResult = Metadata.getMetadataFromCaptureFileLine(numberLine1);
        assertEquals(expNumberResult, numberResult);
        assertEquals(expNumberResult.getType(), numberResult.getType());
        assertEquals(expNumberResult.getStart(), numberResult.getStart());
        assertEquals((Double) expNumberResult.getValue(), (Double) numberResult.getValue());

        // equals() checks for equality of name and trigger offset - must check value, and start, and type manually
        Metadata expNumberResult2 = new Metadata(MetadataType.NUMBER, "R123GMES", -1.2e7, -3.2, -40.1);
        Metadata numberResult2 = Metadata.getMetadataFromCaptureFileLine(numberLine2);
        assertEquals(expNumberResult2, numberResult2);
        assertEquals(expNumberResult2.getType(), numberResult2.getType());
        assertEquals(expNumberResult2.getStart(), numberResult2.getStart());
        assertEquals((Double) expNumberResult2.getValue(), (Double) numberResult2.getValue());

        // equals() checks for equality of name and trigger offset - must check value, and start, and type manually
        Metadata expStringResult = new Metadata(MetadataType.STRING, "R123GMES", "enabled", 3d, 1d);
        Metadata stringResult = Metadata.getMetadataFromCaptureFileLine(stringLine);
        assertEquals(expStringResult, stringResult);
        assertEquals(expStringResult.getType(), stringResult.getType());
        assertEquals(expStringResult.getStart(), stringResult.getStart());
        assertEquals((String) expStringResult.getValue(), (String) stringResult.getValue());

        // equals() checks for equality of name and trigger offset - must check value, and start, and type manually
        Metadata expUnavailableResult = new Metadata(MetadataType.UNAVAILABLE, "R123GMES", null, -3d, null);
        Metadata unavailableResult = Metadata.getMetadataFromCaptureFileLine(unavailableLine);
        assertEquals(expUnavailableResult, unavailableResult);
        assertEquals(expUnavailableResult.getType(), unavailableResult.getType());
        assertEquals(expUnavailableResult.getStart(), unavailableResult.getStart());
        assertEquals(expUnavailableResult.getValue(), unavailableResult.getValue());

        // equals() checks for equality of name and trigger offset - must check value, and start, and type manually
        Metadata expUnarchivedResult = new Metadata(MetadataType.UNARCHIVED, "R123GMES", null, null, null);
        Metadata unarchivedResult = Metadata.getMetadataFromCaptureFileLine(unarchivedLine);
        assertEquals(expUnarchivedResult, unarchivedResult);
        assertEquals(expUnarchivedResult.getType(), unarchivedResult.getType());
        assertEquals(expUnarchivedResult.getStart(), unarchivedResult.getStart());
        assertEquals(expUnarchivedResult.getValue(), unarchivedResult.getValue());
    }
}
