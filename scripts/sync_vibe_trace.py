"""Export visible project dialogue and observed token usage from read-only Codex logs.

Only files marked generated under docs/vibe-coding are replaced. No packages needed.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "vibe-coding"
TOKEN_FIELDS = (
    "input_tokens", "cached_input_tokens", "cache_write_input_tokens",
    "output_tokens", "reasoning_output_tokens", "total_tokens",
)
TZ = timezone(timedelta(hours=8))


def read_rows(path):
    rows = []
    with path.open(encoding="utf-8") as stream:
        for number, line in enumerate(stream, 1):
            try:
                item = json.loads(line)
            except json.JSONDecodeError:
                # A running session can end with a not-yet-complete JSON line.
                continue
            rows.append((number, item))
    return rows


def first_meta(path):
    """Read metadata only when deciding whether a session belongs to this project."""
    try:
        with path.open(encoding="utf-8") as stream:
            line = stream.readline()
        item = json.loads(line)
        return item.get("payload", {}) if item.get("type") == "session_meta" else {}
    except (OSError, json.JSONDecodeError):
        return {}


def text_of(payload):
    return "\n".join(
        part.get("text", "") for part in payload.get("content", [])
        if isinstance(part, dict) and part.get("type") in ("input_text", "output_text", "text")
    ).strip()


def visible_user(text):
    # Submission metadata is transported as user messages but is not dialogue.
    if not text or text.startswith(("<environment_context>", "<external_codex_apps_")):
        return ""
    if "## My request:" in text:
        return text.split("## My request:", 1)[1].strip()
    return text


def local_time(value):
    if not value:
        return "未知"
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(TZ).isoformat(timespec="seconds")


def parse_session(path, own_id, allowed_root_turns, boundary_line=None):
    rows = read_rows(path)
    own_usage_turns = {
        d.get("payload", {}).get("turn_id") for _, d in rows
        if d.get("type") == "token_usage_record"
        and d.get("payload", {}).get("thread_id") == own_id
    }
    is_root = boundary_line is not None
    current = None
    contexts = {}
    messages = []
    usages = []
    completed = set()
    tools = []
    for line, item in rows:
        payload = item.get("payload", {})
        kind = item.get("type")
        if kind == "turn_context":
            turn = payload.get("turn_id")
            root_turn = payload.get("root_turn_id", turn)
            # Child logs include a forked copy of the parent conversation.
            # Only contexts supported by that child's own usage are exported.
            current = payload if is_root or turn in own_usage_turns else None
            if current and root_turn in allowed_root_turns:
                contexts[turn] = {
                    "turn_id": turn, "root_turn_id": root_turn,
                    "model": payload.get("model", "unknown"),
                }
        if kind == "token_usage_record":
            if payload.get("thread_id") != own_id:
                continue
            if payload.get("root_turn_id", payload.get("turn_id")) not in allowed_root_turns:
                continue
            if is_root and line < boundary_line:
                continue
            usage = payload.get("usage")
            if not isinstance(usage, dict):
                continue
            usages.append({
                "source_file": path.name, "source_line": line,
                "timestamp": item.get("timestamp"), "thread_id": own_id,
                "turn_id": payload.get("turn_id"),
                "root_turn_id": payload.get("root_turn_id", payload.get("turn_id")),
                "response_id": payload.get("response_id"),
                "agent": "root" if is_root else "subagent",
                "model": contexts.get(payload.get("turn_id"), {}).get("model", "unknown"),
                "usage": usage,
                "turn_token_usage_snapshot": payload.get("turn_token_usage"),
                "thread_token_usage_snapshot": payload.get("thread_token_usage"),
                "measurement": "observed_local_usage_record",
            })
        if not current or current.get("root_turn_id", current.get("turn_id")) not in allowed_root_turns:
            continue
        if is_root and line < boundary_line:
            continue
        if kind == "event_msg" and payload.get("type") == "task_complete":
            if payload.get("turn_id") == current.get("turn_id"):
                completed.add(payload["turn_id"])
        if kind == "response_item" and payload.get("type") == "message":
            role = payload.get("role")
            channel = payload.get("channel")
            if role not in ("user", "assistant") or channel not in (None, "commentary", "final"):
                continue
            text = text_of(payload)
            if role == "user":
                text = visible_user(text)
            if not text:
                continue
            messages.append({
                "timestamp": item.get("timestamp"), "role": role,
                "channel": channel, "text": text, "thread_id": own_id,
                "turn_id": current.get("turn_id"),
                "root_turn_id": current.get("root_turn_id", current.get("turn_id")),
                "source_file": path.name, "source_line": line,
            })
        if kind == "response_item" and payload.get("type") in ("function_call", "custom_tool_call"):
            tools.append({
                "timestamp": item.get("timestamp"), "name": payload.get("name"),
                "call_id": payload.get("call_id"), "thread_id": own_id,
                "turn_id": current.get("turn_id"), "source_file": path.name,
                "source_line": line,
            })
    return messages, usages, completed, tools, contexts


def collect(registry, sessions_dir):
    root_id = registry["root_thread_id"]
    candidates = list(sessions_dir.rglob(f"*{root_id}.jsonl"))
    if len(candidates) != 1:
        raise SystemExit(f"Expected one source log for {root_id}; found {len(candidates)}. Use --sessions-dir.")
    source = candidates[0]
    root_rows = read_rows(source)
    marker = registry["tracking_start"]["user_message_prefix"]
    boundary = next((line for line, d in root_rows if d.get("type") == "response_item"
                     and d.get("payload", {}).get("role") == "user"
                     and text_of(d["payload"]).startswith(marker)), None)
    if boundary is None:
        raise SystemExit("Tracking boundary user message was not found; refusing to include older usage.")
    allowed = {
        d.get("payload", {}).get("root_turn_id", d.get("payload", {}).get("turn_id"))
        for line, d in root_rows if line >= boundary and d.get("type") == "turn_context"
    }
    allowed.add(registry["tracking_start"]["root_turn_id"])
    messages, usages, completed, tools, contexts = parse_session(source, root_id, allowed, boundary)
    sources = [{"file": source.name, "thread_id": root_id, "kind": "root"}]
    # Read only first-line metadata to identify descendants. Unrelated transcripts
    # are never loaded. An old agent may be reused in a newly tracked root turn,
    # so creation time is not a valid exclusion criterion.
    related = {root_id}
    pending = []
    for path in sessions_dir.rglob("*.jsonl"):
        if path == source:
            continue
        meta = first_meta(path)
        origin = meta.get("source")
        spawn = origin.get("subagent", {}).get("thread_spawn", {}) if isinstance(origin, dict) else {}
        parent = meta.get("parent_thread_id") or spawn.get("parent_thread_id")
        if parent:
            pending.append((path, meta, parent))
    while pending:
        progress = False
        for path, meta, parent in pending[:]:
            if parent not in related:
                continue
            identity = meta.get("id")
            if not identity or identity in related:
                pending.remove((path, meta, parent))
                continue
            # Traverse confirmed lineage even when an intermediate parent has
            # no usage in the tracked turn but one of its descendants does.
            related.add(identity)
            result = parse_session(path, identity, allowed)
            child_messages, child_usages, child_done, child_tools, child_contexts = result
            if child_usages:
                messages.extend(child_messages)
                usages.extend(child_usages)
                completed.update(child_done)
                tools.extend(child_tools)
                contexts.update(child_contexts)
                sources.append({"file": path.name, "thread_id": identity,
                                "kind": "subagent", "agent_path": meta.get("agent_path")})
            pending.remove((path, meta, parent))
            progress = True
        if not progress:
            break
    # Responses can be replayed in logs. Keep the latest observation per response.
    unique = {}
    for row in usages:
        key = (row["thread_id"], row.get("response_id") or (row["source_file"], row["source_line"]))
        unique[key] = row
    usages = sorted(unique.values(), key=lambda row: (row.get("timestamp", ""), row["thread_id"]))
    messages.sort(key=lambda row: (row.get("timestamp", ""), row["thread_id"] != root_id,
                                   row["thread_id"], row["source_line"]))
    for row in usages:
        stage = registry.get("turn_stages", {}).get(row["root_turn_id"], {})
        row["stage_id"] = stage.get("stage_id", "UNCLASSIFIED")
        row["work_id"] = stage.get("work_id")
    return messages, usages, completed, tools, sources, contexts


def write_outputs(registry, result):
    messages, usages, completed, tools, sources, contexts = result
    OUT.mkdir(parents=True, exist_ok=True)
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    last = max((r["timestamp"] for r in usages if r.get("timestamp")), default=None)
    for name, rows in (("usage.jsonl", usages), ("tool-index.jsonl", tools)):
        (OUT / name).write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")
    preamble = [
        "# Vibe coding 可见对话记录", "",
        "> 自动生成。手工补充请写入 work-log.md；重新同步会替换本文件。", "",
        f"- 导出时间：{local_time(now)}。",
        f"- 正式记录边界：{local_time(registry['tracking_start']['timestamp_utc'])} 的用户请求。",
        "- 仅包含用户文字、助手可见回复及关联子代理可见回复；不导出系统指令、内部思考、工具原文或图片数据。",
        "- 原始日志中的图片不复制，必要时在人工轨迹中另行引用截图。",
        "- 日志仍可能继续写入；若本轮尚未结束，本次最终回复会在下一次同步中补齐。", "",
    ]
    for number, row in enumerate(messages, 1):
        agent = "主线程" if row["thread_id"] == registry["root_thread_id"] else "子代理"
        role = ("用户" if agent == "主线程" else "委派任务") if row["role"] == "user" else "助手"
        preamble += [f"## D{number:04d} {role} {agent}", "",
                     f"- 时间：{local_time(row['timestamp'])}",
                     f"- turn：`{row['turn_id']}`",
                     f"- 来源：`{row['source_file']}:{row['source_line']}`", "", row["text"], ""]
    (OUT / "conversation.md").write_text("\n".join(preamble), encoding="utf-8")
    groups = defaultdict(lambda: {field: 0 for field in TOKEN_FIELDS})
    missing = defaultdict(lambda: {field: 0 for field in TOKEN_FIELDS})
    counts = defaultdict(int)
    anomalies = []
    for row in usages:
        group = (row["stage_id"], row["agent"])
        counts[group] += 1
        usage = row["usage"]
        if usage.get("total_tokens") != usage.get("input_tokens", 0) + usage.get("output_tokens", 0):
            anomalies.append(row.get("response_id"))
        for field in TOKEN_FIELDS:
            value = usage.get(field)
            if isinstance(value, int):
                groups[group][field] += value
            else:
                missing[group][field] += 1
    lines = ["# Token 用量快照", "", "> 自动生成，不是最终结算。重新同步会替换本文件。", "",
             f"- 导出时间：{local_time(now)}。",
             f"- 最近可用用量记录：{local_time(last)}。",
             f"- 来源：本地逐响应 `token_usage_record.payload.usage`，按线程和 response_id 去重，共 {len(usages)} 条。",
             "- 记录开始之前的调用全部排除；本轮调用可能仍在进行，当前数值只是已观察到的下界。",
             "- input 包含 cached input；output 包含 reasoning output。缓存和推理列仅作分项展示，不再加到总计。",
             "- 每条记录中的 turn/thread 累计快照仅保留核验，绝不求和。未提供的数据不能视作 0 消耗。",
             "- 本表按阶段拆分主线程与关联子代理；其他工具、外部模型与不可访问的代理用量尚未覆盖，需另行补录。", "",
             "| 阶段 | 执行者 | 响应记录数 | 输入 | 其中缓存输入 | 输出 | 其中推理输出 | 总 Token |",
             "|---|---|---:|---:|---:|---:|---:|---:|"]
    overall = {field: 0 for field in TOKEN_FIELDS}
    missing_overall = {field: 0 for field in TOKEN_FIELDS}
    def display(value, absent):
        return str(value) if not absent else f"已知 {value}，缺 {absent} 条"
    for (stage, agent), values in sorted(groups.items()):
        cells = [display(values[field], missing[(stage, agent)][field]) for field in
                 ("input_tokens", "cached_input_tokens", "output_tokens", "reasoning_output_tokens", "total_tokens")]
        lines.append(f"| {stage} | {agent} | {counts[(stage, agent)]} | " + " | ".join(cells) + " |")
        for field in TOKEN_FIELDS:
            overall[field] += values[field]
            missing_overall[field] += missing[(stage, agent)][field]
    cells = [display(overall[field], missing_overall[field]) for field in
             ("input_tokens", "cached_input_tokens", "output_tokens", "reasoning_output_tokens", "total_tokens")]
    lines += [f"| 合计 | 已观察范围 | {len(usages)} | " + " | ".join(cells) + " |", "", "## 覆盖与状态", ""]
    for source in sources:
        lines.append(f"- `{source['thread_id']}`（{source['kind']}）：`{source['file']}`。")
    for turn, context in contexts.items():
        if any(row["turn_id"] == turn for row in usages):
            state = "已观察到结束事件" if turn in completed else "未观察到结束事件，仍可能新增用量和回复"
            lines.append(f"- turn `{turn}`：{state}；模型 `{context['model']}`。")
    lines += ["", f"字段一致性检查：{'存在异常，需查看原始记录' if anomalies else '已观察记录均满足 total = input + output'}。",
              "", "口径参考：[OpenAI Observability and usage](https://developers.openai.com/api/docs/guides/agents-api/observability)。该文档用于解释分项包含关系；本项目的具体值以保存的本地 usage 原值为证据。", ""]
    (OUT / "token-summary.md").write_text("\n".join(lines), encoding="utf-8")
    snapshot = {"generated_at_utc": now, "last_usage_at_utc": last,
                "record_count": len(usages), "totals_observed": overall,
                "missing_field_counts": missing_overall,
                "sources": sources, "completed_turns": sorted(completed),
                "scope": "observed_since_tracking_boundary", "anomalies": anomalies}
    (OUT / "snapshot.json").write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return snapshot


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sessions-dir", type=Path, default=Path.home() / ".codex" / "sessions")
    parser.add_argument("--registry", type=Path, default=OUT / "stage-registry.json")
    args = parser.parse_args()
    registry = json.loads(args.registry.read_text(encoding="utf-8"))
    snapshot = write_outputs(registry, collect(registry, args.sessions_dir))
    print(json.dumps({"records": snapshot["record_count"], "observed_total_tokens": snapshot["totals_observed"]["total_tokens"],
                      "sources": len(snapshot["sources"]), "last_usage_at_utc": snapshot["last_usage_at_utc"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
