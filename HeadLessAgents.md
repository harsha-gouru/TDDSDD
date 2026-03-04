Yes—if your agents/sub-agents are headless, you can still get “what happened” visibility by adding **observability** at the orchestrator level (traces/logs/metrics) plus optional OS-level file auditing (auditd/inotify/fanotify/eBPF) to see what files are being touched. OpenTelemetry-style distributed tracing is a good fit because it’s designed to debug complex, sometimes nondeterministic distributed behavior by recording parent/child work as spans in a single trace. [groundcover](https://www.groundcover.com/opentelemetry/opentelemetry-metrics)

## Trace agents like a distributed system
Model one “run” as a trace, and each agent/sub-agent step as a span (parent span = orchestrator, child spans = sub-agents). A span is a unit of work with timing plus structured attributes/events, and traces are the tree of spans that shows end-to-end execution flow. [groundcover](https://www.groundcover.com/opentelemetry/opentelemetry-metrics)

Practical schema that makes this usable:
- IDs: `run_id`, `trace_id`, `agent_id`, `parent_agent_id`, `task_id`, `attempt`, `workspace_root`.
- Span attributes: `agent.name`, `tool.name`, `repo`, `branch`, `prompt_hash` (or redacted), `model`, `cost`, `tokens`, `exit_status`. (This “attributes on spans” pattern is exactly what spans are meant for.) [groundcover](https://www.groundcover.com/opentelemetry/opentelemetry-metrics)

## Heartbeats and “what’s it doing”
Treat “heartbeat” as a metric + timestamped events: update `agent_last_seen_seconds` (gauge) and increment counters like `agent_step_started`, `agent_step_failed`, and `tool_calls_total`. Metrics are numeric aggregations over time (request rate, error rate, CPU, etc.), which makes them ideal for dashboards/alerts while traces answer “why did it do that?”. [groundcover](https://www.groundcover.com/opentelemetry/opentelemetry-metrics)

For “current activity” without a UI, emit an event every time state changes:
- `state=PLANNING → CODING → TESTING → FIXING → DONE`
- include `current_file`, `current_test`, `current_command`, and `reason` (short text)

## Seeing what files it touched
There are two complementary approaches—use both if you can.

- App-level (best for correctness): log file ops as span events whenever your agent code reads/writes/patches files, so you can attach *intent* (“writing to fix failing test”) to each file touch.
- OS-level (best for “even if the agent didn’t log it”): monitor filesystem events from the outside.

### OS-level options (Linux)
- inotify: monitors files/directories and returns structured filesystem events via a file descriptor you read from.  It can watch directories (and report events for files inside), but it is **not recursive** unless you add watches for subdirs yourself, and it provides no information about which process/user triggered the event. [opentelemetry](https://opentelemetry.io/docs/concepts/observability-primer/)
- fanotify: can notify on filesystem access and can include the PID that caused the event, and it can monitor mounts (helpful for whole workspaces).  It also has limits: it supports only a limited set of events and explicitly notes no support for create/delete/move events (use inotify for those). [honeycomb](https://www.honeycomb.io/blog/uniting-tracing-logs-open-telemetry-span-events)
- auditd: rule-based auditing that can watch paths for permissions like read/write/execute/attribute-change (e.g., `-w /path -p rwxa`) and then query logs with `ausearch`. [youtube](https://www.youtube.com/watch?v=BDPtfR2Gybs)
- eBPF (advanced): can trace file syscalls at the kernel level with low overhead compared to some traditional approaches, and can capture metadata like PID + filename for opens. [oneuptime](https://oneuptime.com/blog/post/2026-01-07-ebpf-file-access-monitoring/view)

## Yes, it’s dynamic—make traces your ground truth
Agent execution will be dynamic (branching, retries, tool failures, test feedback loops), so you want correlation keys everywhere: propagate `trace_id`/`run_id` into every sub-agent and tool invocation, and attach logs to spans so you can pivot from “run failed” → “which sub-agent” → “which tool call” → “which file changed”. Correlating logs inside spans (or with trace/span IDs) is specifically called out as how logs become much more useful for tracking execution. [groundcover](https://www.groundcover.com/opentelemetry/opentelemetry-metrics)

What environment are these headless agents running in (local dev, CI runner, or Kubernetes/containers)?
