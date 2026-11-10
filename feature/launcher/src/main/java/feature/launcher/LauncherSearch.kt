package feature.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.TimesCircle
import core.data.launchables.model.Launchable
import core.ui.composables.RequestKeyboardFocus

private val searchFieldPadding: Dp = 16.dp
private val searchFieldVerticalPadding: Dp = 8.dp
private val topResultPadding: Dp = 16.dp
private val resultsBottomPadding: Dp = 16.dp

private class SearchAnchors {
    val byKey: MutableMap<String, PopupAnchor> = mutableMapOf()
}

/**
 * The Search screen: a focused query field whose Go action opens the Top result, and the results
 * in match order. Rows are the App List's Launchable rows, so their menu, Pop-up, Profile badge
 * and Custom icon stay the same as the App List. Before typing, the rows are Search suggestions
 * and no Top result is marked.
 */
@Composable
internal fun LauncherSearch(
    modifier: Modifier,
    search: LauncherUiState.Search,
    itemActions: LauncherItemActions,
    onQueryChange: (String) -> Unit,
    onSearchGo: () -> Launchable?
) {
    val focusRequester: FocusRequester = remember { FocusRequester() }
    val searchAnchors: SearchAnchors = remember { SearchAnchors() }
    val searchItemActions: LauncherItemActions = remember(itemActions, searchAnchors) {
        itemActions.copy(
            onItemAnchorChanged = { launchable, anchor ->
                searchAnchors.byKey[launchable.key] = anchor
                itemActions.onItemAnchorChanged(launchable, anchor)
            }
        )
    }
    val onGo: () -> Unit = {
        val topResult: Launchable? = onSearchGo()
        if (topResult != null) {
            searchItemActions.onItemTap(topResult, searchAnchors.byKey[topResult.key])
        }
    }
    RequestKeyboardFocus(focusRequester = focusRequester)
    Column(modifier = modifier) {
        OutlinedTextField(
            value = search.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = searchFieldPadding,
                    top = searchFieldVerticalPadding,
                    end = searchFieldPadding,
                    bottom = searchFieldVerticalPadding
                )
                .focusRequester(focusRequester),
            placeholder = { Text(text = stringResource(R.string.search_hint)) },
            trailingIcon = {
                if (search.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = FontAwesomeIcons.Solid.TimesCircle,
                            contentDescription = stringResource(R.string.clear_search)
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() })
        )
        LauncherSearchResults(
            modifier = Modifier.weight(1f),
            results = search.results,
            itemActions = searchItemActions,
            showTopResult = search.topResult != null
        )
    }
}

@Composable
private fun LauncherSearchResults(modifier: Modifier, results: List<Launchable>, itemActions: LauncherItemActions, showTopResult: Boolean) {
    val listState: LazyListState = rememberLazyListState()
    LaunchedEffect(results) {
        if (results.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(bottom = resultsBottomPadding)
    ) {
        itemsIndexed(items = results, key = { _, launchable -> launchable.key }) { index, launchable ->
            if (showTopResult && index == 0) {
                Text(
                    text = stringResource(R.string.top_result),
                    modifier = Modifier.padding(start = topResultPadding, top = topResultPadding),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            LauncherItem(
                item = launchable,
                isEditingFavorites = false,
                isFavorite = false,
                isPopupSwipeEnabled = true,
                itemActions = itemActions
            )
        }
    }
}
