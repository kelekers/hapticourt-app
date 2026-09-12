// #PACKAGE
package com.takis.hapticourt

// #HAPTIC_MAPPER
object HapticMapper {

    // #MAP_FUNCTION
    fun mapAiToHaptic(aiResult: String): ByteArray {
        val command = when {
            aiResult.contains("Left") -> "L"
            aiResult.contains("Right") -> "R"
            aiResult.contains("Center") -> "C"
            else -> "N"
        }
        return command.toByteArray()
    }
}