package ai.neuron.sdk.skills

/**
 * Validates SKILL.md manifests before they can be loaded.
 * Checks permissions, tool definitions, and required fields.
 */
class SkillValidator {
    /** Known valid permissions. */
    private val validPermissions =
        setOf(
            "LAUNCH_APP",
            "TAP_ELEMENT",
            "TYPE_TEXT",
            "NAVIGATE",
            "SWIPE",
            "READ_SCREEN",
            "SEND_MESSAGE",
            "MAKE_CALL",
            "ACCESS_CONTACTS",
            "ACCESS_CAMERA",
            "ACCESS_LOCATION",
        )

    data class ValidationResult(
        val valid: Boolean,
        val errors: List<String> = emptyList(),
    )

    fun validate(manifest: SkillManifest): ValidationResult {
        val errors = mutableListOf<String>()

        // Name validation
        if (manifest.name.isBlank()) {
            errors.add("Skill name cannot be blank")
        }
        if (!manifest.name.matches(Regex("^[a-z0-9-]+$"))) {
            errors.add("Skill name must be lowercase alphanumeric with hyphens: ${manifest.name}")
        }

        // Version validation
        if (!manifest.version.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))) {
            errors.add("Version must be semver format (x.y.z): ${manifest.version}")
        }

        // Description validation
        if (manifest.description.isBlank()) {
            errors.add("Description cannot be blank")
        }

        // Permission validation
        for (perm in manifest.permissions) {
            if (perm !in validPermissions) {
                errors.add("Unknown permission: $perm")
            }
        }

        // Tool validation
        for (tool in manifest.tools) {
            if (tool.name.isBlank()) {
                errors.add("Tool name cannot be blank")
            }
            if (tool.description.isBlank()) {
                errors.add("Tool '${tool.name}' must have a description")
            }
        }

        // Must have at least one tool or trigger
        if (manifest.tools.isEmpty() && manifest.triggers.isEmpty()) {
            errors.add("Skill must define at least one tool or trigger")
        }

        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }
}
