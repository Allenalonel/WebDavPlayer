# Decompose Session God Class: Feature Map

## Specification

See [spec.md](./spec.md)

## ADR References

- [ADR-0018: Unify System Playback Intent Routing Through App Session](../../docs/adr/0018-unify-system-playback-intent-routing-through-app-session.md)
- [ADR-0023: Internal Seams and Session Decomposition](../../docs/adr/0023-internal-seams-and-session-decomposition.md)

## Task Graph

```mermaid
flowchart TD
    T1["01: Extract SessionPersistenceCoordinator"] --> T2["02: Extract PlaybackQueueManager"]
    T2 --> T3["03: Extract PlaybackFaultTolerance"]
    T3 --> T4["04: Consolidate Session Orchestrator & Verify Budget"]
```

## Tickets

- [ ] [01: Extract SessionPersistenceCoordinator](./issues/01-extract-session-persistence-coordinator.md)
- [ ] [02: Extract PlaybackQueueManager](./issues/02-extract-playback-queue-manager.md)
- [ ] [03: Extract PlaybackFaultTolerance](./issues/03-extract-playback-fault-tolerance.md)
- [ ] [04: Consolidate Session Orchestrator & Verify Budget](./issues/04-consolidate-session-orchestrator-and-verify-budget.md)
