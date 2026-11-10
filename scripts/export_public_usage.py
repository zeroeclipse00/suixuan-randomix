"""Publish only allowlisted aggregate token counts; keep source records local."""
from collections import defaultdict
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / "docs" / "vibe-coding"
DESTINATION = ROOT / "docs" / "process" / "token-summary.md"
FIELDS = ("input_tokens", "cached_input_tokens", "output_tokens", "reasoning_output_tokens", "total_tokens")


def main():
    snapshot = json.loads((PRIVATE / "snapshot.json").read_text(encoding="utf-8"))
    rows = [json.loads(line) for line in (PRIVATE / "usage.jsonl").read_text(encoding="utf-8").splitlines() if line]
    unique = {(row["thread_id"], row["response_id"]): row for row in rows}
    if len(unique) != len(rows):
        raise SystemExit("Duplicate usage records; run local synchronization before exporting.")
    if len(rows) != snapshot["record_count"]:
        raise SystemExit("Local snapshot and usage records disagree; synchronize again.")
    counts = defaultdict(int)
    totals = defaultdict(lambda: defaultdict(int))
    missing = defaultdict(lambda: defaultdict(int))
    for row in rows:
        stage = row.get("stage_id", "UNCLASSIFIED")
        if stage not in {f"ST-{i:02d}" for i in range(8)}:
            stage = "UNCLASSIFIED"
        counts[stage] += 1
        for field in FIELDS:
            value = row.get("usage", {}).get(field)
            if isinstance(value, int) and not isinstance(value, bool) and value >= 0:
                totals[stage][field] += value
            else:
                missing[stage][field] += 1
    def cell(stage, field):
        value, absent = totals[stage][field], missing[stage][field]
        return str(value) if not absent else f"已知 {value}，缺 {absent} 条"
    lines = ["# Token 用量公开快照", "",
             "> 自动生成的聚合统计；不包含原始对话、会话标识或个人路径。", "",
             f"- 已观察用量截止（UTC）：{snapshot.get('last_usage_at_utc') or '未知'}。",
             "- 来源为本地逐响应用量，已去重并覆盖可访问的主流程及关联辅助代理。",
             "- 当前回合尚可能新增用量；最终回复及迟到记录在后续同步补齐。本表不是最终结算。",
             "- 输入包含缓存输入，输出包含推理输出；分项不再加入总量。上下文的重复处理也计入输入 Token。",
             "- 旧选题讨论、无法访问的外部模型和其他工具费用不在本表覆盖范围，未知不视为零。", "",
             "| 阶段 | 已观察响应数 | 输入 | 其中缓存输入 | 输出 | 其中推理输出 | 总 Token |",
             "|---|---:|---:|---:|---:|---:|---:|"]
    for stage in sorted(counts):
        lines.append(f"| {stage} | {counts[stage]} | " + " | ".join(cell(stage, field) for field in FIELDS) + " |")
    lines += ["", "阶段编号对应[实施阶段](../implementation.md)。原始明细留在本地，公开摘要可随后续开发更新。", "",
              "口径参考：[OpenAI 用量说明](https://developers.openai.com/api/docs/guides/agents-api/observability)。Token 数量不能直接等同于现金费用。", ""]
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    DESTINATION.write_text("\n".join(lines), encoding="utf-8")
    print(f"Exported {len(rows)} observed responses in {len(counts)} stages; no raw identifiers exported.")


if __name__ == "__main__":
    main()
