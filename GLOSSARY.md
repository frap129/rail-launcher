# Rail

Rail is an Android home-screen launcher built around a vertical, letter-indexed list of things the user can open.

## Language

**Launchable**:
Anything the user can open from the launcher (an installed app, a launcher shortcut, a Rail deeplink; later folders).
_Avoid_: App (when meaning any launchable), item, entry

**Pop-up**:
The floating card attached to a row, opened by swiping the row toward the Rail, holding that App's shortcuts and, later, widgets, notifications and added items.
_Avoid_: Popup, card menu, long-press menu

**Shortcut**:
A quick action published by an app, shown in a Pop-up and run without opening the app's main screen.
_Avoid_: Deep link, quick action

**Hidden shortcut**:
A Shortcut the user hid from one App's Pop-up in the Edit Pop-up sheet. It stays hidden if the app removes and later republishes the Shortcut.
_Avoid_: Disabled shortcut, removed shortcut

**Added item**:
A Launchable the user added to a Pop-up from the Edit Pop-up sheet, shown below that App's Shortcuts.
_Avoid_: Pinned app, Pop-up favorite, item

**Owner**:
The App whose row a Pop-up is attached to; the Pop-up shows that App's header and shortcuts.
_Avoid_: Parent, source app, host

**Favorite**:
A Launchable the user has pinned to Home, held in a user-defined order.
_Avoid_: Pin, starred app, dock

**Home**:
The launcher's default state, showing the Widget Area and Favorites.
_Avoid_: Favorites screen, home page, dashboard

**App List**:
The launcher state showing every Launchable grouped alphabetically.
_Avoid_: Drawer, all-apps, launcher list

**Rail**:
The letter index along the screen edge used to jump through the App List; touching it from Home opens the App List. Its first entry is the Star, which stands for Home.
_Avoid_: Scroll bar, alphabet, wave

**Widget**:
A self-contained panel shown on Home, either built into Rail (the Header, the Media Player) or an App Widget.
_Avoid_: Card, glance

**App Widget**:
An Android app widget, provided by another app and hosted by Rail.
_Avoid_: Custom widget, third-party widget

**Header**:
The built-in clock-and-date Widget, present in the Widget Area by default.
_Avoid_: Glance, clock bar

**Tap action**:
What a tappable Header line opens: the system default, a chosen Launchable, or nothing. A removed Launchable falls back to the system default.
_Avoid_: Shortcut, click action, gesture

**Media Player**:
The built-in Widget that appears on Home while an app is playing media and shows its playback controls.
_Avoid_: Media card, music widget, now playing

**Excluded apps**:
Apps whose media sessions never trigger the Media Player. Exclusion is by package and stays stored even when the app is uninstalled.
_Avoid_: Ignored apps, blocked apps, media blacklist

**Widget Stack**:
Several Widgets sharing one place and one height in the Widget Area, shown one at a time and switched by swiping sideways. Adding a Widget with **Add next to** places it in the same place as the Widget it was added from, as a new Stack page.
_Avoid_: Carousel, pager

**Stack page**:
One Widget inside a Widget Stack, selected by paging sideways.
_Avoid_: Stack item, tab

**Stack cap**:
The maximum number of pages a Widget Stack may hold, set in Rail Settings (2–8, default 4).
_Avoid_: Stack limit, max pages

**Place**:
An ordered slot in the Widget Area holding one Widget or Widget Stack.
_Avoid_: Slot, row

**Edit Widgets draft**:
The in-memory order, stack membership and removals edited during Edit Widgets, committed atomically on Done.
_Avoid_: Edit state, pending layout

**Resize mode**:
The Home edit mode that changes one Widget Area place's height.
_Avoid_: Resize screen, resize drag

**Widget Area**:
The region at the top of Home, above Favorites, holding an ordered column of Widgets and Widget Stacks.
_Avoid_: Widget page, widget list

**Move Widget Area**:
The Home edit mode that moves the Widget Area, and everything below it, down by a top offset.
_Avoid_: Move clock, shift widgets

**Top offset**:
How far down Home the Widget Area starts, from 0 to half of Home's safe height in 8dp steps.
_Avoid_: Widget padding, top margin

**Widget spacing**:
The gap between Widget Area places, chosen from None, Small, Default, Large and Extra large.
_Avoid_: Widget padding, place margin
