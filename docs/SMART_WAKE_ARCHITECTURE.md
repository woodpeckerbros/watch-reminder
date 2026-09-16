# Smart Wake architecture

## Product priority

Smart Wake estimates **wakeability / likelihood of lighter sleep**. It does not diagnose a medical
sleep stage. Decision priority is:

1. `WAKE_LIGHT_SLEEP_OPPORTUNITY` / `WAKE_TEMPORAL_MULTI_SENSOR_CONFIRMATION`
2. `WAKE_ALREADY_AWAKE` / `WAKE_CLEARLY_AWAKE`
3. `WAKE_FINAL_DEADLINE`

`ASLEEP` is context, not a veto. A valid temporal HR + micro-movement pattern may wake while the
system state remains `ASLEEP`.

## Session timing

- **Baseline/lead-in:** monitoring starts 45 minutes before the earliest allowed wake time.
- **Allowed wake window:** the detector may ring only at or after the configured earliest time.
- **Final deadline:** a separate `AlarmClock` delivery is unconditional.
- Direct accelerometer (10 Hz) and gyro (5 Hz) sampling use 15-second sensor-hub batching during
  lead-in and 2-second batching during the allowed wake window. Decision evaluation remains every
  30 seconds. Sensors are unregistered when the monitoring service ends.

The detector keeps a rolling 15-minute personalized baseline, excluding the latest 75 seconds from
that baseline. The longer lead-in lets this baseline be established during stable sleep rather than
after waking has already begun. When interesting wakeability evidence begins, the detector freezes
the pre-transition snapshot instead of letting a gradual HR/motion rise normalize itself away. It
resumes rolling adaptation only after the candidate/trend has cleared and there is measured quiet
(missing sensor data is never treated as quiet).

## Live detector

- HR uses change relative to the session baseline, recent slope, repeated elevation, freshness and
  sample age. A short spike is not enough.
- Accelerometer and gyro are one movement evidence group. The detector records active samples,
  activity index, active 15-second buckets, clusters and time since the last movement.
- Batched Health Services and SensorManager samples use their measurement timestamps relative to
  boot, not the later callback-delivery time, so freshness and movement clusters remain truthful.
- HR and movement are independent groups; accelerometer and gyro are correlated views of one group.
  Independent groups need not land on the same 30-second evaluation: each may combine while current
  within the bounded 75-second freshness window. A movement event older than that cannot combine
  with new HR as a fake multi-group sample.
- A strong single sample creates candidate context only. It cannot wake.
- Candidate confirmation requires a later multi-group sample, at least 20 seconds later, with both
  newly elevated HR and newly active movement after candidate creation.
- `36/2 -> 22/1` cannot confirm and cancels/decays the candidate.
- Two quiet evaluations decay an isolated candidate. Candidate memory expires after 150 seconds.
- Health Services `PASSIVE`, steps and sustained strong movement are already-awake fallback signals.
  A newer explicit `ASLEEP` clears the preceding awake transition.

## Wakeability trend

The detector retains four minutes of explainable evaluation frames. It reports:

- `STABLE_OR_LOW_WAKEABILITY`
- `NORMAL_SLEEP`
- `WAKEABILITY_RISING`
- `WAKE_OPPORTUNITY`
- `AWAKE`

`WAKEABILITY_RISING` needs repeated interesting frames and distinct fresh cardiovascular and
movement updates. Candidate and trend are integrated: trend describes progression; candidate stores
the concrete opportunity awaiting fresh temporal confirmation.

An isolated `42+/2` observation remains candidate-only. Conversely, a trend can already be a valid
opportunity without a redundant post-candidate event when its **last current frame** is multi-group
and score `>=23`, and the prior **150 seconds** contain at least three interesting frames, two
distinct HR updates, two distinct movement updates, and a non-fading score. This is not candidate
memory confirming itself: each counted update has to be an actual new sensor measurement.

## HRV

Zmanio currently has no real beat-to-beat/RR HRV source. Health Services 1.1 exposes heart-rate BPM
but no HRV data type, and the physical OnePlus log reported Measure capability `HeartRate` only.
Standard deviation of BPM samples is logged only as `HR_BPM_VARIABILITY_PROXY`; it is not labeled or
scored as HRV. Missing HRV is `NO_DATA`, never negative evidence.

## Sleep stages and Health Connect

- The Wear Health Services API used by Zmanio has no live LIGHT/DEEP/REM stream.
- The OnePlus Watch 3/OHealth product supports a phone-side Health Connect connection (OHealth
  4.20.23 or newer), but OnePlus does not document live per-stage write latency.
- Android's official Health Connect sleep guidance requires a `SleepSessionRecord` to have an end
  time and says to write it after the session finishes. It is therefore retrospective, not a safe
  critical live input.
- No Health Connect dependency or permission was added to the watch app. A future phone-side,
  opt-in validator can compare Zmanio decision timestamps with stages synced after the session.
- Actual OHealth-to-Health-Connect latency still requires a connected phone/watch and a measured
  overnight test; it must not be assumed from API availability.

Official references:

- https://developer.android.com/health-and-fitness/health-connect/experiences/sleep
- https://developer.android.com/health-and-fitness/health-connect/availability
- https://developer.android.com/health-and-fitness/health-services
- https://www.oneplus.com/us/oneplus-watch-3-43mm

## Diagnostics

Each evaluation records system state/age; score and groups; HR baseline/delta/slope/samples/age;
explicit unavailable HRV; accelerometer and gyro samples/activity; micro-movement and clusters;
steps; candidate origin/age/confirmation; wakeability state/trend/evidence; fresh evidence groups;
already-awake flags; and the final decision reason. Motion is explicitly `NO_DATA`,
`MEASURED_QUIET`, or `MEASURED_ACTIVITY`.

## Refael log limitation, 16 September 2026

The configured earliest wake was 07:00 and deadline was 07:30. The old monitor started at 06:43:47.
There is no Zmanio HR, accelerometer, gyro, step or score data for 06:15-06:30. The only relevant
entry is a Health Services `PASSIVE` callback at 06:29:00, after the reported natural wake. Therefore
the old log cannot support a truthful replay of a predicted wake opportunity around 06:20-06:24.
Also, regardless of detection, the product must not ring before the configured 07:00 earliest time.
