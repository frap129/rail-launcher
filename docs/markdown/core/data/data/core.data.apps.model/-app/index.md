//[data](../../../index.md)/[core.data.apps.model](../index.md)/[App](index.md)

# App

[main]\
data class [App](index.md)(val defaultName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val profile: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val componentName: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = defaultName, val defaultIcon: [Icon](../../core.data.icons.model/-icon/index.md) = Icon.ActivityIcon(componentName), val icon: [Icon](../../core.data.icons.model/-icon/index.md) = defaultIcon) : [Launchable](../../core.data.launchables.model/-launchable/index.md)

## Constructors

| | |
|---|---|
| [App](-app.md) | [main]<br>constructor(defaultName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), profile: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, componentName: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = defaultName, defaultIcon: [Icon](../../core.data.icons.model/-icon/index.md) = Icon.ActivityIcon(componentName), icon: [Icon](../../core.data.icons.model/-icon/index.md) = defaultIcon) |

## Properties

| Name | Summary |
|---|---|
| [componentName](component-name.md) | [main]<br>val [componentName](component-name.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> |
| [defaultIcon](default-icon.md) | [main]<br>open override val [defaultIcon](default-icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [defaultName](default-name.md) | [main]<br>open override val [defaultName](default-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [icon](icon.md) | [main]<br>open override val [icon](icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [key](key.md) | [main]<br>open override val [key](key.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [name](name.md) | [main]<br>open override val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [packageName](package-name.md) | [main]<br>val [packageName](package-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [profile](profile.md) | [main]<br>val [profile](profile.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> |

## Functions

| Name | Summary |
|---|---|
| [launch](launch.md) | [main]<br>open override fun [launch](launch.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |
| [openAppInfo](open-app-info.md) | [main]<br>fun [openAppInfo](open-app-info.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |
| [withCustomization](with-customization.md) | [main]<br>open override fun [withCustomization](with-customization.md)(appearance: [LaunchableAppearance](../../core.data.launchables.model/-launchable-appearance/index.md)): [App](index.md)<br>Returns a copy of this Launchable showing [appearance](with-customization.md). |
