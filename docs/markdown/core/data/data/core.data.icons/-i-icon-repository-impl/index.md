//[data](../../../index.md)/[core.data.icons](../index.md)/[IIconRepositoryImpl](index.md)

# IIconRepositoryImpl

[main]\
class [IIconRepositoryImpl](index.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, iconPackDao: [CustomIconPackDao](../../core.data.room.dao/-custom-icon-pack-dao/index.md), iconDao: [PackIconDao](../../core.data.room.dao/-pack-icon-dao/index.md), scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [IIconRepository](../-i-icon-repository/index.md)

## Constructors

| | |
|---|---|
| [IIconRepositoryImpl](-i-icon-repository-impl.md) | [main]<br>constructor(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, iconPackDao: [CustomIconPackDao](../../core.data.room.dao/-custom-icon-pack-dao/index.md), iconDao: [PackIconDao](../../core.data.room.dao/-pack-icon-dao/index.md), scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |

## Properties

| Name | Summary |
|---|---|
| [iconPacks](icon-packs.md) | [main]<br>open override val [iconPacks](icon-packs.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[IconPack](../../core.data.icons.model/-icon-pack/index.md)&gt;&gt; |

## Functions

| Name | Summary |
|---|---|
| [getIcon](get-icon.md) | [main]<br>open suspend override fun [getIcon](get-icon.md)(packPackageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), componentName: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->): [Icon](../../core.data.icons.model/-icon/index.md) |
| [updateIconPacks](update-icon-packs.md) | [main]<br>suspend fun [updateIconPacks](update-icon-packs.md)() |
