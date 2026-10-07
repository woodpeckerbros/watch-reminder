# Zmanio — Morning Smart Wake Night Report

Analyze the requested/latest Smart Wake night using the log-source selection rules below.

Do NOT change code.
Do NOT commit or push.
Do NOT tune thresholds or recommend algorithm changes until after presenting the factual report.

The purpose is to produce the SAME structured report every morning so multiple nights can be compared directly.

## LOG SOURCE SELECTION

Use this priority order.

### Priority 1 — files supplied in the current Codex conversation

If the user attached, uploaded, dragged in, or otherwise supplied one or more log files in the CURRENT conversation/task, those files are the primary source.

Do NOT ignore attached/current-task files and silently substitute older workspace logs.

If one attached file contains the relevant Smart Wake session:

→ analyze that file.

If multiple attached files are supplied:

→ inspect timestamps/content and determine which belong to the requested night/user.
→ use all relevant files when needed.

### Priority 2 — workspace logs

Only when NO relevant log file was supplied in the current conversation/task:

search the workspace for the newest relevant Zmanio / Smart Wake log file(s).

Do not use workspace logs merely because they are easier to find if the user already supplied a file.

### Identify files before analysis

Before the actual morning report, print a short section named `LOG_FILES_ANALYZED`.

For every file include:

- filename
- user/device identity if identifiable, e.g. Refael or Moti
- detected Smart Wake session date
- session start/end time if available
- whether the file came from `CURRENT_USER_SUPPLIED_FILE` or `WORKSPACE_FALLBACK`

Do not begin conclusions before confirming which files are being analyzed.

Example:

```text
LOG_FILES_ANALYZED

Refael_Log.txt
source=CURRENT_USER_SUPPLIED_FILE
session_date=2026-10-06
user=Refael
```

### Wrong-night protection

If an attached/current-task file contains no Smart Wake session, belongs to a clearly different night, appears truncated before the relevant session, or belongs to a different user than requested, report that explicitly.

Do NOT silently fall back to another older file unless the user did not supply any usable current file.

If ambiguity can be resolved from timestamps/content, resolve it yourself rather than asking unnecessarily.

### Do not mix nights or users

Never combine different Smart Wake nights into one report unless the user explicitly requests comparison.

If several files belong to the same night/session, combining them is allowed.

If two files contain different users from the same morning, such as `Refael_Log` and `Moti_Log`, keep their data clearly separated.

### Two-user shorthand

When the user says something equivalent to `נתח את לוגי הלילה של שנינו` or `נתח את הלוגים של רפאל ומוטי` and two relevant current-task files are supplied:

- analyze both
- produce separate Smart Wake reports for Refael and Moti
- then add a short comparison section
- never mix their sensor/evaluation data

For the comparison, focus on:

- candidate windows
- wake outcome
- convergence
- distinct movement episodes
- scheduler timing
- final classification

## 1. SESSION SUMMARY

Report:

- Smart Wake session start
- monitoring start
- earliest allowed wake
- final deadline
- actual Smart Wake/alarm time
- final decision reason

Report total evaluations.

Report number of evaluations in:

- NORMAL
- WATCHING
- CANDIDATE

Report counts of:

- candidate creations
- candidate confirmations
- candidate cancellations

Report counts of:

- WAKE_LIGHT_SLEEP_OPPORTUNITY
- WAKE_TEMPORAL_MULTI_SENSOR_CONFIRMATION
- WAKE_ALREADY_AWAKE
- WAKE_FINAL_DEADLINE

Report:

- clearly_awake=true count

## 2. COMPACT TIMELINE

Produce a compact timeline containing only meaningful transitions.

Format:

timestamp
→ WAKE_SCORE
→ groups
→ candidate
→ clearly_awake
→ decision
→ decision reason

Include:

- candidate creation
- candidate cancellation
- WAKEABILITY_RISING
- important score/group changes
- clearly_awake changes
- actual WAKE
- final deadline

