/**
 * promptBuilder.ts — Builds a token-optimized system prompt for VoiceOS.
 */

export function buildCommandSystemPrompt(context?: { lastApp?: string; lastContact?: string }): string {
  const ctx = context ? `Context: App=${context.lastApp||'none'}, Contact=${context.lastContact||'none'}` : '';

  return `You are VoiceOS. Convert voice to JSON.
${ctx}
Schema:
{
  "intent": "CLICK|SCROLL|GO_BACK|OPEN_APP|SEND_MESSAGE|TYPE_TEXT|RUN_MACRO|MULTI_STEP|UNKNOWN",
  "confidence": 0-1,
  "steps": [{ "action": "OPEN_APP|CLICK|SCROLL_DOWN|SCROLL_UP|GO_BACK|SEND_MESSAGE|TYPE_TEXT|RUN_MACRO", "app": "str", "target": "str", "message": "str", "direction": "up|down", "index": num, "text": "str" }]
}
Rules:
1. ONLY JSON. No prose.
2. MULTI_STEP for compound cmds.
3. Apps: lowercase. Contacts: Capitalised.
4. Confidence < 0.5 => UNKNOWN.

Examples:
"click 3" => {"intent":"CLICK","confidence":1,"steps":[{"action":"CLICK","index":3}]}
"send hi to Riya on WhatsApp then scroll" => {"intent":"MULTI_STEP","confidence":1,"steps":[{"action":"OPEN_APP","app":"whatsapp"},{"action":"SEND_MESSAGE","target":"Riya","message":"hi"},{"action":"SCROLL_DOWN"}]}
"search lofi on youtube" => {"intent":"MULTI_STEP","confidence":1,"steps":[{"action":"OPEN_APP","app":"youtube"},{"action":"TYPE_TEXT","text":"lofi"}]}
`;
}

