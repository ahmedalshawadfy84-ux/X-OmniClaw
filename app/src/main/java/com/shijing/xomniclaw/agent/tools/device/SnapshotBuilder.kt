package com.shijing.xomniclaw.agent.tools.device

/**
 * OmniClaw Source Reference:
 * - ../xomniclaw/src/agents/tools/browser/(all)
 *
 * OmniClaw adaptation: build snapshot from Android accessibility tree.
 * Converts ViewNode list to RefNode list with Playwright-style ref IDs.
 *
 * 🔧PATCHED for Unity/Game Support:
 * - يحتفظ بـ SurfaceView / UnityPlayer حتى لو بدون نص أو clickable
 * - يمنحها role = game_surface ليتم التعرف عليها
 */

import android.graphics.Rect
import android.util.Log
import com.shijing.xomniclaw.accessibility.service.ViewNode
import com.shijing.xomniclaw.util.LlmTextNormalize

object SnapshotBuilder {
    private const val TAG = "SnapshotBuilder"

    /**
     * Build ref nodes from ViewNode list (from AccessibilityProxy.dumpViewTree).
     */
    fun buildFromViewNodes(nodes: List<ViewNode>): List<RefNode> {
        val refNodes = mutableListOf<RefNode>()
        var refCounter = 1

        for (viewNode in nodes) {
            // WebView/富文本里 NBSP、全角空格等会多占 token，发往 LLM 前压成普通空格。
            val rawLabel = viewNode.text?.takeIf { it.isNotBlank() }
                ?: viewNode.contentDesc?.takeIf { it.isNotBlank() }
            val displayText = rawLabel?.let { LlmTextNormalize.forToolObservation(it) }

            val isInteractive = viewNode.clickable || viewNode.focusable || viewNode.scrollable
            val hasText = !displayText.isNullOrBlank()

            // 🔧 PATCH: هل هذه عقدة لعبة؟
            val isGameSurface = UnityGameDetector.isGameSurfaceClass(viewNode.className)

            // الأصل: فقط إذا كان interactive أو له نص
            // الجديد: احتفظ أيضاً بـ Game Surfaces مهمة حتى لو غير تفاعلية ظاهرياً
            val shouldKeep = isInteractive || hasText || isGameSurface

            if (shouldKeep) {
                val shortClass = viewNode.className?.substringAfterLast('.') ?: "View"
                val role = if (isGameSurface) {
                    "game_surface"
                } else {
                    mapToRole(shortClass, viewNode)
                }
                val ref = "e${refCounter++}"

                // للألعاب، نعتبرها clickable حتى لو النظام يقول غير ذلك
                // لأن الضغط على الـ Surface نفسه قد يكون له معنى
                val effectiveClickable = viewNode.clickable || isGameSurface

                refNodes.add(RefNode(
                    ref = ref,
                    role = role,
                    text = displayText?.take(100),
                    bounds = Rect(viewNode.left, viewNode.top, viewNode.right, viewNode.bottom),
                    clickable = effectiveClickable,
                    editable = viewNode.focusable && shortClass.contains("Edit", ignoreCase = true),
                    scrollable = viewNode.scrollable,
                    focusable = viewNode.focusable,
                    checkable = viewNode.checkable,
                    checked = viewNode.checked,
                    selected = viewNode.selected,
                    depth = 0,  // ViewNode doesn't carry depth
                    className = shortClass,
                    packageName = viewNode.packageName
                ))
            }
        }

        Log.d(TAG, "Built ${refNodes.size} ref nodes from ${nodes.size} view nodes (game-aware)")

        // 🔧 PATCH: إذا لم يبق شيء وكانت هناك SurfaceView، احتفظ بأكبر واحدة على الأقل
        // حتى يعرف agent أن هناك شيئاً على الشاشة
        if (refNodes.isEmpty() && nodes.isNotEmpty()) {
            // ابحث عن أكبر عقدة
            val largest = nodes.maxByOrNull { (it.right - it.left) * (it.bottom - it.top) }
            largest?.let { viewNode ->
                val shortClass = viewNode.className?.substringAfterLast('.') ?: "View"
                val ref = "e${refCounter++}"
                refNodes.add(RefNode(
                    ref = ref,
                    role = if (UnityGameDetector.isGameSurfaceClass(viewNode.className)) "game_surface" else "element",
                    text = viewNode.text?.take(100) ?: viewNode.contentDesc?.take(100),
                    bounds = Rect(viewNode.left, viewNode.top, viewNode.right, viewNode.bottom),
                    clickable = true, // افترض clickable كملاذ أخير
                    editable = false,
                    scrollable = false,
                    focusable = false,
                    checkable = false,
                    checked = false,
                    selected = false,
                    depth = 0,
                    className = shortClass,
                    packageName = viewNode.packageName
                ))
                Log.d(TAG, "Fallback: kept largest node as ref ${ref} because original list was empty")
            }
        }

        return refNodes
    }

    /**
     * Map Android class names to Playwright-style roles.
     */
    private fun mapToRole(className: String, node: ViewNode): String {
        return when {
            className.contains("Button", ignoreCase = true) -> "button"
            className.contains("EditText", ignoreCase = true) -> "input"
            className.contains("TextView", ignoreCase = true) -> {
                if (node.clickable) "link" else "text"
            }
            className.contains("ImageView", ignoreCase = true) -> "image"
            className.contains("ImageButton", ignoreCase = true) -> "button"
            className.contains("CheckBox", ignoreCase = true) -> "checkbox"
            className.contains("RadioButton", ignoreCase = true) -> "radio"
            className.contains("Switch", ignoreCase = true) -> "switch"
            className.contains("SeekBar", ignoreCase = true) -> "slider"
            className.contains("Spinner", ignoreCase = true) -> "select"
            className.contains("RecyclerView", ignoreCase = true) -> "list"
            className.contains("ListView", ignoreCase = true) -> "list"
            className.contains("ScrollView", ignoreCase = true) -> "scrollable"
            className.contains("WebView", ignoreCase = true) -> "webview"
            // Unity/Game
            className.contains("Unity", ignoreCase = true) -> "game_surface"
            className.contains("SurfaceView", ignoreCase = true) -> "game_surface"
            className.contains("GLSurfaceView", ignoreCase = true) -> "game_surface"
            className.contains("TextureView", ignoreCase = true) -> "game_surface"
            node.scrollable -> "scrollable"
            node.clickable -> "button"
            node.focusable -> "input"
            else -> "element"
        }
    }
}
