package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.nio.file.*;
import static org.junit.Assert.*;

public class SimpleKioskLayoutTest {
    @Test public void cameraIsOptInAndCannotSubmitOrBeGrantedInHidOnlyMode() throws Exception {
        String main=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java")),java.nio.charset.StandardCharsets.UTF_8);
        String settings=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/SettingsActivity.java")),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(settings.contains("getBoolean(\"camera_check_in_enabled\", false)"));
        assertTrue(settings.contains("putBoolean(\"camera_check_in_enabled\", cameraCheckIn.isChecked())"));
        assertTrue(main.contains("camera && cameraCheckInEnabled && policy.allowCamera(origin)"));
        int callback=main.indexOf("@Override public void barcodeResult(BarcodeResult result)");
        int parser=main.indexOf("WalletQrPayloadParser.parse(result.getText())",callback);
        assertTrue(main.substring(callback,parser).contains("if (!cameraCheckInEnabled) return;"));
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        Document d=f.newDocumentBuilder().parse("src/main/res/layout/activity_settings.xml");
        assertNotNull(id(d,"enable_camera_check_in"));
    }
    @Test public void kioskPresentationIsExplicitWithoutWeakeningOriginOrAuthentication() throws Exception {
        String main=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java")),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(main.contains("webSettings.getUserAgentString() + \" TrailsRewardsKiosk/1\""));
        assertTrue(main.contains("beginCheckInCapabilityProbe(view, url)"));
        assertTrue(main.contains("rewardsPagePolicy.isEligiblePageCompletion"));
    }
    private static final String A = "http://schemas.android.com/apk/res/android";
    private Document layout() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse("src/main/res/layout/activity_main.xml");
    }
    private Element id(Document d, String id) {
        NodeList all = d.getElementsByTagName("*");
        for (int i=0;i<all.getLength();i++) { Element e=(Element)all.item(i);
            if (("@+id/"+id).equals(e.getAttributeNS(A,"id"))) return e;
        } return null;
    }
    @Test public void paymentIsLastChildBelowWeightedCustomerContent() throws Exception {
        Document d=layout(); Element payment=id(d,"btn_collect_payment"), split=id(d,"main_split_layout");
        Node last=split.getLastChild(); while (!(last instanceof Element)) last=last.getPreviousSibling();
        assertSame("Payment remains reachable at page bottom",payment,last);
        Element content=id(d,"customer_content"); assertNotNull(content);
        assertEquals("1",content.getAttributeNS(A,"layout_weight"));
        assertEquals("48dp",payment.getAttributeNS(A,"layout_height"));
    }
    @Test public void idleAndConfirmedContentOccupySameCardWithoutInstructionTiles() throws Exception {
        Document d=layout(); assertNull(id(d,"scan_here_prompt")); assertNull(id(d,"tap_action_card"));
        String xml=new String(Files.readAllBytes(Paths.get("src/main/res/layout/activity_main.xml")),java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(xml.contains("SHOW QR")); assertFalse(xml.contains("TAP CARD"));
        Element card=id(d,"customer_content");
        assertSame(card,id(d,"webview").getParentNode());
        assertSame(card,id(d,"nfc_reader_overlay").getParentNode());
        assertSame(card,id(d,"check_in_confetti").getParentNode());
        assertEquals("@color/rewards_cream",id(d,"nfc_reader_overlay").getAttributeNS(A,"background"));
    }
}
