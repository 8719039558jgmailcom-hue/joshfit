package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.CoachLanguage
import com.example.model.SessionSummaryState
import com.example.ui.theme.CharcoalBg
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.GoodFormGreen
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TimerMonoFontFamily

@Composable
fun SessionSummaryScreen(
    summary: SessionSummaryState,
    language: CoachLanguage,
    onRestartWorkout: () -> Unit,
    onBackToHome: () -> Unit,
    onOpenChallenge: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hinglish = language == CoachLanguage.HINGLISH

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Victory Hero Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                border = BorderStroke(1.5.dp, CrimsonPrimary.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(CrimsonContainer, CharcoalCard)
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = CrimsonPrimary,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = "Workout Complete",
                                    tint = PureWhite,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        Text(
                            text = if (hinglish) "KAMAAL KAR DIYA!" else "WORKOUT COMPLETE!",
                            style = MaterialTheme.typography.displayMedium,
                            color = PureWhite,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = summary.exercise.title(language),
                            style = MaterialTheme.typography.titleLarge,
                            color = CrimsonBright
                        )

                        Surface(
                            color = CharcoalElevated,
                            shape = RoundedCornerShape(50.dp),
                            border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.55f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = FlameOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (hinglish) {
                                        "${summary.updatedStreakDays} Day Streak Active!"
                                    } else {
                                        "${summary.updatedStreakDays} Day Streak Active!"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PureWhite
                                )
                            }
                        }
                    }
                }
            }

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryMetricCard(
                    title = if (hinglish) "Sets Done" else "Sets Done",
                    value = "${summary.setsCompleted}",
                    accentColor = PureWhite,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = if (summary.exercise.isHoldExercise) "Hold Time" else "Reps Logged",
                    value = if (summary.exercise.isHoldExercise) "${summary.totalWorkSeconds}s" else "${summary.repsCompleted}",
                    accentColor = CrimsonBright,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryMetricCard(
                    title = if (hinglish) "AI Form Score" else "AI Form Score",
                    value = if (summary.totalFormChecks > 0) "${summary.formAccuracyPercent}%" else "—",
                    accentColor = GoodFormGreen,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = if (hinglish) "Est. Burn" else "Est. Burn",
                    value = "${summary.estimatedCalories} kcal",
                    accentColor = FlameOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            // Gemini AI Recovery Tip Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                border = BorderStroke(1.dp, CharcoalOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = CrimsonBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (hinglish) "GEMINI AI COACH TIP" else "GEMINI AI COACH TIP",
                            style = MaterialTheme.typography.labelLarge,
                            color = CrimsonBright
                        )
                    }

                    when {
                        summary.isAiTipLoading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = CrimsonPrimary,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (hinglish) {
                                        "Tumhare session ke liye AI recovery tip ban rahi hai..."
                                    } else {
                                        "Generating personalized recovery advice..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSilver
                                )
                            }
                        }

                        summary.aiTipError != null -> {
                            Text(
                                text = summary.aiTipError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CrimsonBright
                            )
                        }

                        summary.aiTip.isNotBlank() -> {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = GoodFormGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = summary.aiTip,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = PureWhite
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRestartWorkout,
                    border = BorderStroke(1.dp, CharcoalOutline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("summary_restart_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (hinglish) "Phir Se" else "Repeat")
                }

                Button(
                    onClick = onBackToHome,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonPrimary,
                        contentColor = PureWhite
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(52.dp)
                        .testTag("summary_home_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (hinglish) "Home Par Jao" else "All Workouts")
                }
            }

            if (onOpenChallenge != null) {
                OutlinedButton(
                    onClick = onOpenChallenge,
                    border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonBright),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("summary_open_challenge_button")
                ) {
                    Text(
                        text = if (hinglish) {
                            "30-Day Push-Up Progress Dekhein →"
                        } else {
                            "View 30-Day Challenge Progress →"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryMetricCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, CharcoalOutline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MutedSilver
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = TimerMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor
            )
        }
    }
}
