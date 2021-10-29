import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.lang.Integer.min
import java.time.Instant
import java.util.Date

typealias Cookie = String

const val SECONDS_TO_END_FOR_BIDDING = 30
const val HW_AUCTION_LOGIN = ""
val HW_AUCTION_TEAMMATES = arrayOf("","")
const val HW_AUCTION_PASSWORD = ""

fun main() {
    println("Load Items and reset configuration: Reload")
    println("Load Configuration wait for the right time and start bidding: START")
    when (readLine()) {
        "Reload" -> {
            val auctionItems = loadCurrentAuctionItems(OkHttpClient())
            writeBidItemsConfiguration(auctionItems)
        }
        "START" -> {
            val client = OkHttpClient()
            // load configuration (zero max bid means im skipping this item)
            val items = loadCurrentConfiguration()
            items.printAuctionItems()
            val cookie = loginToAuction(client)
            waitForBiddingTime(items, client, SECONDS_TO_END_FOR_BIDDING)

            spinThatWheeeeeeel(items, client, cookie, HW_AUCTION_LOGIN)
        }
    }
}

private fun spinThatWheeeeeeel(
    items: List<AuctionItem>,
    client: OkHttpClient,
    cookie: Cookie,
    userName: String
) {
    while (true) {
        println("_".repeat(30))
        println("Run bidding loop")
        items.parallelStream().forEach { auctionItem ->
            bidItemAuctionRound(auctionItem, client, cookie, userName)
        }
        Thread.sleep(250)
    }
}

fun bidItemAuctionRound(
    auctionItem: AuctionItem,
    client: OkHttpClient,
    cookie: String,
    userName: String
) {
    val request = Request.Builder().url(auctionItem.link)
        .addHeader("Cookie", cookie)
        .get()
        .build()

    val document = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        return@use Jsoup.parse(response.body!!.string())
    }

    val element = document.getElementsByClass("auction-history-table")[0]
    val lastBidRow = element.child(1).child(0)
    val bidder = lastBidRow.getElementsByClass("bid_username")[0].text()
    if (bidder == userName || HW_AUCTION_TEAMMATES.contains(bidder)) {
        println("My bid is winning for: ${auctionItem.title}")
        return
    }
    val currentBid = lastBidRow.getElementsByTag("bdi")[0].text().split(",")[0].toInt()
    println("Current price is $currentBid and limit is ${auctionItem.bidLimit} for: ${auctionItem.title}")
    if (currentBid >= auctionItem.bidLimit) return
    val productForm = document.getElementsByClass("uwa_auction_form cart")[0]
    val productId = productForm.attr("data-product_id")
    val userId = productForm.children().last()!!.attr("value")
    placeBid(client, cookie, auctionItem, currentBid + 1, productId, userId)
}

private fun waitForBiddingTime(items: List<AuctionItem>, client: OkHttpClient, SECONDS_TO_END_FOR_BIDDING: Int) {
    val deadlineInstant = getCurrentAuctionDeadline(items, client)
    println("\nAuction is ending on: ")
    println(Date.from(deadlineInstant))
    while (true) {
        val instantNow = Date().toInstant()
        val remainingSeconds = deadlineInstant.epochSecond - instantNow.epochSecond
        if (SECONDS_TO_END_FOR_BIDDING >= remainingSeconds) {
            println("WAITING IS OVER, remaining seconds: $remainingSeconds")
            return
        }
        println("${remainingSeconds / 3600}h:${(remainingSeconds % 3600) / 60}m:${remainingSeconds % 60}s remaining to end of auction")
        val waitTimeSeconds = min((remainingSeconds - SECONDS_TO_END_FOR_BIDDING).toInt() / 2, 60)
        println("Checking again in $waitTimeSeconds seconds")
        println()
        Thread.sleep(waitTimeSeconds * 1000L)
    }

}

fun getCurrentAuctionDeadline(items: List<AuctionItem>, client: OkHttpClient): Instant {
    val request = Request.Builder().url(items.firstOrNull()!!.link).get().build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        val auctionEndTimestamp = Jsoup.parse(response.body!!.string())
            .getElementsByClass("uwa_auction_product_countdown")[0].attr("data-time")
        return Instant.ofEpochSecond(auctionEndTimestamp.toLong())
    }
}

fun loadCurrentConfiguration(): List<AuctionItem> {
    val lines = File("configuration.txt").readLines()
    val floorDiv = Math.floorDiv(lines.size - 1, 3)
    return (0..floorDiv).map {
        AuctionItem(
            link = lines[it * 3],
            title = lines[it * 3 + 1],
            type = AuctionType.BID,
            bidLimit = lines[it * 3 + 2].toInt()
        )
    }
}

fun writeBidItemsConfiguration(auctionItems: List<AuctionItem>) {
    File("configuration.txt").printWriter().use { writer ->
        auctionItems.filter { it.type == AuctionType.BID }.forEach {
            writer.println(it.link)
            writer.println(it.title)
            writer.println(it.bidLimit)
        }
    }
}

fun placeBid(client: OkHttpClient, cookie: Cookie, auctionItem: AuctionItem, bid: Int, productId: String, user_id: String) {
    println("About to place bid $bid for ${auctionItem.title}")
    val request = Request.Builder().url(auctionItem.link)
        .addHeader("Cookie", cookie)
        .post(
            FormBody.Builder()
                .add("bid", productId)
                .add("uwa_bid_value", bid.toString())
                .add("uwa-place-bid", productId)
                .add("product_id", productId)
                .add("user_id", user_id)
                .build()
        )
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        if(Jsoup.parse(response.body!!.string()).getElementsByClass("woocommerce-error").isEmpty()){
            println("Managed to bid $bid on ${auctionItem.title}")
        }else{
            println("BIDDING FAILED FOR ${auctionItem.title}")
        }
    }
}

fun loginToAuction(client: OkHttpClient): Cookie {
    val nonceRequest = Request.Builder().url("https://hwauction.ba.innovatrics.net/my-account/")
        .get()
        .build()
    val nonce = client.newCall(nonceRequest).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        return@use Jsoup.parse(response.body?.string()).getElementById("woocommerce-login-nonce")!!.attr("value")
    }

    val request = Request.Builder().url("https://hwauction.ba.innovatrics.net/my-account/")
        .post(
            FormBody.Builder()
                .add("username", HW_AUCTION_LOGIN)
                .add("password", HW_AUCTION_PASSWORD)
                .add("woocommerce-login-nonce", nonce)
                .add("login", "Log+in")
                .build()
        )
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        return response.priorResponse?.headers?.get("Set-Cookie")!!
    }
}

private fun loadCurrentAuctionItems(client: OkHttpClient): List<AuctionItem> {
    val request = Request.Builder().url("https://hwauction.ba.innovatrics.net/").get().build()
    val body = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        return@use response.body!!.string()
    }

    val auctionEntries = Jsoup.parse(body).getElementsByClass("woo-entry-inner clr")
        .map {
            val titleElement = it.getElementsByClass("title")[0].child(0).child(0)
            val auctionTypeText = it.getElementsByClass("btn-wrap clr")[0].child(0).text()
            val item = AuctionItem(
                type = AuctionType.fromText(auctionTypeText),
                title = titleElement.text(),
                link = titleElement.attr("href")
            )
            println("Loaded: $item")
            item
        }

    return auctionEntries
}

