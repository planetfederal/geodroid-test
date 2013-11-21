package org.jeo.geopkg;

import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Set;

import org.jeo.android.geopkg.GeoPackage;
import org.jeo.android.geopkg.GeoPkgWorkspace;
import org.jeo.data.Cursor;
import org.jeo.data.Cursors;
import org.jeo.data.Query;
import org.jeo.data.VectorDataset;
import org.jeo.feature.Feature;
import org.jeo.feature.Schema;
import org.jeo.geom.Envelopes;

import android.os.Environment;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Envelope;

import junit.framework.TestCase;

public class GeoPkgTest extends TestCase {

    VectorDataset data;
    
    protected void setUp() throws Exception {
        init();
        data = createVectorData();
    }
    
    protected void init() throws Exception {
    }
    
    protected VectorDataset createVectorData() throws Exception {
        GeoPkgWorkspace gpkg = GeoPackage.open(
            new File(Environment.getExternalStorageDirectory(), "states.geopackage"));
        return (VectorDataset) gpkg.get("states");
    }

    public void testGetName() {
        assertEquals("states", data.getName());
    }

    public void testSchema() throws IOException {
        Schema schema = data.schema();
        assertNotNull(schema);
    
        assertNotNull(schema.geometry());
    }

    public void testBounds() throws IOException {
        Envelope bbox = data.bounds();
        assertNotNull(bbox);
    
        assertEquals(-124.73, bbox.getMinX(), 0.01);
        assertEquals(24.96, bbox.getMinY(), 0.01);
        assertEquals(-66.96, bbox.getMaxX(), 0.01);
        assertEquals(49.37, bbox.getMaxY(), 0.01);
    }

    public void testCount() throws IOException {
        // count all
        assertEquals(49, data.count(new Query()));
    
        // count within bounds
        Set<String> abbrs = Sets.newHashSet("MO", "OK", "TX", "NM", "AR", "LA"); 
    
        Envelope bbox = new Envelope(-106.649513, -93.507217, 25.845198, 36.493877);
        assertEquals(abbrs.size(), data.count(new Query().bounds(bbox)));
    
        // count with spatial filters
        assertEquals(abbrs.size(), data.count(new Query().filter(String.format("INTERSECTS(%s, %s)", 
            data.schema().geometry().getName(), Envelopes.toPolygon(bbox)))));
    
        // count with attribute filters
        assertEquals(1, data.count(new Query().filter("STATE_NAME = 'Texas'")));
        assertEquals(48, data.count(new Query().filter("STATE_NAME <> 'Texas'")));
        assertEquals(2, data.count(new Query().filter("P_MALE > P_FEMALE")));
        assertEquals(3, data.count(new Query().filter("P_MALE >= P_FEMALE")));
    
        // count with logical filters
        assertEquals(1, data.count(new Query().filter("P_MALE > P_FEMALE AND SAMP_POP > 200000")));
        assertEquals(5, data.count(new Query().filter("P_MALE > P_FEMALE OR SAMP_POP > 2000000")));
        assertEquals(1, data.count(new Query().filter("P_MALE > P_FEMALE AND NOT SAMP_POP > 200000")));
    
        // count with id filters
        String fid = fidFor(data, "STATE_NAME = 'Texas'");
        assertEquals(1, data.count(new Query().filter(String.format("IN ('%s')", fid))));
    }

    public void testCursorRead() throws Exception {
        // all
        assertEquals(49, Cursors.size(data.cursor(new Query())));
    
        // limit offset
        assertEquals(39, Cursors.size(data.cursor(new Query().offset(10))));
        assertEquals(10, Cursors.size(data.cursor(new Query().limit(10))));
    
        // bounds
        Envelope bbox = new Envelope(-106.649513, -93.507217, 25.845198, 36.493877);
        assertCovered(data.cursor(new Query().bounds(bbox)), "MO", "OK", "TX", "NM", "AR", "LA");
    
        // spatial filter
        assertCovered(data.cursor(new Query().filter(String.format("INTERSECTS(%s, %s)", 
            data.schema().geometry().getName(), Envelopes.toPolygon(bbox)))), 
            "MO", "OK", "TX", "NM", "AR", "LA");
    
        // comparison filter
        assertCovered(data.cursor(new Query().filter("STATE_NAME = 'Texas'")), "TX");
        assertNotCovered(data.cursor(new Query().filter("STATE_NAME <> 'Texas'")), "TX");
        assertCovered(data.cursor(new Query().filter("P_MALE > P_FEMALE")), "NV", "CA");
        assertCovered(data.cursor(new Query().filter("P_MALE >= P_FEMALE")), "NV", "CA", "WY");
    
        // logic filters
        assertCovered(
            data.cursor(new Query().filter("P_MALE > P_FEMALE AND SAMP_POP > 200000")), "CA");
        assertCovered(data.cursor(new Query().filter("P_MALE > P_FEMALE OR SAMP_POP > 2000000")), 
            "TX", "NY", "PA", "NV", "CA");
        assertCovered(
            data.cursor(new Query().filter("P_MALE > P_FEMALE AND NOT SAMP_POP > 200000")), "NV");
    
        // id filter
        String fid = fidFor(data, "STATE_NAME = 'Texas'");
        assertCovered(data.cursor(new Query().filter(String.format("IN ('%s')", fid))), "TX");
    }
    
    void assertNotCovered(Cursor<Feature> cursor, String... abbrs) throws IOException {
        final Set<String> set = Sets.newHashSet(abbrs);
        try {
            Iterables.find(cursor, new Predicate<Feature>() {
                @Override
                public boolean apply(Feature input) {
                    return set.contains(input.get("STATE_ABBR"));
                }
            });
            fail();
        }
        catch(NoSuchElementException expected) {}
    }
    
    void assertCovered(Cursor<Feature> cursor, String... abbrs) throws IOException {
        Set<String> set = Sets.newHashSet(abbrs);
        int count = 0;
        while(cursor.hasNext()) {
            set.remove(cursor.next().get("STATE_ABBR"));
            count++;
        }
    
        assertTrue(set.isEmpty());
        assertEquals(abbrs.length, count);
    }
    
    String fidFor(VectorDataset dataset, String filter) throws IOException {
        Cursor<Feature> c = dataset.cursor(new Query().filter(filter));
        try {
            assertTrue(c.hasNext());
            return c.next().getId();
        }
        finally {
            c.close();
        }
    }
}