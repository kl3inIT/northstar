package com.northstar.worker.brief;

import static com.northstar.worker.brief.BriefSourceSupport.matchesTopics;
import static com.northstar.worker.brief.BriefSourceSupport.plainText;
import static com.northstar.worker.brief.BriefSourceSupport.recent;

import com.northstar.core.brief.BriefCandidate;
import com.northstar.core.brief.BriefCollectionRequest;
import com.northstar.core.brief.BriefKind;
import com.northstar.core.brief.BriefSourceProvider;
import com.northstar.core.brief.BriefSourceResult;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
class RssBriefSource implements BriefSourceProvider {

    private static final Set<String> OFFICIAL_HOSTS = Set.of(
            "openai.com", "github.blog", "spring.io", "react.dev", "inside.java");

    private final BriefHttpClient http;

    RssBriefSource(BriefHttpClient http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "rss";
    }

    @Override
    public String displayName() {
        return "RSS & Atom";
    }

    @Override
    public BriefSourceResult collect(BriefCollectionRequest request) {
        if (request.feedUrls().isEmpty()) return BriefSourceResult.of(List.of());
        int concurrency = Math.min(4, request.feedUrls().size());
        List<FeedOutcome> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency, Thread.ofVirtual().factory())) {
            List<Callable<FeedOutcome>> calls = request.feedUrls().stream()
                    .<Callable<FeedOutcome>>map(url -> () -> collectFeed(url, request)).toList();
            outcomes = executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    return new FeedOutcome(List.of(), exception);
                }
            }).toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Feed collection was interrupted", exception);
        }
        long failed = outcomes.stream().filter(outcome -> outcome.failure() != null).count();
        if (failed == outcomes.size()) throw new IllegalStateException("Every configured RSS/Atom feed failed");
        List<BriefCandidate> items = outcomes.stream().flatMap(outcome -> outcome.items().stream())
                .limit(request.maxItems() * 3L).toList();
        return new BriefSourceResult(items, Map.of(
                "feeds", request.feedUrls().size(),
                "failedFeeds", failed));
    }

    private FeedOutcome collectFeed(String value, BriefCollectionRequest request) {
        try {
            URI uri = URI.create(value);
            String xml = http.get(uri, "application/atom+xml,application/rss+xml,application/xml,text/xml", Map.of());
            Document document = parse(xml);
            String feedTitle = firstText(document.getDocumentElement(), "title");
            if (feedTitle.isBlank()) feedTitle = uri.getHost();
            NodeList entries = document.getElementsByTagName("item");
            if (entries.getLength() == 0) entries = document.getElementsByTagNameNS("*", "entry");
            List<BriefCandidate> items = new ArrayList<>();
            for (int index = 0; index < entries.getLength() && index < 12; index++) {
                Element entry = (Element) entries.item(index);
                String title = childText(entry, "title");
                String link = link(entry);
                String author = firstNonBlank(childText(entry, "creator"), childText(entry, "author"), feedTitle);
                String rawSummary = firstNonBlank(childText(entry, "description"), childText(entry, "summary"),
                        childText(entry, "content"));
                Instant publishedAt = firstInstant(childText(entry, "pubDate"), childText(entry, "published"),
                        childText(entry, "updated"));
                String searchable = title + " " + rawSummary;
                if (title.isBlank() || link.isBlank() || !recent(publishedAt, request.since())
                        || !matchesTopics(searchable, request.topics())) continue;
                BriefKind kind = official(uri.getHost()) ? BriefKind.OFFICIAL : BriefKind.PEOPLE;
                items.add(new BriefCandidate(kind, title, link, plainText(rawSummary, 480),
                        feedTitle, author, publishedAt, 0));
            }
            return new FeedOutcome(items, null);
        } catch (Exception exception) {
            return new FeedOutcome(List.of(), exception);
        }
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String link(Element entry) {
        NodeList links = entry.getElementsByTagNameNS("*", "link");
        if (links.getLength() == 0) links = entry.getElementsByTagName("link");
        for (int index = 0; index < links.getLength(); index++) {
            Element link = (Element) links.item(index);
            String href = link.getAttribute("href").strip();
            String rel = link.getAttribute("rel").strip();
            if (!href.isBlank() && (rel.isBlank() || "alternate".equals(rel))) return href;
            String text = link.getTextContent().strip();
            if (!text.isBlank()) return text;
        }
        return "";
    }

    private static String childText(Element parent, String localName) {
        NodeList children = parent.getElementsByTagNameNS("*", localName);
        if (children.getLength() == 0) children = parent.getElementsByTagName(localName);
        if (children.getLength() == 0) return "";
        Node node = children.item(0);
        if ("author".equals(localName) && node instanceof Element author) {
            String name = childText(author, "name");
            if (!name.isBlank()) return name;
        }
        return node.getTextContent() == null ? "" : node.getTextContent().strip();
    }

    private static String firstText(Element root, String localName) {
        NodeList values = root.getElementsByTagNameNS("*", localName);
        if (values.getLength() == 0) values = root.getElementsByTagName(localName);
        return values.getLength() == 0 ? "" : values.item(0).getTextContent().strip();
    }

    private static Instant firstInstant(String... values) {
        for (String value : values) {
            Instant instant = BriefSourceSupport.instant(value);
            if (instant != null) return instant;
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.strip();
        return "";
    }

    private static boolean official(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase();
        return OFFICIAL_HOSTS.stream().anyMatch(value -> normalized.equals(value) || normalized.endsWith("." + value));
    }

    private record FeedOutcome(List<BriefCandidate> items, Exception failure) {
    }
}
