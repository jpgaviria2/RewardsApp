package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PosDiscoveryTest {
    static final String ORIGIN = "https://pay.example.com";
    // Synthetic rendering of v2.4.7 Views/UIApps/ListApps.cshtml, not a captured authenticated response.
    static String row(String id, String name, String type, String store) {
        return "<tr><td><a href=\"/apps/" + id + "/settings/pos\" id=\"App-" + id + "\">" + name
            + "</a></td><td>Point of Sale <span>-</span><span>" + type + "</span></td><td><span><a href=\"/stores/" + store + "/settings\">Store name</a></span></td></tr>";
    }
    static List<PosDiscovery.App> parse(String html) { return PosDiscovery.parse(ORIGIN, "Store1", html); }
    static void reject(String html) {
        try { parse(html); fail("unsafe markup accepted"); } catch (IllegalArgumentException expected) { }
    }
    @Test public void exactStoreRouteAndOriginOnly() {
        assertEquals(ORIGIN + "/stores/Store1/apps", PosDiscovery.listUrl(ORIGIN, "Store1"));
        for (String bad : new String[]{"", "../Other", "Store1?archived=true", "Store%31", "Store/Other", "Store\n"}) {
            try { PosDiscovery.listUrl(ORIGIN, bad); fail(); } catch (IllegalArgumentException expected) { }
        }
        try { PosDiscovery.listUrl("http://pay.example.com", "Store1"); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void singleKeypadSelectedOnlyAfterFilteringOtherViews() {
        List<PosDiscovery.App> apps = parse("<table>" + row("Cart1", "Catalog", "Product list with cart", "Store1") + row("Key1", "Counter &amp; café", "Keypad", "Store1") + "</table>");
        assertEquals(1, apps.size());
        assertEquals("Counter & café", apps.get(0).name);
        assertEquals(ORIGIN + "/apps/Key1/pos", apps.get(0).url);
    }
    @Test public void multipleKeypadsPreserveAllNamesAndIdsNotFirst() {
        List<PosDiscovery.App> apps = parse(row("Key1", "Counter", "Keypad", "Store1") + row("Key2", "Patio", "Keypad", "Store1"));
        assertEquals(2, apps.size()); assertEquals("Counter", apps.get(0).name); assertEquals("Patio", apps.get(1).name);
        assertNotEquals(apps.get(0).id, apps.get(1).id);
    }
    @Test public void noKeypadDoesNotFallBackToCartOrArbitraryApp() {
        assertTrue(parse(row("Cart1", "Catalog", "Product list with cart", "Store1")).isEmpty());
        assertTrue(parse("<p>There are no apps yet.</p>").isEmpty());
        assertTrue(parse(row("Crowd1", "Campaign", "Keypad", "Store1").replace("/settings/pos", "/settings/crowdfund")).isEmpty());
    }
    @Test public void crossStoreForeignOriginQueriesAndAmbiguityFailClosed() {
        reject(row("Key1", "Counter", "Keypad", "Other"));
        reject(row("Key1", "Counter", "Keypad", "Store1").replace("/stores/Store1/settings", "https://evil.example/stores/Store1/settings"));
        reject(row("Key1", "Counter", "Keypad", "Store1").replace("/apps/Key1/settings/pos", "https://evil.example/apps/Key1/settings/pos"));
        reject(row("Key1", "Counter", "Keypad", "Store1").replace("/settings/pos\"", "/settings/pos?next=x\""));
        reject(row("Key1", "Counter", "Keypad", "Store1").replace("id=\"App-Key1\"", "id=\"App-Key1\" href=\"/apps/Other/settings/pos\""));
        reject(row("Key1", "Counter", "Keypad", "Store1") + row("Key1", "Duplicate", "Keypad", "Store1"));
        reject(row("Key1", "Counter", "Keypad", "Store1") + row("Key2", "Second", "Keypad", "Store1").replace("id=\"App-Key2\"", "class=\"unknown\""));
        reject(row("Key1", "Counter", "Keypad", "Store1") + row("Key2", "Second", "Keypad", "Store1").replace("</td></tr>", "</tr>"));
    }
    @Test public void markupAndAuthenticationPagesNeverYieldAnApp() {
        reject("<form action='/login'>Sign in</form>");
        reject("<script>" + row("Key1", "Fake", "Keypad", "Store1") + "</script>");
        reject("<!--" + row("Key1", "Fake", "Keypad", "Store1") + "-->");
        reject("<template>" + row("Key1", "Fake", "Keypad", "Store1") + "</template>");
        reject(row("Key1", "Counter", "Keypad", "Store1").replace("</a></td>", "</a><span>archived</span></td>"));
    }
    @Test public void boundsAndSafeLabels() {
        reject("a".repeat(PosDiscovery.MAX_BYTES + 1));
        StringBuilder rows = new StringBuilder();
        for (int i=0; i<=PosDiscovery.MAX_APPS; i++) rows.append(row("Key"+i, "Counter", "Keypad", "Store1"));
        reject(rows.toString());
        String name = parse(row("Key1", "&lt;script&gt;&#10;&#x202e;Name", "Keypad", "Store1")).get(0).name;
        assertFalse(name.contains("\n")); assertFalse(name.contains("\u202e")); assertTrue(name.contains("<script>"));
        assertEquals(120, parse(row("Key1", "a".repeat(200), "Keypad", "Store1")).get(0).name.length());
    }
}
