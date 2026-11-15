# Hermes Mobile — Model Provider & Offline Fallback Contract

## Purpose
Codifies the multi-tier model provider cascade and offline resiliency strategy.

---

## 1. Provider Tier Cascade

| Tier | Target | Provider / Endpoint | Trigger Condition |
|---|---|---|---|
| **Tier 1 (Primary)** | `gemini-3.8-flash` | Cloud API / Antigravity SDK | Default online state with active network connectivity |
| **Tier 2 (High Reasoning)** | `gemini-4-hydrogen-high` | Cloud API / Antigravity SDK | Delegated coding tasks & architecture design |
| **Tier 3 (Local Offline Fallback)** | `gemma-2-2b-it` | Local `llama-server` (`http://127.0.0.1:8080/v1`) | Network offline or connection timeout (> 15s) |

---

## 2. Dynamic Switching Behavior
- When `ConnectivityManager` emits offline, Hermes automatically swaps active provider to Tier 3 without blocking the UI.
- While on Tier 3 (offline local), heavy tool calls (web search, subagent delegations) are queued or cleanly declined with a friendly message.
- When network connectivity returns, the agent restores Tier 1/2 routing seamlessly.
