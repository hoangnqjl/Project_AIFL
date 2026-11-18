package com.dacn2.objectdetect.ml

enum class DistanceLevel(val vietnameseName: String) {
    NEAR("rất gần"),       // Dưới ~1m (vật chiếm > 35% diện tích hoặc chiều cao khung hình)
    MEDIUM("khoảng cách vừa"), // ~1m - 2.5m (chiếm 15% - 35%)
    FAR("ở xa")           // > 2.5m (chiếm < 15%)
}

enum class Direction(val vietnameseName: String) {
    LEFT("bên trái"),
    CENTER("phía trước"),
    RIGHT("bên phải")
}

data class BoundingBox(
    val x1: Float, // Normalized 0..1
    val y1: Float, // Normalized 0..1
    val x2: Float, // Normalized 0..1
    val y2: Float, // Normalized 0..1
    val cx: Float, // Center X
    val cy: Float, // Center Y
    val w: Float,  // Width
    val h: Float,  // Height
    val score: Float,
    val classIndex: Int,
    val className: String,
    val labelVi: String,
    val distanceLevel: DistanceLevel,
    val direction: Direction
)
