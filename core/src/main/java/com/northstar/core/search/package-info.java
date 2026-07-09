/**
 * Hybrid search: keyword (tsvector) + fuzzy title matching (pg_trgm) + semantic
 * retrieval (pgvector), fused with reciprocal rank fusion. The vector index is
 * derived and disposable.
 */
package com.northstar.core.search;
