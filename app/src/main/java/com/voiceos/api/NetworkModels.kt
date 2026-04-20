package com.voiceos.api

import com.voiceos.model.Command

// ── Authentication ────────────────────────────────────────────────────────

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val token: String?,
    val message: String?,
    val user: UserDto?
)

data class UserDto(
    val id: String,
    val email: String,
    val plan: String,
    val name: String? = null,
    val isAdmin: Boolean? = null
)

// ── Devices ───────────────────────────────────────────────────────────────

data class DeviceDto(
    val id: String,
    val device_name: String,
    val device_type: String,
    val status: String,
    val device_token: String? = null
)

data class ConnectDeviceRequest(
    val deviceName: String,
    val deviceType: String = "android",
    val deviceToken: String
)

data class ConnectResponse(
    val success: Boolean,
    val message: String?,
    val device: DeviceDto? = null
)

// ── Commands ──────────────────────────────────────────────────────────────

data class TextCommandRequest(
    val input: String,
    val deviceId: String? = null,
    val context: Map<String, String>? = null
)

data class CommandResponse(
    val success: Boolean,
    val commandId: String?,
    val structured: CommandDto?,
    val message: String?,
    val delivered: Boolean? = null
)

/**
 * Maps the backend's StructuredCommand JSON object directly back into
 * the local app's Command data class hierarchy.
 * We use a bridging DTO here because the backend might return simplified structures.
 */
data class CommandDto(
    val intent: String,
    val confidence: Double,
    val steps: List<CommandStepDto>
)

data class CommandStepDto(
    val action: String,
    val app: String?,
    val target: String?,
    val message: String?,
    val direction: String?,
    val index: Int?,
    val text: String?
)

// ── Macros ────────────────────────────────────────────────────────────────

data class MacroStepDto(
    val action: String,
    val app: String? = null,
    val target: String? = null,
    val message: String? = null,
    val direction: String? = null,
    val index: Int? = null,
    val text: String? = null
)

data class MacroDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val steps: List<MacroStepDto>,
    val delay_ms: Long = 1500L,
    val is_active: Boolean = true
)

data class MacrosResponse(
    val success: Boolean,
    val macros: List<MacroDto> = emptyList(),
    val message: String? = null
)

// Extension to map DTO back to internal sealed classes
fun CommandDto.toInternalCommand(): Command {
    if (this.steps.isEmpty()) return Command.Unknown(this.intent)

    return when (this.intent) {
         "CLICK" -> Command.Click(this.steps.first().index ?: 0)
         "SCROLL" -> {
             val dir = this.steps.first().direction?.trim()?.uppercase()
             if (dir == "UP") {
                 Command.Scroll(Command.ScrollDirection.UP)
             } else {
                 Command.Scroll(Command.ScrollDirection.DOWN)
             }
         }
         "GO_BACK" -> Command.GoBack
         "OPEN_APP" -> Command.OpenApp(this.steps.first().app ?: "")
         "SEND_MESSAGE" -> {
             val step = this.steps.first()
             Command.SendMessage(
                 contact = step.target ?: "",
                 message = step.message ?: "",
                 app = step.app ?: "whatsapp"
             )
         }
         "MULTI_STEP" -> {
             val internalSteps = this.steps.mapNotNull { 
                 CommandDto(it.action, 1.0, listOf(it)).toInternalCommand() 
             }
             Command.MultiStep(internalSteps)
         }
         "RUN_MACRO" -> Command.RunMacro(this.steps.first().text ?: "")
         else -> Command.Unknown(this.intent)
    }
}
