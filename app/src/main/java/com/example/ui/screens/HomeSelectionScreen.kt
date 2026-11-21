package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CoachLanguage
import com.example.model.ExerciseSpec
import com.example.ui.theme.CharcoalBg
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TimerMonoFontFamily
import com.example.viewmodel.JoshFitUiState

@Composable
fun HomeSelectionScreen(
    uiState: JoshFitUiState,
    onSelectExercise: (ExerciseSpec) -> Unit,
    onScanRoomSetup: (ExerciseSpec) -> Unit,
    onStartWorkout: (ExerciseSpec, Boolean) -> Unit,
    onAdjustSets: (Int) -> Unit,
    onAdjustWorkSec: (Int) -> Unit,
    onAdjustRestSec: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hinglish = uiState.streakStats.language == CoachLanguage.HINGLISH

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroMotivationCard(
                    hinglish = hinglish,
                    streakDays = uiState.streakStats.streakDays,
                    totalWorkouts = uiState.streakStats.totalWorkouts,
                    totalMinutes = uiState.streakStats.totalMinutes
                )
            }

            item {
                TimerCustomizerCard(
                    hinglish = hinglish,
                    selectedExercise = uiState.selectedExercise,
                    sets = uiState.totalSets,
                    workSec = uiState.workDurationSec,
                    restSec = uiState.restDurationSec,
                    onAdjustSets = onAdjustSets,
                    onAdjustWorkSec = onAdjustWorkSec,
                    onAdjustRestSec = onAdjustRestSec
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hinglish) "STARTER HOME WORKOUTS" else "STARTER HOME WORKOUTS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MutedSilver
                    )
                    Text(
                        text = if (hinglish) "AI Camera Coach Ready" else "AI Camera Coach Ready",
                        style = MaterialTheme.typography.labelMedium,
                        color = CrimsonBright
                    )
                }
            }

            items(uiState.workouts, key = { it.type.name }) { exercise ->
                val isSelected = exercise.type == uiState.selectedExercise.type
                WorkoutCard(
                    exercise = exercise,
                    isSelected = isSelected,
                    hinglish = hinglish,
                    sets = if (isSelected) uiState.totalSets else exercise.defaultSets,
                    workSec = if (isSelected) uiState.workDurationSec else exercise.defaultWorkSeconds,
                    restSec = if (isSelected) uiState.restDurationSec else exercise.defaultRestSeconds,
                    onSelect = { onSelectExercise(exercise) },
                    onStartNormal = { onStartWorkout(exercise, false) },
                    onScanRoomSetup = { onScanRoomSetup(exercise) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HeroMotivationCard(
    hinglish: Boolean,
    streakDays: Int,
    totalWorkouts: Int,
    totalMinutes: Int
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            CrimsonContainer,
                            CharcoalCard,
                            CharcoalBg
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = CrimsonPrimary,
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (hinglish) "AAJ KA JOSH" else "DAILY GRIND",
                                style = MaterialTheme.typography.labelMedium,
                                color = PureWhite
                            )
                        }
                    }

                    Text(
                        text = if (hinglish) "No Equipment Needed" else "Zero Equipment Needed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSilver
                    )
                }

                Text(
                    text = if (hinglish) {
                        "Chalo Apna Form Perfect Karein!"
                    } else {
                        "Train Harder With Live AI Form Coaching"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = PureWhite
                )

                Text(
                    text = if (hinglish) {
                        "Workout chuno, timer chalao aur Camera AI Coach se real-time posture tips pao."
                    } else {
                        "Pick a starter workout, fire up the beat timer, and get real-time Camera AI posture cues."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSilver
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMiniPill(
                        icon = Icons.Filled.LocalFireDepartment,
                        iconTint = FlameOrange,
                        value = "$streakDays",
                        label = if (hinglish) "Day Streak" else "Day Streak",
                        modifier = Modifier.weight(1f)
                    )
                    StatMiniPill(
                        icon = Icons.Filled.FitnessCenter,
                        iconTint = CrimsonBright,
                        value = "$totalWorkouts",
                        label = if (hinglish) "Sessions" else "Sessions",
                        modifier = Modifier.weight(1f)
                    )
                    StatMiniPill(
                        icon = Icons.Filled.Timer,
                        iconTint = PureWhite,
                        value = "${totalMinutes}m",
                        label = if (hinglish) "Active" else "Active",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMiniPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = CharcoalElevated.copy(alpha = 0.85f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CharcoalOutline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TimerMonoFontFamily),
                    color = PureWhite
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedSilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TimerCustomizerCard(
    hinglish: Boolean,
    selectedExercise: ExerciseSpec,
    sets: Int,
    workSec: Int,
    restSec: Int,
    onAdjustSets: (Int) -> Unit,
    onAdjustWorkSec: (Int) -> Unit,
    onAdjustRestSec: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, CharcoalOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hinglish) {
                        "Timer Setup • ${selectedExercise.titleEn}"
                    } else {
                        "Session Setup • ${selectedExercise.titleEn}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                Text(
                    text = if (hinglish) "Tap +/- to adjust" else "Tap +/- to adjust",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedSilver
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StepperBox(
                    label = if (hinglish) "Sets" else "Sets",
                    valueText = "$sets",
                    onMinus = { onAdjustSets(-1) },
                    onPlus = { onAdjustSets(1) },
                    tagPrefix = "sets",
                    modifier = Modifier.weight(1f)
                )
                StepperBox(
                    label = if (hinglish) "Work" else "Work",
                    valueText = "${workSec}s",
                    onMinus = { onAdjustWorkSec(-5) },
                    onPlus = { onAdjustWorkSec(5) },
                    tagPrefix = "work",
                    modifier = Modifier.weight(1f)
                )
                StepperBox(
                    label = if (hinglish) "Rest" else "Rest",
                    valueText = "${restSec}s",
                    onMinus = { onAdjustRestSec(-5) },
                    onPlus = { onAdjustRestSec(5) },
                    tagPrefix = "rest",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StepperBox(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = CharcoalElevated,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MutedSilver
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onMinus,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CharcoalCard,
                        contentColor = PureWhite
                    ),
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("${tagPrefix}_minus_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Decrease $label",
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = TimerMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PureWhite
                )

                IconButton(
                    onClick = onPlus,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CrimsonPrimary.copy(alpha = 0.2f),
                        contentColor = CrimsonBright
                    ),
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("${tagPrefix}_plus_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Increase $label",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    exercise: ExerciseSpec,
    isSelected: Boolean,
    hinglish: Boolean,
    sets: Int,
    workSec: Int,
    restSec: Int,
    onSelect: () -> Unit,
    onStartNormal: () -> Unit,
    onScanRoomSetup: () -> Unit
) {
    val lang = if (hinglish) CoachLanguage.HINGLISH else CoachLanguage.ENGLISH
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CrimsonPrimary else CharcoalOutline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect)
            .testTag("workout_card_${exercise.type.name.lowercase()}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
            ) {
                Image(
                    painter = painterResource(id = exercise.imageRes),
                    contentDescription = exercise.title(lang),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    CharcoalBg.copy(alpha = 0.45f),
                                    CharcoalCard
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = CharcoalBg.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(50.dp),
                        border = BorderStroke(1.dp, CharcoalOutline)
                    ) {
                        Text(
                            text = exercise.difficultyLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (isSelected) {
                        Surface(
                            color = CrimsonPrimary,
                            shape = CircleShape
                        ) {
                            Text(
                                text = if (hinglish) "SELECTED" else "SELECTED",
                                style = MaterialTheme.typography.labelMedium,
                                color = PureWhite,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = exercise.title(lang),
                        style = MaterialTheme.typography.headlineLarge,
                        color = PureWhite
                    )
                    Text(
                        text = exercise.subtitle(lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSilver
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetaTag(text = "$sets Sets")
                    MetaTag(text = "${workSec}s Work")
                    MetaTag(text = "${restSec}s Rest")
                    MetaTag(
                        text = if (exercise.isHoldExercise) "Hold" else "${exercise.targetRepsPerSet} Reps/set"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onStartNormal,
                        border = BorderStroke(1.dp, CharcoalOutline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                        modifier = Modifier
                            .weight(0.75f)
                            .height(48.dp)
                            .testTag("start_timer_${exercise.type.name.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Timer",
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = onScanRoomSetup,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonPrimary,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .weight(1.45f)
                            .height(48.dp)
                            .testTag("scan_room_setup_${exercise.type.name.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scan Room & Setup Camera",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaTag(text: String) {
    Surface(
        color = CharcoalElevated,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = PureWhite,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
