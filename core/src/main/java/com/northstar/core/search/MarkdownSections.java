package com.northstar.core.search;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Header-aware markdown splitting for embedding: one section per H1–H3, each
 * carrying its breadcrumb ({@code Title > H2 > H3}) so a chunk embeds with the
 * context of every heading above it — a paraphrased question then lands on the
 * right section instead of anywhere in the note. Deeper headings (H4+) stay
 * inside their parent section; {@code #} lines inside fenced code are content,
 * not headers. A document with no headers is one section titled by the note.
 */
final class MarkdownSections {

    private static final Pattern HEADER = Pattern.compile("(#{1,3})\\s+(.*)");

    /** One header-bounded slice: where it sits ({@code breadcrumb}) and its markdown. */
    record Section(String breadcrumb, String text) {
    }

    private MarkdownSections() {
    }

    static List<Section> split(String title, String markdown) {
        List<Section> out = new ArrayList<>();
        String[] headings = new String[3];
        StringBuilder current = new StringBuilder();
        String breadcrumb = title;
        boolean fenced = false;

        for (String line : markdown.split("\n", -1)) {
            String stripped = line.strip();
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
                fenced = !fenced;
                current.append(line).append('\n');
                continue;
            }
            Matcher header = fenced ? null : HEADER.matcher(stripped);
            if (header != null && header.matches()) {
                flush(out, breadcrumb, current);
                int level = header.group(1).length();
                headings[level - 1] = header.group(2).strip();
                for (int deeper = level; deeper < headings.length; deeper++) {
                    headings[deeper] = null;
                }
                breadcrumb = breadcrumb(title, headings);
            }
            current.append(line).append('\n');
        }
        flush(out, breadcrumb, current);

        return out.isEmpty() ? List.of(new Section(title, markdown)) : out;
    }

    private static void flush(List<Section> out, String breadcrumb, StringBuilder text) {
        if (!text.toString().isBlank()) {
            out.add(new Section(breadcrumb, text.toString()));
        }
        text.setLength(0);
    }

    /** {@code Title > H1 > H2 > H3}, skipping a heading that just repeats the title. */
    private static String breadcrumb(String title, String[] headings) {
        StringBuilder crumb = new StringBuilder(title);
        for (String heading : headings) {
            if (heading != null && !heading.isBlank() && !heading.equalsIgnoreCase(title.strip())) {
                crumb.append(" > ").append(heading);
            }
        }
        return crumb.toString();
    }
}
