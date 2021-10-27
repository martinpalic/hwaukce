data class AuctionItem(
    val title: String,
    val link: String,
    val type: AuctionType,
    val bidLimit: Int = 0
)

fun List<AuctionItem>.printAuctionItems(){
    println("${"ITEM TITLE".padStart(45).padEnd(100)}|${"BID LIMIT".padStart(10)} | ${"ITEM LINK".padStart(35).padEnd(100)}")
    println("_".repeat(220))
    this.forEach {
        println("${it.title.padEnd(100)}|${it.bidLimit.toString().padStart(10)} | ${it.link.padEnd(100)}")
    }
    println("_".repeat(220))
}