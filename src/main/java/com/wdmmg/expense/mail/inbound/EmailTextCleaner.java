package com.wdmmg.expense.mail.inbound;

import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns an email body into just what the sender typed: HTML → text,
 * quoted history and signatures removed, whitespace tidied.
 */
public final class EmailTextCleaner {
    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style|head)[^>]*>.*?</\\1>");
    private static final Pattern BLOCK_BREAK = Pattern.compile("(?i)<\\s*(br|/p|/div|/li|/tr|/h[1-6])\\s*/?>");
    private static final Pattern GMAIL_QUOTE = Pattern.compile("(?is)<div class=\"gmail_quote.*");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    // "On Tue, 3 Sep 2026 at 10:00, Name <a@b.c> wrote:" (may wrap onto two lines)
    private static final Pattern REPLY_HEADER = Pattern.compile("(?m)^\\s*On\\b.{0,200}?\\bwrote:\\s*$", Pattern.DOTALL);
    private static final Pattern FORWARD_MARKERS = Pattern.compile(
            "(?m)^\\s*(-{2,}\\s*(Original Message|Forwarded message)\\s*-{2,}|_{10,})\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENT_FROM = Pattern.compile("(?im)^\\s*Sent from my \\w+.*$");

    private EmailTextCleaner() {}

    public static String htmlToText(String html) {
        if (html == null) return "";
        String s = GMAIL_QUOTE.matcher(html).replaceAll("");
        s = SCRIPT_STYLE.matcher(s).replaceAll("");
        s = BLOCK_BREAK.matcher(s).replaceAll("\n");
        s = TAG.matcher(s).replaceAll("");
        return HtmlUtils.htmlUnescape(s).replace(' ', ' ');
    }

    /** Keeps only the new part of a reply/forward and drops the signature. */
    public static String stripQuotedAndSignature(String text) {
        if (text == null) return "";
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        s = cutAt(s, REPLY_HEADER);
        s = cutAt(s, FORWARD_MARKERS);
        s = cutAt(s, SENT_FROM);

        List<String> kept = new ArrayList<>();
        for (String line : s.split("\n", -1)) {
            if (line.equals("-- ") || line.equals("--")) break; // RFC 3676 signature separator
            if (line.startsWith(">")) continue;
            kept.add(line.stripTrailing());
        }
        return String.join("\n", kept).replaceAll("\n{3,}", "\n\n").strip();
    }

    public static String clean(String plain, String html) {
        String base = plain != null && !plain.isBlank() ? plain : htmlToText(html);
        return stripQuotedAndSignature(base);
    }

    private static String cutAt(String s, Pattern p) {
        var m = p.matcher(s);
        return m.find() ? s.substring(0, m.start()) : s;
    }
}
