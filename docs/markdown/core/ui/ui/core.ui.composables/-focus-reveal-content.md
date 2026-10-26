//[ui](../../index.md)/[core.ui.composables](index.md)/[FocusRevealContent](-focus-reveal-content.md)

# FocusRevealContent

[main]\
fun &lt;[T](-focus-reveal-content.md) : [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-any/index.html)&gt; [FocusRevealContent](-focus-reveal-content.md)(targetState: [T](-focus-reveal-content.md), focusState: [KClass](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.reflect/-k-class/index.html)&lt;*&gt;, revealState: [KClass](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.reflect/-k-class/index.html)&lt;*&gt;, modifier: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> = Modifier, defaultSpec: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> = fadeIn(animationSpec = tween(90))
            .togetherWith(fadeOut(animationSpec = tween(90))), focusSpec: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> = fadeIn(animationSpec = tween(110))
            .togetherWith(fadeOut(animationSpec = tween(0))), revealSpec: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> = fadeIn(animationSpec = tween(220, 90))
            .togetherWith(fadeOut(animationSpec = tween(440, 90))), contentAlignment: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> = Alignment.Center, label: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = &quot;FocusRevealContent&quot;, contentKey: (targetState: [T](-focus-reveal-content.md)) -&gt; [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-any/index.html) = { it }, content: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->.(targetState: [T](-focus-reveal-content.md)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

[FocusRevealContent](-focus-reveal-content.md) is a container that automatically animates its content when [targetState](-focus-reveal-content.md) changes. Its [content](-focus-reveal-content.md) for different target states is defined in a mapping between a target state and a composable function.

**IMPORTANT**: The targetState parameter for the [content](-focus-reveal-content.md) lambda should *always* be taken into account in deciding what composable function to return as the content for that state. This is critical to ensure a successful lookup of all the incoming and outgoing content during content transform.

When [targetState](-focus-reveal-content.md) changes, content for both new and previous targetState will be looked up through the [content](-focus-reveal-content.md) lambda. They will go through a ContentTransform so that the new target content can be animated in while the initial content animates out. Meanwhile the container will animate its size as needed to accommodate the new content, unless SizeTransform is set to `null`. Once the ContentTransform is finished, the outgoing content will be disposed.

If [targetState](-focus-reveal-content.md) is expected to mutate frequently and not all mutations should be treated as target state change, consider defining a mapping between [targetState](-focus-reveal-content.md) and a key in [contentKey](-focus-reveal-content.md). As a result, transitions will be triggered when the resulting key changes. In other words, there will be no animation when switching between [targetState](-focus-reveal-content.md)s that share the same key. By default, the key will be the same as the targetState object.

[FocusRevealContent](-focus-reveal-content.md) displays only the content for [targetState](-focus-reveal-content.md) when not animating. However, during the transient content transform, there will be more than one set of content present in the [FocusRevealContent](-focus-reveal-content.md) container. It may be sometimes desired to define the positional relationship among the different content and the overlap. This can be achieved by defining [contentAlignment](-focus-reveal-content.md) and ContentTransform.targetContentZIndex. By default, [contentAlignment](-focus-reveal-content.md) aligns all content to Alignment.TopStart, and the `zIndex` for all the content is 0f. **Note**: The target content will always be placed last, therefore it will be on top of all the other content unless zIndex is specified.

[label](-focus-reveal-content.md) is an optional parameter to differentiate from other animations in Android Studio.

#### Parameters

main

| | |
|---|---|
| targetState | is a key representing your target layout state. Every time you change a key the animation will be triggered. The [content](-focus-reveal-content.md) called with the old key will be faded out while the [content](-focus-reveal-content.md) called with the new key will be faded in. |
| focusState | is the KClass of the targetState when focusing. |
| revealState | is the KClass of the targetState when revealing. |
| modifier | Modifier to be applied to the animation container. |
| defaultSpec | the ContentTransform to configure the animation when not revealing or focusing. |
| focusSpec | the ContentTransform to configure the animation when focusing. |
| revealSpec | the ContentTransform to configure the animation when revealing. |
| label | An optional label to differentiate from other animations in Android Studio. |
| contentAlignment | The Alignment within the animation container. |

#### See also

| |
|---|
| ContentTransform |
| AnimatedContentScope |
