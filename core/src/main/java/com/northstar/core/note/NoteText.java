package com.northstar.core.note;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure text helpers for the note module: parsing {@code [[wiki links]]}, slugifying
 * titles (diacritic-aware, so Vietnamese titles slug cleanly), and building list
 * snippets. Kept dependency-free and static so it is trivially unit-testable.
 */
final class NoteText {

    // [[Title]] or [[Title|alias]] — capture the target title, ignore the alias.
    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[\\s*([^\\]|]+?)\\s*(?:\\|[^\\]]*)?\\]\\]");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private NoteText() {
    }

    /** Distinct wiki-link target titles in order of first appearance. */
    static Set<String> parseLinks(String markdown) {
        Set<String> titles = new LinkedHashSet<>();
        if (markdown == null || markdown.isBlank()) {
            return titles;
        }
        Matcher m = WIKI_LINK.matcher(markdown);
        while (m.find()) {
            String title = m.group(1).strip();
            if (!title.isEmpty()) {
                titles.add(title);
            }
        }
        return titles;
    }

    static String slugify(String title) {
        String decomposed = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFD);
        String noMarks = DIACRITICS.matcher(decomposed).replaceAll("");
        String slug = NON_SLUG.matcher(noMarks.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        return slug.isEmpty() ? "note" : slug;
    }

    /** Normalise a folder path: forward slashes, no leading/trailing/duplicate slashes ('' = root). */
    static String normalizeFolderPath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.strip().replace('\\', '/');
        p = p.replaceAll("/{2,}", "/");
        p = p.replaceAll("^/+", "").replaceAll("/+$", "");
        return p;
    }

    /** Normalise tags: drop leading '#', lowercase, trim, drop blanks, de-duplicate in order. */
    static Set<String> normalizeTags(Collection<String> tags) {
        Set<String> out = new LinkedHashSet<>();
        if (tags == null) {
            return out;
        }
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            String tag = raw.strip().replaceFirst("^#+", "").strip().toLowerCase(Locale.ROOT);
            if (!tag.isEmpty()) {
                out.add(tag);
            }
        }
        return out;
    }

    /** First {@code max} chars of the body with wiki-link brackets unwrapped and whitespace collapsed. */
    static String snippet(String markdown, int max) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String unwrapped = WIKI_LINK.matcher(markdown).replaceAll("$1");
        String flat = unwrapped.replaceAll("\\s+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max).strip() + "…";
    }
}
