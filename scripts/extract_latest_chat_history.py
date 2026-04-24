#!/usr/bin/env python3

from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import subprocess
import xml.etree.ElementTree as xml_et
from collections import Counter
from pathlib import Path
from typing import Any


DEFAULT_PACKAGE = "com.chaomixian.vflow"
DEFAULT_PREFS_NAME = "chat_session_prefs"
LEGACY_PREFS_NAMES = ("module_config_prefs",)
DEFAULT_STATE_KEY = "chat_session_state_json"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Extract the latest vFlow chat history record and emit structured review data."
    )
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument(
        "--xml",
        help="Path to a local SharedPreferences XML file such as chat_session_prefs.xml.",
    )
    source.add_argument(
        "--adb-serial",
        help="ADB serial used to fetch the chat session SharedPreferences XML via run-as.",
    )
    parser.add_argument("--package", default=DEFAULT_PACKAGE, help="Android package name.")
    parser.add_argument(
        "--prefs-name",
        default=DEFAULT_PREFS_NAME,
        help=(
            "SharedPreferences file name without .xml suffix. "
            "Default prefers chat_session_prefs and falls back to module_config_prefs."
        ),
    )
    parser.add_argument(
        "--state-key",
        default=DEFAULT_STATE_KEY,
        help="SharedPreferences key that stores the serialized ChatSessionState JSON.",
    )
    parser.add_argument(
        "--conversation-id",
        help="Extract a specific conversation instead of choosing the latest one.",
    )
    parser.add_argument(
        "--select",
        choices=("updated", "active"),
        default="updated",
        help="How to choose the latest conversation when --conversation-id is omitted.",
    )
    parser.add_argument(
        "--format",
        choices=("json", "pretty", "both"),
        default="json",
        help="Output format. Default is json.",
    )
    parser.add_argument(
        "--max-content-chars",
        type=int,
        default=180,
        help="Maximum preview length for message and tool output fields.",
    )
    parser.add_argument(
        "--full-content",
        action="store_true",
        help="Include full message content instead of previews.",
    )
    parser.add_argument(
        "--output",
        help="Optional path to write the structured JSON payload.",
    )
    return parser


def run_adb(package: str, prefs_name: str, adb_serial: str) -> str:
    command = [
        "adb",
        "-s",
        adb_serial,
        "shell",
        "run-as",
        package,
        "cat",
        f"shared_prefs/{prefs_name}.xml",
    ]
    try:
        completed = subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        stderr = exc.stderr.strip()
        stdout = exc.stdout.strip()
        detail = stderr or stdout or str(exc)
        raise SystemExit(f"Failed to fetch SharedPreferences XML via adb: {detail}") from exc
    return completed.stdout


def prefs_candidates(args: argparse.Namespace) -> list[str]:
    if args.prefs_name != DEFAULT_PREFS_NAME:
        return [args.prefs_name]
    return [DEFAULT_PREFS_NAME, *LEGACY_PREFS_NAMES]


def load_xml_text(args: argparse.Namespace) -> tuple[str, dict[str, Any]]:
    if args.xml:
        xml_path = Path(args.xml).expanduser().resolve()
        if not xml_path.exists():
            raise SystemExit(f"SharedPreferences XML file does not exist: {xml_path}")
        return load_xml_text_from_file(xml_path, args)
    return load_xml_text_via_adb(args)


def load_xml_text_from_file(xml_path: Path, args: argparse.Namespace) -> tuple[str, dict[str, Any]]:
    xml_text = xml_path.read_text(encoding="utf-8")
    if has_state_key(xml_text, args.state_key):
        return xml_text, {
            "kind": "xml_file",
            "xmlPath": str(xml_path),
            "prefsName": xml_path.stem,
        }

    sibling_candidates = [
        xml_path.with_name(f"{prefs_name}.xml")
        for prefs_name in prefs_candidates(args)
        if prefs_name != xml_path.stem
    ]
    for sibling_path in sibling_candidates:
        if not sibling_path.exists():
            continue
        sibling_text = sibling_path.read_text(encoding="utf-8")
        if has_state_key(sibling_text, args.state_key):
            return sibling_text, {
                "kind": "xml_file",
                "xmlPath": str(sibling_path),
                "requestedXmlPath": str(xml_path),
                "prefsName": sibling_path.stem,
            }

    return xml_text, {
        "kind": "xml_file",
        "xmlPath": str(xml_path),
        "prefsName": xml_path.stem,
    }


