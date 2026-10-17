# Previous performance and estimated one-rep max

Previous values come from the user's most recently finished workout containing a completed set
for that exercise, across all routines. Recency uses the finish timestamp, then workout ID.
Incomplete sets and active workouts are excluded. Original weight units, set numbers, and set
types are retained. Repeated occurrences of an exercise are matched by their order in the
previous and current workout; an empty occurrence remains empty instead of shifting sets.

The app uses the Epley equation for estimated one-rep max:

`estimated 1RM = weight × (1 + reps / 30)`

For one completed rep, the app uses the actual lifted weight. For 2–10 completed reps it applies
the equation. Warmups, incomplete sets, zero loads, and sets outside 1–10 reps do not contribute.
The best qualifying estimate is shown for an exercise in the current workout and history;
the exercise record view can query the best estimate across finished workouts. Calculations use
kilograms internally, with pounds converted using `1 lb = 0.45359237 kg`.
Displayed estimates follow the profile's LB/KG preference; existing sets and
previous-set references retain their original recorded units.

This is a strength estimate from logged load, separate from the four personal-record categories.
It does not adjust for effort/RPE, fatigue, bodyweight, assistance, or machine mechanics.
Lower-rep sets near the user's limit are more informative than easy sets. The 10-rep limit is an
application policy to avoid extrapolating high-rep sets; estimates remain exercise dependent.

Sources checked for the implementation:

- [Marzagao, research preprint (2026), literature review and Epley equation](https://sportrxiv.org/index.php/server/preprint/download/768/1661).
  This primary research paper documents the equation and discusses the limits of applying
  generic estimates across exercises and effort levels; the app does not use its proposed new equation.
- [Knutzen, Brilla, and Caine (2002), Accuracy of Seven Equations for Predicting 1-RM Performance](https://www.tandfonline.com/doi/abs/10.1207/S15327841MPEE0602_1).
  Its published abstract reports improved prediction accuracy when repetitions to fatigue were
  at most 10 in the studied older-adult population; it does not establish universal accuracy.