Merge repetitive quiet evaluations.

## 3. EVERY CANDIDATE WINDOW

For every candidate report:

- creation timestamp
- origin score/groups
- candidate duration
- maximum score
- maximum groups
- why it was created
- why it was confirmed OR why it failed/cancelled
- exact missing confirmation condition if it failed

Also report counts during the candidate:

- HR_NEW_SINCE_LAST_EVAL
- MOVEMENT_NEW_SINCE_LAST_EVAL
- STEP_NEW_SINCE_LAST_EVAL

Do not describe NEW_SINCE_LAST_EVAL as "recent" unless the source age actually supports that.

## 4. EVIDENCE RECENCY

For every important candidate / WAKE evaluation report:

- HR_AGE_MS
- MOVEMENT_AGE_MS
- STEP_AGE_MS
- HEALTH_STATE_AGE_MS where available

Report:

- HR_RECENT_5S
- HR_RECENT_15S
- HR_RECENT_30S

- MOVEMENT_RECENT_5S
- MOVEMENT_RECENT_15S
- MOVEMENT_RECENT_30S

Report:

- CURRENT_EVIDENCE_GROUP_COUNT
- NEW_SINCE_LAST_EVAL_GROUP_COUNT
- RECENT_5S_GROUP_COUNT
- RECENT_15S_GROUP_COUNT
- RECENT_30S_GROUP_COUNT

The distinction between:

NEW_SINCE_LAST_EVAL

and:

RECENT_NOW

is critical.

## 5. CROSS-MODAL TIMING

For every important candidate / WAKE evaluation report:

- CROSS_MODAL_TIME_GAP_MS
- CROSS_MODAL_ORDER
- CROSS_MODAL_CONVERGENCE

Possible convergence values:

- 5S
- 15S
- 30S
- NONE
- NO_DATA

Highlight evaluations where:

CURRENT_EVIDENCE_GROUP_COUNT=2

but:

RECENT_15S_GROUP_COUNT < 2

or:

RECENT_30S_GROUP_COUNT < 2

These cases are especially important because they mean two production evidence groups existed, but the evidence did not occur close together in real time.

## 6. MOVEMENT RENEWAL SHADOW METRICS

Report session maxima and important candidate-window values for:

- movement episodes in 60s
- movement episodes in 90s
- existing 15-second movement segmentation
- gap-based movement episodes if available
- movement renewal count
- cross-modal renewal count
- movement reuse metric
- maximum shadow transition state
- timestamp of maximum shadow transition state

Do NOT interpret hundreds of active sensor samples as hundreds of movement episodes.

Distinguish:

one continuous movement burst

from:

multiple distinct movement episodes separated by quiet periods.

## 7. HR TEMPORAL BEHAVIOR

For the strongest 3 candidate windows report:

- HR current
- HR baseline
- HR delta
- HR slope
- number of fresh HR updates in 60s/90s if available
- HR trend direction
- HR trend consistency

Do not rely only on peak HR delta.

## 8. MOVEMENT TEMPORAL BEHAVIOR

For the strongest 3 candidate windows report:

- accelerometer activity
- gyroscope activity
- burst counts
- movement clusters
- active buckets
- strong buckets
- microMovementCount
- latest movement event timestamp
- number of DISTINCT movement episodes

Explicitly distinguish:

one large burst

from:

several separate movement episodes over time.

## 9. EVALUATION CADENCE / SCHEDULER

Report:

NORMAL:
- target interval
- actual average interval
- average lateness
- maximum lateness

WATCHING:
- target interval
- actual average interval
- average lateness
- maximum lateness

CANDIDATE:
- target interval
- actual average interval
- average lateness
- maximum lateness

Also report:

- MAX_EVALUATION_DURATION_MS
- EVALUATION_OVERRUN_COUNT
- possible suspend-gap telemetry

Report recorded isWakeUpSensor() values for:

