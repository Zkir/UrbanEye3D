package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import ru.zkir.urbaneye3d.meshers.s3db.MesherHipped;
import ru.zkir.urbaneye3d.meshers.s3db.RoofShapes;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the "frozen 3D scene" bug (straight skeleton endless/quadratic loop).
 *
 * Root cause scenario: relation 452031 ("Средние торговые ряды", Moscow, Red Square) is a multipolygon
 * building with roof:shape=gabled and holes (courtyards), and a very complex outline (480 outer + 460
 * inner points). As long as its member ways were not downloaded, the relation was incomplete and Scene
 * skipped it. After the "Automatically download incomplete multipolygons" step completed it,
 * MesherHipped.generateStraightSkeleton() entered an endless/quadratic event loop inside the
 * campskeleton library (confirmed by thread dump: Skeleton.skeleton() spinning in
 * refindFaceEventsIfNeeded / CollisionQ.addCorner). Because the scene update runs on a single-threaded
 * executor, that froze all 3D mesh updates forever, while the ground plane (updated on the EDT) kept
 * repainting -- the exact symptom reported by users.
 *
 * The fix guards the skeleton call with a time-budget watchdog plus contour sanitization, and degrades
 * to a watertight flat-roof prism if the straight skeleton cannot be produced in time.
 *
 * This test must FAIL (not hang) if that protection is removed. The worker thread is a daemon and the
 * assertion has a hard join timeout, so even a regression cannot wedge the JVM.
 */
class SkeletonFreezeTest {

    /** Must comfortably exceed the sum of the watchdog attempt budgets in MesherHipped. */
    private static final long FREEZE_TIMEOUT_MS =
            Long.getLong("urbaneye3d.freezeTimeoutSec", 90) * 1000L;

    @BeforeAll
    public static void setUp() {
        Config.setPreferencesInstance(new MemoryPreferences());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Config.setUrlsProvider(JosmUrls.getInstance());
    }

    private DataSet loadDataSetFromOsmFile(String resourceName) throws Exception {
        InputStream is = getClass().getResourceAsStream("/osm_test_files/" + resourceName);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        return OsmReader.parseDataSet(is, null);
    }

    /**
     * If the building outline contains a lot of points, like 'Moscow Market Hall' (relation 452031) (943 ones),
     * The camp-skeleton lib just freezes. Here we test, that it does not.
     * (some measures, to prevent it, must be taken in the MesherHipped (like outline simplification)
     */
    @Test
    void testCampSkeletonFreeze() throws Exception {
        DataSet dataSet = loadDataSetFromOsmFile("market_hall.osm");
        Scene scene = new Scene();

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
                scene.applyUpdate(update);
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "skeleton-watchdog");
        worker.setDaemon(true); // never block JVM exit if it hangs
        worker.start();
        worker.join(FREEZE_TIMEOUT_MS);

        if (worker.isAlive()) {
            worker.interrupt(); // best effort; the camp loop is not interruptible, but a daemon thread won't block exit
            fail("Scene.calculateUpdate() did not complete within " + (FREEZE_TIMEOUT_MS / 1000)
                    + " s for market_hall.osm - straight skeleton freeze regression!"
                    + " Worker stack:\n" + stackTraceOf(worker));
        }
        if (failure.get() != null) {
            fail("Scene.calculateUpdate() threw: " + failure.get());
        }
        // Even in the degraded flat-roof path, the building must still be produced (not disappear).
        assertTrue(scene.getObjectCount() > 0, "No elements rendered for market_hall.osm");
    }

    private static String stackTraceOf(Thread t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : t.getStackTrace()) sb.append("\tat ").append(e).append('\n');
        return sb.toString();
    }

    @Test
    void testCampSkeletonInterrupt() throws Exception {
        DataSet ds;
        try (InputStream is = getClass().getResourceAsStream("/osm_test_files/market_hall.osm")) {
            assertNotNull(is, "market_hall.osm fixture not found");
            ds = OsmReader.parseDataSet(is, null);
        }
        OsmPrimitive relation = null;
        for (var p : ds.allPrimitives()) {
            if (p.getUniqueId() == 452031L) {
                relation = p;
                break;
            }
        }
        assertNotNull(relation, "relation 452031 not found in market_hall.osm");
        Contour contour = new Contour(relation);
        List<Point2D> outer = contour.outerRings.get(0);
        List<List<Point2D>> inners = contour.innerRings;

        MesherHipped mesher = new MesherHipped();
        final Throwable[] failure = new Throwable[1];
        final Thread worker = new Thread(() -> {
            try {
                mesher.calculateStraightSkeleton(outer, inners, RoofShapes.GABLED, null);
            } catch (Throwable t) {
                failure[0] = t;
            }
        }, "camp-interrupt-probe");
        worker.setDaemon(true);

        PrintStream realErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
        try {
            worker.start();
            worker.join(1500);
            assertTrue(worker.isAlive(), "the raw market_hall skeleton should still be spinning after 1.5 s");

            worker.interrupt();
            worker.join(2000);
            assertFalse(worker.isAlive(),
                    "campskeleton did not react to interrupt() within 2 s - the interruptibility patch "
                            + "in the campskeleton fork is missing, or the plugin still uses the unpatched release jar");
        } finally {
            System.setErr(realErr);
        }

        //assertTrue(failure[0] instanceof InterruptedException,
        //        "expected SkeletonInterruptedException, got: " + failure[0]);

        String err = capturedErr.toString(StandardCharsets.UTF_8);
        if (!err.isEmpty()) {
            //camp keeps spinning after its own per-event Error in input - fine, but it must be visible
            //somewhere, even if we keep it out of the surefire console
            assertTrue(err.contains("Error in input") || err.contains("org.twak.camp"),
                    "unexpected stderr from campskeleton probe: " + err);
            System.out.println("note: campskeleton wrote " + err.length()
                    + " chars of internal 'Error in input' noise to stderr during the probe (captured, not shown)");
        }
    }
}
