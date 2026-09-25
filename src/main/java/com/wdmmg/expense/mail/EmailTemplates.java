package com.wdmmg.expense.mail;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Builds the app's outgoing messages. Plain text first (it's what many clients preview),
 * with a restrained HTML version: system fonts, square edges, one accent colour.
 */
@Component
public class EmailTemplates {
    private static final String ACCENT = "#1d5e4a";

    public OutboundEmail verificationCode(String to, String name, String code, int minutes) {
        String first = firstName(name);
        String subject = code + " is your verification code";
        String text = """
                Hi %s,

                Your verification code is: %s

                Enter it on the sign-up screen to confirm your email address. It expires in %d minutes.

                If you didn't create an account with Where did my money go, you can ignore this email.
                """.formatted(first, code, minutes);
        String html = layout("""
                <p style="margin:0 0 16px">Hi %s,</p>
                <p style="margin:0 0 16px">Enter this code on the sign-up screen to confirm your email address.</p>
                <p style="margin:0 0 16px;font-size:28px;font-weight:600;letter-spacing:6px;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;color:#111">%s</p>
                <p style="margin:0 0 16px;color:#555">It expires in %d minutes.</p>
                <p style="margin:24px 0 0;color:#777;font-size:13px">If you didn't create an account, you can ignore this email.</p>
                """.formatted(HtmlUtils.htmlEscape(first), code, minutes));
        return OutboundEmail.of(to, subject, text, html);
    }

    /** One expense created from an email, for the confirmation reply. */
    public record AddedLine(String name, BigDecimal amount, String currency, LocalDate date, String category) {}

    public OutboundEmail expensesAdded(String to, String name, String subject, String inReplyTo, String appUrl,
                                       List<AddedLine> added, List<String> skipped) {
        String first = firstName(name);
        String heading = added.size() == 1 ? "Added 1 expense" : "Added " + added.size() + " expenses";
        StringBuilder text = new StringBuilder("Hi %s,\n\n%s from your email:\n\n".formatted(first, heading));
        StringBuilder rows = new StringBuilder();
        for (AddedLine a : added) {
            text.append("• %s – %s (%s, %s)\n".formatted(a.name(), money(a.amount(), a.currency()), a.category(), day(a.date())));
            rows.append("""
                    <tr><td style="padding:8px 0;border-bottom:1px solid #ecece8">%s<br><span style="color:#777;font-size:13px">%s · %s</span></td>
                    <td style="padding:8px 0;border-bottom:1px solid #ecece8;text-align:right;white-space:nowrap;font-variant-numeric:tabular-nums">%s</td></tr>
                    """.formatted(esc(a.name()), esc(a.category()), esc(day(a.date())), esc(money(a.amount(), a.currency()))));
        }
        String skippedText = skipped.isEmpty() ? "" : "\nNot added:\n" + String.join("\n", skipped.stream().map(x -> "• " + x).toList()) + "\n";
        text.append(skippedText);
        text.append("\nSomething wrong? Edit or delete it in the app%s.\n".formatted(appUrl == null ? "" : ": " + appUrl + "/expenses"));

        String skippedHtml = skipped.isEmpty() ? "" : "<p style=\"margin:16px 0 0;color:#8a5a00\">Not added: " +
                esc(String.join("; ", skipped)) + "</p>";
        String link = appUrl == null ? "in the app" : "<a href=\"" + esc(appUrl) + "/expenses\" style=\"color:" + ACCENT + "\">in the app</a>";
        String html = layout("""
                <p style="margin:0 0 16px">Hi %s,</p>
                <p style="margin:0 0 12px;font-weight:600">%s from your email</p>
                <table style="width:100%%;border-collapse:collapse;font-size:15px">%s</table>
                %s
                <p style="margin:20px 0 0;color:#777;font-size:13px">Something wrong? Edit or delete it %s.</p>
                """.formatted(esc(first), esc(heading), rows, skippedHtml, link));
        return new OutboundEmail(to, replySubject(subject), text.toString(), html, null, inReplyTo);
    }

    public OutboundEmail notAnExpense(String to, String name, String subject, String inReplyTo, String reason) {
        String first = firstName(name);
        String example = "I had an ice cream for 30rs";
        String text = """
                Hi %s,

                I couldn't find an expense to add in your email. %s

                To add one, just describe what you spent, e.g. "%s" or "Uber 240 and lunch 180 yesterday".
                """.formatted(first, reason, example);
        String html = layout("""
                <p style="margin:0 0 16px">Hi %s,</p>
                <p style="margin:0 0 16px">I couldn't find an expense to add in your email. %s</p>
                <p style="margin:0;color:#555">To add one, just describe what you spent, e.g. <em>“%s”</em> or <em>“Uber 240 and lunch 180 yesterday”</em>.</p>
                """.formatted(esc(first), esc(reason), esc(example)));
        return new OutboundEmail(to, replySubject(subject), text, html, null, inReplyTo);
    }

    public OutboundEmail couldNotProcess(String to, String name, String subject, String inReplyTo) {
        String first = firstName(name);
        String text = """
                Hi %s,

                Sorry, I couldn't process your email just now, so nothing was added. Please try again in a few minutes, or add the expense in the app.
                """.formatted(first);
        String html = layout("""
                <p style="margin:0 0 16px">Hi %s,</p>
                <p style="margin:0">Sorry, I couldn't process your email just now, so nothing was added. Please try again in a few minutes, or add the expense in the app.</p>
                """.formatted(esc(first)));
        return new OutboundEmail(to, replySubject(subject), text, html, null, inReplyTo);
    }

    private static String replySubject(String subject) {
        if (subject == null || subject.isBlank()) return "Re: your expense email";
        return subject.regionMatches(true, 0, "Re:", 0, 3) ? subject : "Re: " + subject;
    }

    private static String money(BigDecimal amount, String currency) {
        try {
            NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
            f.setCurrency(Currency.getInstance(currency));
            return f.format(amount);
        } catch (IllegalArgumentException e) {
            return currency + " " + amount.toPlainString();
        }
    }

    private static String day(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH));
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    private String layout(String body) {
        return """
                <!doctype html>
                <html><body style="margin:0;padding:24px;background:#f4f4f2">
                <div style="max-width:480px;margin:0 auto;background:#fff;border:1px solid #e2e2de;border-top:3px solid %s;padding:28px 28px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.5;color:#222">
                <p style="margin:0 0 20px;font-size:13px;font-weight:600;color:#555">Where did my money go</p>
                %s
                </div>
                </body></html>
                """.formatted(ACCENT, body);
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) return "there";
        return name.trim().split("\\s+")[0];
    }
}
