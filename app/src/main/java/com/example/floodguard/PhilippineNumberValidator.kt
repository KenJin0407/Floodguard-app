package com.example.floodguard

/**
 * Strict Philippine Mobile Number Validator.
 * Enforces real Philippine telecommunications network prefixes (Globe, Smart, DITO, Sun, TM, TNT)
 * and rejects fake, made-up, dummy, and sequential numbers.
 */
object PhilippineNumberValidator {

    // Valid telecom network prefixes in the Philippines (4-digit prefixes)
    private val VALID_TELCO_PREFIXES = setOf(
        // Globe / TM
        "0905", "0906", "0915", "0916", "0917", "0926", "0927", "0935", "0936", "0937",
        "0945", "0953", "0954", "0955", "0956", "0965", "0966", "0967", "0975", "0976",
        "0977", "0978", "0979", "0995", "0996", "0997",

        // Smart / TNT
        "0907", "0908", "0909", "0910", "0912", "0913", "0914", "0918", "0919", "0920",
        "0921", "0928", "0929", "0930", "0931", "0938", "0939", "0940", "0946", "0947",
        "0948", "0949", "0950", "0951", "0961", "0963", "0964", "0968", "0969", "0970",
        "0971", "0981", "0982", "0985", "0989", "0992", "0998", "0999",

        // Sun Cellular
        "0922", "0923", "0924", "0925", "0932", "0933", "0934", "0942", "0943",

        // DITO Telecommunity
        "0895", "0896", "0897", "0898", "0991", "0992", "0993", "0994"
    )

    private val REJECTED_DUMMY_NUMBERS = setOf(
        "09123456789", "09876543210", "09000000000", "09111111111",
        "09222222222", "09333333333", "09444444444", "09555555555",
        "09666666666", "09777777777", "09888888888", "09999999999",
        "09123123123", "09987654321"
    )

    /**
     * Standardizes any Philippine phone input (e.g., "+63 917 123 4567", "639171234567", "0917-123-4567")
     * into a clean 11-digit string starting with 09 (or 08 for DITO).
     */
    fun standardize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        val normalized = when {
            digits.startsWith("63") && digits.length == 12 -> "0" + digits.substring(2)
            (digits.startsWith("09") || digits.startsWith("08")) && digits.length == 11 -> digits
            digits.startsWith("9") && digits.length == 10 -> "0$digits"
            digits.startsWith("8") && digits.length == 10 -> "0$digits"
            else -> return null
        }
        return if (normalized.length == 11) normalized else null
    }

    /**
     * Full validation of the number. Returns Pair(isValid, errorMessage).
     */
    fun validate(raw: String?): Pair<Boolean, String> {
        val clean = standardize(raw)
            ?: return Pair(false, "Invalid format: Must be an 11-digit Philippine mobile number starting with 09 (e.g. 09171234567).")

        if (clean in REJECTED_DUMMY_NUMBERS) {
            return Pair(false, "Invalid number: Sequential or dummy phone numbers (e.g. $clean) are not allowed.")
        }

        // Check if the subscriber suffix (last 7 digits) consists of only 1 repeating digit (e.g., 0917-0000000)
        val suffix = clean.substring(4)
        if (suffix.toSet().size <= 1) {
            return Pair(false, "Invalid number: Repetitive subscriber digits are not allowed.")
        }

        val prefix = clean.substring(0, 4)
        if (prefix !in VALID_TELCO_PREFIXES) {
            return Pair(false, "Invalid network prefix ($prefix): Must be a valid Philippine telecommunications operator (Globe, TM, Smart, TNT, Sun, or DITO).")
        }

        return Pair(true, clean)
    }

    /**
     * Returns true if valid, false otherwise.
     */
    fun isValid(raw: String?): Boolean {
        return validate(raw).first
    }
}
