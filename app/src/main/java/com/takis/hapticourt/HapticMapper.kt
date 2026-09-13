package com.takis.hapticourt

object HapticMapper {

    fun mapAiToHaptic(aiResult: String): ByteArray {

        if (aiResult == "Error" || aiResult.contains("N,N")) {
            return "N,0".toByteArray()
        }

        try {
            val parts = aiResult.split("|")
            val ballPart = parts[0].removePrefix("B:")
            val playerPart = parts[1].removePrefix("P:")

            val ballCoords = ballPart.split(",")
            val ballX = ballCoords[0].toInt()

            val playerCoords = playerPart.split(",")
            val playerX = playerCoords[0].toInt()

            val deltaX = ballX - playerX

            val command = when {
                deltaX < -30 -> "L,255"
                deltaX > 30 -> "R,255"
                else -> "C,128"
            }
            return command.toByteArray()

        } catch (e: Exception) {
            return "N,0".toByteArray()
        }
    }
}