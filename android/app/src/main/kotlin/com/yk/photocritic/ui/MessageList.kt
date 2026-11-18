package com.yk.photocritic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yk.photocritic.R
import com.yk.photocritic.core.providers.shared.Segment
import com.yk.photocritic.core.session.MessageStatus
import com.yk.photocritic.core.session.VisibleMessage
import com.yk.photocritic.ui.design.ErrorBlock
import com.yk.photocritic.ui.design.Radii
import com.yk.photocritic.ui.design.SmallAction
import com.yk.photocritic.ui.design.Spacing
import com.yk.photocritic.ui.design.Tokens

/** The conversation (file 38): stable keys, so a streaming answer redraws only its own item. */
@Composable
fun MessageList(
    messages: List<VisibleMessage>,
    actions: MessageActions,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val last = messages.lastOrNull()
    LaunchedEffect(messages.size, last?.answer?.length, last?.status) {
        if (messages.isNotEmpty()) state.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(modifier, state = state, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        items(messages, key = { it.id }) { m -> MessageItem(m, actions.takeIf { m == last } ?: actions.withoutDismiss()) }
    }
}

private fun MessageActions.withoutDismiss() = MessageActions(copy, share, showDetails, null)

@Composable
fun MessageItem(
    m: VisibleMessage,
    actions: MessageActions,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                m.question,
                color = Tokens.text,
                modifier =
                    Modifier
                        .widthIn(
                            max = 320.dp,
                        ).background(Tokens.field, Radii.small)
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
        }
        m.segments.forEach { segment ->
            when (segment) {
                is Segment.Text -> SelectionContainer { Text(segment.text, color = Tokens.text, fontSize = 16.sp, lineHeight = 23.sp) }
                is Segment.Card -> TextCard(segment.text, actions)
            }
        }
        if (m.status == MessageStatus.STREAMING && m.answer.isEmpty()) {
            val answering = stringResource(R.string.answering)
            CircularProgressIndicator(
                color = Tokens.accent,
                strokeWidth = 2.dp,
                modifier =
                    Modifier.size(20.dp).semantics {
                        contentDescription =
                            answering
                    },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (m.status) {
                MessageStatus.STOPPED -> Tag(stringResource(R.string.tag_stopped))
                MessageStatus.FAILED -> if (m.answer.isNotEmpty()) Tag(stringResource(R.string.tag_failed))
                else -> Unit
            }
            if (m.status != MessageStatus.STREAMING && m.answer.isNotEmpty()) {
                SmallAction(R.drawable.ic_copy, stringResource(R.string.copy_answer)) { actions.copy(m.answer) }
            }
        }
        m.error?.let { error -> ErrorBlock(onShowDetails = { actions.showDetails(error) }, onDismiss = actions.dismiss) }
    }
}

/** "Text in photo" (G3, CUJ 2): keeps line breaks; Copy and Share give the text exactly. */
@Composable
private fun TextCard(
    text: String,
    actions: MessageActions,
) {
    Column(Modifier.fillMaxWidth().background(Tokens.surface, Radii.card).padding(Spacing.m)) {
        Text(stringResource(R.string.card_title), color = Tokens.muted, fontSize = 13.sp, modifier = Modifier.semantics { heading() })
        SelectionContainer {
            Text(text, color = Tokens.text, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(vertical = Spacing.s))
        }
        Row {
            SmallAction(R.drawable.ic_copy, stringResource(R.string.copy)) { actions.copy(text) }
            SmallAction(R.drawable.ic_share_2, stringResource(R.string.share)) { actions.share(text) }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        color = Tokens.text,
        fontSize = 12.sp,
        modifier = Modifier.background(Tokens.field, Radii.pill).padding(horizontal = Spacing.s, vertical = 2.dp),
    )
}
