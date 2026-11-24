# Prompts

Each file starts with a version header (`id`, `task`, `output`). The body is
everything after the closing `---`, trimmed.

## Assembly (both apps do exactly this)

- **Parse:** `body(parse-v2)` + `"\n\nNote:\n<<<\n"` + `note` + `"\n>>>"`.
  Before insertion, remove every `<<<` and `>>>` from the note, repeating until
  none remain (one pass can create a new delimiter: `<<>>><` → `<<<`), so the
  trader's text cannot close the delimited section.
- **Retry:** the parse prompt + `"\n\nYour last JSON:\n"` + previous output
  (trimmed to 2000 chars) + `"\n\n"` + `body(parse-retry-v1)` with `{{errors}}`
  replaced by one error per line, each starting with `- `.
- **Voice and receipt:** `body(voice-v1)` or `body(receipt-v1)` with
  `{{parseRules}}` replaced by `body(parse-v2)`. The audio or image is sent as
  an inline data part after the text part.
- **Summary and insights:** replace `{{totals}}` or `{{week}}` with compact JSON.
  These contain totals and item names only. Customer names appear only in
  insights ("Who to follow up"), which is a cloud call the trader starts.

The prompt version that produced each draft is stored on the journal
(`source.promptVersion`) and in `aiEvents`.
