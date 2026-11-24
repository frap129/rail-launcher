# Rail

Rail is an Android home-screen launcher built around a vertical, letter-indexed list of things the user can open.

## Language

**Launchable**:
Anything the user can open from the launcher (an installed app, a launcher shortcut, a Rail deeplink, or a Folder).
_Avoid_: App (when meaning any launchable), item, entry

**Launchable customization**:
The overrides the user stores for a Launchable key, such as its custom name or Custom icon.
_Avoid_: Name customization (when meaning the whole stored row), app customization

**Custom icon**:
A user-chosen icon for a Launchable that replaces its normal icon: either an icon-pack drawable name, stored by name so pack updates keep it, or a copy of a gallery image stored in Rail's private icons directory.
_Avoid_: Themed icon, custom image

**Icon picker**:
The bottom sheet that lists the Suggested icons, Original, Gallery and every installed Icon pack and applies a Custom icon or resets it.
_Avoid_: Icon chooser, theme picker

**Pending gallery pick**:
A Gallery choice the Icon picker is still waiting for. While one is pending, the picker stays open when the host stops, so the Photo Picker result still applies to its owner.
_Avoid_: Open gallery, gallery import (until the image is actually imported)

**Suggested icons**:
The locally computed icons the Icon picker lists above Original: for an App, its original icon plus one icon from each installed Icon pack that maps it; for a Shortcut or Folder, the pack icons whose drawable names have one of its label's words of three or more characters as a whole word, with underscores read as word breaks, capped at 12.
_Avoid_: Recommended icons, AI suggestions

**Icon pack**:
An installed app that publishes themed icons through a launcher icon-pack intent; Rail may use one globally or read its drawables as Custom icons.
_Avoid_: Theme, icon theme

**Icon pack category**:
A heading in an Icon pack's drawable list that groups the icons under it in the Icon picker; a pack without headings shows one untitled category.
_Avoid_: Group, section

**Home icon cache**:
The rendered App icons Rail keeps on disk for every icon it draws (Home, the App List, Search and the pickers), keyed by the icon's identity, the App's update time, density and request size, so an icon draws without loading its App's resources; a background revalidation replaces a stored icon whose system icon changed, and the icons drawn from it reload in place.
_Avoid_: Icon disk cache, bitmap cache

**App key**:
The stored identity of an App: its activity component and Profile, which makes each launcher activity in a Profile one App.
_Avoid_: Component key, activity key, package key

**Package representative**:
The App whose launcher activity is the alphabetically first in its package and Profile. It owns the package's Shortcuts and notifications.
_Avoid_: Main app, primary activity

**System App**:
An App preinstalled on the system image; an updated one can be uninstalled back to its factory version.
_Avoid_: Preinstalled app, bundled app

**Removable App**:
A downloaded App or an updated System App, whose package the system lets the user uninstall.
_Avoid_: Downloaded app, uninstallable app

**Profile**:
The personal, work or Private Space profile an App belongs to. Every App belongs to exactly one Profile.
_Avoid_: User, space, account

**Profile token**:
The stored text form of a Profile's user handle, appended to a Launchable key and embedded in a Shortcut key.
_Avoid_: User id, profile suffix

