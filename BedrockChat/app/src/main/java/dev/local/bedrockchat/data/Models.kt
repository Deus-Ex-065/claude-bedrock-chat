package dev.local.bedrockchat.data

/** Claude models offered in the model picker. IDs are the Bedrock (Mantle) model IDs. */
data class ClaudeModel(val id: String, val name: String, val description: String)

val MODELS = listOf(
    ClaudeModel("anthropic.claude-fable-5-1", "Fable 5.1", "For your toughest challenges"),
    ClaudeModel("anthropic.claude-opus-5-5", "Opus 5.5", "For complex work and everyday tasks"),
    ClaudeModel("anthropic.claude-sonnet-5-5", "Sonnet 5.5", "Most efficient for simpler tasks"),
    ClaudeModel("anthropic.claude-haiku-5-5", "Haiku 5.5", "Fastest for quick answers"),
)

/** Extra models listed under "More models". */
val MORE_MODELS = listOf(
    ClaudeModel("anthropic.claude-fable-5", "Fable 5", "Previous Fable"),
    ClaudeModel("anthropic.claude-opus-5", "Opus 5", "Previous Opus"),
    ClaudeModel("anthropic.claude-sonnet-5", "Sonnet 5", "Previous Sonnet"),
    ClaudeModel("anthropic.claude-opus-4-8", "Opus 4.8", "Older Opus"),
)

const val DEFAULT_MODEL_ID = "anthropic.claude-opus-5-5"
const val TITLE_MODEL_ID = "anthropic.claude-haiku-5-5"

fun modelById(id: String): ClaudeModel =
    (MODELS + MORE_MODELS).firstOrNull { it.id == id } ?: ClaudeModel(id, id.removePrefix("anthropic."), "")

enum class Effort(val label: String, val apiValue: String, val note: String? = null) {
    LOW("Low", "low"),
    MEDIUM("Medium", "medium", "Recommended"),
    HIGH("High", "high"),
    EXTRA("Extra", "xhigh"),
    MAX("Max", "max", "5× or more usage"),
}

/** Regions where Claude in Amazon Bedrock is served. */
val REGIONS = listOf(
    "us-east-1", "us-east-2", "us-west-1", "us-west-2", "ca-central-1", "ca-west-1",
    "eu-central-1", "eu-central-2", "eu-north-1", "eu-south-1", "eu-south-2",
    "eu-west-1", "eu-west-2", "eu-west-3",
    "ap-northeast-1", "ap-northeast-2", "ap-northeast-3", "ap-south-1", "ap-south-2",
    "ap-southeast-1", "ap-southeast-2", "ap-southeast-3", "ap-southeast-4",
    "af-south-1", "il-central-1", "me-central-1", "sa-east-1",
)
