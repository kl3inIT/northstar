package com.northstar.core.brief;

/** External ranked-news source; provider wire types never cross this boundary. */
public interface BriefFeedProvider {

    BriefFeed feed();

    BriefStoryDetail story(String topic, String slug);
}
