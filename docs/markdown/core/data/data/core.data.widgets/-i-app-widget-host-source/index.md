//[data](../../../index.md)/[core.data.widgets](../index.md)/[IAppWidgetHostSource](index.md)

# IAppWidgetHostSource

interface [IAppWidgetHostSource](index.md)

The only data source that talks to the Android App Widget service. The ViewModel and its fakes work with the pure value types above.

#### Inheritors

| |
|---|
| [IAppWidgetHostSourceImpl](../-i-app-widget-host-source-impl/index.md) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [main]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [providerChanges](provider-changes.md) | [main]<br>abstract val [providerChanges](provider-changes.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html)&gt;<br>Emits when the host or a package/profile change may have altered providers. |
| [providers](providers.md) | [main]<br>abstract val [providers](providers.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[WidgetProviderInfo](../-widget-provider-info/index.md)&gt;&gt;<br>Home-screen providers from every profile, excluding Rail's own providers. |

## Functions

| Name | Summary |
|---|---|
| [allocateId](allocate-id.md) | [main]<br>abstract fun [allocateId](allocate-id.md)(): [AppWidgetId](../-app-widget-id/index.md) |
| [availability](availability.md) | [main]<br>abstract fun [availability](availability.md)(id: [AppWidgetId](../-app-widget-id/index.md), provider: [WidgetProvider](../-widget-provider/index.md), profileSerial: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)): [WidgetAvailability](../-widget-availability/index.md)<br>The current availability of a placed App Widget. |
| [bindIfAllowed](bind-if-allowed.md) | [main]<br>abstract fun [bindIfAllowed](bind-if-allowed.md)(request: [BindRequest](../-bind-request/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [boundIds](bound-ids.md) | [main]<br>abstract fun [boundIds](bound-ids.md)(): [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[AppWidgetId](../-app-widget-id/index.md)&gt;<br>Every App Widget id the system currently attributes to Rail's host. |
| [deleteId](delete-id.md) | [main]<br>abstract fun [deleteId](delete-id.md)(id: [AppWidgetId](../-app-widget-id/index.md)) |
| [providerInfo](provider-info.md) | [main]<br>abstract fun [providerInfo](provider-info.md)(id: [AppWidgetId](../-app-widget-id/index.md)): [WidgetProviderInfo](../-widget-provider-info/index.md)?<br>Provider details looked up directly from the system for a bound App Widget. |
| [startConfiguring](start-configuring.md) | [main]<br>abstract fun [startConfiguring](start-configuring.md)(activity: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, id: [AppWidgetId](../-app-widget-id/index.md), requestCode: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))<br>Starts the provider's configuration activity; failures throw. |
| [startListening](start-listening.md) | [main]<br>abstract fun [startListening](start-listening.md)() |
| [stopListening](stop-listening.md) | [main]<br>abstract fun [stopListening](stop-listening.md)() |
