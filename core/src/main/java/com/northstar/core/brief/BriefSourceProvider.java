package com.northstar.core.brief;

/** Delivery adapter that discovers public candidates for one Morning Brief run. */
public interface BriefSourceProvider {

    String id();

    String displayName();

    default boolean configured() {
        return true;
    }

    BriefSourceResult collect(BriefCollectionRequest request);
}
