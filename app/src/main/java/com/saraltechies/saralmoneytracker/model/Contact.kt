package com.saraltechies.saralmoneytracker.model

/** Who a loan is with. [SELF] is you: your details printed at the top of statements. */
enum class ContactKind { PERSON, BANK, COMPANY, VENDOR, SELF }

/**
 * A person, bank, finance company or vendor you lend to or borrow from, with the details a statement needs, or your
 * own details ([ContactKind.SELF]). Everything here stays on the phone; it only leaves inside a statement you share.
 */
data class Contact(
    val id: Long = 0,
    val name: String,
    val kind: ContactKind = ContactKind.PERSON,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    /** PAN, GSTIN or another tax or ID number. */
    val taxId: String = "",
    val bankName: String = "",
    val bankAccount: String = "",
    val ifsc: String = "",
    val upi: String = "",
    /** A bank's loan account number, an agreement number, or any reference. */
    val reference: String = "",
    val note: String = "",
    /** For your own details ([ContactKind.SELF]): the book they are for, so each business has its own. */
    val bookId: Long = Books.PERSONAL_ID,
) {
    /** True when there is something besides the name worth printing. */
    val hasDetails: Boolean
        get() = listOf(phone, email, address, taxId, bankName, bankAccount, ifsc, upi, reference).any { it.isNotBlank() }

    val hasBankDetails: Boolean get() = bankAccount.isNotBlank() || upi.isNotBlank()

    fun trimmed() = copy(
        name = name.trim(), phone = phone.trim(), email = email.trim(), address = address.trim(), taxId = taxId.trim().uppercase(),
        bankName = bankName.trim(), bankAccount = bankAccount.trim(), ifsc = ifsc.trim().uppercase(), upi = upi.trim(),
        reference = reference.trim(), note = note.trim(),
    )

    companion object {
        /** A UPI ID looks like name@bank. */
        fun isUpiId(text: String): Boolean = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,64}$").matches(text.trim())

        fun isEmail(text: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(text.trim())

        /** An Indian IFSC: 4 letters, a zero, then 6 letters or digits. */
        fun isIfsc(text: String): Boolean = Regex("^[A-Za-z]{4}0[A-Za-z0-9]{6}$").matches(text.trim())
    }
}

/**
 * A UPI payment link (the content of a "scan to pay" QR code), understood by every UPI app in India.
 * [amount] in hundredths; zero leaves the amount for the payer to type.
 */
object UpiLink {
    fun build(upiId: String, payeeName: String, amount: Long, note: String): String {
        val params = mutableListOf("pa=" + enc(upiId.trim()), "pn=" + enc(payeeName.trim()))
        if (amount > 0) params += "am=" + java.math.BigDecimal.valueOf(amount, 2).toPlainString()
        params += "cu=INR"
        if (note.isNotBlank()) params += "tn=" + enc(note.trim().take(50))
        return "upi://pay?" + params.joinToString("&")
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