def load_xml_text_via_adb(args: argparse.Namespace) -> tuple[str, dict[str, Any]]:
    attempts: list[str] = []
    for prefs_name in prefs_candidates(args):
        attempts.append(prefs_name)
        try:
            xml_text = run_adb(
                package=args.package,
                prefs_name=prefs_name,
                adb_serial=args.adb_serial,
            )
        except SystemExit:
            continue
        if has_state_key(xml_text, args.state_key):
            return xml_text, {
                "kind": "adb",
                "adbSerial": args.adb_serial,
                "package": args.package,
                "prefsName": prefs_name,
                "attemptedPrefsNames": attempts,
            }
    raise SystemExit(
        "Failed to find chat session state in SharedPreferences via adb. "
        f"Tried prefs: {', '.join(attempts) or args.prefs_name}."
    )


def has_state_key(xml_text: str, state_key: str) -> bool:
    try:
        root = xml_et.fromstring(xml_text)
    except xml_et.ParseError:
        return False
    return any(
        child.attrib.get("name") == state_key
        for child in root.findall("string")
    )


def parse_session_state(xml_text: str, state_key: str) -> dict[str, Any]:
    root = xml_et.fromstring(xml_text)
    for child in root.findall("string"):
        if child.attrib.get("name") == state_key:
            raw_json = html.unescape(child.text or "")
            if not raw_json.strip():
                raise SystemExit(f"State key `{state_key}` exists but is empty.")
            try:
                return json.loads(raw_json)
            except json.JSONDecodeError as exc:
                raise SystemExit(f"Failed to decode `{state_key}` JSON: {exc}") from exc
    raise SystemExit(f"Could not find `<string name=\"{state_key}\">` in SharedPreferences XML.")


def select_conversation(
    session_state: dict[str, Any],
    conversation_id: str | None,
    selection_mode: str,
) -> tuple[dict[str, Any], str]:
    conversations = session_state.get("conversations") or []
    if not conversations:
        raise SystemExit("No conversations were found in chat_session_state_json.")
    if conversation_id:
        for conversation in conversations:
            if conversation.get("id") == conversation_id:
                return conversation, "conversation_id"
        raise SystemExit(f"Conversation `{conversation_id}` was not found.")
    if selection_mode == "active":
        active_id = session_state.get("activeConversationId")
        if active_id:
            for conversation in conversations:
                if conversation.get("id") == active_id:
                    return conversation, "active_conversation"
    latest = max(
        conversations,
        key=lambda conversation: (
            safe_int(conversation.get("updatedAtMillis")),
            safe_int(conversation.get("createdAtMillis")),
        ),
    )
    return latest, "latest_updated_at"


