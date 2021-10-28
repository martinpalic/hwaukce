enum class AuctionType(val typeText: String) {
    OTHER("I Don't Care for This"),
    BID("Bid now");

    companion object {
        fun fromText(text: String): AuctionType {
            return if (BID.typeText == text) BID else OTHER
        }
    }
}