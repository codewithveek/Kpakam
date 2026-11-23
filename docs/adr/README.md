# Architecture decision records

Each record explains one important choice: the problem, the decision, and what
it costs. Write a new record when you make a choice that a new team member
would question. Do not edit an accepted record; write a new one that replaces it.

| No.                                              | Decision                                         | Status   |
| ------------------------------------------------ | ------------------------------------------------ | -------- |
| [0001](0001-custom-hybrid-router.md)             | Kpakam picks device or cloud for each request    | Accepted |
| [0002](0002-client-only-firebase.md)             | No custom server: apps talk to Firebase directly | Accepted |
| [0003](0003-integer-kobo.md)                     | All money is integer kobo                        | Accepted |
| [0004](0004-immutable-journals-and-reversals.md) | Journals never change; corrections are reversals | Accepted |
| [0005](0005-shared-contract-package.md)          | One contract package for both apps               | Accepted |
| [0006](0006-durable-ai-job-queue.md)             | Every AI request is a stored job first           | Accepted |
| [0007](0007-automatic-voice-language.md)         | The app works out the voice language             | Accepted |
| [0008](0008-load-firebase-after-first-paint.md)  | Load Firebase after the first screen paints      | Accepted |
| [0009](0009-plain-language-ui.md)                | Plain words in the app, no technical terms       | Accepted |

Template: copy any record, keep the four headings (Status, Context, Decision,
Consequences), and add it to this table.
