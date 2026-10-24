# Sparely design guidelines

How every screen in Sparely should look and behave. New screens follow these rules, and an existing
screen that's being reworked is brought in line with them. When a rule doesn't fit a screen, change
the rule here first rather than making an exception in one place.

Sparely uses Material 3 (the Expressive style) with Sparely's own palette and fonts (Poppins for
headings and numbers, Roboto Flex for text).

## 1. Principles

1. **The answer first.** Each page opens on the one thing the person came to know: what's left to
   spend, what's owed, how close a goal is. Everything else comes after.
2. **Show direction, not just a number.** A balance means more with where it's heading: a pace mark,
   a trend line, a forecast.
3. **One way to do a thing.** The same action looks and works the same on every page (mark paid,
   add money, edit, delete).
4. **Calm by default, loud when it matters.** Colour is for status (late, over, close), not for
   decoration. A screen with nothing wrong is mostly neutral.
5. **Words people use.** "Reste 80 $", not "Remaining balance: 80.00". Every visible text is
   translated, and Québec French uses "tu".

## 2. Anatomy of a page

From top to bottom:

1. **Top bar.** A large title that shrinks into a regular bar as the list scrolls
   (`LargeTopAppBar` with `exitUntilCollapsedScrollBehavior`). Actions: at most two icons (search,
   filter). Screens inside the main scaffold don't add their own insets (`WindowInsets(0)`).
2. **Headline.** The page's main number and its direction (see §3). One per page, never two.
3. **Quick actions.** When a page has two to four common actions, a connected button group under
   the headline (for example Add / Withdraw / Adjust on a vault).
4. **Content in groups.** Segmented groups with a header (§4).
5. **Main action.** One floating action button. When there are several kinds of thing to add, the
   FAB opens a menu (`FloatingActionButtonMenu`) instead of a second FAB or a dialog.

Side padding is 16dp. Gap between groups is 16–24dp; between rows of one group, 2dp (`SegmentGap`).

## 3. The headline

A `primaryContainer` surface with a 28dp corner, 20dp padding, containing:

- a short label ("Reste ce mois-ci", "Tu dois"),
- the number in `displaySmall` bold, on one line (`SingleLineText` shrinks it rather than wrapping),
- one line of context ("1 188 $ dépensés sur 1 400 $ · 7 jours"),
- **a visual that shows direction**, chosen for the page:

| Page | Visual |
|---|---|
| Home | Cash-flow line for the next 30 days, with paydays and bills as dots |
| Expenses | Spending per day this month as bars, today marked |
| Budgets | Spent bar with a **pace mark** at today's share of the month |
| Recurring | A 30-day strip with each payment placed on its date |
| Vaults / one vault | Progress ring, and the balance line over time |
| Credit cards | Utilization bar with the **30 % mark** |
| Savings accounts | Balance line with interest added |

- up to three chips (`HeroPill`) for secondary facts; a chip that leads somewhere has an arrow.

Never put a donut chart, a legend or a second big number in the headline.

## 4. Lists

- Items are grouped by what they need, not by type: "À payer / D'ici 30 jours / Plus tard",
  "Dépassés / Attention / Ça va", "Aujourd'hui / Hier / Lundi 20 oct.".
- Each group has an `ExpressiveListHeader`: an icon, the title, and the group's total or count.
  On long lists (expenses) the day headers stick to the top while scrolling.
- Rows in a group use `segmentedShape(index, count)`: 24dp outer corners, 6dp inner, 2dp gaps.
- Empty groups are hidden. An empty page shows `ExpressiveEmptyState` with one action.

## 5. Rows

```
[icon 44dp]  Name                         Amount
             Status line (coloured)       sur 350 $
             ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬ (bar / sparkline, optional)
```

- **Leading:** a 44dp circle, the item's colour at 14 % with its icon in full colour, or the
  store's logo.
- **Title:** `titleMedium` semibold, one line.
- **Status line:** `bodySmall`, one line, coloured only when it says something:
  red (`critical`) late or over, orange (`warning`) close, green (`success`) done, otherwise
  `onSurfaceVariant`. Colour always comes with words; never colour alone.
- **Trailing:** the amount in `titleMedium` bold, with its reference under it ("sur 350 $").
- **Below:** a 6dp bar or a sparkline, aligned with the text (58dp from the start).
- **One quick action at most**, only when the row is waiting for it (✓ on a payment that's due,
  ＋ on a vault). Everything else is in the detail.
- **Swipe:** start-to-end does the row's quick action, end-to-start deletes with an Undo
  snackbar. Same on every list that allows it, or not at all.

## 6. Details: sheet or page

- **Sheet** (`SparelyBottomSheet`) for **one quick task**: mark paid, add money, change a limit,
  a short editor, a confirmation. It ends with `SheetActions` (Cancel / confirm) pinned at the bottom.
