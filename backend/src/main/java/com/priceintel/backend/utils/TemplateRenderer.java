package com.priceintel.backend.utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal {{variable}} substitution for email templates. Unknown placeholders
 * are left blank rather than printed, so a stray {{foo}} never reaches a client.
 */
public final class TemplateRenderer {

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private TemplateRenderer() {
    }

    /** Replaces every {{key}} in the text with vars.get(key) (or "" if absent/null). */
    public static String render(String text, Map<String, ?> vars) {
        if (text == null) {
            return "";
        }
        Matcher m = VAR.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object value = vars == null ? null : vars.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Wraps a plain-text body (with newlines) in a simple, safe HTML shell. */
    public static String toHtml(String body) {
        String escaped = (body == null ? "" : body)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        // Re-enable anchor rendering for bare URLs so links stay clickable.
        escaped = escaped.replaceAll("(https?://\\S+)",
                "<a href=\"$1\" style=\"color:#2563eb\">$1</a>");
        String withBreaks = escaped.replace("\n", "<br>");
        return "<div style=\"font-family:system-ui,Arial,sans-serif;max-width:560px;margin:auto;"
                + "line-height:1.55;color:#0f172a\">" + withBreaks + "</div>";
    }
}
