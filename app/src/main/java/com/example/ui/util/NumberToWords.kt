package com.example.ui.util

object NumberToWords {

    private val units = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )

    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    /**
     * Converts a numeric currency amount into Indian English words.
     * Handles Crores, Lakhs, Thousands, Hundreds, and Paise.
     * Example: 125450.50 -> "One Lakh Twenty-Five Thousand Four Hundred Fifty Rupees and Fifty Paise Only"
     */
    fun convert(amount: Double): String {
        val totalPaise = Math.round(Math.abs(amount) * 100.0)
        if (totalPaise == 0L) return "Zero Rupees Only"

        val rupees = totalPaise / 100
        val paise = (totalPaise % 100).toInt()

        val sb = StringBuilder()
        if (rupees > 0) {
            sb.append(convertNumber(rupees)).append(" Rupees")
        }
        if (paise > 0) {
            if (sb.isNotEmpty()) sb.append(" and ")
            sb.append(convertNumber(paise.toLong())).append(" Paise")
        }
        sb.append(" Only")
        return sb.toString().trim()
    }

    private fun convertNumber(n: Long): String {
        if (n == 0L) return ""
        if (n < 20L) return units[n.toInt()]
        if (n < 100L) {
            val rem = (n % 10L).toInt()
            return tens[(n / 10L).toInt()] + (if (rem > 0) "-" + units[rem] else "")
        }
        if (n < 1000L) {
            val rem = n % 100L
            return units[(n / 100L).toInt()] + " Hundred" + (if (rem > 0) " " + convertNumber(rem) else "")
        }
        if (n < 100000L) { // Less than 1 Lakh
            val rem = n % 1000L
            return convertNumber(n / 1000L) + " Thousand" + (if (rem > 0) " " + convertNumber(rem) else "")
        }
        if (n < 10000000L) { // Less than 1 Crore
            val rem = n % 100000L
            return convertNumber(n / 100000L) + " Lakh" + (if (rem > 0) " " + convertNumber(rem) else "")
        }
        val rem = n % 10000000L
        return convertNumber(n / 10000000L) + " Crore" + (if (rem > 0) " " + convertNumber(rem) else "")
    }
}
