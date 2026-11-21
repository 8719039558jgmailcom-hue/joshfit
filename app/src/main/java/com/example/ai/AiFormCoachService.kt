package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.CoachLanguage
import com.example.model.ExerciseSpec
import com.example.model.FormFeedback
import com.example.model.FormStatus
import com.example.model.LightingStatus
import com.example.model.RoomSetupGuidance
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiFormCoachService"
private const val AI_REQUEST_TIMEOUT_MS = 60_000L

class AiFormCoachService {

    private val roomSetupSchema = Schema.obj(
        properties = mapOf(
            "lightingQuality" to Schema.enumeration(
                values = listOf("GOOD", "LOW", "TOO_DARK"),
                description = "Lighting condition observed in the room"
            ),
            "bestWorkoutSpot" to Schema.string(
                description = "Practical 1-2 sentence guidance pointing out clear floor space and lighting in the room"
            ),
            "exactCameraPlacement" to Schema.string(
                description = "Specific instruction on where, how high (in feet), at what angle, and how far back to place the phone"
            ),
            "spotReady" to Schema.boolean(
                description = "True if the room has usable floor space and enough light for a safe workout"
            )
        )
    )

    private val fullBodyVerifySchema = Schema.obj(
        properties = mapOf(
            "fullBodyVisible" to Schema.boolean(
                description = "True if the person's full body (or head-to-ankles required for this exercise) is clearly visible with adequate lighting"
            ),
            "message" to Schema.string(
                description = "Short, clear 1-sentence Hinglish or English instruction confirming readiness or telling them how to adjust camera/step back"
            )
        )
    )

    private val formSchema = Schema.obj(
        properties = mapOf(
            "formStatus" to Schema.enumeration(
                values = listOf("GOOD_FORM", "ADJUST_POSTURE", "GET_IN_FRAME"),
                description = "Overall posture status observed in the camera frame"
            ),
            "cue" to Schema.string(
                description = "Short, energetic coaching feedback (max 12 words) in the requested language"
            ),
            "postureTip" to Schema.string(
                description = "Specific biomechanical tip (max 10 words), e.g., straight back, deeper squat, elbows tucked"
            ),
            "repDetected" to Schema.boolean(
                description = "True if the person appears to be completing an active repetition peak/bottom"
            )
        )
    )