- **Full page** for **an item's details**: its numbers, history, settings and actions. It opens
  from the row with a container transform (the row grows into the page) and closes with
  predictive back.
- A sheet never opens another sheet. From a detail page, a sheet is fine.
- Destructive actions are an outlined error button at the bottom of the detail page, and ask once
  in a sheet. No `AlertDialog`s, except the system date and time pickers.

A detail page follows §2: large title (the item's name), headline with its visual, quick action
button group, then groups (details, history).

## 7. Editors

- A sheet when the form fits on one screen; a full page otherwise.
- Sections with `SheetSectionLabel`. The most important field first.
- Amounts use `SparelyAmountTextField` (calculator keys). Choices of two to four use a connected
  button group; more use chips (`FlowRow`); long lists use a searchable picker.
- Dates use the system date picker, shown in the user's language.
- Save is disabled until the form is valid, and the reason is shown under the field.

## 8. Wide screens

Use the window size class (`currentWindowAdaptiveInfo()`):

- **Compact (phones):** as above.
- **Medium and expanded (foldables, tablets, landscape):** list pages become list–detail
  (`ListDetailPaneScaffold`): the list on the left, the detail page on the right instead of
  navigating. The navigation bar becomes a navigation rail.

## 9. Motion

- Use the Expressive motion scheme (spring-based). No linear tweens for movement.
- Bars and rings animate to their value when they first appear (`EmphasizedEasing`).
- Row → detail: container transform. Pages: shared axis. Sheets: the default sheet motion.
- Pressed buttons and chips change shape (rounder to squarer), as Material 3 Expressive does.
- Respect the system "remove animations" setting.

## 10. Colour and theme

- Sparely's brand palette is the default. Material You (wallpaper colours) is offered as a
  setting, not forced; status colours (`success`, `warning`, `critical`) stay fixed in both.
- Every screen works in dark mode. Containers use the `surfaceContainer*` roles, never hard-coded
  colours.
- Text on a coloured surface uses its `on…` role (`onPrimaryContainer` on the headline).

## 11. Type

- Headline number: `displaySmall` bold. A detail page's number: `headlineMedium` bold.
- Group headers: `titleMedium` semibold. Row titles: `titleMedium` semibold. Secondary text:
  `bodySmall`.
- Numbers use tabular figures where they line up in columns.
- Layouts must hold at 200 % font size: one-line numbers shrink, labels wrap, nothing is cut.

## 12. Data visualisation

- Prefer a bar with a mark (pace, limit, target) or a sparkline over pie and donut charts.
- A chart always has its key number written next to it; it never replaces the number.
- Category colours come from `getCategoryColor`; status colours mean status only.
- Charts have a content description that says what they show ("Dépenses en hausse de 12 % par
  rapport à septembre").

## 13. Words, numbers and dates

- Every visible string is a resource (`values`, `values-fr`, plus `values-fr-rCA` for "tu").
  Nothing user-facing is stored in English in the database.
- Money: `formatCurrency`, without decimals in summaries ("212 $") and with them on single
  items ("42,50 $").
- Percentages: "87 %" in French, "87%" in English.
- Dates: never a fixed pattern like `"MMM d, yyyy"`. Use `DateTimeFormatter.ofLocalizedDate` or
  `DateFormat.getBestDateTimePattern(locale, "dMMM")`. Relative words for the near future and past:
  "Aujourd'hui", "Demain", "Dans 3 jours", "Hier".

## 14. Accessibility

- Touch targets at least 48dp.
- Status is never colour alone (see §5).
- Every icon button has a content description; decorative icons have none.
- Contrast at least 4.5:1 for text, in light and dark.

## 15. Components

| Need | Use |
|---|---|
| Headline | `primaryContainer` `Surface` + `HeroPill` |
| Group header | `ExpressiveListHeader` |
| Row | `SegmentedListItem` / a row built the same way, `segmentedShape`, `segmentSpacing` |
| Small numbers in a detail | `StatTile` |
| Sheet | `SparelyBottomSheet`, `SparelySheetHeader`, `SheetActions` |
| Amount field | `SparelyAmountTextField` |
| Empty page | `ExpressiveEmptyState` |
| Large title, button groups, FAB menu, floating toolbar, Expressive motion | Material 3 1.4+ (needs a newer Compose BOM than 2025.05) |
| List–detail | `androidx.compose.material3.adaptive` |

## 16. Checklist for a screen

- [ ] Large title that collapses; no extra insets.
- [ ] One headline with a visual that shows direction.
- [ ] Groups by what they need, with totals; segmented rows.
- [ ] Rows: icon, title, coloured status with words, amount, at most one quick action.
- [ ] Details on a full page; sheets only for quick tasks; no `AlertDialog`.
- [ ] Works in dark mode, at 200 % text, and on a wide screen.
- [ ] No hard-coded English, no fixed date patterns.
