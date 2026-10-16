package com.example.ilyphoto.config

/** One photography service/package card shown in the Pricing & Booking screens. */
data class PricingPackage(
    val name: String,
    val price: String,
    val priceAmount: Int,
    val durationHours: Int,
    val unit: String,
    val features: List<String>,
    val highlighted: Boolean = false,
)

/** Banking details shown on Invoices for EFT payments. No bank API is involved. */
data class BankDetails(
    val bankName: String,
    val accountHolder: String,
    val accountType: String,
    val accountNumber: String,
    val branchCode: String,
    val referenceHint: String,
)

/**
 * Official business details for ILY PHOTO (from WIL Task 1 Specification).
 */
object BusinessConfig {
    const val BUSINESS_NAME = "ILY Photo"
    const val TAGLINE = "Capture forever — where the heart is"
    const val OWNER_NAME = "Aysha Allie"
    const val BUSINESS_ADDRESS = "8 Green Way, Strandfontein, Cape Town"

    const val ABOUT_TITLE = "About ILY Photo"
    const val ABOUT_TEXT =
        "ILY Photo is an independent photography business in Strandfontein, Cape Town, " +
            "owned by Aysha Allie. We specialise in weddings, matric balls, birthdays, " +
            "portraits, and special events — capturing high-quality photographs so you can " +
            "keep and share your precious memories forever."

    const val CONTACT_EMAIL = "ayshaallie23@gmail.com"
    const val CONTACT_PHONE = "084 899 3280"

    const val REQUIRE_LOGIN_TO_VIEW_PHOTOS = true

    /** Categories offered when uploading and filtering portfolio items. */
    val categories = listOf("Birthday", "Wedding", "Matric Ball", "Events", "Portraits", "Other")

    val packages = listOf(
        PricingPackage(
            name = "Portrait / Mini Session",
            price = "R 1 500",
            priceAmount = 1500,
            durationHours = 1,
            unit = "1 hour session",
            features = listOf("1 hour shoot", "1 location", "15 edited photos", "Online gallery"),
        ),
        PricingPackage(
            name = "Matric Ball / Birthday",
            price = "R 3 500",
            priceAmount = 3500,
            durationHours = 3,
            unit = "3 hour package",
            features = listOf("3 hour coverage", "Up to 2 locations", "45 edited photos", "Online gallery"),
            highlighted = true,
        ),
        PricingPackage(
            name = "Wedding / Full Event",
            price = "R 8 000",
            priceAmount = 8000,
            durationHours = 6,
            unit = "6 hour package",
            features = listOf("Up to 6 hours coverage", "100+ edited photos", "Online gallery", "Priority editing"),
        ),
    )

    val bank = BankDetails(
        bankName = "First National Bank (FNB)",
        accountHolder = "ILY Photo (Aysha Allie)",
        accountType = "Cheque / Current",
        accountNumber = "62849103847",
        branchCode = "250655",
        referenceHint = "Invoice # or Full Name + Event Date",
    )
}