def safe_int(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def ms_to_iso(timestamp_millis: Any) -> str | None:
    millis = safe_int(timestamp_millis)
    if millis <= 0:
        return None
    return dt.datetime.fromtimestamp(millis / 1000, tz=dt.timezone.utc).isoformat()


def shorten_text(value: Any, max_chars: int, full_content: bool) -> str:
    text = (value or "").replace("\r\n", "\n").strip()
    if full_content or len(text) <= max_chars:
        return text
    return text[: max_chars - 3] + "..."


def parse_json_string(raw: Any) -> Any:
    if not isinstance(raw, str) or not raw.strip():
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw


def build_tool_executions(messages: list[dict[str, Any]], max_chars: int, full_content: bool) -> list[dict[str, Any]]:
    pending_calls: dict[str, dict[str, Any]] = {}
    executions: list[dict[str, Any]] = []
    sequence = 0
    for message_index, message in enumerate(messages):
        tool_calls = message.get("toolCalls") or []
        if tool_calls:
            for tool_call in tool_calls:
                sequence += 1
                call_id = tool_call.get("id") or f"unknown_call_{sequence}"
                entry = {
                    "sequence": sequence,
                    "messageIndex": message_index,
                    "assistantMessageId": message.get("id"),
                    "requestedAtMillis": message.get("timestampMillis"),
                    "requestedAtIso": ms_to_iso(message.get("timestampMillis")),
                    "approvalState": message.get("toolApprovalState"),
                    "toolName": tool_call.get("name"),
                    "callId": call_id,
                    "arguments": parse_json_string(tool_call.get("argumentsJson")),
                    "result": None,
                }
                executions.append(entry)
                pending_calls[call_id] = entry
        tool_result = message.get("toolResult")
        if tool_result:
            result_call_id = tool_result.get("callId")
            result_payload = {
                "messageIndex": message_index,
                "toolMessageId": message.get("id"),
                "status": tool_result.get("status"),
                "summary": tool_result.get("summary"),
                "outputTextPreview": shorten_text(tool_result.get("outputText"), max_chars, full_content),
                "artifactHandles": [
                    artifact.get("handle")
                    for artifact in (tool_result.get("artifacts") or [])
                    if artifact.get("handle")
                ],
                "recordedAtMillis": message.get("timestampMillis"),
                "recordedAtIso": ms_to_iso(message.get("timestampMillis")),
            }
            matched = pending_calls.pop(result_call_id, None) if result_call_id else None
            if matched is not None:
                matched["result"] = result_payload
            else:
                sequence += 1
                executions.append(
                    {
                        "sequence": sequence,
                        "messageIndex": message_index,
                        "assistantMessageId": None,
                        "requestedAtMillis": None,
                        "requestedAtIso": None,
                        "approvalState": None,
                        "toolName": tool_result.get("name"),
                        "callId": result_call_id,
                        "arguments": None,
                        "result": result_payload,
                    }
                )
    return executions


def build_timeline(messages: list[dict[str, Any]], max_chars: int, full_content: bool) -> list[dict[str, Any]]:
    timeline: list[dict[str, Any]] = []
    for index, message in enumerate(messages):
        tool_calls = []
        for tool_call in message.get("toolCalls") or []:
            tool_calls.append(
                {
                    "id": tool_call.get("id"),
                    "name": tool_call.get("name"),
                    "arguments": parse_json_string(tool_call.get("argumentsJson")),
                }
            )
        tool_result = message.get("toolResult")
        timeline.append(
            {
                "index": index,
                "id": message.get("id"),
                "role": message.get("role"),
                "timestampMillis": message.get("timestampMillis"),
                "timestampIso": ms_to_iso(message.get("timestampMillis")),
                "contentPreview": shorten_text(message.get("content"), max_chars, full_content),
                "reasoningPreview": shorten_text(message.get("reasoningContent"), max_chars, full_content),
                "tokenCount": message.get("tokenCount"),
                "isPending": message.get("isPending"),
                "toolApprovalState": message.get("toolApprovalState"),
                "toolCalls": tool_calls,
                "toolResult": (
                    {
                        "callId": tool_result.get("callId"),
                        "name": tool_result.get("name"),
                        "status": tool_result.get("status"),
                        "summary": tool_result.get("summary"),
                        "outputTextPreview": shorten_text(tool_result.get("outputText"), max_chars, full_content),
                        "artifactHandles": [
                            artifact.get("handle")
                            for artifact in (tool_result.get("artifacts") or [])
                            if artifact.get("handle")
                        ],
                    }
                    if tool_result
                    else None
                ),
            }
        )
    return timeline


def find_repeated_tool_streaks(executions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    streaks: list[dict[str, Any]] = []
    current_name: str | None = None
    current_start = 0
    current_count = 0
    for execution in executions:
        tool_name = execution.get("toolName")
        if tool_name == current_name:
            current_count += 1
            continue
        if current_name is not None and current_count >= 2:
            streaks.append(
                {
                    "toolName": current_name,
                    "count": current_count,
                    "startSequence": current_start,
                    "endSequence": current_start + current_count - 1,
                }
            )
        current_name = tool_name
        current_start = execution.get("sequence") or 0
        current_count = 1 if tool_name else 0
    if current_name is not None and current_count >= 2:
        streaks.append(
            {
                "toolName": current_name,
                "count": current_count,
                "startSequence": current_start,
                "endSequence": current_start + current_count - 1,
            }
        )
    return streaks


def build_swipe_summary(executions: list[dict[str, Any]]) -> dict[str, Any]:
    swipe_executions = []
    direction_counts = Counter()
    for execution in executions:
        tool_name = execution.get("toolName") or ""
        if "swipe" not in tool_name and "scroll" not in tool_name:
            continue
        arguments = execution.get("arguments")
        direction = arguments.get("direction") if isinstance(arguments, dict) else None
        swipe_executions.append(
            {
                "sequence": execution.get("sequence"),
                "toolName": tool_name,
                "direction": direction,
                "status": (execution.get("result") or {}).get("status"),
            }
        )
        if direction:
            direction_counts[direction] += 1
    return {
        "count": len(swipe_executions),
        "byDirection": dict(direction_counts),
        "executions": swipe_executions,
    }


def build_signals(executions: list[dict[str, Any]]) -> list[str]:
    signals: list[str] = []
    repeated_streaks = find_repeated_tool_streaks(executions)
    for streak in repeated_streaks:
        if streak["count"] >= 3:
            signals.append(
                f"Repeated tool streak: `{streak['toolName']}` ran {streak['count']} times in a row "
                f"(sequence {streak['startSequence']}..{streak['endSequence']})."
            )
    swipe_summary = build_swipe_summary(executions)
    down_count = swipe_summary["byDirection"].get("down", 0)
    if down_count >= 2:
        signals.append(f"Detected {down_count} downward swipe/scroll executions in this conversation.")
    return signals


def build_structured_payload(
    args: argparse.Namespace,
    source_meta: dict[str, Any],
    session_state: dict[str, Any],
    conversation: dict[str, Any],
    selected_by: str,
) -> dict[str, Any]:
    messages = conversation.get("messages") or []
    timeline = build_timeline(
        messages=messages,
        max_chars=args.max_content_chars,
        full_content=args.full_content,
    )
    executions = build_tool_executions(
        messages=messages,
        max_chars=args.max_content_chars,
        full_content=args.full_content,
    )

    role_counts = Counter(message.get("role") for message in messages if message.get("role"))
    tool_names = [execution.get("toolName") for execution in executions if execution.get("toolName")]
    tool_status_counts = Counter(
        (execution.get("result") or {}).get("status")
        for execution in executions
        if execution.get("result") and (execution.get("result") or {}).get("status")
    )
    first_user_message = next((message for message in messages if message.get("role") == "USER"), None)
    latest_user_message = next((message for message in reversed(messages) if message.get("role") == "USER"), None)
    latest_assistant_message = next((message for message in reversed(messages) if message.get("role") == "ASSISTANT"), None)
    latest_tool_message = next((message for message in reversed(messages) if message.get("role") == "TOOL"), None)

    return {
        "source": {
            **source_meta,
            "stateKey": args.state_key,
            "selectedBy": selected_by,
            "extractedAtIso": dt.datetime.now(tz=dt.timezone.utc).isoformat(),
        },
        "session": {
            "conversationCount": len(session_state.get("conversations") or []),
            "activeConversationId": session_state.get("activeConversationId"),
        },
        "conversation": {
            "id": conversation.get("id"),
            "title": conversation.get("title"),
            "presetId": conversation.get("presetId"),
            "createdAtMillis": conversation.get("createdAtMillis"),
            "createdAtIso": ms_to_iso(conversation.get("createdAtMillis")),
            "updatedAtMillis": conversation.get("updatedAtMillis"),
            "updatedAtIso": ms_to_iso(conversation.get("updatedAtMillis")),
            "messageCount": len(messages),
        },
        "metrics": {
            "roleCounts": dict(role_counts),
            "toolCallCount": len(executions),
            "toolResultCount": sum(1 for execution in executions if execution.get("result") is not None),
            "uniqueTools": sorted(set(tool_names)),
            "toolStatusCounts": dict(tool_status_counts),
            "swipeSummary": build_swipe_summary(executions),
            "repeatedToolStreaks": find_repeated_tool_streaks(executions),
        },
        "derived": {
            "firstUserRequest": shorten_text(
                first_user_message.get("content") if first_user_message else "",
                args.max_content_chars,
                args.full_content,
            ),
            "latestUserMessage": shorten_text(
                latest_user_message.get("content") if latest_user_message else "",
                args.max_content_chars,
                args.full_content,
            ),
            "latestAssistantMessage": shorten_text(
                latest_assistant_message.get("content") if latest_assistant_message else "",
                args.max_content_chars,
                args.full_content,
            ),
            "latestAssistantReasoning": shorten_text(
                latest_assistant_message.get("reasoningContent") if latest_assistant_message else "",
                args.max_content_chars,
                args.full_content,
            ),
            "latestToolOutput": shorten_text(
                latest_tool_message.get("content") if latest_tool_message else "",
                args.max_content_chars,
                args.full_content,
            ),
            "signals": build_signals(executions),
        },
        "toolExecutions": executions,
        "timeline": timeline,
    }


def render_pretty(payload: dict[str, Any]) -> str:
    conversation = payload["conversation"]
    metrics = payload["metrics"]
    derived = payload["derived"]
    lines = [
        "=== Latest Chat History Review ===",
        f"conversation_id: {conversation['id']}",
        f"title: {conversation['title']}",
        f"selected_by: {payload['source']['selectedBy']}",
        f"updated_at: {conversation['updatedAtIso']}",
        f"message_count: {conversation['messageCount']}",
        f"tool_calls: {metrics['toolCallCount']}",
        f"unique_tools: {', '.join(metrics['uniqueTools']) or '(none)'}",
        f"first_user_request: {derived['firstUserRequest']}",
    ]
    if derived["signals"]:
        lines.append("signals:")
        lines.extend(f"  - {signal}" for signal in derived["signals"])
    if payload["toolExecutions"]:
        lines.append("tool_executions:")
        for execution in payload["toolExecutions"]:
            result = execution.get("result") or {}
            lines.append(
                "  - "
                f"seq={execution.get('sequence')} "
                f"name={execution.get('toolName')} "
                f"status={result.get('status') or 'PENDING'} "
                f"args={json.dumps(execution.get('arguments'), ensure_ascii=False)}"
            )
    lines.append("timeline:")
    for item in payload["timeline"]:
        header = f"  - [{item['index']}] {item['timestampIso']} {item['role']}"
        content = item.get("contentPreview") or item.get("reasoningPreview") or ""
        if content:
            header += f" :: {content}"
        lines.append(header)
        for tool_call in item.get("toolCalls") or []:
            lines.append(
                "      tool_call="
                f"{tool_call.get('name')} args={json.dumps(tool_call.get('arguments'), ensure_ascii=False)}"
            )
        tool_result = item.get("toolResult")
        if tool_result:
            lines.append(
                "      tool_result="
                f"{tool_result.get('name')} status={tool_result.get('status')} "
                f"summary={tool_result.get('summary')}"
            )
    return "\n".join(lines)


def main() -> int:
    args = build_parser().parse_args()
    xml_text, source_meta = load_xml_text(args)
    session_state = parse_session_state(xml_text, args.state_key)
    conversation, selected_by = select_conversation(
        session_state=session_state,
        conversation_id=args.conversation_id,
        selection_mode=args.select,
    )
    payload = build_structured_payload(
        args=args,
        source_meta=source_meta,
        session_state=session_state,
        conversation=conversation,
        selected_by=selected_by,
    )
    json_output = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output:
        output_path = Path(args.output).expanduser().resolve()
        output_path.write_text(json_output + "\n", encoding="utf-8")
    if args.format in {"pretty", "both"}:
        print(render_pretty(payload))
        if args.format == "both":
            print()
    if args.format in {"json", "both"}:
        print(json_output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
