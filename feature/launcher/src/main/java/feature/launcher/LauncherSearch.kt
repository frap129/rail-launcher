package feature.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Calculator
import compose.icons.fontawesomeicons.solid.Comment
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Phone
import compose.icons.fontawesomeicons.solid.Search
import compose.icons.fontawesomeicons.solid.Store
import compose.icons.fontawesomeicons.solid.TimesCircle
import compose.icons.fontawesomeicons.solid.User
import core.data.search.SearchResult
import core.data.shortcuts.ShortcutLaunchable
import core.ui.composables.RequestKeyboardFocus
import core.ui.composables.toTextFieldValueAtEnd

private val searchFieldPadding: Dp = 16.dp
private val searchFieldVerticalPadding: Dp = 8.dp
private val topResultPadding: Dp = 16.dp
private val resultsBottomPadding: Dp = 16.dp

private class SearchAnchors {
    val byKey: MutableMap<String, PopupAnchor> = mutableMapOf()
}

/**
 * The Search screen: a focused query field whose Go action opens the Top result of the query it
 * shows or copies a Calculator row, and the results in match order. Launchable and Shortcut rows
 * are the App List's rows, so their menu, Pop-up, Profile badge and Custom icon stay the same as
 * the App List. Before typing, the rows are Search suggestions and no Top result is marked.
 * Bottom-anchored results reverse only the list, keeping the query field at the top so the Top
 * result ends up next to the keyboard.
 */
@Composable
internal fun LauncherSearch(
    modifier: Modifier,
    search: LauncherUiState.Search,
    shouldAutoLaunch: (SearchResult.LaunchableResult) -> Boolean,
    itemActions: LauncherItemActions,
    onQueryChange: (String) -> Unit,
    onSearchGo: (String) -> SearchResult?,
    onCopyCalculatorRow: (String) -> Unit,
    onOpenSearchRow: (SearchResult.ExternalRow) -> Unit,
    onContactRowAction: (SearchResult.ContactRow, ContactRowAction) -> Unit
) {
    val focusRequester: FocusRequester = remember { FocusRequester() }
    // The field owns the query while typing, so each Typed character shows and reaches the
    // launcher before its UI state round trip.
    var fieldValue: TextFieldValue by remember { mutableStateOf(search.query.toTextFieldValueAtEnd()) }
    // The texts the field typed that have not returned through the launcher state yet.
    val sentQueries: MutableList<String> = remember { mutableListOf() }
    fun updateFieldValue(value: TextFieldValue) {
        val isTextChanged: Boolean = value.text != fieldValue.text
        fieldValue = value
        if (isTextChanged) {
            sentQueries += value.text
            onQueryChange(value.text)
        }
    }
    LaunchedEffect(search.query) {
        val externalQuery: String = search.query
        val echoIndex: Int = sentQueries.lastIndexOf(externalQuery)
        when {
            externalQuery == fieldValue.text -> {
                // The echo of the latest text: keep the cursor and any composition as typed.
                sentQueries.clear()
            }

            echoIndex >= 0 -> {
                // An earlier edit's echo: the field already shows newer text, so keep it.
                sentQueries.subList(0, echoIndex + 1).clear()
            }

            else -> {
                // A change from outside the field, including a clear or a hardware typed
                // character, is adopted with the cursor after its characters.
                fieldValue = externalQuery.toTextFieldValueAtEnd()
                sentQueries.clear()
            }
        }
    }
    val searchAnchors: SearchAnchors = remember { SearchAnchors() }
    val searchItemActions: LauncherItemActions = remember(itemActions, searchAnchors) {
        itemActions.copy(
            onItemAnchorChanged = { launchable, anchor ->
                searchAnchors.byKey[launchable.key] = anchor
                itemActions.onItemAnchorChanged(launchable, anchor)
            }
        )
    }
    val onResultTap: (SearchResult) -> Unit = { result ->
        when (result) {
            is SearchResult.LaunchableResult -> searchItemActions.onItemTap(
                result.launchable,
                searchAnchors.byKey[result.launchable.key]
            )

            is SearchResult.CalculatorRow -> onCopyCalculatorRow(result.value)

            is SearchResult.ContactRow -> onContactRowAction(result, ContactRowAction.Open)

            is SearchResult.ExternalRow -> onOpenSearchRow(result)
        }
    }
    LaunchedEffect(search.autoLaunchResult) {
        val result: SearchResult.LaunchableResult = search.autoLaunchResult ?: return@LaunchedEffect
        if (shouldAutoLaunch(result)) {
            onResultTap(result)
        }
    }
    RequestKeyboardFocus(focusRequester = focusRequester)
    Column(modifier = modifier) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { value -> updateFieldValue(value) },
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
                if (fieldValue.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            updateFieldValue("".toTextFieldValueAtEnd())
                        }
                    ) {
                        Icon(
                            imageVector = FontAwesomeIcons.Solid.TimesCircle,
                            contentDescription = stringResource(R.string.clear_search)
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSearchGo(fieldValue.text)?.let(onResultTap) })
        )
        LauncherSearchResults(
            modifier = Modifier.weight(1f),
            query = search.query,
            results = search.results,
            itemActions = searchItemActions,
            topResultKey = search.topResult?.key,
            isBottomAnchored = search.isBottomAnchored,
            onResultTap = onResultTap,
            onContactRowAction = onContactRowAction
        )
    }
}

