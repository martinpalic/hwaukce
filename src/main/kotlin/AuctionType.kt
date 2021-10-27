enum class AuctionType(val typeText: String) {
    BID("Bid now"),
    BUY("Add to cart");

    companion object {
        fun fromText(text: String): AuctionType {
            return values().first { it.typeText == text }
        }
    }
}