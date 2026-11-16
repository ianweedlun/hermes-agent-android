# Hermes Mobile — Cross-Node & Subagent Delegation Contract

## Purpose
Codifies the delegation rules, roles, message formats, and execution boundaries between the primary mobile host (Pixel 10 Pro Fold), remote mesh nodes (MacBook Air / developer machines), and local subagents executing inside the PRoot environment.

---

## 1. Node Topology & Roles

| Node / Agent | Role | Capabilities | Primary Execution Scope |
|---|---|---|---|
| **Primary Host (Pixel 10 Pro Fold)** | Orchestrator & Local Agent | On-device sensors, native UI, notifications, mobile bridge | Mobile workflows, telemetry capture, local inference/tools, user interaction |
| **Mesh Peer (MacBook Air / Remote Host)** | Heavy Compute & Build Worker | Full SDK/NDK toolchain, desktop AVD emulator lab, CI/Git operations | Compilations, automated emulator verification, multi-arch packaging, heavy git/CI pipelines |
| **In-App Subagent (PRoot Local)** | Isolated Task Worker | Headless Python/CLI runtime, sandboxed workspace | Ephemeral scripts, local data transformation, file formatting, batch analysis |

---

## 2. Cross-Node Delegation Protocol (`ai-memory` Bus)

Cross-node tasks (e.g. Pixel 10 Pro Fold ↔ MacBook Air) communicate asynchronously over Tailscale via the `ai-memory` message broker on port `49374`.

### A. Delegation Dispatch Schema (`mcp__ai_memory__memory_message_send`)
When delegating a task to a peer node:
```json
{
  "to_workspace": "default",
  "to_project": "workspace",
  "subject": "Task Delegation: <Task Title>",
  "body": "Detailed actionable instructions, deliverable requirements, and verification criteria.",
  "nature": "measured",
  "evidence": "Commit SHA, branch name, or specific log reference",
  "from_agent": "hermes-mobile" | "hermes-macbook",
  "from_workspace": "default",
  "from_project": "workspace"
}
```

### B. Autonomous Consumption & Execution Loop
1. **Continuous Watcher:** Nodes run background watchers (`hermes-mesh-watcher.sh`) polling `memory_message_list` every 15s.
2. **Claim & Lock:** The receiving node pops the message with `memory_message_pop`, locking the task ID.
3. **Headless Execution:** Watcher triggers autonomous execution via `hermes chat -q "<instruction>"` without requiring human prompt intervention.
4. **Handshake & Receipt:** Upon completion, the worker node dispatches a completion message via `memory_message_send` referencing the branch, PR, or test artifact bundle.

---

## 3. Local Subagent Delegation (PRoot / In-App)

Within the PRoot daemon on Android:
1. **Concurrency Cap:** At most 1 active subagent process on mobile hardware to prevent out-of-memory (OOM) kills and thermal throttling.
2. **Resource Boundaries:**
   - Subagents must respect CPU limits and release wakelocks upon completion.
   - Heavy builds or continuous emulators must **never** run inside the mobile PRoot; they must be delegated over the mesh to the remote workstation.
3. **Tier Gating:** If the device falls back to Tier 3 (`gemma-2-2b-it` offline) or battery is < 15% without AC power, subagent spawning is deferred or cleanly rejected per `MODEL-FALLBACK-CONTRACT.md` and `LIFECYCLE-CONTRACT.md`.

---

## 4. Guarantees & Constraints

1. **Durable Auditing:** All cross-node delegations, task assignments, and completion proofs must pass through `ai-memory` as persistent records.
2. **No Silent Drops:** If a node cannot fulfill a delegated task (e.g. missing dependencies, emulator boot failure), it must send an explicit failure response back to the originator inbox.
3. **Branch & PR Isolation:** Delegated work modifying code repositories must take place on dedicated feature branches (`feat/...`, `fix/...`) with automated CI/lab checks before merging.
