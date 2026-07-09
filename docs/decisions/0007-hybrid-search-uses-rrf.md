# 0007 - Hybrid Search Uses Reciprocal Rank Fusion

## Status

Accepted.

## Context

Northstar search combines lexical note retrieval and semantic vector retrieval.
Those retrievers produce incompatible score scales: PostgreSQL full-text ranking
and pgvector cosine similarity cannot be safely averaged without query-specific
normalization and evaluation data.

The original RRF paper by Cormack, Clarke, and Buettcher reports that rank
fusion is a strong unsupervised baseline and uses `k = 60`. Current production
engines also expose the same pattern: Elasticsearch uses `rank_constant` and
`rank_window_size`, Qdrant exposes RRF over prefetched dense/sparse queries, and
LanceDB defaults hybrid search reranking to RRF while allowing stronger
rerankers later.

## Decision

Use Reciprocal Rank Fusion as Northstar's default hybrid search combiner:

- retrieve separate lexical and semantic candidate windows;
- fuse by rank contribution, not raw score;
- use the standard `k = 60` constant;
- use deterministic tie-breaks for stable results;
- keep the final result limit separate from each retriever's candidate window.

Lexical retrieval uses PostgreSQL `simple` full-text configuration because the
vault is mixed English/Vietnamese, with title trigram fallback for typo-tolerant
named-thing lookup.

## Consequences

- Search stays robust without a labeled relevance dataset.
- RRF remains replaceable: if Northstar later has query logs and relevance
  labels, a tuned weighted fusion or learned reranker can be added after the
  candidate stage.
- Semantic vectors are derived data. Archived notes are removed from the vector
  index on reindex/status changes, while durable note state remains in the note
  table.
