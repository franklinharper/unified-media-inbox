# ADR 0008: Batch Twitter Feed Retrieval Across Followed Handles

## Status

Accepted

## Context

The project uses the official X API v2 for Twitter support.

Twitter API access is constrained by a low monthly request budget. A design that issues one request per followed handle scales poorly as the number of configured Twitter follows grows.

For example:

- retrieving items for one handle with one request is acceptable
- retrieving items for ten handles with ten separate requests is not acceptable when the same refresh could be represented as one batched query

The project needs to minimize request volume for Twitter feed refreshes wherever the official API allows batching.

## Decision

Twitter feed retrieval should batch multiple followed handles into a single API request whenever the official X API supports an equivalent batched query.

Repository and client design should prefer one Twitter request for N followed handles over N separate per-handle requests.

If batching requires a different query shape than the current single-source loading model, the code should evolve to support a Twitter-specific batched retrieval path rather than preserving a per-handle request pattern for architectural convenience.

## Consequences

- the current per-handle request approach is not the desired long-term design for Twitter
- Twitter repository and client integration should be optimized for request-budget efficiency first
- future implementation work may need to batch configured Twitter follows before calling the Twitter client
- if the official API places hard limits on query size or batching semantics, the implementation should use the smallest number of requests allowed by those limits