    private val roomModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = roomSetupSchema
                temperature = 0.3f
            }
        )
    }

    private val verifyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = fullBodyVerifySchema
                temperature = 0.2f
            }
        )
    }

    private val formModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = formSchema
                temperature = 0.25f
            }
        )
    }

    private val summaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.6f
            }
        )
    }

    suspend fun analyzeRoomSetup(
        bitmap: Bitmap,
        exercise: ExerciseSpec,
        language: CoachLanguage,
        localLighting: LightingStatus
    ): RoomSetupGuidance {
        val scaledBitmap = withContext(Dispatchers.Default) {
            scaleDownIfNeeded(bitmap, maxDimension = 384)
        }
        val langInstruction = if (language == CoachLanguage.HINGLISH) {
            "Respond in friendly, natural, practical Hinglish (Hindi written in Roman script mixed with simple English words, e.g., 'Phone ko zameen se 2 feet ooncha diwar ke sahare rakhein aur 6-7 feet peeche jayein taaki poori body dikhe')."
        } else {
            "Respond in friendly, practical English with exact feet/height camera placement instructions."
        }

        val prompt = """
            You are an intelligent AI Home Workout Room & Camera Setup Assistant.
            The user is preparing to do: ${exercise.titleEn} (${exercise.framingGuideEn}).
            Local brightness check: $localLighting.
            Language requirement: $langInstruction

            Inspect the room image carefully and provide:
            1. lightingQuality: GOOD, LOW, or TOO_DARK.
            2. bestWorkoutSpot: Point out where in the room there is clear floor space (away from furniture/obstacles) and good lighting to safely do ${exercise.titleEn}. If the view only shows a close-up wall or is too dark, tell them gently to pan across the floor/room or turn on lights.
            3. exactCameraPlacement: Give specific, practical advice on where and at what height/angle/distance to place their phone (e.g., against a wall/chair/table, height in feet, and 6-7 feet distance).
            4. spotReady: true if clear enough to set up the phone, false if obstructed or completely dark.
        """.trimIndent()

        return try {
            val response = withTimeout(AI_REQUEST_TIMEOUT_MS) {
                roomModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text
                ?: throw IllegalStateException("AI Room Assistant returned an empty response.")
            val json = JSONObject(jsonText)

            val aiLight = when (json.optString("lightingQuality", "GOOD")) {
                "TOO_DARK" -> LightingStatus.TOO_DARK
                "LOW" -> LightingStatus.LOW
                else -> LightingStatus.GOOD
            }
            val effectiveLight = if (localLighting == LightingStatus.TOO_DARK) LightingStatus.TOO_DARK else aiLight

            val lightingHint = buildLightingHint(effectiveLight, language)

            RoomSetupGuidance(
                lightingStatus = effectiveLight,
                lightingHint = lightingHint,
                bestWorkoutSpot = json.optString("bestWorkoutSpot", ""),
                exactCameraPlacement = json.optString("exactCameraPlacement", exercise.defaultPlacementTip(language)),
                spotReady = json.optBoolean("spotReady", true),
                isScanning = false,
                errorMessage = null
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Room setup scan timed out after ${AI_REQUEST_TIMEOUT_MS}ms", e)
            RoomSetupGuidance(
                lightingStatus = localLighting,
                lightingHint = buildLightingHint(localLighting, language),
                bestWorkoutSpot = fallbackSpotGuidance(localLighting, language),
                exactCameraPlacement = exercise.defaultPlacementTip(language),
                spotReady = false,
                isScanning = false,
                errorMessage = if (language == CoachLanguage.HINGLISH) {
                    "AI Room scan slow hai — 'Scan Room Karein' dabakar retry karein ya neeche placement guide follow karein."
                } else {
                    "AI Room scan timed out — tap 'Scan Room Now' to retry or follow the placement guide below."
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Room setup scan failed", e)
            RoomSetupGuidance(
                lightingStatus = localLighting,
                lightingHint = buildLightingHint(localLighting, language),
                bestWorkoutSpot = fallbackSpotGuidance(localLighting, language),
                exactCameraPlacement = exercise.defaultPlacementTip(language),
                spotReady = false,
                isScanning = false,
                errorMessage = e.localizedMessage ?: "Room scan unavailable right now."
            )
        }
    }

    suspend fun verifyFullBodyFraming(
        bitmap: Bitmap,
        exercise: ExerciseSpec,
        language: CoachLanguage,
        poseFullBodyDetected: Boolean,
        localLighting: LightingStatus
    ): Pair<Boolean, String> {
        if (localLighting == LightingStatus.TOO_DARK) {
            val darkMsg = if (language == CoachLanguage.HINGLISH) {
                "Andhera zyada hai — kamre ki light on karke dobara check karein."
            } else {
                "Lighting is too low — turn on room lights and re-check framing."
            }
            return false to darkMsg
        }

        val scaledBitmap = withContext(Dispatchers.Default) {
            scaleDownIfNeeded(bitmap, maxDimension = 384)
        }
        val langInstruction = if (language == CoachLanguage.HINGLISH) {
            "Respond in short, encouraging Hinglish (Roman Hindi + English)."
        } else {
            "Respond in short, clear English."
        }

        val prompt = """
            You are checking if the user's phone camera is properly positioned before starting ${exercise.titleEn}.
            On-device Pose Detector fullBodyDetected=$poseFullBodyDetected.
            Requirement: ${exercise.framingGuideEn}.
            $langInstruction

            Inspect the image:
            - Return fullBodyVisible = true ONLY if a person is clearly visible in frame with enough space around them to perform ${exercise.titleEn} safely.
            - If nobody is in frame, or their head/feet/arms are cut off, return fullBodyVisible = false and tell them specifically how to adjust (e.g., step back 2-3 feet or tilt phone slightly).
        """.trimIndent()

        return try {
            val response = withTimeout(AI_REQUEST_TIMEOUT_MS) {
                verifyModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Framing verification returned empty text.")
            val json = JSONObject(jsonText)
            val ok = json.optBoolean("fullBodyVisible", poseFullBodyDetected)
            val msg = json.optString("message", "").ifBlank {
                if (ok) {
                    if (language == CoachLanguage.HINGLISH) "Shabaash! Poori body frame mein hai — Start Workout dabao!" else "Great framing! Full body visible — ready to start!"
                } else {
                    if (language == CoachLanguage.HINGLISH) "2 kadam peeche jayein taaki poori body saaf dikhe." else "Step back 2-3 feet so your full body fits in frame."
                }
            }
            ok to msg
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Full body framing verification timed out", e)
            if (poseFullBodyDetected) {
                val msg = if (language == CoachLanguage.HINGLISH) {
                    "Pose Detector ne poori body verify kar li! Workout shuru karein."
                } else {
                    "Full body detected by Pose Tracker! Ready to start."
                }
                true to msg
            } else {
                val msg = if (language == CoachLanguage.HINGLISH) {
                    "Verification timeout hua — 6 feet peeche frame mein khade hokar dobara check karein."
                } else {
                    "Verification timed out — step into frame and tap Confirm Placement again."
                }
                false to msg
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Full body framing verification failed", e)
            if (poseFullBodyDetected) {
                val msg = if (language == CoachLanguage.HINGLISH) {
                    "Pose Detector ne poori body verify kar li! Workout shuru karein."
                } else {
                    "Full body detected by Pose Tracker! Ready to start."
                }
                true to msg
            } else {
                false to (e.localizedMessage ?: "Could not verify framing. Step back into frame and retry.")
            }
        }
    }

    suspend fun analyzeFrame(
        bitmap: Bitmap,
        exercise: ExerciseSpec,
        language: CoachLanguage
    ): FormFeedback {
        val scaledBitmap = withContext(Dispatchers.Default) {
            scaleDownIfNeeded(bitmap, maxDimension = 384)
        }
        val langInstruction = if (language == CoachLanguage.HINGLISH) {
            "Speak like a strict, high-energy Desi Indian gym trainer in crisp Hinglish (Roman Hindi + simple English fitness terms, e.g. 'Kamar seedhi rakho!', 'Aur neeche jao, rukna mat!', 'Shabaash, tagda rep!')."
        } else {
            "Speak like a strict, high-energy Indian gym trainer in punchy English cues (e.g. 'Lock that back flat!', 'Lower! Don't cheat the rep!', 'Strong push, keep going!')."
        }

        val prompt = """
            You are a strict, energetic Indian (Desi) gym trainer analyzing a live workout camera frame.
            Active exercise: ${exercise.titleEn}
            Key form checkpoints: ${exercise.checkpointsEn.joinToString("; ")}
            Language style: $langInstruction

            Inspect the person's posture in the image:
            - If no person is clearly visible or body alignment cannot be seen at all, return GET_IN_FRAME and command them clearly to position the camera.
            - If they are doing the exercise with solid alignment (straight back, good depth/hold), return GOOD_FORM with bold, motivating trainer feedback.
            - If you spot a posture issue (hips sagging/too high, rounded back, shallow depth, knees caving, neck strained), return ADJUST_POSTURE with a firm, direct correction cue.
            Keep 'cue' short and punchy so it can be spoken aloud during a workout.
        """.trimIndent()

        return try {
            val response = withTimeout(AI_REQUEST_TIMEOUT_MS) {
                formModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text
                ?: throw IllegalStateException("AI Coach returned an empty response.")
            val json = JSONObject(jsonText)
            val statusStr = json.optString("formStatus", "GET_IN_FRAME")
            val cue = json.optString("cue", "").ifBlank {
                if (language == CoachLanguage.HINGLISH) "Position check kar rahe hain..." else "Checking alignment..."
            }
            val tip = json.optString("postureTip", "")
            val repDetected = json.optBoolean("repDetected", false)

            val status = when (statusStr) {
                "GOOD_FORM" -> FormStatus.GOOD_FORM
                "ADJUST_POSTURE" -> FormStatus.ADJUST_POSTURE
                else -> FormStatus.GET_IN_FRAME
            }

            val badge = when (status) {
                FormStatus.GOOD_FORM -> if (language == CoachLanguage.HINGLISH) "Sahi Form" else "Good Form"
                FormStatus.ADJUST_POSTURE -> if (language == CoachLanguage.HINGLISH) "Form Sudharein" else "Adjust Posture"
                FormStatus.GET_IN_FRAME -> if (language == CoachLanguage.HINGLISH) "Frame Mein Aao" else "Align in Frame"
                else -> "AI Coach"
            }

            FormFeedback(
                status = status,
                badgeText = badge,
                cueText = cue,
                postureTip = tip,
                repDetected = repDetected,
                timestampMs = System.currentTimeMillis(),
                isDemoFeedback = false
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "AI frame analysis timed out after ${AI_REQUEST_TIMEOUT_MS}ms", e)
            FormFeedback(
                status = FormStatus.ERROR,
                badgeText = "Timeout",
                cueText = if (language == CoachLanguage.HINGLISH) {
                    "AI response slow hai — Pose Tracker chalu hai."
                } else {
                    "AI Coach timed out — live Pose Tracker active."
                },
                postureTip = "Check internet connection",
                timestampMs = System.currentTimeMillis()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI frame analysis failed", e)
            FormFeedback(
                status = FormStatus.ERROR,
                badgeText = "AI Error",
                cueText = e.localizedMessage ?: "Unable to analyze frame right now.",
                postureTip = "Pose Tracker still running",
                timestampMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun generateSessionSummaryTip(
        exercise: ExerciseSpec,
        setsCompleted: Int,
        repsCompleted: Int,
        durationSeconds: Int,
        formScorePercent: Int,
        streakDays: Int,
        language: CoachLanguage
    ): String {
        val langStyle = if (language == CoachLanguage.HINGLISH) {
            "Write in friendly, high-energy Hinglish (Roman Hindi + English)."
        } else {
            "Write in encouraging, high-energy English."
        }
        val prompt = """
            A user just completed a home workout session in JoshFit:
            - Exercise: ${exercise.titleEn}
            - Sets completed: $setsCompleted
            - Reps logged: $repsCompleted
            - Active time: ${durationSeconds}s
            - AI Form Score: $formScorePercent%
            - Current Streak: $streakDays day(s)

            $langStyle
            Give a 2-sentence personalized post-workout recovery & next-session progression tip. No markdown bullets.
        """.trimIndent()

        val response = withTimeout(AI_REQUEST_TIMEOUT_MS) {
            summaryModel.generateContent(prompt)
        }
        return response.text?.trim()
            ?: throw IllegalStateException("Empty summary response from AI Coach.")
    }

    private fun buildLightingHint(status: LightingStatus, language: CoachLanguage): String = when (status) {
        LightingStatus.TOO_DARK ->
            if (language == CoachLanguage.HINGLISH) "Light bahut kam hai — room light on karein!" else "Too dark — please turn on room lights!"
        LightingStatus.LOW ->
            if (language == CoachLanguage.HINGLISH) "Light thodi kam hai — window ya bulb ke paas spot chunein." else "Low lighting — try facing a window or bright lamp."
        LightingStatus.GOOD ->
            if (language == CoachLanguage.HINGLISH) "Lighting badhiya hai!" else "Great lighting!"
    }

    private fun fallbackSpotGuidance(status: LightingStatus, language: CoachLanguage): String {
        return if (status == LightingStatus.TOO_DARK) {
            if (language == CoachLanguage.HINGLISH) {
                "Kamre mein roshni kam hai — light on karke 6x4 feet saaf floor space chunein."
            } else {
                "Room appears dark — turn on overhead lights and pick a clear 6x4 ft floor area."
            }
        } else {
            if (language == CoachLanguage.HINGLISH) {
                "Furniture se door 6x4 feet saaf floor space chunein jahan light acchi ho."
            } else {
                "Pick a clear 6x4 ft floor space away from furniture with good lighting."
            }
        }
    }

    private fun scaleDownIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val ratio = maxDimension.toFloat() / maxOf(width, height).toFloat()
        val targetW = maxOf(1, (width * ratio).toInt())
        val targetH = maxOf(1, (height * ratio).toInt())
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}
