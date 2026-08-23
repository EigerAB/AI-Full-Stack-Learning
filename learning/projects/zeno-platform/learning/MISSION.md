# Mission

## Why I'm learning this

I'm a frontend-strong engineer who is rusty on the backend side (especially FastAPI/HTTP layer). I work on the ZenFlux Agent platform (`nwaip` monorepo) and need to **troubleshoot AI-chain problems** end-to-end: when a chat reply is wrong, a tool isn't called, or routing misfires, I want to trace from the HTTP entry point all the way into the Agent loop / workflow engine and pinpoint where it broke — not just hand it off blind.

## What "done" looks like

- Given a symptom ("agent didn't call the tool", "reply was empty", "workflow node errored"), I can name the layer and the file where the failure most likely lives, and read the code to confirm.
- I can read a backend stack trace from the HTTP layer down to `core/agent/` and follow it.
- I understand the two-phase chat protocol, the AgentLoop, the workflow DSL, and how tools/RAG/memory plug into a turn — well enough to debug them, not necessarily to write them from scratch.

## Scope priority

1. **Agent orchestration + workflow** (AgentLoop, Workflow DSL, node executors, SubAgent, Team) — primary
2. **FastAPI/HTTP entry layer** (routes, SSE, JWT, tenant deps) — secondary, needed to *enter* the chain
3. LLM call path, routing, tools, RAG, memory — touched as they cross the orchestration path

## Out of scope (for now)

- Writing new backend features from scratch
- gRPC face (the browser/admin path is HTTP-only)
- Infra/persistence tuning

## How I want to be taught

- I'm frontend-fluent — skip generic web concepts, focus on what's *different* in this codebase.
- Read code, don't just describe it. Point me at real files and line ranges.
- Lessons should end with a "where would X fail?" retrieval check, not a vocabulary quiz.