@Composable
private fun LauncherSearchResults(
    modifier: Modifier,
    query: String,
    results: List<SearchResult>,
    itemActions: LauncherItemActions,
    topResultKey: String?,
    isBottomAnchored: Boolean,
    onResultTap: (SearchResult) -> Unit,
    onContactRowAction: (SearchResult.ContactRow, ContactRowAction) -> Unit
) {
    val listState: LazyListState = rememberLazyListState()
    // Only a new query or anchoring resets the list, so rows appended after it stay in place.
    LaunchedEffect(query, isBottomAnchored) {
        if (results.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = isBottomAnchored,
        contentPadding = PaddingValues(bottom = resultsBottomPadding)
    ) {
        items(items = results, key = { result -> result.key }) { result ->
            if (result.key == topResultKey) {
                Text(
                    text = stringResource(R.string.top_result),
                    modifier = Modifier.padding(start = topResultPadding, top = topResultPadding),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            when (result) {
                is SearchResult.LaunchableResult -> LauncherItem(
                    item = result.launchable,
                    isEditingFavorites = false,
                    isFavorite = false,
                    isPopupSwipeEnabled = true,
                    itemActions = itemActions,
                    secondaryText = (result.launchable as? ShortcutLaunchable)?.publisherName
                )

                is SearchResult.CalculatorRow -> LauncherActionRow(
                    imageVector = FontAwesomeIcons.Solid.Calculator,
                    label = stringResource(R.string.calculator_row, result.expression, result.value),
                    onClick = { onResultTap(result) }
                )

                is SearchResult.ContactRow -> LauncherActionRow(
                    imageVector = FontAwesomeIcons.Solid.User,
                    label = result.contact.name,
                    onClick = { onResultTap(result) },
                    onClickLabel = stringResource(R.string.search_contact_open),
                    trailingContent = if (result.contact.phoneNumber != null) {
                        {
                            IconButton(onClick = { onContactRowAction(result, ContactRowAction.Dial) }) {
                                Icon(
                                    imageVector = FontAwesomeIcons.Solid.Phone,
                                    contentDescription = stringResource(R.string.search_contact_call, result.contact.name)
                                )
                            }
                            IconButton(onClick = { onContactRowAction(result, ContactRowAction.Message) }) {
                                Icon(
                                    imageVector = FontAwesomeIcons.Solid.Comment,
                                    contentDescription = stringResource(R.string.search_contact_message, result.contact.name)
                                )
                            }
                        }
                    } else {
                        null
                    }
                )

                is SearchResult.ExternalRow -> {
                    val action: SearchRowAction = result.searchRowAction()
                    LauncherActionRow(
                        imageVector = action.imageVector,
                        label = action.label,
                        onClick = { onResultTap(result) }
                    )
                }
            }
        }
    }
}

/** The leading icon and label of a Search result row that opens outside Rail. */
private data class SearchRowAction(val imageVector: ImageVector, val label: String)

@Composable
private fun SearchResult.ExternalRow.searchRowAction(): SearchRowAction = when (this) {
    is SearchResult.TypedUrlRow -> SearchRowAction(
        imageVector = FontAwesomeIcons.Solid.Globe,
        label = stringResource(R.string.search_open_url, url)
    )

    is SearchResult.WebSearchRow -> SearchRowAction(
        imageVector = FontAwesomeIcons.Solid.Search,
        label = stringResource(R.string.search_web_action)
    )

    is SearchResult.WebSuggestionRow -> SearchRowAction(
        imageVector = FontAwesomeIcons.Solid.Search,
        label = suggestion
    )

    is SearchResult.PlayStoreRow -> SearchRowAction(
        imageVector = FontAwesomeIcons.Solid.Store,
        label = stringResource(R.string.search_play_store_action)
    )
}
