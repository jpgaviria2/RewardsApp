package com.bitcoinrewards.nfcdisplay;
import org.junit.Test;
import static org.junit.Assert.*;

public class HidScanFramerTest {
    @Test public void completeFramePreservesExactCaseAndPunctuation() throws Exception {
        Class<?> type;
        try { type = Class.forName("com.bitcoinrewards.nfcdisplay.HidScanFramer"); }
        catch (ClassNotFoundException e) { fail("Missing production HID frame collector"); return; }
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object frame = constructor.newInstance();
        java.lang.reflect.Method accept = type.getDeclaredMethod("accept", String.class, int.class, boolean.class, boolean.class, long.class, boolean.class);
        accept.setAccessible(true);
        String exact = "lightning:Alice.Test+_-/?:@Example.COM";
        long now = 1;
        for (char c : exact.toCharArray()) assertNull(accept.invoke(frame, "scanner", (int)c, false, false, now++, true));
        assertEquals(exact, accept.invoke(frame, "scanner", 0, true, false, now, true));
    }

    @Test public void busyRejectsWholeFrameUntilDelimiter() {
        HidScanFramer f = new HidScanFramer();
        f.accept("a", 'A', false, false, 1, true);
        f.accept("a", 'B', false, false, 2, false);
        f.accept("a", 'C', false, false, 3, true);
        assertNull(f.accept("a", 0, true, false, 4, true));
        f.accept("a", 'D', false, false, 5, true);
        assertEquals("D", f.accept("a", 0, true, false, 6, true));
        f.accept("a", 'E', false, false, 7, true);
        assertNull(f.accept("a", 0, true, false, 8, false));
    }

    @Test public void repeatAndControlPoisonTheEntireFrame() {
        for (int bad : new int[]{0, 9, 27, 31, 127, 0x301, 0xd800}) {
            HidScanFramer f = new HidScanFramer();
            f.accept("a", 'A', false, false, 1, true);
            f.accept("a", bad, false, false, 2, true);
            f.accept("a", 'B', false, false, 3, true);
            assertNull(f.accept("a", 0, true, false, 4, true));
        }
        HidScanFramer f = new HidScanFramer();
        f.accept("a", 'A', false, false, 1, true);
        assertNull(f.accept("a", 0, true, true, 2, true));
        f.accept("a", 'B', false, true, 3, true);
        assertNull(f.accept("a", 0, true, false, 4, true));
    }


    @Test public void staleInterKeyAndTotalFramesDiscardThroughNextDelimiter() {
        HidScanFramer f = new HidScanFramer();
        f.accept("a", 'A', false, false, 1, true);
        f.accept("a", 'B', false, false, 502, true);
        assertNull(f.accept("a", 0, true, false, 503, true));
        f.accept("a", 'C', false, false, 504, true);
        assertNull(f.accept("a", 0, true, false, 1005, true));
        for (int i=0; i<30; i++) f.accept("a", 'D', false, false, 2000+i*400, true);
        assertNull(f.accept("a", 0, true, false, 14000, true));
        f.accept("a", 'E', false, false, 15000, true);
        assertEquals("E", f.accept("a", 0, true, false, 15001, true));
        f.accept("a", 'F', false, false, 16000, true);
        assertNull(f.accept("a", 0, true, false, 15999, true));
    }


    @Test public void overflowDoesNotSubmitPrefixOrSuffix() {
        HidScanFramer f = new HidScanFramer();
        for (int i=0; i<4097; i++) f.accept("a", 'A', false, false, i, true);
        assertNull(f.accept("a", 0, true, false, 4098, true));
        for (int i=0; i<4096; i++) f.accept("a", 'B', false, false, 5000+i, true);
        assertEquals(4096, f.accept("a", 0, true, false, 9096, true).length());
        assertNull(f.accept("a", 0, true, false, 9097, true));
    }


    @Test public void sourceChangeCannotJoinPartialOrAcceptNewSuffix() {
        HidScanFramer f = new HidScanFramer();
        f.accept("a", 'A', false, false, 1, true);
        f.accept("b", 'B', false, false, 2, true);
        assertNull(f.accept("b", 0, true, false, 3, true));
        f.accept("b", 'C', false, false, 4, true);
        assertEquals("C", f.accept("b", 0, true, false, 5, true));
    }


    @Test public void pauseOrLockDiscardsPartialThroughDelimiter() throws Exception {
        HidScanFramer f = new HidScanFramer();
        f.accept("a", 'A', false, false, 1, true);
        java.lang.reflect.Method reset;
        try { reset = HidScanFramer.class.getDeclaredMethod("reset"); }
        catch (NoSuchMethodException e) { fail("Missing lifecycle frame reset"); return; }
        reset.setAccessible(true);
        reset.invoke(f);
        f.accept("a", 'B', false, false, 2, true);
        assertNull(f.accept("a", 0, true, false, 3, true));
        reset.invoke(f);
        f.accept("a", 'C', false, false, 4, true);
        assertEquals("C", f.accept("a", 0, true, false, 5, true));
    }

}
