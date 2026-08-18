package com.shijing.xomniclaw.agent.tools.device

import android.graphics.Rect
import android.util.Log
import com.shijing.xomniclaw.accessibility.service.ViewNode

/**
 * Unity / OpenGL Game Detector for X-OmniClaw
 * يكشف إذا كانت الشاشة الحالية هي لعبة Unity / SurfaceView
 * حيث تكون شجرة Accessibility فارغة
 */
object UnityGameDetector {

    private const val TAG = "UnityGameDetector"

    data class GameDetectionResult(
        val isGame: Boolean,
        val reason: String,
        val surfaceNodes: List<ViewNode>,
        val coverageRatio: Float,
        val isUnity: Boolean
    )

    // كلمات مفتاحية تدل على محرك ألعاب
    private val GAME_CLASS_KEYWORDS = listOf(
        "unityplayer",
        "unity",
        "surfaceview",
        "glsurfaceview",
        "textureview",
        "com.unity3d",
        "com.epicgames", // Unreal
        "org.cocos2dx",  // Cocos
        "net.luckygames" // بعض المحاكيات
    )

    // كلاسات تعتبر Game Surface حتى لو غير clickable
    fun isGameSurfaceClass(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        val lower = className.lowercase()
        return GAME_CLASS_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * يحلّل العقد ويقرر هل نحن في وضع لعبة
     */
    fun detect(
        viewNodes: List<ViewNode>,
        refNodes: List<RefNode>,
        screenW: Int,
        screenH: Int
    ): GameDetectionResult {
        if (screenW <=0 || screenH <=0) {
            return GameDetectionResult(false, "invalid_screen", emptyList(), 0f, false)
        }
        val screenArea = screenW * screenH.toFloat()

        // 1) جمع كل الـ surface المرشحة
        val surfaceNodes = viewNodes.filter { node ->
            isGameSurfaceClass(node.className)
        }

        // 2) احسب أكبر تغطية
        var maxCoverage = 0f
        var maxNode: ViewNode? = null
        var isUnity = false

        for (node in viewNodes) {
            // احسب مساحة العقدة
            val w = (node.right - node.left).coerceAtLeast(0)
            val h = (node.bottom - node.top).coerceAtLeast(0)
            val area = w * h.toFloat()
            val cov = if (screenArea > 0) area / screenArea else 0f

            if (cov > maxCoverage) {
                maxCoverage = cov
                maxNode = node
            }

            // تحقق Unity
            if (node.className?.contains("Unity", ignoreCase = true) == true ||
                node.className?.contains("unity3d", ignoreCase = true) == true ||
                node.packageName?.contains("unity", ignoreCase = true) == true
            ) {
                isUnity = true
            }
        }

        // تحقق من surfaceNodes أيضاً
        if (surfaceNodes.any { it.className?.contains("Unity", ignoreCase = true) == true }) {
            isUnity = true
        }

        // 3) منطق القرار
        val refCount = refNodes.size
        val viewCount = viewNodes.size

        // الحالة الأكثر وضوحاً: لدينا Surface يغطي >60% و refs قليلة
        val largeCoverage = maxCoverage >= 0.60f
        val fewRefs = refCount <= 10
        val hasGameSurface = surfaceNodes.isNotEmpty()

        val isGame = when {
            // Unity صريح + تغطية كبيرة
            isUnity && largeCoverage -> true
            // Surface كبير + refs قليلة جداً
            largeCoverage && fewRefs && hasGameSurface -> true
            // ViewNodes قليلة جداً (1-3) وكلها Surface
            viewCount in 1..3 && hasGameSurface && largeCoverage -> true
            // refs = 0 تماماً وشاشة غير فارغة (لعبة مرسومة GPU)
            refCount == 0 && viewCount <= 5 && maxCoverage > 0.4f -> true
            else -> false
        }

        val reason = buildString {
            append("viewNodes=$viewCount refs=$refCount ")
            append("maxCoverage=${"%.2f".format(maxCoverage)} ")
            append("surfaceCount=${surfaceNodes.size} ")
            append("isUnity=$isUnity largeCov=$largeCoverage fewRefs=$fewRefs")
            maxNode?.let { append(" maxNode=${it.className} [${it.left},${it.top},${it.right},${it.bottom}]") }
        }

        Log.d(TAG, "Game detection: isGame=$isGame, $reason")

        return GameDetectionResult(
            isGame = isGame,
            reason = reason,
            surfaceNodes = surfaceNodes,
            coverageRatio = maxCoverage,
            isUnity = isUnity
        )
    }

    /**
     * يبني رسالة تعليمية للـ LLM عند اكتشاف لعبة
     */
    fun buildGameModeBanner(
        result: GameDetectionResult,
        packageName: String,
        screenW: Int,
        screenH: Int
    ): String {
        // شبكة 3x3 كمرجع إحداثيات
        val w = screenW
        val h = screenH
        val grid = """
            | الشبكة المرجعية (نقاط مركزية مقترحة):
            |   top-left (${w * 0.125}, ${h * 0.15}) | top-center (${w / 2}, ${h * 0.15}) | top-right (${w * 0.875}, ${h * 0.15})
            |   center-left (${w * 0.125}, ${h * 0.5}) | CENTER (${w / 2}, ${h * 0.5}) | center-right (${w * 0.875}, ${h * 0.5})
            |   bottom-left (${w * 0.125}, ${h * 0.85}) | bottom-center (${w / 2}, ${h * 0.85}) | bottom-right (${w * 0.875}, ${h * 0.85})
        """.trimMargin()

        return """
            |
            |[🎮 UNITY/GAME MODE DETECTED - وضع لعبة Unity/SurfaceView]
            |الحزمة: $packageName
            |السبب: ${result.reason}
            |تغطية الشاشة: ${"%.0f".format(result.coverageRatio * 100)}% بواسطة ${if (result.isUnity) "UnityPlayer" else "Surface/GL Surface"}
            |
            |⚠️ تنبيه هام: هذه لعبة تعتمد على محرك Unity/OpenGL، الرسم يتم عبر GPU مباشرة على SurfaceView وليس عبر Android Views.
            |لذلك شجرة Accessibility فارغة وهذا طبيعي تماماً - ليس خطأ في النظام.
            |
            |🚫 ممنوع: الاعتماد على ref للضغط (refs ستكون 0 أو غير مفيدة)
            |✅ المطلوب: استخدم التأريض البصري VLM Visual Grounding:
            |
            |  الطريقة الصحيحة (Dual-Track سيعمل تلقائياً):
            |  1) device(action="act", kind="tap", target="play button") 
            |     → اكتب وصف بصري دقيق للزر: اللون، الشكل، النص، الموقع
            |     أمثلة:
            |     - target="green PLAY button at bottom center with white text"
            |     - target="red attack button on bottom right"
            |     - target="X close button top right corner"
            |     - target="joystick at bottom left"
            |     - target="settings gear icon top left"
            |
            |  2) إذا احتجت إحداثيات تقريبية:
            |     device(action="act", kind="tap", x=540, y=1200, target="play button")
            |
            |  3) بعد كل إجراء، تحقق:
            |     device(action="screenshot") أو device(action="snapshot")
            |
            |$grid
            |  أنواع الضغط المتاحة للألعاب:
            |  - tap: ضغطة عادية (للأزرار)
            |  - long_press: ضغط مطول (لشحن مهارة)
            |  - swipe: سحب (لتحريك joystick أو سحب الشاشة)
            |    مثال: device(action="act", kind="swipe", startX=200, startY=1600, endX=500, endY=1600, durationMs=400)
            |  - scroll: سحب عمودي
            |
            |  إذا كان target لا يعمل، جرب وصفاً مختلفاً أو استخدم screenshot + وصف جديد.
            |
            |[نهاية وضع اللعبة - استخدم target-based tapping فقط]
            |
        """.trimMargin()
    }
}
