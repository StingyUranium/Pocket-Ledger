package `in`.thomas.pocketledger

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

val india: ZoneId = ZoneId.of("Asia/Kolkata")
fun paise(text: String): Long = BigDecimal(text.trim().replace(",", "").removePrefix("₹")).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
fun digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
fun normalizedSender(sender: String): String = sender.trim().uppercase()
data class ParsedSms(val bank: String, val suffix: String?, val amount: Long?, val direction: String?, val date: Long?, val dateOnly: Boolean, val payee: String, val reference: String?, val balance: Long?, val complete: Boolean)

object SmsParser {
    private const val MONEY = "([0-9][0-9,]*(?:\\.[0-9]*)?)"
    private val ignored = Regex(
        "credit\\s*card|one[ -]time password|\\bOTP\\b|\\bfailed\\b|declined|unsuccessful|" +
            "will be (?:debited|credited)|scheduled|due date|pre[- ]?approved|apply now|" +
            "limited[- ]time|cashback offer|reward points|has requested money|" +
            "mandate (?:is |has been )?successfully (?:created|modified|revoked)|funds blocked",
        RegexOption.IGNORE_CASE
    )

    private fun find(pattern: String, text: String, group: Int = 1): String? =
        Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(group)

    private fun moneyValue(value: String?): Long? = value?.let {
        runCatching { paise(it) }.getOrNull()
    }

    private data class Stamp(val millis: Long?, val dateOnly: Boolean)

    private fun stamp(text: String): Stamp {
        val iso = Regex("\\b(\\d{4}-\\d{2}-\\d{2})(?:[ T](\\d{2}:\\d{2}(?::\\d{2})?))?\\b").find(text)
        val indian = Regex("\\b(\\d{2}-\\d{2}-(?:\\d{4}|\\d{2}))(?:,?\\s+(?:at\\s+)?(\\d{2}:\\d{2}(?::\\d{2})?))?\\b", RegexOption.IGNORE_CASE).find(text)
        val match = iso ?: indian ?: return Stamp(null, false)
        val dateText = match.groupValues[1]
        val timeText = match.groupValues[2]
        val value = runCatching {
            val format = when {
                iso != null -> "uuuu-MM-dd"
                dateText.length == 10 -> "dd-MM-uuuu"
                else -> "dd-MM-uu"
            }
            val day = LocalDate.parse(dateText, DateTimeFormatter.ofPattern(format).withResolverStyle(ResolverStyle.STRICT))
            val local = if (timeText.isBlank()) day.atStartOfDay() else day.atTime(LocalTime.parse(timeText))
            local.atZone(india).toInstant().toEpochMilli()
        }.getOrNull()
        return Stamp(value, value != null && timeText.isBlank())
    }

    private data class Details(val reference: String?, val payee: String)

    private fun details(text: String): Details {
        val upi = Regex("\\bUPI/[A-Z0-9]+/(\\d{6,})/([^/]*)(?:/|$)", RegexOption.IGNORE_CASE).find(text)
        if (upi != null) return Details(upi.groupValues[1], upi.groupValues[2].trim())

        val imps = Regex("\\bIMPS/[A-Z0-9]+/(?:[A-Z]+/)?(\\d{6,})/([^/]*)(?:/|$)", RegexOption.IGNORE_CASE).find(text)
        if (imps != null) return Details("IMPS:${imps.groupValues[1]}", imps.groupValues[2].trim())

        val bankTransfer = Regex("\\b(NEFT|RTGS|IFT)/([A-Z0-9]{6,})(?:/([^/]+))?", RegexOption.IGNORE_CASE).find(text)
        if (bankTransfer != null) {
            val channel = bankTransfer.groupValues[1].uppercase()
            return Details("$channel:${bankTransfer.groupValues[2].uppercase()}", bankTransfer.groupValues[3].trim().ifBlank { "$channel transfer" })
        }

        val ach = Regex("\\b(ACH_CR|ACH_DR)/(.+)", RegexOption.IGNORE_CASE).find(text)
        if (ach != null) {
            val tokens = ach.groupValues[2].split('/').map { it.trim() }.filter { it.isNotBlank() }
            val reference = tokens.firstOrNull { it.matches(Regex("[A-Z0-9]{6,}", RegexOption.IGNORE_CASE)) && it.any(Char::isDigit) }
            return Details(reference?.let { "${ach.groupValues[1].uppercase()}:${it.uppercase()}" }, tokens.firstOrNull { it != reference } ?: "ACH payment")
        }
        if (text.contains("Int.Pd", true)) return Details(null, "Savings interest")
        return Details(null, "")
    }

