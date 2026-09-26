# Phone: setup and operation, step by step

This is the full walkthrough for letting a Melaya agent operate the user's Android phone. Each step says what to call, what to tell the user, what success looks like and what to do if it fails.

## What the user needs first

| Requirement | Why | How to check |
|---|---|---|
| A Melaya account with a model connected | Phone agents still think with a model (a cloud key, or a model on the runner). Nothing runs without one. | `melaya_model_list` or `melaya_setup_status` |
| An Android phone | iPhone cannot be driven: iOS does not let any app read or tap inside other apps. The iOS Melaya app is a workspace app only. | Ask the user |
| The Melaya Android app, signed in to the same account | It is what reads the screen and taps. | `melaya_phone_devices` after pairing |
| The local runner with Claude Code signed in | ONLY for `melaya_run_phone_agent`. Not needed to drive the phone from this chat. | `melaya_runner_status` |

## Step 1. Check what is already done

1. Call `melaya_setup_status`.
2. Call `melaya_phone_status` (cheap): is a phone paired, reachable now, and which apps are allowed.
3. If paired, online and the needed app is allowed, go to "Operating the phone".

Say to the user: "Let me check what is already set up on your Melaya account."

## Step 2. Pair the phone

1. Call `melaya_phone_pair`. Optional `label` (for example "Work Pixel").
2. You get an 8-character code (valid 5 minutes, single use) and an install link.
3. Tell the user, in these words or close:
   - "On your phone, open this link: <link>. It downloads the Melaya app directly, not from the Play Store, so Android will ask whether to allow installs from this source. That is expected; allow it."
   - "Open the Melaya app and enter this code: <code>. If the app is already installed, the link may pair it for you without the code."
   - "Please enter the code in the phone app, not here in the chat."
4. Success: `melaya_phone_devices` lists the phone with a recent "last seen" time.
5. Failure: the code expired (5 minutes) -> call `melaya_phone_pair` again. Pairing screen errors inside the app -> ask the user to update or reinstall the app from the same link.

On the web, the same pairing is available in **Device Control -> Pair a phone** (a QR code the phone scans).

## Step 3. Allow restricted settings (Android 13 and later)

Android protects the accessibility switch of apps installed outside the Play Store. Until this is done, the switch in step 4 is greyed out.

Tell the user:
1. "On the phone, open Settings -> Apps -> Melaya (App info). The Melaya setup screen has a button that opens it for you."
2. "Tap the three-dot menu in the top-right corner."
3. "Tap **Allow restricted settings** and confirm."

If they reinstall the app later, this may need doing again.

## Step 4. Turn on the Melaya accessibility service

Tell the user:
1. "Open Settings -> Accessibility. On some phones it is under System or Additional settings."
2. "Open Installed services (or Downloaded apps)."
3. "Tap Melaya (it may be called Melaya Phone Control) and switch it ON. Accept the permissions it asks for."

Why: this is how the agent reads the screen and taps. The app cannot switch it on by itself. The Melaya setup screen does not let the user continue until it is on.

## Step 5. Keep the phone reachable when the screen is off

Tell the user: "When Melaya asks to ignore battery optimisation, choose Allow. If you missed it: Settings -> Apps -> Melaya -> Battery -> Unrestricted (the wording varies by phone brand)."

Why: without this, Android's power saving (Doze) cuts the app's network when the phone sleeps, and every command times out as if the phone were off.

Also good to know:
- When a task starts, Melaya wakes the screen so it can see the apps.
- A PIN, pattern or fingerprint lock **cannot** be opened by the agent. For unattended jobs, the user decides whether to use a dedicated phone with a simpler lock; never suggest weakening security on a personal phone without saying what it means.
- Some phone brands kill background apps aggressively. If the phone keeps dropping offline, ask the user to also allow the app to run in the background / auto-start in the brand's own battery settings.

## Step 6. Approve the apps the agent may use

Pairing grants nothing. The agent can only open or read apps the user approves.

