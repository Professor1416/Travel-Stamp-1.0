package com.example.ui.reminder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.TripReminderPreset
import com.example.ui.components.Spacing
import com.example.ui.theme.ForestPine

/**
 * Compact, Material 3 pre-trip journey reminder section for Create and Edit Journey flows.
 *
 * Adheres strictly to Travel Stamp N6.2 design and UX specifications:
 * - Switch controls enabled state (OFF by default for new journeys)
 * - Single-choice selector (ExposedDropdownMenu) for the 4 allowed presets
 * - Displays user-facing strings without emojis
 * - Clear helper copy reflecting the current state/preset
 * - Inline validation error feedback (e.g. StartTimeRequired, TriggerAlreadyPassed)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyReminderSection(
    reminderEnabled: Boolean,
    onReminderEnabledChange: (Boolean) -> Unit,
    selectedPreset: TripReminderPreset,
    onPresetSelected: (TripReminderPreset) -> Unit,
    validationResult: ReminderFormValidation,
    onRequestPermission: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    containerTestTag: String = "reminder_section_container",
    switchTestTag: String = "reminder_enable_switch",
    presetSelectorTestTag: String = "reminder_preset_selector",
    dropdownItemTagPrefix: String = "reminder_preset_dropdown_item_",
    helperTextTestTag: String = "reminder_helper_text",
    validationErrorTestTag: String = "reminder_validation_error",
    isCardContainer: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    val content = @Composable {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCardContainer) Spacing.cardPadding else Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header Row with Title, Status, and Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (reminderEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = if (reminderEnabled) ForestPine else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Column {
                        Text(
                            text = stringResource(R.string.journey_reminder_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (reminderEnabled) {
                                stringResource(R.string.journey_reminder_status_active)
                            } else {
                                stringResource(R.string.journey_reminder_status_off)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            onRequestPermission {
                                onReminderEnabledChange(true)
                            }
                        } else {
                            onReminderEnabledChange(false)
                        }
                    },
                    modifier = Modifier.testTag(switchTestTag),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ForestPine
                    )
                )
            }

            // Compact Dropdown Selector when Reminder is ON
            AnimatedVisibility(
                visible = reminderEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = stringResource(selectedPreset.labelResId),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.journey_reminder_remind_me)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (!validationResult.isValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (!validationResult.isValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag(presetSelectorTestTag)
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            TripReminderPreset.ALL_PRESETS_IN_ORDER.forEach { preset ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(preset.labelResId),
                                            fontWeight = if (preset == selectedPreset) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onPresetSelected(preset)
                                        expanded = false
                                    },
                                    modifier = Modifier.testTag("$dropdownItemTagPrefix${preset.name}")
                                )
                            }
                        }
                    }
                }
            }

            // Inline Validation Error Message (when reminder is ON and invalid)
            if (reminderEnabled && !validationResult.isValid) {
                val errorMsg = validationResult.errorMessageResId?.let { stringResource(it) }
                    ?: stringResource(R.string.journey_reminder_time_passed)
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(validationErrorTestTag)
                )
            }

            // Supporting Truthful Helper Copy
            val helperCopy = if (reminderEnabled) {
                stringResource(selectedPreset.helperCopyResId)
            } else {
                stringResource(R.string.journey_reminder_optional)
            }

            Text(
                text = helperCopy,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 12.sp,
                modifier = Modifier.testTag(helperTextTestTag)
            )
        }
    }

    if (isCardContainer) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag(containerTestTag),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag(containerTestTag),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            content()
        }
    }
}
