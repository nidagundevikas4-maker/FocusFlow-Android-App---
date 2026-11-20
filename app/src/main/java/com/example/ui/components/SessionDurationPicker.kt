package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

private val DurationPresets = listOf(15, 25, 45, 60)

@Composable
fun SessionDurationPicker(
  selectedDurationMinutes: Int,
  onDurationSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.home_duration_label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text = "$selectedDurationMinutes min",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      DurationPresets.forEach { minutes ->
        val selected = minutes == selectedDurationMinutes
        Surface(
          onClick = { onDurationSelected(minutes) },
          modifier =
            Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("duration_chip_$minutes"),
          shape = MaterialTheme.shapes.small,
          color =
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
          border =
            BorderStroke(
              width = if (selected) 1.5.dp else 1.dp,
              color =
                if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
        ) {
          Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "${minutes}m",
              style = MaterialTheme.typography.labelLarge,
              color =
                if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}
