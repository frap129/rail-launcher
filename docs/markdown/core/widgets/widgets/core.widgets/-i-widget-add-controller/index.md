//[widgets](../../../index.md)/[core.widgets](../index.md)/[IWidgetAddController](index.md)

# IWidgetAddController

interface [IWidgetAddController](index.md)

Owns the App Widget add flow: pick, bind, configure, place and restore.

#### Inheritors

| |
|---|
| [IWidgetAddControllerImpl](../-i-widget-add-controller-impl/index.md) |

## Properties

| Name | Summary |
|---|---|
| [events](events.md) | [main]<br>abstract val [events](events.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[WidgetEvent](../-widget-event/index.md)&gt;<br>Events the add flow raises for the launcher to run. |
| [state](state.md) | [main]<br>abstract val [state](state.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[AddWidgetFlowState](../-add-widget-flow-state/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [cancel](cancel.md) | [main]<br>abstract suspend fun [cancel](cancel.md)() |
| [choosePlacement](choose-placement.md) | [main]<br>abstract fun [choosePlacement](choose-placement.md)(placement: [AddWidgetPlacement](../-add-widget-placement/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Applies the placement choice; returns true once the picker opens. |
| [onBindResult](on-bind-result.md) | [main]<br>abstract suspend fun [onBindResult](on-bind-result.md)(granted: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)?<br>Completes the system bind dialog; returns the stack that grew when it is placed immediately. |
| [onConfigureResult](on-configure-result.md) | [main]<br>abstract suspend fun [onConfigureResult](on-configure-result.md)(ok: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)?<br>Completes the provider configuration; returns the stack that grew when it is placed. |
| [pick](pick.md) | [main]<br>abstract suspend fun [pick](pick.md)(provider: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)?<br>Binds the picked provider; returns the stack that grew when it is placed immediately. |
| [restore](restore.md) | [main]<br>abstract suspend fun [restore](restore.md)()<br>Restores an add flow interrupted by process recreation. |
| [setQuery](set-query.md) | [main]<br>abstract fun [setQuery](set-query.md)(query: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)) |
| [start](start.md) | [main]<br>abstract fun [start](start.md)(origin: [AddWidgetOrigin](../-add-widget-origin/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Opens the picker at [origin](start.md); returns false while another Home edit mode is active. |
| [startFromMenu](start-from-menu.md) | [main]<br>abstract suspend fun [startFromMenu](start-from-menu.md)(target: [AddWidgetTarget](../-add-widget-target/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Starts the add flow from the Widget menu; returns true only once the picker opens. |
| [startWidgetWiring](../start-widget-wiring.md) | [main]<br>fun [IWidgetAddController](index.md).[startWidgetWiring](../start-widget-wiring.md)(scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, appWidgetHost: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, lifecycleReconciler: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, onEvent: suspend ([WidgetEvent](../-widget-event/index.md)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))<br>Starts the add flow's events, restore and provider wiring every Widget host needs. |
| [updateProviders](update-providers.md) | [main]<br>abstract fun [updateProviders](update-providers.md)(providers: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;)<br>Keeps the picker groups in step with the host's provider list. |
