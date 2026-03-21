# ADR 0005: Use Official X API v2 Only For Twitter/X Support

## Status

Accepted

## Context

Twitter/X support has multiple implementation paths in the ecosystem. Many open source read clients use guest-token flows, undocumented internal APIs, browser cookies, or reverse-engineered mobile or web endpoints.

Those approaches are common, but they are brittle and create higher maintenance and platform-risk costs.

## Decision

Twitter/X support in this project will use the official X API v2 only.

The project will not implement Twitter/X support using:

- guest-token flows
- undocumented internal APIs
- browser cookie authentication
- reverse-engineered mobile endpoints
- reverse-engineered web endpoints

## Consequences

- Twitter/X support should be more stable and easier to reason about.
- The project accepts official API pricing, access, and rate-limit constraints.
- Some open source client techniques are intentionally excluded from scope.