    fun parse(body: String): ParsedSms? {
        val text = body.replace(Regex("\\s+"), " ").trim()
        if (ignored.containsMatchIn(text)) return null
        val bank = when {
            Regex("SBM\\s+BANK\\s*(?:\\(INDIA\\)|INDIA)", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "SBM"
            text.contains("Axis Bank", true) -> "AXIS"
            else -> return null
        }
        val topUp = bank == "AXIS" && text.contains("successful top-up of", true)
        val direction = when {
            Regex("\\bdebited\\b|^Debit\\s+INR\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) || topUp -> "EXPENSE"
            Regex("\\bcredited\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "INCOME"
            else -> return null
        }
        val suffix = find("(?:account|a/c\\s*(?:no\\.?)?)\\s*[Xx*]+(\\d{4,})\\b", text)?.takeLast(4)
        val amountText = when {
            bank == "SBM" -> find("(?:credited|debited)\\s+with\\s+INR\\s*$MONEY", text)
            topUp -> find("successful top-up of\\s+INR\\s*$MONEY", text)
            Regex("^Debit\\s+INR", RegexOption.IGNORE_CASE).containsMatchIn(text) -> find("^Debit\\s+INR\\s*$MONEY", text)
            text.contains("Your A/c has been debited towards", true) -> find("\\bfor\\s+INR\\s*$MONEY", text)
            else -> find("^INR\\s*$MONEY\\s+(?:credited|debited)\\b", text)
        }
        val amount = moneyValue(amountText)?.takeIf { it > 0 }
        val transactionText = if (bank == "SBM") text.split(Regex("\\binfo(?:r)?\\s*:", RegexOption.IGNORE_CASE), limit = 2)[0] else text
        val date = stamp(transactionText)
        val balance = moneyValue(find("(?:curr\\s+bal\\s+is|avl\\s+bal\\s*:)\\s*(?:INR\\s*)?$MONEY", text))

        // Stop at bank prose before interpreting slash-delimited references and counterparties.
        val info = if (bank == "SBM") {
            find("\\binfo(?:r)?\\s*:\\s*(.*?)(?=\\.?\\s*The\\s+curr\\s+bal|$)", text).orEmpty()
        } else {
            text.split(Regex("\\s+(?:Not you\\?|WhatsApp BAL)|\\.?\\s*(?:View bal:|Chk Bal)|\\s+-\\s+Axis Bank", RegexOption.IGNORE_CASE), limit = 2)[0]
        }
        var detail = details(info)
        if (bank == "AXIS") {
            val mandatePayee = find("Your A/c has been debited towards (.+?) for INR", text)
            val cardPayee = find("debited from A/c no\\.\\s*[Xx*]+\\d+\\s+on\\s+(.+?)\\s+\\d{2}-\\d{2}-\\d{4}\\b", text)
            if (mandatePayee != null) detail = Details(null, mandatePayee.trim())
            else if (cardPayee != null) detail = detail.copy(payee = cardPayee.trim())
            else if (topUp) detail = Details(find("Ref no\\.\\s*([A-Z0-9]{6,})", text)?.let { "TOPUP:$it" }, "UPI Lite top-up")
        }
        // Account-less mandates, interest credits without references, and wallet top-ups need review.
        val complete = suffix != null && amount != null && date.millis != null && detail.reference != null && !topUp
        return ParsedSms(bank, suffix, amount, direction, date.millis, date.dateOnly, detail.payee, detail.reference, balance, complete)
    }

    fun bodyKey(sender: String, body: String, received: Long): String =
        digest("${normalizedSender(sender)}|${body.replace(Regex("\\s+"), " ").trim()}|$received")

    fun key(p: ParsedSms, sender: String, body: String, received: Long): String {
        if (!p.complete || p.reference == null) return bodyKey(sender, body, received)
        // ACH references can be reused by a mandate; scope them to the actual posting date.
        val posting = if (p.reference.startsWith("ACH_")) "|${p.date?.let { Instant.ofEpochMilli(it).atZone(india).toLocalDate() }}" else ""
        return digest("${p.bank}|${p.suffix}|${p.reference}|${p.direction}$posting")
    }
}
