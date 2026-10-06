package com.ps2.shell

import org.json.JSONArray
import org.json.JSONObject

data class TouchElementData(
    val id: String,
    val xNorm: Float,
    val yNorm: Float,
    val scale: Float,
    val opacity: Float
)

data class TouchLayoutData(
    val globalOpacity: Float = 0.8f,
    val hapticsEnabled: Boolean = true,
    val elements: List<TouchElementData> = emptyList()
)

object TouchLayoutSerializer {

    fun serialize(view: TouchOverlayView): String {
        val root = JSONObject()
        root.put("globalOpacity", view.globalOpacity.toDouble())
        root.put("hapticsEnabled", view.hapticsEnabled)

        val elementsArray = JSONArray()
        for (elem in view.elements.values) {
            val elemObj = JSONObject()
            elemObj.put("id", elem.id)
            elemObj.put("xNorm", elem.xNorm.toDouble())
            elemObj.put("yNorm", elem.yNorm.toDouble())
            elemObj.put("scale", elem.scale.toDouble())
            elemObj.put("opacity", elem.opacity.toDouble())
            elementsArray.put(elemObj)
        }
        root.put("elements", elementsArray)
        return root.toString(2)
    }

    fun deserialize(jsonString: String): TouchLayoutData {
        if (jsonString.isBlank()) return TouchLayoutData()

        val root = JSONObject(jsonString)
        val globalOpacity = root.optDouble("globalOpacity", 0.8).toFloat()
        val hapticsEnabled = root.optBoolean("hapticsEnabled", true)

        val elementsList = mutableListOf<TouchElementData>()
        val elementsArray = root.optJSONArray("elements")
        if (elementsArray != null) {
            for (i in 0 until elementsArray.length()) {
                val elemObj = elementsArray.getJSONObject(i)
                val id = elemObj.getString("id")
                val xNorm = elemObj.optDouble("xNorm", 0.5).toFloat()
                val yNorm = elemObj.optDouble("yNorm", 0.5).toFloat()
                val scale = elemObj.optDouble("scale", 1.0).toFloat()
                val opacity = elemObj.optDouble("opacity", 0.8).toFloat()

                elementsList.add(TouchElementData(id, xNorm, yNorm, scale, opacity))
            }
        }

        return TouchLayoutData(globalOpacity, hapticsEnabled, elementsList)
    }

    fun applyToView(view: TouchOverlayView, layoutData: TouchLayoutData) {
        view.globalOpacity = layoutData.globalOpacity
        view.hapticsEnabled = layoutData.hapticsEnabled

        for (elemData in layoutData.elements) {
            val elem = view.elements[elemData.id]
            if (elem != null) {
                elem.xNorm = elemData.xNorm
                elem.yNorm = elemData.yNorm
                elem.scale = elemData.scale
                elem.opacity = elemData.opacity
            }
        }
        view.invalidate()
    }
}
