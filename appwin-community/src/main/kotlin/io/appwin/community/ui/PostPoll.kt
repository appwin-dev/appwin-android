package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.appwin.community.domain.CommunityConfig
import kotlin.math.roundToInt

/**
 * Figma feed poll: bg/low options; once results show, a bar fills each one to
 * its share, the member's pick (or the leader) in the accent gradient on a
 * brand-soft track, the others in the border tone, with the percentage.
 */
@Composable
internal fun PostPollBlock(
  poll: io.appwin.community.domain.CommunityPoll,
  config: CommunityConfig,
  showsResults: Boolean,
  onVote: (String) -> Unit,
  ts: (Float) -> TextUnit,
) {
  val accent = MaterialTheme.colorScheme.primary
  val optionShape = RoundedCornerShape(config.radiusField)
  val hasVoted = poll.myOptionId != null
  val leaderVotes = poll.options.maxOfOrNull { it.voteCount } ?: 0

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    poll.options.forEach { option ->
      val ratio =
        if (poll.totalVotes > 0) option.voteCount.toFloat() / poll.totalVotes.toFloat() else 0f
      val highlighted = showsResults && (
        poll.myOptionId == option.id ||
          (!hasVoted && leaderVotes > 0 && option.voteCount == leaderVotes)
        )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(optionShape)
          .background(if (highlighted) accent.copy(alpha = 0.24f) else CommunityColors.surfaceMuted)
          .then(if (!hasVoted) Modifier.clickable { onVote(option.id) } else Modifier),
      ) {
        if (showsResults && ratio > 0f) {
          Box(modifier = Modifier.matchParentSize()) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .clip(optionShape)
                .then(
                  if (highlighted) Modifier.background(accentPillBrush(config))
                  else Modifier.background(CommunityColors.border),
                ),
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = option.text,
            fontSize = ts(14f),
            fontWeight = FontWeight.Medium,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary else CommunityColors.textPrimary,
            modifier = Modifier.weight(1f),
          )
          if (showsResults) {
            Text(
              text = "${(ratio * 100).roundToInt()}%",
              fontSize = ts(12f),
              fontWeight = FontWeight.Medium,
              color = CommunityColors.textPrimary,
            )
          }
        }
      }
    }
  }
}