1. Tell the user: "Open Device Control -> Apps (on the web at https://app.melaya.org or in the Melaya app), find the apps you want me to use, and switch them on."
2. Call `melaya_phone_apps` to confirm: every installed app with an `allowed` flag.
3. You cannot add an app. That is deliberate: the agent reads text from the screen that could try to talk it into more access.
4. To take access away, call `melaya_phone_restrict_apps` with the package ids that should REMAIN (read them from `melaya_phone_apps` first; anything omitted loses access). To remove everything: empty `packages` plus `confirm_revoke_all: true`.

The rule is enforced on the phone itself: a non-approved app returns `app_not_allowed` even if a server is bypassed.

## Operating the phone (driving it from this chat)

### The loop

```
playbook (once per app) -> open -> READ -> act on one element -> READ again -> next step
```

| Step | Tool | Notes |
|---|---|---|
| Know the app | `melaya_phone_playbook` with `app` | Navigation map, stable ids, known traps. Omit `app` to list apps that have one. A pipeline agent also receives it automatically the first time it looks at an app. |
| Open | `melaya_phone_open` with `app` or `url` (exactly one) | The target must be approved. Read the screen after: apps often restore their last screen. |
| Read | `melaya_phone_screen` | Header `app=<package> nodes=<n>`, then one line per element: index, class, `tap`/`edit`, "text", ~description, #resource-id, bounds. |
| Read cheaply | `melaya_phone_current_app` | Only which app is in front. |
| Read visually | `melaya_phone_screenshot` | When the tree is empty or unhelpful (games, video, image posts, unlabelled icons). Apps that block screenshots (banking, protected video) cannot be captured. |
| Click by label or id | `melaya_phone_click` with `text` or `resource_id` | The most reliable action. `mode: "tap"` taps the element's centre if a normal click does nothing. |
| Type | `melaya_phone_type` with `text` | Focus the field first (click it). `clear_first` replaces content. `submit: true` presses Enter, which SENDS in chat apps. |
| Scroll | `melaya_phone_scroll` with `direction`, `amount` | No coordinates needed. |
| System keys | `melaya_phone_navigate` with `to` | `back`, `home`, `recents`, `notifications`. Back is the safe way out of a wrong screen. |
| Tap a position | `melaya_phone_tap` with `x`, `y`, optional `gesture` (`tap`, `double`, `long`) | Only for targets with no text or id. Positions are screenshot pixels or 0-1 fractions. Double-tap likes media in most social apps; long-press opens menus. |
| Swipe | `melaya_phone_swipe` with `x1,y1,x2,y2` | Dismiss cards, precise gestures. Prefer scroll for feeds. |
| Drag and drop | `melaya_phone_drag_hold` with `x1,y1,x2,y2` (optional `hold_ms`, `move_ms`, `pauses`, `waypoints`) | Press, hold until the item lifts, then move. For reordering icons, timeline clips, list rows. Raise `hold_ms` if the item never lifts. |
| Wait | `melaya_phone_wait` with `seconds` (0.5 to 20) | After loads and animations. |
| Short known sequence | `melaya_phone_batch` with `steps` | Each step `{do, args, expect?, settle_ms?}`; `do` is one of open_app, tap, long_press, double_tap, swipe, scroll, click_text, click_id, input_text, clear_text, paste, press_enter, home, back; `expect` is `{app}`, `{text_visible}` or `{editable_focused: true}`. Aborts when an expectation fails. Not for exploring, never for publishing. |
| Publish | `melaya_phone_publish` with `kind` (`comment`/`post`) and `text` | Always shows an approval card on the phone. See below. |
| Stop | `melaya_phone_stop` | Halts everything, removes the Melaya overlay, gives the phone back; further actions are refused for a few minutes. |

### Rules of thumb

