package ai.freeapp.bricktown.legal

/**
 * Who the legal pages name. Set APP_NAME, CONTACT_EMAIL and COMPANY_NAME on the
 * server; until APP_NAME and CONTACT_EMAIL are set, every page footer says the
 * values are the template's.
 *
 * The company is the controller in the privacy policy. Without COMPANY_NAME the
 * app name stands in, which is how a product without a registered entity is
 * named anyway; when an entity exists, use its registered name, because the
 * store lists the developer under it and the policy has to match.
 */
data class LegalInfo(
    val appName: String,
    val contactEmail: String,
    val companyName: String,
    val usesTemplateValues: Boolean,
) {
    companion object {
        const val TEMPLATE_APP_NAME = "kmp-ktor-template"
        const val TEMPLATE_CONTACT_EMAIL = "privacy@example.com"

        /** Change it whenever the copy in [LegalPages] changes. */
        const val EFFECTIVE_DATE = "5 November 2026"

        fun from(appName: String, contactEmail: String, companyName: String): LegalInfo {
            val name = appName.ifBlank { TEMPLATE_APP_NAME }
            return LegalInfo(
                appName = name,
                contactEmail = contactEmail.ifBlank { TEMPLATE_CONTACT_EMAIL },
                companyName = companyName.ifBlank { name },
                usesTemplateValues = appName.isBlank() || contactEmail.isBlank(),
            )
        }
    }
}