- accelerometer
- gyroscope
- step detector
- direct HR fallback if present

Explicitly state whether the real night approximately achieved the intended:

30s / 10s / 5s cadence

or not.

## 10. TOP THREE MOST INTERESTING WINDOWS

Choose the three most informative windows of the night.

For each provide a compact forensic table with:

- timestamp
- score/groups
- HR age
- movement age
- HR_NEW_SINCE_LAST_EVAL
- MOVEMENT_NEW_SINCE_LAST_EVAL
- recent groups 5s/15s/30s
- cross-modal gap
- convergence
- movement episodes
- candidate state
- wakeability state
- decision

Then explain in 2–4 sentences why the window matters.

## 11. PRE-WAKE WINDOW

If Smart Wake fired before deadline:

Analyze the final 90 seconds before WAKE in detail.

Determine:

- what first started the transition
- whether HR and movement converged closely in time
- how many genuinely distinct movement episodes occurred
- how many fresh HR updates occurred
- whether confirmation relied on repeated old evidence or genuinely renewed evidence
- exact reason WAKE was finally allowed

If the alarm reached final deadline instead:

Analyze the final 5 minutes before deadline.

Identify the strongest missed candidate and explain exactly why it did not confirm.

## 12. EXTERNAL DISTURBANCES

Search available logs for obvious external disturbances near important Smart Wake windows, including:

- phone calls
- notifications
- other alarms
- Zmanio reminders
- screen wake
- user interaction
- charging / disconnection if relevant

List exact timestamps where available.

Do NOT automatically attribute sensor evidence to an external event unless timing supports it.

Distinguish:

PRE_DISTURBANCE evidence

from:

POST_DISTURBANCE evidence

using source-event timestamps whenever possible.

## 13. SESSION-END SHADOW SUMMARY

Report:

- MIN_CROSS_MODAL_TIME_GAP_MS
- highest convergence level
- COUNT_CROSS_MODAL_WITHIN_5S
- COUNT_CROSS_MODAL_WITHIN_15S
- COUNT_CROSS_MODAL_WITHIN_30S
- COUNT_GROUPS_2_BUT_ONLY_ONE_SOURCE_RECENT_15S
- COUNT_GROUPS_2_BUT_ONLY_ONE_SOURCE_RECENT_30S
- maximum distinct movement episodes 60s
- maximum distinct movement episodes 90s
- maximum cross-modal renewals
- maximum shadow state
- timestamp of maximum shadow state

## 14. GROUND TRUTH

At the END of the report include:

USER_REPORTED_WAKE_TIME = UNKNOWN

USER_REPORTED_STATE_AT_WAKE = UNKNOWN

USER_REPORTED_EXTERNAL_DISTURBANCE = UNKNOWN

Do NOT infer these values from sensor data.

They will be supplied manually afterward.

## 15. FINAL CLASSIFICATION

End with only the relevant classifications:

A. Smart Wake likely caught a real developing transition.

B. Smart Wake fired, but evidence was externally contaminated.

C. Interesting transition occurred but current model did not confirm it.

D. Mostly isolated sleep activity / insufficient evidence.

E. Final deadline only, with no meaningful candidate.

F. Already-awake detection failure.

More than one classification may apply if different parts of the night support different conclusions.

For every selected classification provide exact supporting timestamps.

Do NOT recommend threshold or algorithm changes unless a clear implementation bug is found.

## 16. MISSING DATA RULE

If information is not present in the logs:

write:

NO_DATA

Do not reconstruct missing sensor values from assumptions.

Do not infer exact sleep stage.

Do not claim LIGHT / DEEP / REM from HR and wrist movement.

## 17. OUTPUT STYLE

Keep the report detailed but structured.

Use tables for candidate/window comparison where useful.

Do not dump every quiet evaluation.

Focus on meaningful transitions.

Always distinguish:

sensor event timestamp
callback receipt timestamp
evaluation timestamp

where those values are available.