**Profile badge**:
The system-drawn badge on the icon of an App or App Widget that belongs to a non-personal Profile.
_Avoid_: Work badge (when meaning any Profile's badge), Rail badge

**Work profile**:
The managed Profile a device administrator controls, separate from the personal Profile.
_Avoid_: Work user, work space, managed profile

**Paused**:
A Work profile in quiet mode: its Apps stay in place but are greyed out, and tapping one offers to turn work apps on.
_Avoid_: Off, disabled, stopped

**Disabled**:
A Launchable its publisher currently disables, so it cannot open.
_Avoid_: Off, removed

**Suspended**:
An App whose package the system has suspended, as Digital Wellbeing's "Pause app" or a freezer app does. Rail greys its icon, including any Custom icon and Profile badge, while its label, row and place stay as usual, and tapping it still asks the system to launch it, which shows the system's own paused dialog.
_Avoid_: Paused, Disabled, frozen app

**Private Space**:
The hidden Profile Android 15+ provides for Apps the user keeps private, unlocked with the system credential and locked again by the system.
_Avoid_: Private profile, vault, space

**Locked**:
A Private Space in quiet mode: Rail shows only its Private Space container row, unless it is Hidden, and no private Apps anywhere, while keeping their Favorites, Launchable customizations, Header tap actions, Added items, Folder members, hidden Shortcuts, Shortcut pins, Standalone shortcuts and the Places of their Pop-ups for when it is Unlocked.
_Avoid_: Off, closed, disabled

**Lock requested**:
A Private Space that Rail has asked the system to Lock, before the system confirms; Rail treats it as Locked until the request settles.
_Avoid_: Pending lock, locking

**Hidden Private Space**:
A Private Space whose container Rail leaves out of the App List and Rail because it is Locked, or a Lock is requested, and the Rail Settings switch or the system's "Hide private space" setting hides it; Unlocking shows it again.
_Avoid_: Invisible space, hidden profile

**Kept key**:
The key of a Launchable or Shortcut that belongs to a Locked Profile Rail still sees, or to a Private Space that has Unlocked but not listed its Apps yet. Stale-key cleanup keeps it until the Profile lists its Apps or is removed, and resolution hides it meanwhile.
_Avoid_: Retained key, stale key

**Restored key**:
A key read from a database that Android cloud backup or device transfer restored. It resolves when its Profile token still matches a current Profile and its Launchable or Shortcut is still available; otherwise stale-key cleanup removes it, except for a Kept key, which survives until its Profile lists its Apps or is removed.
_Avoid_: Backup key, old key

**Stale-key cleanup**:
The one job in `core:data` that classifies the keys stored by Launchable customizations, Favorites, Pop-up owners and items, Header tap actions, Standalone shortcuts and Stopped suggestions, then removes the rows whose key's Launchable, Shortcut or Profile is gone. Each stored row kind only supplies its keys and how to delete them.
_Avoid_: Cleanup paths, stale purge, key sweep

**private App**:
An App that belongs to Private Space. It appears only in the Private Space group while the space is Unlocked, except that a recently installed one also appears in the Recently installed section.
_Avoid_: Private profile app, vault app

**Install in Private Space**:
The action an App's menu offers on an App outside Private Space that is not a System App, while the Private Space is Unlocked. It opens the App's Store page inside the Private Space Profile, so the user can install a private copy without leaving Rail, and tells the user when the system cannot open it.
_Avoid_: Private install, private copy action

**Store page**:
An app market's listing for one package, which Install in Private Space opens through the system's own market activity intent for the Private Space Profile.
_Avoid_: Play Store link, market page

**Folder**:
A Launchable that only opens a Pop-up listing the Launchables the user put in it.
_Avoid_: Group, category, pop-up folder

**Folder member**:
A Launchable the user put in a Folder, shown on its own row inside that Folder's Pop-up.
_Avoid_: Folder item, folder entry

**Pop-up**:
The floating card attached to a row, opened by swiping an App row or by tapping or swiping a Folder row, holding that Launchable's shortcuts, added items and, later, widgets and notifications.
_Avoid_: Popup, card menu, long-press menu

**Pop-up row**:
The list a Pop-up's row sits in: Home, an App List group, Recently installed or Search. With its Owner it names one row, so a recently installed App's two rows, in its Initial group and in Recently installed, stay distinct and the open Pop-up follows only the one that opened it, even after that row scrolls away and returns.
_Avoid_: Anchor row, source row

**Pop-up card**:
The floating surface of an open Pop-up: its header, Shortcuts, Added items, Notifications, Widget and menu. It is not a Widget.
_Avoid_: Card (when meaning a Widget), pop-up window

**Pop-up editor**:
The Edit Pop-up sheet that hides or shows the Owner's Shortcuts and adds, reorders or removes Added items; it opens the item and Shortcut pickers.
_Avoid_: Popup editor, edit menu

**Pop-up session**:
The card, editor, pickers and Folder actions of one Pop-up host, and the order Back closes them in.
_Avoid_: Popup state, session (alone)

**Pop-up collaborator**:
A shared UI component, not a ViewModel, that a Pop-up host attaches to its Pop-up session: the editor, Add apps and Shortcut pickers, Folder actions, and the card and aggregate contracts that connect them.
_Avoid_: Pop-up ViewModel, popup component

**Shortcut**:
A quick action published by an app, shown in a Pop-up and run without opening the app's main screen.
_Avoid_: Deep link, quick action

**Shortcut listener**:
The single LauncherApps callback Rail keeps registered while it listens for the Shortcuts of any App, which refreshes only the App whose Shortcuts or package changed.
_Avoid_: Shortcut observer, shortcut callback

**Hidden shortcut**:
A Shortcut the user hid from one App's Pop-up in the Edit Pop-up sheet. It stays hidden if the app removes and later republishes the Shortcut.
_Avoid_: Disabled shortcut, removed shortcut

**Hidden app**:
An App or Standalone shortcut the user hid from the App List with the long-press menu's Hide from App List action. It leaves the App List groups, including the Private Space group, and the Rail marks, while keeping its Favorite, Folder and Added item places, its custom name and Custom icon, and its row in every picker. It still matches Search queries, marked Hidden, unless the Show hidden apps Search setting is off, and is never a Search suggestion. The user unhides it on the Hidden apps screen in Rail Settings. Hiding only tidies the App List; it is not private, because Private Space covers privacy.
_Avoid_: Hidden Launchable, archived app

**Hidden apps screen**:
The Rail Settings screen listing every Hidden app by resolved name and icon, each with an Unhide action, and an empty state when nothing is Hidden. While the Private Space is Locked, its private Apps are left off the screen.
_Avoid_: Hidden app list, unhide menu

**Show hidden apps**:
The Search setting on the Search settings screen that keeps Hidden apps in Search results, marked Hidden. It is on by default; while it is off, Search leaves Hidden apps out of its results. Search suggestions leave Hidden apps out either way.
_Avoid_: Hidden apps filter, hide apps setting

**Standalone shortcut**:
A Shortcut another app asked Rail to add through a Pin request. Rail lists it in the App List and Favorites as its own Launchable instead of keeping it inside a Pop-up.
_Avoid_: Pinned shortcut (when meaning a Standalone shortcut), home screen shortcut

**Shortcut placeholder**:
A Standalone shortcut whose Paused or Locked Profile cannot resolve it yet. Rail keeps its key and shows it under its Package representative's name until the Profile resumes.
_Avoid_: Paused shortcut, empty shortcut

**Pin request**:
The system's confirm-pin-shortcut request another app sends to the default launcher, asking it to add a Shortcut.
_Avoid_: Shortcut install, add-to-home request

**Added item**:
A Launchable the user added to a Pop-up from its edit sheet, shown below that Launchable's Shortcuts. A Shortcut added this way is a Shortcut item.
_Avoid_: Pinned app, Pop-up favorite, item

**Shortcut item**:
An added item that is a Shortcut. It stays in the Pop-up while the publishing app still offers it, and is hidden from the Pop-up while the app disables it.
_Avoid_: Pinned shortcut, added shortcut

**Owner**:
The Launchable whose row a Pop-up is attached to; the Pop-up shows that Launchable's header, shortcuts and added items.
_Avoid_: Parent, source app, host

**Notification**:
An active system notification belonging to an App, shown in that App's Pop-up and, under an Added item's row or a Folder member's row, inside a Pop-up. In an App's Pop-up the Owner's notifications start expanded to their full text, actions and inline reply; a Folder member's or Added item's notifications start collapsed. Either can be toggled when expandable.
_Avoid_: Alert, message (when meaning a notification)

**Notification action**:
A button a Notification offers, including the inline reply field shown when it accepts a RemoteInput.
_Avoid_: Notification button, quick action

**Folder summary**:
The second line of a Folder row naming the Folder members whose Apps have notifications, with a trailing chevron; the only notification-derived content on a row.
_Avoid_: Badge, count, preview

**Favorite**:
A Launchable the user has pinned to Home, held in a user-defined order.
_Avoid_: Pin, starred app, dock

**Home**:
The launcher's default state, showing the Widget Area and Favorites.
_Avoid_: Favorites screen, home page, dashboard

**Home suggestions**:
The Launchables Home shows below the Favorites that the user opens often and recently but has not made Favorites (at least three launches and a Recent use score of at least 1.0, so an App not opened for two to three weeks drops off), drawn quieter and announced as suggested. Folders are never suggested, whatever their Launch count and Recent use score. They are chosen when the launcher starts or stops and on leaving Home, never while Home is shown, and Edit Favorites leaves them out.
_Avoid_: Suggested apps, predictions, Search suggestions (which are another list)

**Stopped suggestion**:
A Launchable key the user chose Stop suggesting for in a Home suggestion's long-press menu. Home suggestions never show it again until the user resets the stopped suggestions, and the whole-database backup keeps it.
_Avoid_: Blocked suggestion, dismissed suggestion

**Suggest often-used Apps**:
The Rail Settings switch that shows or hides Home suggestions. It defaults to on; while it is off, Home shows only its Favorites.
_Avoid_: Suggestions toggle, predictions setting

**Home Apps**:
The Apps Home resolves its Favorites from: the Last-known App list until the live LauncherApps load finishes, then the live Apps. Only Favorites, Folders and Pop-ups resolve from a Last-known Home Apps state; the App List, Rail groups, picker groups, stale-key cleanup and App Widget reconciliation require live Apps.
_Avoid_: Saved Apps, cached Apps

**Home readiness**:
The first Launchable readiness stage: Apps have loaded, either from the Last-known App list before the live LauncherApps load finishes or from that live load itself, so Home resolves its Favorites with Launchable customizations, Folder membership and Private Space rules. Only Favorites wait for this stage; every other Launchable consumer waits for App List readiness.
_Avoid_: Home loaded, Favorites loaded (when meaning readiness)

**Last-known Home readiness**:
Home readiness reached from the Last-known App list before the live LauncherApps load finishes. Only Favorites, Folders and Pop-ups resolve from it, and its groups and picker groups are empty; the App List, Rail groups, stale-key cleanup and App Widget reconciliation wait for the live load and App List readiness.
_Avoid_: Saved readiness, cached Home

**App List readiness**:
The Launchable readiness stage after Home readiness: the Launch counts that order the App List groups have loaded, so every group is complete and ordered. The App List, Rail, Search and every non-Home Launchable consumer wait for this stage.
_Avoid_: Launchables loaded, App List loaded

**Home fully drawn**:
The point when Home has been drawn with its Header, Widget Area and Favorites loaded, and Rail has reported it through the activity's fully-drawn reporter; an empty Home counts.
_Avoid_: Home ready (when meaning drawn)

**Home icons drawn**:
The point when Home has drawn every Favorite icon it shows, or failed to draw one, so the startup benchmark can time Home icons apart from Home fully drawn; a Home with no visible Favorite icon reaches it as soon as it is shown.
_Avoid_: Icons loaded, icons ready

**Last-known App list**:
The installed App list Rail saves after each live App load in its own Room database outside the backed-up AppDatabase and Android backup, so Home can resolve Favorites before the live LauncherApps load finishes. It carries each App's component, Profile serial, label, badged label, flags and install and update times, never holds Private Space Apps, and every live change replaces it.
_Avoid_: App cache, saved App list, app snapshot

**Group key**:
The key an App List group forms by, from a Launchable's resolved name in the device locale: its first letter uppercased, then replaced by the canonical letter its locale collates it as, which keeps letters the locale treats as its own (Æ, Ø and Å in Danish and Norwegian; Å, Ä and Ö in Swedish and Finnish; Ñ in Spanish) and folds every other accented letter into its base letter (É into E, Ü into U, Ё into Е in Russian). Among the letters the locale collates as one, the canonical one is the letter the locale sorts first, drawn from A-Z, the letters the supported alphabets add (Æ, Ø, Å, Ä, Ö, Ñ, Œ) and the locale's own script, so Danish and Norwegian group Ä under Æ, Swedish and Finnish group it under Ä, and a ligature collated as no single letter, such as Œ, keys by itself. A first letter in a script the locale does not write, such as Cyrillic, Greek or CJK on an English phone, keys by the first letter of the Latin transliteration of the whole name, so a polyphonic Han word transliterates in context; a letter ICU cannot romanize keeps its native letter, as does a non-letter initial such as an emoji, which keys by its whole code point; a name in Han, Hangul, Hiragana or Katakana on a locale that writes it (Korean, Japanese, Chinese) keys by that locale's index bucket instead of its first letter (Korean: initial consonant; Japanese: kana row, hiragana and katakana merged; Chinese: pinyin initial A-Z), and a name the index only catches in its overflow bucket, such as Japanese kanji, keys under "#"; a Latin name on a locale that writes another script keeps its Latin letter, ordered among the locale's own groups. Digit-prefixed and empty names share the "#" key, which stays first.
_Avoid_: First character, initial letter

**Initial group**:
The App List group and Rail mark for one Group key, ordered after the Star and before the Builtin group: "#" first, then, where the locale has an index, its index letters in index order, then the rest in the locale's collation order.
_Avoid_: Letter group, alphabet group

**App List**:
The launcher state showing every Launchable grouped by Group key in the device locale's order, then the Private Space group last, leaving out Hidden apps. While Show Recently installed is on, the list ends with the Recently installed section. The App List order setting chooses the order inside each group. Folders appear in the group for their resolved name, and the Apps they hold are left out until they leave the Folder. Private Apps appear only in the Private Space group.
_Avoid_: Drawer, all-apps, launcher list

**App List order**:
The setting choosing how the App List orders the Launchables inside each group: Alphabetical, Most used or Recently used. It defaults to Recently used.
_Avoid_: Sort order, app order

**Rank Folders by use**:
The setting choosing whether the App List ranks Folders by use alongside the other Launchables, or keeps them last in their group in their current relative order. It applies only to Most used and Recently used, and defaults to on.
_Avoid_: Folder sort, folder ranking

**Recently installed**:
The App List section after the Private Space group that lists the Apps first installed in the last 3 days, newest first, and an App leaves it once it is older than 3 days. Its Apps also stay in their Initial groups; it is not a group of its own, so it has no Rail mark, and scrolling to the end of the App List reaches it. Hidden apps and the private Apps of a Locked Private Space are left out.
_Avoid_: New apps, recent installs

**Show Recently installed**:
The Rail Settings switch that shows or hides the Recently installed section. It defaults to off.
_Avoid_: Recently installed toggle, new apps setting

**Launch count**:
The all-time number of launches Rail recorded for a Launchable or Shortcut key, or Pop-up opens for a Folder key.
_Avoid_: Usage count, launch tally

**Recent use score**:
A launch score that decays exponentially, so a recent launch, or a Folder Pop-up open, weighs more than an older one.
_Avoid_: Recency score, usage score

**Search**:
The launcher state for typing a query, which shows the matching Launchables and Shortcuts as Search results, followed by every other Search result such as the Calculator row.
_Avoid_: Search mode, finder

**Search entry**:
The exported `rail-launcher://search` intent other apps call to open Search.
_Avoid_: Search link, exported entry, search deeplink

**Search options sheet**:
The bottom sheet at the trailing end of the Search field that toggles the Search preferences used most while searching and opens the Search settings screen.
_Avoid_: Search quick settings, search menu

**Search settings screen**:
The Rail Settings screen holding every Search preference.
_Avoid_: Search section, Search screen

**Typed character**:
A printable character typed with a hardware keyboard while the launcher is shown, which opens Search with that character as the query.
_Avoid_: Key press, keystroke

**Keyboard shortcut**:
A key combination or unmodified Tab the launcher consumes on Home or in the App List, such as Ctrl/Meta+F for Search or Ctrl/Meta+/ for the Keyboard shortcuts help sheet. Rail keeps one list of them, which feeds the system keyboard shortcuts helper and the Keyboard shortcuts help sheet; any other Ctrl, Alt or Meta combination passes through, Tab passes through outside Home and the App List, and a Keyboard shortcut passes through while a text field has focus.
_Avoid_: Hotkey, key binding, Launcher shortcut

**Row focus**:
The hardware keyboard's focus on a Launcher row on Home or in the App List, which the arrow keys move and Enter opens as a tap would. The focused row paints the focus indicator and moves TalkBack's accessibility focus to itself, so keyboard focus and TalkBack focus stay on one row. Row focus is not text-field focus, so Keyboard shortcuts and Typed characters still fire while a row has focus, and a modified key passes through.
_Avoid_: Cursor, selection, highlight

**Row hover**:
The tinted background a Launcher row paints while a mouse hovers it, removed when the pointer leaves. A row that also has Row focus paints the focus background only, so the two states never stack.
_Avoid_: Hover highlight, mouse-over

**Secondary click**:
A press of the mouse's secondary button or of a stylus button on a Launcher row, which opens the row's long-press menu instead of tapping it, and never fires for a press an ancestor already consumed or for a drag that entered the row. Touch input has no secondary click, so tap and long-press behave as before.
_Avoid_: Right-click, stylus press

**Launcher signal**:
A one-shot request the launcher UI acts on: a HOME intent, a Search entry, a Keyboard shortcut or a Typed character the launcher activity hands it, or the agenda the launcher UI opens on its own.
_Avoid_: Home event, intent event

**Home press**:
A HOME intent that arrives while Rail is already resumed and showing Home. The Home-press entry in Rail Settings opens Search on such a press instead of staying on Home.
_Avoid_: Home tap, re-selection

**Swipe up**:
An upward drag on Home past the end of its lists. The swipe-up entry in Rail Settings opens Search on it.
_Avoid_: Upward swipe, flick

**Swipe down**:
A downward drag on Home past the start of its lists. The swipe-down entry in Rail Settings expands the notification shade on it.
_Avoid_: Pull down, downward swipe

**Search result**:
One row of Search results: a Launchable or Shortcut match, or a row Search adds such as the Calculator row.
_Avoid_: Match, hit

**Calculator row**:
The Search result that shows an arithmetic expression and its result, with `%` as modulo, and copies the result to the clipboard when tapped or opened with Go. It always follows the Launchable and Shortcut results and is the Top result only when none match.
_Avoid_: Calculator result, calculator widget

**Search engine**:
The web engine the Web search row uses: None, Google, DuckDuckGo, Brave, Kagi, Startpage, Bing or Perplexity, or a custom `http` or `https` URL template containing `%s` where the encoded query goes. The Search engine picker offers None at any time; choosing it stores no engine and turns Live web suggestions off. With none, Search shows no Web search row and no Web suggestions. Google, DuckDuckGo, Brave, Kagi and Bing also offer Web suggestions; Startpage, Perplexity, a custom template and None offer none.
_Avoid_: Search provider, web engine

**Typed URL**:
A query that looks like a web address, which Search offers to open directly in the default browser instead of searching for it.
_Avoid_: Entered URL, guessed URL

**Typed URL row**:
The Search result that opens a Typed URL in the default browser. It follows the Calculator row and precedes the Web search and Play Store rows.
_Avoid_: URL result, link row

**Web search row**:
The Search result that opens the query as a web search with the Search engine. It follows the Typed URL row and precedes the Play Store row.
_Avoid_: Web result, search row

**Live web suggestions**:
The Search setting that fetches Web suggestions from the Search engine while the user types. It is off by default; while it is off, Search never contacts the Search engine.
_Avoid_: Autocomplete, autosuggest, web result setting

**Web suggestion**:
A query the Search engine suggests for what the user has typed, fetched only from the selected engine while Live web suggestions is on. Search asks for none while the query shows its Calculator row or Typed URL row.
_Avoid_: Autocomplete entry, autosuggest, prediction

**Web suggestion row**:
The Search result that opens the Web search for a Web suggestion with the Search engine. It follows every other Search result, including the Play Store row.
_Avoid_: Autocomplete row, suggestion result, web result

**Play Store row**:
The Search result that opens a Play Store search for the query, falling back to the Play Store web address when no Play Store app accepts it. It follows the Web search row, is off by default and is never the Top result.
_Avoid_: Store row, app store row

**Publisher name**:
The name of the App that publishes a Shortcut, shown under that Shortcut in Search so Shortcuts with the same name are distinguishable.
_Avoid_: Owner name, app label

**Shortcut search source**:
The source Search reads the Shortcuts of every Package representative from, with their Publisher names and whether they are Hidden.
_Avoid_: Shortcut picker, shortcut provider

**Contact search source**:
The source Search reads matching contacts of the current profile from while Contact results is on and the Contacts permission is granted.
_Avoid_: Contacts provider, contact picker

**Contact row**:
The Search result for a matching contact of the current profile, with call and message buttons that open the dialer and the messaging app; a contact without a number has neither button. Only the current profile's contacts are searched.
_Avoid_: Person row, contact result

**Top result**:
The first Search result other than a Contact row, a Web suggestion row or the Play Store row, which the IME Go action opens; when it is the Calculator row, Go copies its result instead.
_Avoid_: First result, primary result

**Auto-launch**:
The Search setting that opens the only Launchable or Shortcut result without pressing Enter, and only when the new query is longer than the previous one and starts with it, so deleting text or replacing it with something that does not extend it never fires it. It does not fire when that result is Paused, Disabled or a Folder, which opens its Pop-up instead, or when a Calculator or Contact row is also showing.
_Avoid_: Auto-open, auto-start

**Bottom-anchored results**:
The Search setting that reverses the Search results, leaving the query field at the top so the Top result sits next to the keyboard.
_Avoid_: Bottom search, reversed results

**Search suggestions**:
The rows Search shows before typing: recently installed Apps, the last Launchable opened from Search within 15 minutes, and the Launchables opened most often from Search. They can be turned off in Rail Settings and removed one at a time.
_Avoid_: Recents, predictions, quick access

**Search history**:
The launches Rail records from Search, each with its launch count, last-launched time and whether the user removed it from Search suggestions.
_Avoid_: Usage log, search log

**Search projection**:
The Search history, clock reading, Shortcuts, contacts and Web suggestions the Search rows and Search suggestions are built from. Its sources are read only while Search is open, and stop after the shared timeout once it closes, so Home and the App List never read them; a session that starts after that timeout starts unloaded.
_Avoid_: Search data, Search cache

**Group mark**:
What a group shows on the Rail and above its App List section: its text label or, for Private Space, a shield.
_Avoid_: Glyph, icon (when meaning a mark)

**Rail**:
The index of marks along the screen edge used to jump through the App List; touching it or any Scrub edge from Home opens the App List. Its marks are the Star, which stands for Home, the Initial groups, the Builtin group and, when a Private Space exists, its shield last. While Show Rail is off it draws no marks, and its Scrub edges still scrub the App List.
_Avoid_: Scroll bar, alphabet, wave

**Rail side**:
The screen side, relative to the layout direction, where the Rail is drawn: the layout start or the layout end. It defaults to the layout end.
_Avoid_: Rail position, rail alignment

**Rail haptics**:
The Rail Settings switch that turns the tick haptics the Rail plays while scrubbing on or off. It defaults to on; while it is off, scrubbing the Rail or a Scrub edge stays silent and the launcher's other haptics are unchanged.
_Avoid_: Haptic feedback toggle, scrub vibration

**Show Rail**:
The Rail Settings switch that draws the Rail's marks. It defaults to on; while it is off, the App List draws no Rail, and every enabled Scrub edge still scrubs it, including the edge where the Rail would be drawn.
_Avoid_: Show alphabet, Rail visibility

**Scrub edges**:
The screen edges, relative to the layout direction, whose touches scrub the Rail: the layout start, the layout end, or both.
_Avoid_: Scroll edges, touch edges, Rail edges

**Rail scrub strip**:
The invisible touch strip on a Scrub edge that scrubs the Rail as if the finger were at the same height and the same distance in from the Rail's own edge. Every enabled Scrub edge without a drawn Rail has one, and while Show Rail is off both do, so the edge where the Rail would be drawn still scrubs.
_Avoid_: Rail mirror strip, ghost Rail, invisible Rail, edge strip

**Rail wheel step**:
The move one mouse wheel notch over the Rail makes: one mark per notch, toward the last mark on a downward notch and the first on an upward one, stopping at the first and last mark. A hovering pointer never steps the Rail.
_Avoid_: Wheel scroll, Rail scroll

**Widget**:
A self-contained panel shown on Home, either built into Rail (the Header, the Media Player) or an App Widget.
_Avoid_: Card, glance

**App Widget**:
An Android app widget, provided by another app and hosted by Rail.
_Avoid_: Custom widget, third-party widget

**Header**:
The built-in clock-and-date Widget, present in the Widget Area by default.
_Avoid_: Glance, clock bar

**Weekday clock style**:
The Header style that shows the weekday prominently above the time and date. Its system-default Tap action opens the alarm list for both the time and the date.
_Avoid_: Day clock

**Tap action**:
What a tappable Header line opens: the system default, a chosen Launchable, the agenda, or nothing. A removed Launchable falls back to the system default.
_Avoid_: Shortcut, click action, gesture

**Agenda**:
The bottom sheet listing the upcoming events the Event settings select, grouped by local day: Today, Tomorrow, then dates. A Header time or date Tap action opens it through a Launcher signal; tapping an event opens it in the calendar App.
_Avoid_: Calendar sheet, schedule, event list

**Media Player**:
The built-in Widget that appears on Home while an app is playing media and shows its playback controls.
_Avoid_: Media card, music widget, now playing

**Excluded apps**:
Apps whose media sessions never trigger the Media Player. Exclusion is by package and stays stored even when the app is uninstalled.
_Avoid_: Ignored apps, blocked apps, media blacklist

**Widget Stack**:
Several Widgets sharing one place and one height in the Widget Area or a Pop-up, shown one at a time and switched by swiping sideways. Adding a Widget with **Add next to** places it in the same place as the Widget it was added from, as a new Stack page.
_Avoid_: Carousel, pager

**Stack page**:
One Widget inside a Widget Stack, selected by paging sideways.
_Avoid_: Stack item, tab

**Stack cap**:
The maximum number of pages a Widget Stack may hold, set in Rail Settings (2–8, default 4). One setting covers the Widget Area and Pop-ups.
_Avoid_: Stack limit, max pages

**Place**:
An ordered slot holding one Widget or Widget Stack: a place in the Widget Area, or the single widget place at the top of a Pop-up.
_Avoid_: Slot, row

**Edit Widgets draft**:
The in-memory order, stack membership and removals edited during Edit Widgets, committed atomically on Done.
_Avoid_: Edit state, pending layout

**Drag handle placement**:
Where a drag handle sits relative to the content it reorders: before it on the layout start or after it on the layout end, aligned to that same side and clear of the Rail's touch column.
_Avoid_: Handle position, handle side

**Resize mode**:
The Home edit mode that changes one Widget Area place's height.
_Avoid_: Resize screen, resize drag

**Widget Area**:
The region at the top of Home, above Favorites, holding an ordered column of Widgets and Widget Stacks.
_Avoid_: Widget page, widget list

**Pop-up widget**:
The App Widget or Widget Stack at the top of a Pop-up, owned by that Pop-up and kept out of the Widget Area, Edit Widgets and the Widgets settings list.
_Avoid_: Card widget, popup widget

**Move Widget Area**:
The Home edit mode that moves the Widget Area, and everything below it, down by a top offset.
_Avoid_: Move clock, shift widgets

**Top offset**:
How far down Home the Widget Area starts, from 0 to half of Home's safe height in 8dp steps.
_Avoid_: Widget padding, top margin

**Widget spacing**:
The gap between Widget Area places, chosen from None, Small, Default, Large and Extra large.
_Avoid_: Widget padding, place margin
