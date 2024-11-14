/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package core.ui.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.reflect.KClass

/**
 * [FocusRevealContent] is a container that automatically animates its content when [targetState]
 * changes. Its [content] for different target states is defined in a mapping between a target state
 * and a composable function.
 *
 * **IMPORTANT**: The targetState parameter for the [content] lambda should *always* be taken into
 * account in deciding what composable function to return as the content for that state. This is
 * critical to ensure a successful lookup of all the incoming and outgoing content during content
 * transform.
 *
 * When [targetState] changes, content for both new and previous targetState will be looked up
 * through the [content] lambda. They will go through a [ContentTransform] so that the new target
 * content can be animated in while the initial content animates out. Meanwhile the container will
 * animate its size as needed to accommodate the new content, unless [SizeTransform] is set to
 * `null`. Once the [ContentTransform] is finished, the outgoing content will be disposed.
 *
 * If [targetState] is expected to mutate frequently and not all mutations should be treated as
 * target state change, consider defining a mapping between [targetState] and a key in [contentKey].
 * As a result, transitions will be triggered when the resulting key changes. In other words, there
 * will be no animation when switching between [targetState]s that share the same key. By default,
 * the key will be the same as the targetState object.

 * [FocusRevealContent] displays only the content for [targetState] when not animating. However, during
 * the transient content transform, there will be more than one set of content present in the
 * [FocusRevealContent] container. It may be sometimes desired to define the positional relationship
 * among the different content and the overlap. This can be achieved by defining [contentAlignment]
 * and [zOrder][ContentTransform.targetContentZIndex]. By default, [contentAlignment] aligns all
 * content to [Alignment.TopStart], and the `zIndex` for all the content is 0f. __Note__: The target
 * content will always be placed last, therefore it will be on top of all the other content unless
 * zIndex is specified.
 *
 * [label] is an optional parameter to differentiate from other animations in Android Studio.
 *
 * @see ContentTransform
 * @see AnimatedContentScope
 * @param targetState is a key representing your target layout state. Every time you change a key
 *   the animation will be triggered. The [content] called with the old key will be faded out while
 *   the [content] called with the new key will be faded in.
 * @param focusState is the KClass of the targetState when focusing.
 * @param revealState is the KClass of the targetState when revealing.
 * @param modifier Modifier to be applied to the animation container.
 * @param defaultSpec the [ContentTransform] to configure the animation when not revealing or focusing.
 * @param focusSpec the [ContentTransform] to configure the animation when focusing.
 * @param revealSpec the [ContentTransform] to configure the animation when revealing.
 * @param label An optional label to differentiate from other animations in Android Studio.
 * @param contentAlignment The Alignment within the animation container.
 */

@Composable
fun <T : Any> FocusRevealContent(
    targetState: T,
    focusState: KClass<*>,
    revealState: KClass<*>,
    modifier: Modifier = Modifier,
    defaultSpec: ContentTransform =
        fadeIn(animationSpec = tween(90))
            .togetherWith(fadeOut(animationSpec = tween(90))),
    focusSpec: ContentTransform =
        fadeIn(animationSpec = tween(110))
            .togetherWith(fadeOut(animationSpec = tween(0))),
    revealSpec: ContentTransform =
        fadeIn(animationSpec = tween(220, 90))
            .togetherWith(fadeOut(animationSpec = tween(440, 90))),
    contentAlignment: Alignment = Alignment.Center,
    label: String = "FocusRevealContent",
    contentKey: (targetState: T) -> Any = { it },
    content: @Composable AnimatedContentScope.(targetState: T) -> Unit
) {
    val transition = updateTransition(targetState = targetState, label = label)
    val transitionSpec: AnimatedContentTransitionScope<T>.() -> ContentTransform = {
        when {
            // Focusing
            initialState::class == revealState && targetState::class == focusState -> focusSpec

            // Revealing
            initialState::class == focusState && targetState::class == revealState -> revealSpec

            else -> defaultSpec
        }
    }
    transition.AnimatedContent(
        modifier,
        transitionSpec,
        contentAlignment,
        contentKey,
        content = content
    )
}
