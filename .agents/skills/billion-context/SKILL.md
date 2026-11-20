---
name: billion-context
description: Manage long context windows, token reduction, and multi-generational session compression using Active Context Pruning (ACP) and hierarchical compression principles from billion-context. Use when long-running agent sessions blow up context, token optimization is needed, or configuring the bili proxy / context management pipeline.
allowed-tools: Read, Write, Edit, Glob, Grep, Command
---

# billion-context Skill

Context-compression and long-session memory architecture based on [`ranxianglei/billion-context`](https://github.com/ranxianglei/billion-context).

`billion-context` (`bili`) provides model-driven, incremental, hierarchical compression using **Active Context Pruning (ACP)** and Log-Structured Merge (LSM)-style context management. It enables coding agents to operate across month-long sessions (billions of tokens) within tight context windows (e.g. 100K tokens) while cutting token usage up to 5x.

---

## Core Architecture & Mechanisms

### 1. Two Compression Modes
- **Plugin Mode (`x-bili-plugin` / registered agent):**
  - The ACP-native agent owns compression.
  - The model calls `compress` directly, executed locally.
  - The wire summary carrier is the tool call itself; raw history never carries the kernel's intermediate summary message.
- **Proxy Mode (Transparent upstream bridge):**
  - The `bili` proxy sits between the client and LLM API (OpenAI/Anthropic wire format).
  - Proxy executes compression server-side; the tool call is ephemeral and not saved to host history.
  - The carrier is an `acp_summary` message re-voiced to role `user` at its anchor position to preserve byte stability for prefix caches.

### 2. The Four ACP Context-Management Tools
Injected dynamically into the model prompt when active:
1. **`compress`**: Fold an older message index/range into a detailed, structured summary.
2. **`decompress`**: Restore a previously compressed range when exact code or context details are required.
3. **`search_context`**: Fast keyword and semantic search over compressed summaries and visible messages.
4. **`acp_status`**: Diagnostic report on context utilization, tokens saved, and foldable ranges.

### 3. Key Design Invariants
- **Prefix-Cache Friendliness:** Healthy sessions maintain a **96–99%** prefix-cache hit rate. In-place rewriting respects anchor points and avoids cache invalidation.
- **Thinking / Signature Blocks are Sacred:** Byte-for-byte exact replay is mandatory for Anthropic `thinking`/`redacted_thinking` and Gemini `thoughtSignature` parts. Never strip, edit, or summarize active signature blocks.
- **Nudge Cadence:** The standard growth interval before prompting the model to consider folding history is pinned at 50,000 tokens (`compress.nudgeGrowthTokens`).
- **Message Identity Invariance:** Message IDs and reference pointers (`m00001`...) are immutable and strictly unique within a session; never reuse or re-index ref tags.

---

## Operating and Configuring `bili`

### Installation
```bash
# Global install (recommended)
npm install -g billion-context

# Or run directly via npx / launcher
npx billion-context
```

### CLI Commands
- `bili`: Start the local proxy server (defaults to port 8080 or configured port).
- `bili doctor`: Diagnostic check of proxy status, ports, and configuration integrity.
- `bili status`: Display active session cache hit-rates, storage state, and token savings.

### Configuration (`~/.config/billion-context/`)
Configuration files govern compression thresholds, providers, and storage:
- `providers.yaml`: Upstream endpoint routing and API keys.
- `compress.yaml`: Custom nudge thresholds (`nudgeGrowthTokens`), minimum compressible range size, and compression model choices.

---

## When & How to Apply This Skill

1. **Context Bloat Diagnostics:** When an agent session approaches token limits or begins degrading in reasoning quality, run compaction or summarize completed milestones into structured anchors.
2. **Session Persistence:** When designing multi-turn workflows or long-lived tasks, structure memory using hierarchical layers:
   - **Active Turn / Scratchpad:** Verbatim code and recent outputs.
   - **Recent Milestone:** Key technical decisions, file changes, and current blockages.
   - **Compressed History:** Compact architectural summaries with pointers to files on disk.
3. **Prompt & Tool Injection:** For projects implementing custom agent proxies or long-context harnesses, refer to the specification in `references/` for protocol and identity handling.
