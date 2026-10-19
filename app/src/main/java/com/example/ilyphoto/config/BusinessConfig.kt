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
 * Official business details and community social-impact mission for ILY PHOTO
 * (from WIL Task 1 & Task 2 Specification).
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
            "portraits, and community outreach events — capturing high-quality photographs " +
            "so local families and organisations can keep and share their precious memories forever."

    const val COMMUNITY_OUTREACH_TITLE = "Strandfontein Community & NGO Impact"
    const val COMMUNITY_OUTREACH_TEXT =
        "Rooted in Strandfontein and the Cape Flats, ILY Photo partners with local schools, " +
            "youth development NGOs, soup kitchens, and faith-based community organisations to " +
            "provide subsidised Matric Farewell portraits for under-resourced learners and " +
            "pro-bono photography for registered NGO charity drives."

    const val CONTACT_EMAIL = "ayshaallie23@gmail.com"
    const val CONTACT_PHONE = "084 899 3280"

    const val REQUIRE_LOGIN_TO_VIEW_PHOTOS = true

    /** Categories offered when uploading and filtering portfolio items. */
    val categories = listOf(
        "Birthday",
        "Wedding",
        "Matric Ball",
        "Events",
        "Portraits",
        "Community / NGO Outreach",
        "Other"
    )

    val packages = listOf(
        PricingPackage(
            name = "Community / NGO Outreach",
            price = "R 200",
            priceAmount = 200,
            durationHours = 2,
            unit = "2 hour subsidised NGO & youth session",
            features = listOf(
                "2 hour event or learner coverage",
                "Subsidised Cape Flats NGO & school rate",
                "30+ high-res digital photos",
                "Cloud gallery for donors & community"
            ),
        ),
        PricingPackage(
            name = "Portrait / Mini Session",
            price = "R 300",
            priceAmount = 300,
            durationHours = 1,
            unit = "1 hour session",
            features = listOf("1 hour shoot", "1 location", "15 edited photos", "Online gallery"),
        ),
        PricingPackage(
            name = "Matric Ball / Events",
            price = "R 400",
            priceAmount = 400,
            durationHours = 2,
            unit = "2 hour package",
            features = listOf("2 hour coverage", "Up to 2 locations", "45 edited photos", "Online gallery"),
            highlighted = true,
        ),
        PricingPackage(
            name = "Weddings",
            price = "R 1 200",
            priceAmount = 1200,
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
