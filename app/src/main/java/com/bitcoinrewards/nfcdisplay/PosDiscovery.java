package com.bitcoinrewards.nfcdisplay;

import java.util.*;
import java.util.regex.*;

/** Deliberately narrow adapter for BTCPay's cookie-authorized ListApps view, not a general scraper. */
final class PosDiscovery {
    static final int MAX_BYTES = 524288;
    static final int MAX_APPS = 100;
    static final class App {
        final String id, name, url;
        App(String id, String name, String url) { this.id = id; this.name = name; this.url = url; }
    }
    static String listUrl(String origin, String store) {
        if (store == null || !store.matches("[A-Za-z0-9]{1,128}")) throw new IllegalArgumentException();
        return ApiOrigin.requireConfigured(origin) + "/stores/" + store + "/apps";
    }
    private static Matcher matches(String regex, String value) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(value);
    }
    static List<App> parse(String origin, String store, String html) {
        listUrl(origin, store);
        if (html == null || html.length() > MAX_BYTES) throw new IllegalArgumentException();
        // Never consider text inside scripts, styles, templates or HTML comments as app evidence.
        String clean = html.replaceAll("(?is)<!--.*?-->|<(script|style|template)\\b[^>]*>.*?</\\1\\s*>", "");
        // Prove completeness of the entire known list container before cardinality can authorize
        // automatic launch. Finding only complete rows would silently lose a truncated second app.
        Matcher tableTags = matches("</?table\\b[^>]*>", clean);
        int tableTagCount = 0;
        while (tableTags.find()) tableTagCount++;
        if (tableTagCount == 0 && clean.contains("There are no apps yet.")
            && !matches("<tr\\b|<td\\b|\\bid\\s*=\\s*[\"']App-", clean).find()) return Collections.emptyList();
        if (tableTagCount != 2) throw new IllegalArgumentException();
        Matcher table = matches("<table\\b[^>]*>(.*?)</table\\s*>", clean);
        if (!table.find()) throw new IllegalArgumentException();
        String outside = clean.substring(0, table.start()) + clean.substring(table.end());
        if (matches("<tr\\b|<td\\b|\\bid\\s*=\\s*[\"']App-", outside).find()) throw new IllegalArgumentException();
        Matcher body = matches("^\\s*<thead\\b[^>]*>(.*?)</thead\\s*>\\s*<tbody\\b[^>]*>(.*?)</tbody\\s*>\\s*$", table.group(1));
        if (!body.matches() || matches("<td\\b|<thead\\b|<tbody\\b|\\bid\\s*=\\s*[\"']App-", body.group(1)).find()) throw new IllegalArgumentException();
        String bodyHtml = body.group(2);
        Matcher rows = matches("<tr\\b[^>]*>(.*?)</tr\\s*>", bodyHtml);
        List<App> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int count = 0;
        int consumed = 0;
        while (rows.find()) {
            if (!bodyHtml.substring(consumed, rows.start()).trim().isEmpty()) throw new IllegalArgumentException();
            consumed = rows.end();
            String row = rows.group(1);
            if (!matches("\\bid\\s*=\\s*[\"']App-", row).find()) throw new IllegalArgumentException();
            if (++count > MAX_APPS) throw new IllegalArgumentException();
            Matcher cells = matches("<td\\b[^>]*>(.*?)</td\\s*>", row);
            List<String> td = new ArrayList<>();
            int cellEnd = 0;
            while (cells.find()) {
                if (!row.substring(cellEnd, cells.start()).trim().isEmpty()) throw new IllegalArgumentException();
                td.add(cells.group(1));
                cellEnd = cells.end();
            }
            if (td.size() != 3 || !row.substring(cellEnd).trim().isEmpty()) throw new IllegalArgumentException();
            Matcher link = matches("^\\s*<a\\b([^>]*)>([^<]*)</a\\s*>\\s*$", td.get(0));
            if (!link.matches()) throw new IllegalArgumentException();
            Map<String,String> attrs = attributes(link.group(1));
            String id = attrs.get("id");
            if (id == null || !id.matches("App-[A-Za-z0-9]{1,128}")) throw new IllegalArgumentException();
            id = id.substring(4);
            if (!seen.add(id)) throw new IllegalArgumentException();
            String expected = "/apps/" + id + "/settings/pos";
            String href = attrs.get("href");
            // Other supported app types have different settings routes and are not POS candidates.
            if (href == null) throw new IllegalArgumentException();
            String canonicalOrigin = ApiOrigin.requireConfigured(origin).toString();
            String path = href.startsWith(canonicalOrigin + "/") ? href.substring(canonicalOrigin.length()) : href;
            if (!path.matches("/apps/" + id + "/settings/[A-Za-z0-9-]+")) throw new IllegalArgumentException();
            boolean pos = path.equals(expected);
            Matcher owner = matches("<a\\b([^>]*)>[^<]*</a\\s*>", td.get(2));
            if (!owner.find()) throw new IllegalArgumentException();
            String storeLink = attributes(owner.group(1)).get("href");
            String storePath = "/stores/" + store + "/settings";
            if (!(storePath.equals(storeLink) || (ApiOrigin.requireConfigured(origin) + storePath).equals(storeLink)))
                throw new IllegalArgumentException();
            if (owner.find()) throw new IllegalArgumentException();
            String style = text(td.get(1));
            if (pos && style.equals("Point of Sale - Keypad")) {
                String name = text(link.group(2));
                if (name.isEmpty()) name = "Point of Sale";
                apps.add(new App(id, name, PosUrlPolicy.require(origin, ApiOrigin.requireConfigured(origin) + "/apps/" + id + "/pos")));
            }
        }
        if (!bodyHtml.substring(consumed).trim().isEmpty() || count == 0) throw new IllegalArgumentException();
        return Collections.unmodifiableList(apps);
    }
    private static Map<String,String> attributes(String raw) {
        Matcher m = matches("\\s+([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*([\"'])(.*?)\\2", raw);
        Map<String,String> attrs = new HashMap<>();
        int end = 0;
        while (m.find()) {
            if (!raw.substring(end, m.start()).trim().isEmpty()) throw new IllegalArgumentException();
            String key = m.group(1).toLowerCase(Locale.US);
            if (attrs.put(key, m.group(3)) != null) throw new IllegalArgumentException();
            end = m.end();
        }
        if (!raw.substring(end).trim().isEmpty()) throw new IllegalArgumentException();
        return attrs;
    }
    private static String text(String raw) {
        String plain = raw.replaceAll("(?s)<[^>]*>", " ");
        Matcher m = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);|&(amp|lt|gt|quot|apos|#39|nbsp);").matcher(plain);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String v;
            if (m.group(1) != null) {
                try {
                    String n = m.group(1);
                    int cp = Integer.parseInt(n.startsWith("x") ? n.substring(1) : n, n.startsWith("x") ? 16 : 10);
                    v = Character.isValidCodePoint(cp) && !(cp >= 0xd800 && cp <= 0xdfff) ? new String(Character.toChars(cp)) : " ";
                } catch (RuntimeException e) { v = " "; }
            } else {
                switch (m.group(2)) {
                    case "amp": v = "&"; break;
                    case "lt": v = "<"; break;
                    case "gt": v = ">"; break;
                    case "quot": v = "\""; break;
                    case "apos": case "#39": v = "'"; break;
                    default: v = " ";
                }
            }
            m.appendReplacement(out, Matcher.quoteReplacement(v));
        }
        m.appendTail(out);
        String safe = out.toString().replaceAll("[\\p{C}]", " ").replaceAll("\\s+", " ").trim();
        return safe.codePointCount(0, safe.length()) > 120 ? safe.substring(0, safe.offsetByCodePoints(0, 120)) : safe;
    }
    private PosDiscovery() { }
}