- Prefer element text or id over coordinates: ids survive layout changes, positions do not.
- An element marked as covered by another view should not be tapped by position; scroll it into view or click it by text or id.
- One action, then verify. If the screen is not what you expected, press Back and re-read rather than tapping on.
- A command that times out while the phone is alive is usually a slow wake-up: retry the SAME call once before concluding anything. If it still fails, call `melaya_phone_wake`.
- Human pace. Rapid-fire actions on social apps look like a bot.

### Publishing and approvals on the phone

- `melaya_phone_publish` (and, in pipelines, `phone_post_comment` / `phone_create_post`) always stages a card on the phone showing the draft. The user can edit the text, approve, or reject with a reason; the agent receives the reason and should revise, not repost the same thing.
- Only one approval can be pending at a time. A second publish is refused until the first is decided.
- If the phone is locked or asleep, the card arrives as a discreet notification with Approve / Reject; the user can also decide from the Melaya web app.
- In `safe` mode, the phone also asks before payments and before risky taps such as share, repost, save, follow and "not interested". Liking is not gated.
- `melaya_approval_list` shows what is waiting across runs. It cannot approve. Tell the user where to decide.

### Waking a sleeping phone

Call `melaya_phone_wake` (optional `wait_seconds`, default 6) as soon as a device tool times out or reports the phone unreachable, and before a long sequence on an idle phone.

| Result | Meaning | Say to the user |
|---|---|---|
| `awake` | Ready | Nothing; retry the action. |
| `already_awake` | It was not asleep | The failure is something else; check `melaya_phone_status` and the app allow-list. |
| `locked` | Reachable but locked | "Your phone is locked with a PIN or fingerprint. Please unlock it; I cannot do that for you." |
| `still_unreachable` | Did not come back | "Please pick up your phone, open the Melaya app, and check it has internet." |
| `no_token` | This phone cannot be woken remotely | "Please open the Melaya app on the phone once; keep battery optimisation off for it." |
| `disabled` | Remote wake is off | Ask the user to keep the phone awake while the task runs. |

Waking never unlocks the phone or grants anything.

## Delegating: `melaya_run_phone_agent`

Use it for long or repetitive phone work that should continue after the conversation ("go through my Instagram DMs and summarise them"). For short or exploratory tasks, driving directly is faster and the user sees each step.

1. Check `melaya_setup_status`: the runner must be connected and Claude Code signed in on that computer. If not, call `melaya_runner_setup`; with shell access on the user's own computer run the returned command in the background, otherwise give it to the user to run on the computer that will host the runner. The command carries a live credential: do not write it to a file or repeat it later. Poll `melaya_runner_status` (the first start takes up to a minute). A connected runner with no Claude Code models means the user must run `claude` once to sign in, then restart the runner.
2. Call `melaya_run_phone_agent` with:
   - `instruction`: a clear brief with the goal and what "done" looks like, including what NOT to do (for example "read only, do not reply to anyone");
   - `hitl_mode`: leave `safe` (default) unless the user explicitly asked for unattended operation; `payments_only` gates only purchases; `autonomous` removes every on-device approval card;
   - optional `model` (`sonnet` default, `opus`, `fable`, `haiku`), `name`, `project` (default `Melaya-Agents`).
3. It returns a run id. Poll `melaya_run_status` and read `outcome`. While it runs, the phone shows a small Melaya overlay the user can use to stop it.
4. To stop: `melaya_run_cancel` with `run_id` and `pipeline`, AND `melaya_phone_stop` to halt the device immediately.

## Revoking

| The user wants | Do |
|---|---|
| Stop the current activity | `melaya_phone_stop` |
| Stop the agent touching one app | `melaya_phone_restrict_apps` without that package, or the user switches it off in Device Control -> Apps |
| Stop all app access but keep the pairing | `melaya_phone_restrict_apps` with `packages: []` and `confirm_revoke_all: true` |
| The phone was lost or sold | `melaya_phone_revoke_device` with the `device_id` from `melaya_phone_devices`. Only when the user explicitly asks; the phone must be paired again from scratch. |
| Pause everything temporarily | The user switches the Melaya accessibility service off on the phone |
