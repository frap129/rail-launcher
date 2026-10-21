# Rail

Rail is an Android home-screen launcher built around a vertical, letter-indexed list of things the user can open.

## Language

**Launchable**:
Anything the user can open from the launcher (an installed app, a Rail deeplink; later shortcuts and folders).
_Avoid_: App (when meaning any launchable), item, entry

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

**Widget Stack**:
Several Widgets sharing one place and one height in the Widget Area, shown one at a time and switched by swiping sideways.
_Avoid_: Carousel, pager

**Place**:
An ordered slot in the Widget Area holding one Widget or Widget Stack.
_Avoid_: Slot, row

**Resize mode**:
The Home edit mode that changes one Widget Area place's height.
_Avoid_: Resize screen, resize drag

**Widget Area**:
The region at the top of Home, above Favorites, holding an ordered column of Widgets and Widget Stacks.
_Avoid_: Widget page, widget list
