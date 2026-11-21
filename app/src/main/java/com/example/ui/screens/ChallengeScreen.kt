package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ChallengeDayLog
import com.example.model.ChallengeDayPlan
import com.example.model.ChallengePlan30Days
import com.example.model.CoachLanguage
import com.example.ui.theme.AdjustAmber
import com.example.ui.theme.AdjustAmberBg
import com.example.ui.theme.CharcoalBg
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.GoodFormBg
import com.example.ui.theme.GoodFormGreen
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RestCyan
import com.example.ui.theme.RestCyanBg
import com.example.ui.theme.TimerMonoFontFamily
import com.example.viewmodel.JoshFitUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChallengeScreen(
    uiState: JoshFitUiState,
    onSelectDay: (Int) -> Unit,
    onStartDayWithCameraSetup: (Int) -> Unit,
    onStartDayTimerOnly: (Int) -> Unit,
    onMarkRestCompleted: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = uiState.challengeProgress
    val lang = uiState.streakStats.language
    val hinglish = lang == CoachLanguage.HINGLISH

    val selectedDayNumber = progress.selectedDayNumber.coerceIn(1, 30)
    val selectedPlan = progress.plans[selectedDayNumber - 1]
    val selectedLog = progress.logs[selectedDayNumber]

    val workoutLogsSorted = remember(progress.logs) {
        progress.logs.values.sortedBy { it.dayNumber }
    }
    val latestLog = workoutLogsSorted.lastOrNull()
    val previousWorkoutLog = workoutLogsSorted
        .filter { !it.isRestCompleted && it.dayNumber != latestLog?.dayNumber }
        .lastOrNull()

    val comparisonBanner = remember(latestLog, previousWorkoutLog, lang) {
        ChallengePlan30Days.buildComparisonMessage(latestLog, previousWorkoutLog, lang)
    }

    val dateFormatter = remember {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Progress Header
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(CrimsonContainer, CharcoalCard, CharcoalBg)
                                )
                            )
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (hinglish) "30-DAY PUSH-UP CHALLENGE" else "30-DAY PUSH-UP CHALLENGE",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = PureWhite
                                )
                                Text(
                                    text = if (hinglish) {
                                        "Active Day: Day ${progress.activeDayNumber} of 30"
                                    } else {
                                        "Active Day: Day ${progress.activeDayNumber} of 30"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CrimsonBright
                                )
                            }
                            Surface(
                                color = CrimsonPrimary,
                                shape = CircleShape,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.EmojiEvents,
                                        contentDescription = "30-Day Challenge",
                                        tint = PureWhite
                                    )
                                }
                            }
                        }

                        LinearProgressIndicator(
                            progress = { (progress.completedDaysCount / 30f).coerceIn(0f, 1f) },
                            color = CrimsonPrimary,
                            trackColor = CharcoalElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50.dp))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ChallengeStatTile(
                                label = if (hinglish) "Days Done" else "Days Done",
                                value = "${progress.completedDaysCount}/30",
                                modifier = Modifier.weight(1f)
                            )
                            ChallengeStatTile(
                                label = if (hinglish) "Total Push-Ups" else "Total Push-Ups",
                                value = "${progress.totalPushUpsLogged}",
                                modifier = Modifier.weight(1f)
                            )
                            ChallengeStatTile(
                                label = if (hinglish) "Avg Form" else "Avg Form",
                                value = if (progress.completedDaysCount > 0) "${progress.averageFormAccuracy}%" else "—",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Daily Comparison / Coaching Banner
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("challenge_comparison_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = FlameOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (hinglish) "AAJ KA COACH FEEDBACK" else "DAILY PROGRESS COACH",
                                style = MaterialTheme.typography.labelMedium,
                                color = FlameOrange
                            )
                            Text(
                                text = comparisonBanner,
                                style = MaterialTheme.typography.bodyLarge,
                                color = PureWhite
                            )
                        }
                    }
                }
            }

            // Day 1 - 30 Horizontal Selector Strip
            item {
                Text(
                    text = if (hinglish) "DAY 1 SE DAY 30 PLAN" else "DAY 1 TO DAY 30 PLAN",
                    style = MaterialTheme.typography.labelLarge,
                    color = MutedSilver
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(progress.plans, key = { it.dayNumber }) { plan ->
                        val log = progress.logs[plan.dayNumber]
                        val isSelected = plan.dayNumber == selectedDayNumber
                        val isActive = plan.dayNumber == progress.activeDayNumber
                        DayChip(
                            plan = plan,
                            log = log,
                            isSelected = isSelected,
                            isActive = isActive,
                            onClick = { onSelectDay(plan.dayNumber) }
                        )
                    }
                }
            }

            // Selected Day Action Card
            item {
                SelectedDayCard(
                    plan = selectedPlan,
                    log = selectedLog,
                    hinglish = hinglish,
                    lang = lang,
                    dateFormatter = dateFormatter,
                    onStartWithCamera = { onStartDayWithCameraSetup(selectedPlan.dayNumber) },
                    onStartTimerOnly = { onStartDayTimerOnly(selectedPlan.dayNumber) },
                    onMarkRestCompleted = { onMarkRestCompleted(selectedPlan.dayNumber) }
                )
            }

            // Daily Workout Log History (Day 1 to Day 30)
            item {
                Text(
                    text = if (hinglish) "COMPLETED DAYS LOG" else "DAILY WORKOUT LOG",
                    style = MaterialTheme.typography.labelLarge,
                    color = MutedSilver
                )
            }

            if (workoutLogsSorted.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        border = BorderStroke(1.dp, CharcoalOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (hinglish) {
                                "Abhi koi challenge day log nahi hua hai. Day 1 start karein — aapke reps, form % aur date yahan save honge!"
                            } else {
                                "No challenge days logged yet. Start Day 1 above to automatically record reps, form accuracy, and date!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSilver,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(workoutLogsSorted.reversed(), key = { "log_${it.dayNumber}" }) { log ->
                    ChallengeLogRow(
                        log = log,
                        lang = lang,
                        dateText = dateFormatter.format(Date(log.completedAtMillis))
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ChallengeStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = CharcoalElevated.copy(alpha = 0.85f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CharcoalOutline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = TimerMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
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

@Composable
private fun DayChip(
    plan: ChallengeDayPlan,
    log: ChallengeDayLog?,
    isSelected: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> CrimsonPrimary
        log != null -> GoodFormBg
        plan.isRestDay -> RestCyanBg
        isActive -> CrimsonContainer
        else -> CharcoalCard
    }
    val borderColor = when {
        isSelected -> PureWhite
        log != null -> GoodFormGreen.copy(alpha = 0.65f)
        isActive -> CrimsonBright
        plan.isRestDay -> RestCyan.copy(alpha = 0.5f)
        else -> CharcoalOutline
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .size(width = 74.dp, height = 74.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("challenge_day_chip_${plan.dayNumber}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "D${plan.dayNumber}",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = TimerMonoFontFamily),
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    log != null -> if (log.isRestCompleted) "Rest ✓" else "${log.completedReps} ✓"
                    plan.isRestDay -> "REST"
                    else -> "${plan.targetReps}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    log != null -> GoodFormGreen
                    plan.isRestDay -> RestCyan
                    else -> PureWhite.copy(alpha = 0.9f)
                }
            )
        }
    }
}

@Composable
private fun SelectedDayCard(
    plan: ChallengeDayPlan,
    log: ChallengeDayLog?,
    hinglish: Boolean,
    lang: CoachLanguage,
    dateFormatter: SimpleDateFormat,
    onStartWithCamera: () -> Unit,
    onStartTimerOnly: () -> Unit,
    onMarkRestCompleted: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, CharcoalOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hinglish) plan.titleHinglish else plan.titleEn,
                        style = MaterialTheme.typography.headlineLarge,
                        color = PureWhite
                    )
                    Text(
                        text = if (hinglish) plan.focusHintHinglish else plan.focusHintEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSilver
                    )
                }

                Surface(
                    color = if (plan.isRestDay) RestCyanBg else CrimsonContainer,
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Text(
                        text = if (plan.isRestDay) "RECOVERY" else "TARGET ${plan.targetReps}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (plan.isRestDay) RestCyan else CrimsonBright,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (log != null) {
                Surface(
                    color = CharcoalElevated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (log.isRestCompleted) {
                                    "Recovery Day Done"
                                } else {
                                    "${log.completedReps} / ${log.targetReps} Push-Ups • ${log.formAccuracyPercent}% Form"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite
                            )
                            Text(
                                text = dateFormatter.format(Date(log.completedAtMillis)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSilver
                            )
                        }
                        PostureChip(
                            label = log.postureLabel(lang),
                            isGood = log.isPostureGood
                        )
                    }
                }
            }

            if (plan.isRestDay) {
                Button(
                    onClick = onMarkRestCompleted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RestCyan,
                        contentColor = CharcoalBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("complete_rest_day_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SelfImprovement,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (log != null) {
                            if (hinglish) "Recovery Logged ✓" else "Recovery Logged ✓"
                        } else {
                            if (hinglish) "Mark Day ${plan.dayNumber} Recovery Done" else "Complete Recovery Day"
                        }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onStartTimerOnly,
                        border = BorderStroke(1.dp, CharcoalOutline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                        modifier = Modifier
                            .weight(0.75f)
                            .height(48.dp)
                            .testTag("challenge_start_timer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Timer")
                    }

                    Button(
                        onClick = onStartWithCamera,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonPrimary,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("challenge_start_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Start Day ${plan.dayNumber} + AI Coach",
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
private fun ChallengeLogRow(
    log: ChallengeDayLog,
    lang: CoachLanguage,
    dateText: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, CharcoalOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = if (log.isPostureGood) GoodFormBg else AdjustAmberBg,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (log.isPostureGood) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (log.isPostureGood) GoodFormGreen else AdjustAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = if (log.isRestCompleted) {
                            "Day ${log.dayNumber}: Rest & Recovery"
                        } else {
                            "Day ${log.dayNumber}: ${log.completedReps} / ${log.targetReps} Push-Ups"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite
                    )
                    Text(
                        text = if (log.isRestCompleted) {
                            dateText
                        } else {
                            "${log.formAccuracyPercent}% Form • $dateText"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSilver
                    )
                }
            }

            PostureChip(
                label = log.postureLabel(lang),
                isGood = log.isPostureGood
            )
        }
    }
}

@Composable
private fun PostureChip(label: String, isGood: Boolean) {
    Surface(
        color = if (isGood) GoodFormBg else AdjustAmberBg,
        shape = RoundedCornerShape(50.dp),
        border = BorderStroke(1.dp, if (isGood) GoodFormGreen else AdjustAmber)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isGood) GoodFormGreen else AdjustAmber,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
