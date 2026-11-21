package com.example.pose

import android.graphics.Bitmap
import android.util.Log
import com.example.model.CoachLanguage
import com.example.model.ExerciseType
import com.example.model.FormFeedback
import com.example.model.FormStatus
import com.example.model.LightingStatus
import com.example.model.NormalizedPoint
import com.example.model.PoseSkeletonData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

private const val TAG = "PoseFormEvaluator"

data class PoseEvaluationResult(
    val skeleton: PoseSkeletonData,
    val feedback: FormFeedback?,
    val repIncremented: Boolean,
    val lightingStatus: LightingStatus
)

class PoseFormEvaluator {

    private val detector: PoseDetector by lazy {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    private enum class RepStage {
        UP_OR_STANDING,
        DOWN_OR_BOTTOM
    }

    private var currentStage = RepStage.UP_OR_STANDING
    private var reachedGoodDepthThisRep = false
    private var postureWasCleanThisRep = true
    private var lastCueTimestamp = 0L

    fun resetRepState() {
        currentStage = RepStage.UP_OR_STANDING
        reachedGoodDepthThisRep = false
        postureWasCleanThisRep = true
        lastCueTimestamp = 0L
    }

    suspend fun processFrame(
        bitmap: Bitmap,
        exerciseType: ExerciseType,
        language: CoachLanguage,
        evaluateFormAndReps: Boolean
    ): PoseEvaluationResult {
        val lighting = estimateLighting(bitmap)
        val pose = detectPose(bitmap)
        val width = bitmap.width.coerceAtLeast(1).toFloat()
        val height = bitmap.height.coerceAtLeast(1).toFloat()

        if (pose == null || pose.allPoseLandmarks.isEmpty()) {
            val noBodyFeedback = if (evaluateFormAndReps) {
                FormFeedback(
                    status = FormStatus.GET_IN_FRAME,
                    badgeText = if (language == CoachLanguage.HINGLISH) "Frame Mein Aao" else "Align in Frame",
                    cueText = when {
                        lighting == LightingStatus.TOO_DARK ->
                            if (language == CoachLanguage.HINGLISH) "Light bahut kam hai — kamre ki light on karein!" else "Room is too dark — please turn on a light!"
                        language == CoachLanguage.HINGLISH -> "Poori body camera frame mein rakhein."
                        else -> "Step back so your body is visible in frame."
                    },
                    postureTip = if (lighting != LightingStatus.GOOD) "Low lighting detected" else "Align inside corner guides",
                    isLowLight = lighting != LightingStatus.GOOD,
                    timestampMs = System.currentTimeMillis()
                )
            } else null

            return PoseEvaluationResult(
                skeleton = PoseSkeletonData(),
                feedback = noBodyFeedback,
                repIncremented = false,
                lightingStatus = lighting
            )
        }

        val landmarkMap = buildNormalizedLandmarks(pose, width, height)
        val upperVisible = hasVisible(landmarkMap, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER) &&
            hasVisible(landmarkMap, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        val fullVisible = upperVisible &&
            hasVisible(landmarkMap, PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE) &&
            hasVisible(landmarkMap, PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE) &&
            isWellFramed(landmarkMap)

        var angleDeg: Int? = null
        var angleLabel = ""
        var repIncremented = false
        var feedback: FormFeedback? = null

        if (evaluateFormAndReps) {
            val eval = evaluateExercisePose(
                landmarks = landmarkMap,
                exerciseType = exerciseType,
                language = language,
                lighting = lighting
            )
            angleDeg = eval.first
            angleLabel = eval.second
            repIncremented = eval.third.first
            feedback = eval.third.second
        } else {
            angleDeg = computePrimaryAngle(landmarkMap, exerciseType)?.toInt()
        }

        val skeleton = PoseSkeletonData(
            landmarks = landmarkMap,
            connections = SKELETON_CONNECTIONS,
            primaryAngleDeg = angleDeg,
            primaryAngleLabel = angleLabel,
            fullBodyVisible = fullVisible,
            upperBodyVisible = upperVisible
        )

        return PoseEvaluationResult(
            skeleton = skeleton,
            feedback = feedback,
            repIncremented = repIncremented,
            lightingStatus = lighting
        )
    }

    private fun evaluateExercisePose(
        landmarks: Map<Int, NormalizedPoint>,
        exerciseType: ExerciseType,
        language: CoachLanguage,
        lighting: LightingStatus
    ): Triple<Int?, String, Pair<Boolean, FormFeedback>> {
        val hinglish = language == CoachLanguage.HINGLISH
        val now = System.currentTimeMillis()

        val shoulder = bestPoint(landmarks, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        val elbow = bestPoint(landmarks, PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW)
        val wrist = bestPoint(landmarks, PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
        val hip = bestPoint(landmarks, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        val knee = bestPoint(landmarks, PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE)
        val ankle = bestPoint(landmarks, PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)

        when (exerciseType) {
            ExerciseType.PUSH_UPS -> {
                val elbowAngle = if (shoulder != null && elbow != null && wrist != null) {
                    angleDegrees(shoulder, elbow, wrist)
                } else null
                val spineAngle = if (shoulder != null && hip != null && ankle != null) {
                    angleDegrees(shoulder, hip, ankle)
                } else null

                val spineAligned = spineAngle == null || spineAngle >= 148.0
                if (!spineAligned) postureWasCleanThisRep = false

                var repDone = false
                if (elbowAngle != null) {
                    if (elbowAngle < 105.0) {
                        currentStage = RepStage.DOWN_OR_BOTTOM
                        reachedGoodDepthThisRep = true
                    } else if (elbowAngle > 148.0 && currentStage == RepStage.DOWN_OR_BOTTOM) {
                        currentStage = RepStage.UP_OR_STANDING
                        if (reachedGoodDepthThisRep) {
                            repDone = true
                        }
                        reachedGoodDepthThisRep = false
                        postureWasCleanThisRep = true
                    }
                }

                val status: FormStatus
                val badge: String
                val cue: String
                val tip: String

                when {
                    elbowAngle == null -> {
                        status = FormStatus.GET_IN_FRAME
                        badge = if (hinglish) "Frame Mein Aao" else "Align in Frame"
                        cue = if (hinglish) "Side profile mein aayein taaki haath aur kamar dikhe." else "Turn side-on so arms and torso are visible."
                        tip = "Show shoulders, elbows & hips"
                    }
                    !spineAligned -> {
                        status = FormStatus.ADJUST_POSTURE
                        badge = if (hinglish) "Form Sudharein" else "Adjust Posture"
                        cue = if (hinglish) "Kamar seedhi rakho, hips neeche mat girao!" else "Keep your back flat — brace core and hips!"
                        tip = "Spine angle ${spineAngle?.toInt()}° (keep >150°)"
                    }
                    repDone -> {
                        status = FormStatus.GOOD_FORM
                        badge = if (hinglish) "Sahi Form" else "Good Form"
                        cue = if (hinglish) "Bahut badhiya! Rep count hua!" else "Great form! Rep counted!"
                        tip = "Elbow ${elbowAngle.toInt()}° • Clean lockout"
                    }
                    elbowAngle < 115.0 -> {
                        status = FormStatus.GOOD_FORM
                        badge = if (hinglish) "Sahi Form" else "Good Form"
                        cue = if (hinglish) "Zabardast depth! Ab upar push karo." else "Great depth! Drive upward cleanly."
                        tip = "Elbow ${elbowAngle.toInt()}° • Solid bottom position"
                    }
                    else -> {
                        status = FormStatus.GOOD_FORM
                        badge = if (hinglish) "Sahi Form" else "Good Form"
                        cue = if (hinglish) "Kamar seedhi hai — thoda aur neeche jao." else "Back straight — lower chest steadily."
                        tip = "Elbow ${elbowAngle.toInt()}°"
                    }
                }

                return Triple(
                    elbowAngle?.toInt(),
                    if (elbowAngle != null) "Elbow ${elbowAngle.toInt()}°" else "",
                    repDone to FormFeedback(
                        status = status,
                        badgeText = badge,
                        cueText = cue,
                        postureTip = tip,
                        repDetected = repDone,
                        isLowLight = lighting != LightingStatus.GOOD,
                        timestampMs = now
                    )
                )
            }

            ExerciseType.SQUATS -> {
                val kneeAngle = if (hip != null && knee != null && ankle != null) {
                    angleDegrees(hip, knee, ankle)
                } else null
                val torsoLeanDeg = if (shoulder != null && hip != null) {
                    val dx = abs(shoulder.x - hip.x)
                    val dy = abs(hip.y - shoulder.y).coerceAtLeast(0.01f)
                    (atan2(dx.toDouble(), dy.toDouble()) * 180.0 / PI)
                } else null

                val excessiveLean = torsoLeanDeg != null && torsoLeanDeg > 42.0
                var repDone = false

                if (kneeAngle != null) {
                    if (kneeAngle < 112.0) {
                        currentStage = RepStage.DOWN_OR_BOTTOM
                        reachedGoodDepthThisRep = true
                    } else if (kneeAngle > 152.0 && currentStage == RepStage.DOWN_OR_BOTTOM) {
                        currentStage = RepStage.UP_OR_STANDING
                        if (reachedGoodDepthThisRep) {
                            repDone = true
                        }
                        reachedGoodDepthThisRep = false
                    }
                }

                val status: FormStatus
                val badge: String
                val cue: String
                val tip: String

                when {
                    kneeAngle == null -> {
                        status = FormStatus.GET_IN_FRAME
                        badge = if (hinglish) "Frame Mein Aao" else "Align in Frame"
                        cue = if (hinglish) "Peeche hatke khade hon taaki ghutne aur pair dikhe." else "Step back so hips, knees, and ankles are visible."
                        tip = "Full body framing needed"
                    }
                    excessiveLean -> {
                        status = FormStatus.ADJUST_POSTURE
                        badge = if (hinglish) "Form Sudharein" else "Adjust Posture"
                        cue = if (hinglish) "Seena upar aur kamar seedhi rakho, aage mat jhuko!" else "Keep chest upright — don't lean too far forward!"
                        tip = "Torso upright • Weight on heels"
                    }
                    repDone -> {
                        status = FormStatus.GOOD_FORM
                        badge = if (hinglish) "Sahi Form" else "Good Form"
                        cue = if (hinglish) "Bahut badhiya! Rep count hua!" else "Awesome squat! Rep counted!"
                        tip = "Knee ${kneeAngle.toInt()}° • Strong drive"
                    }
                    kneeAngle <= 112.0 -> {
                        status = FormStatus.GOOD_FORM
                        badge = if (hinglish) "Sahi Form" else "Good Form"
                        cue = if (hinglish) "Perfect squat depth! Heels se upar aao." else "Great depth! Push up through heels."
                        tip = "Knee ${kneeAngle.toInt()}° • Parallel reached"
                    }
                    else -> {
                        status = FormStatus.GOOD_FORM
                        badge = if (hinglish) "Sahi Form" else "Good Form"
                        cue = if (hinglish) "Smooth control — thoda aur neeche jao." else "Good posture — sit back a little deeper."
                        tip = "Knee ${kneeAngle.toInt()}°"
                    }
                }

                return Triple(
                    kneeAngle?.toInt(),
                    if (kneeAngle != null) "Knee ${kneeAngle.toInt()}°" else "",
                    repDone to FormFeedback(
                        status = status,
                        badgeText = badge,
                        cueText = cue,
                        postureTip = tip,
                        repDetected = repDone,
                        isLowLight = lighting != LightingStatus.GOOD,
                        timestampMs = now
                    )
                )
            }

            ExerciseType.PLANK -> {
                val spineAngle = if (shoulder != null && hip != null && ankle != null) {
                    angleDegrees(shoulder, hip, ankle)
                } else null

                val status: FormStatus
                val badge: String
                val cue: String
                val tip: String

                when {
                    spineAngle == null -> {
                        status = FormStatus.GET_IN_FRAME
                        badge = if (hinglish) "Frame Mein Aao" else "Align in Frame"
                        cue = if (hinglish) "Side profile mein aayein taaki sir se pair dikhe." else "Position camera side-on showing head to ankles."
                        tip = "Full side profile needed"
                    }
                    spineAngle < 156.0 -> {
                        status = FormStatus.ADJUST_POSTURE
                        badge = if (hinglish) "Form Sudharein" else "Adjust Posture"
                        cue = if (hinglish) "Hips ko line mein laao — kamar seedhi rakho!" else "Straighten your hips — keep a flat line head to heels!"
                        tip = "Alignment ${spineAngle.toInt()}° (target 165°-180°)"
                    }
                    else -> {
                        status = FormStatus.GOOD_FORM
                        badge = if (hinglish) "Sahi Form" else "Good Form"
                        cue = if (hinglish) "Bahut badhiya! Pet tight rakh ke hold karo." else "Rock-solid plank! Keep core braced."
                        tip = "Spine ${spineAngle.toInt()}° • Straight alignment"
                    }
                }

                return Triple(
                    spineAngle?.toInt(),
                    if (spineAngle != null) "Line ${spineAngle.toInt()}°" else "",
                    false to FormFeedback(
                        status = status,
                        badgeText = badge,
                        cueText = cue,
                        postureTip = tip,
                        repDetected = false,
                        isLowLight = lighting != LightingStatus.GOOD,
                        timestampMs = now
                    )
                )
            }
        }
    }

    private fun computePrimaryAngle(
        landmarks: Map<Int, NormalizedPoint>,
        exerciseType: ExerciseType
    ): Double? {
        val shoulder = bestPoint(landmarks, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        val elbow = bestPoint(landmarks, PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW)
        val wrist = bestPoint(landmarks, PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
        val hip = bestPoint(landmarks, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        val knee = bestPoint(landmarks, PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE)
        val ankle = bestPoint(landmarks, PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)

        return when (exerciseType) {
            ExerciseType.PUSH_UPS -> if (shoulder != null && elbow != null && wrist != null) angleDegrees(shoulder, elbow, wrist) else null
            ExerciseType.SQUATS -> if (hip != null && knee != null && ankle != null) angleDegrees(hip, knee, ankle) else null
            ExerciseType.PLANK -> if (shoulder != null && hip != null && ankle != null) angleDegrees(shoulder, hip, ankle) else null
        }
    }

    private suspend fun detectPose(bitmap: Bitmap): Pose? =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                detector.process(image)
                    .addOnSuccessListener { pose ->
                        if (cont.isActive) cont.resume(pose)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "ML Kit pose detection failed", e)
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Pose detection error", e)
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun buildNormalizedLandmarks(
        pose: Pose,
        width: Float,
        height: Float
    ): Map<Int, NormalizedPoint> {
        val ids = listOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
        )
        val map = mutableMapOf<Int, NormalizedPoint>()
        for (id in ids) {
            val lm = pose.getPoseLandmark(id) ?: continue
            map[id] = NormalizedPoint(
                x = (lm.position.x / width).coerceIn(0f, 1f),
                y = (lm.position.y / height).coerceIn(0f, 1f),
                likelihood = lm.inFrameLikelihood
            )
        }
        return map
    }

    private fun hasVisible(map: Map<Int, NormalizedPoint>, leftId: Int, rightId: Int): Boolean {
        val left = map[leftId]
        val right = map[rightId]
        return (left != null && left.likelihood >= 0.45f) || (right != null && right.likelihood >= 0.45f)
    }

    private fun isWellFramed(map: Map<Int, NormalizedPoint>): Boolean {
        val tracked = listOf(
            bestPoint(map, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
            bestPoint(map, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
            bestPoint(map, PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)
        ).filterNotNull()
        if (tracked.size < 3) return false
        return tracked.all { it.x in 0.03f..0.97f && it.y in 0.03f..0.97f }
    }

    private fun bestPoint(
        map: Map<Int, NormalizedPoint>,
        leftId: Int,
        rightId: Int
    ): NormalizedPoint? {
        val l = map[leftId]?.takeIf { it.likelihood >= 0.4f }
        val r = map[rightId]?.takeIf { it.likelihood >= 0.4f }
        return when {
            l != null && r != null -> if (l.likelihood >= r.likelihood) l else r
            l != null -> l
            else -> r
        }
    }

    private fun angleDegrees(
        first: NormalizedPoint,
        mid: NormalizedPoint,
        last: NormalizedPoint
    ): Double {
        val a1 = atan2((last.y - mid.y).toDouble(), (last.x - mid.x).toDouble())
        val a2 = atan2((first.y - mid.y).toDouble(), (first.x - mid.x).toDouble())
        var deg = abs((a1 - a2) * 180.0 / PI)
        if (deg > 180.0) deg = 360.0 - deg
        return deg
    }

    fun estimateLighting(bitmap: Bitmap): LightingStatus {
        val stepX = (bitmap.width / 16).coerceAtLeast(1)
        val stepY = (bitmap.height / 16).coerceAtLeast(1)
        var sum = 0L
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                val luma = (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                sum += luma
                count++
                x += stepX
            }
            y += stepY
        }
        val avg = if (count > 0) sum / count else 128L
        return when {
            avg < 40L -> LightingStatus.TOO_DARK
            avg < 68L -> LightingStatus.LOW
            else -> LightingStatus.GOOD
        }
    }

    fun close() {
        try {
            detector.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        val SKELETON_CONNECTIONS = listOf(
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
            PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
            PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
        )

        fun buildDemoSkeleton(exerciseType: ExerciseType, isGoodForm: Boolean): PoseSkeletonData {
            val landmarks = when (exerciseType) {
                ExerciseType.SQUATS -> {
                    val hipY = if (isGoodForm) 0.56f else 0.48f
                    mapOf(
                        PoseLandmark.NOSE to NormalizedPoint(0.50f, 0.20f, 0.99f),
                        PoseLandmark.LEFT_SHOULDER to NormalizedPoint(0.43f, 0.30f, 0.99f),
                        PoseLandmark.RIGHT_SHOULDER to NormalizedPoint(0.57f, 0.30f, 0.99f),
                        PoseLandmark.LEFT_ELBOW to NormalizedPoint(0.36f, 0.42f, 0.99f),
                        PoseLandmark.RIGHT_ELBOW to NormalizedPoint(0.64f, 0.42f, 0.99f),
                        PoseLandmark.LEFT_WRIST to NormalizedPoint(0.42f, 0.45f, 0.99f),
                        PoseLandmark.RIGHT_WRIST to NormalizedPoint(0.58f, 0.45f, 0.99f),
                        PoseLandmark.LEFT_HIP to NormalizedPoint(0.44f, hipY, 0.99f),
                        PoseLandmark.RIGHT_HIP to NormalizedPoint(0.56f, hipY, 0.99f),
                        PoseLandmark.LEFT_KNEE to NormalizedPoint(0.39f, 0.68f, 0.99f),
                        PoseLandmark.RIGHT_KNEE to NormalizedPoint(0.61f, 0.68f, 0.99f),
                        PoseLandmark.LEFT_ANKLE to NormalizedPoint(0.42f, 0.86f, 0.99f),
                        PoseLandmark.RIGHT_ANKLE to NormalizedPoint(0.58f, 0.86f, 0.99f)
                    )
                }
                ExerciseType.PUSH_UPS -> {
                    val hipY = if (isGoodForm) 0.56f else 0.65f
                    mapOf(
                        PoseLandmark.NOSE to NormalizedPoint(0.24f, 0.42f, 0.99f),
                        PoseLandmark.LEFT_SHOULDER to NormalizedPoint(0.32f, 0.47f, 0.99f),
                        PoseLandmark.RIGHT_SHOULDER to NormalizedPoint(0.34f, 0.48f, 0.99f),
                        PoseLandmark.LEFT_ELBOW to NormalizedPoint(0.30f, 0.63f, 0.99f),
                        PoseLandmark.RIGHT_ELBOW to NormalizedPoint(0.35f, 0.63f, 0.99f),
                        PoseLandmark.LEFT_WRIST to NormalizedPoint(0.32f, 0.78f, 0.99f),
                        PoseLandmark.RIGHT_WRIST to NormalizedPoint(0.36f, 0.78f, 0.99f),
                        PoseLandmark.LEFT_HIP to NormalizedPoint(0.54f, hipY, 0.99f),
                        PoseLandmark.RIGHT_HIP to NormalizedPoint(0.56f, hipY, 0.99f),
                        PoseLandmark.LEFT_KNEE to NormalizedPoint(0.70f, 0.68f, 0.99f),
                        PoseLandmark.RIGHT_KNEE to NormalizedPoint(0.71f, 0.68f, 0.99f),
                        PoseLandmark.LEFT_ANKLE to NormalizedPoint(0.84f, 0.78f, 0.99f),
                        PoseLandmark.RIGHT_ANKLE to NormalizedPoint(0.85f, 0.78f, 0.99f)
                    )
                }
                ExerciseType.PLANK -> {
                    val hipY = if (isGoodForm) 0.57f else 0.45f
                    mapOf(
                        PoseLandmark.NOSE to NormalizedPoint(0.22f, 0.47f, 0.99f),
                        PoseLandmark.LEFT_SHOULDER to NormalizedPoint(0.31f, 0.51f, 0.99f),
                        PoseLandmark.RIGHT_SHOULDER to NormalizedPoint(0.33f, 0.52f, 0.99f),
                        PoseLandmark.LEFT_ELBOW to NormalizedPoint(0.30f, 0.73f, 0.99f),
                        PoseLandmark.RIGHT_ELBOW to NormalizedPoint(0.33f, 0.73f, 0.99f),
                        PoseLandmark.LEFT_WRIST to NormalizedPoint(0.20f, 0.74f, 0.99f),
                        PoseLandmark.RIGHT_WRIST to NormalizedPoint(0.22f, 0.74f, 0.99f),
                        PoseLandmark.LEFT_HIP to NormalizedPoint(0.55f, hipY, 0.99f),
                        PoseLandmark.RIGHT_HIP to NormalizedPoint(0.56f, hipY, 0.99f),
                        PoseLandmark.LEFT_KNEE to NormalizedPoint(0.72f, 0.66f, 0.99f),
                        PoseLandmark.RIGHT_KNEE to NormalizedPoint(0.73f, 0.66f, 0.99f),
                        PoseLandmark.LEFT_ANKLE to NormalizedPoint(0.86f, 0.74f, 0.99f),
                        PoseLandmark.RIGHT_ANKLE to NormalizedPoint(0.87f, 0.74f, 0.99f)
                    )
                }
            }
            return PoseSkeletonData(
                landmarks = landmarks,
                connections = SKELETON_CONNECTIONS,
                primaryAngleDeg = if (isGoodForm) 95 else 135,
                primaryAngleLabel = if (isGoodForm) "Aligned" else "Check Hip",
                fullBodyVisible = true,
                upperBodyVisible = true
            )
        }
    }
}
