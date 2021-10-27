import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException

typealias Cookie = String

const val SECONDS_TO_END_FOR_BIDDING = 5
const val HW_AUCTION_LOGIN = ""
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
            // load configuration (zero max bid means im skipping this item)
            val items = loadCurrentConfiguration()
            items.printAuctionItems()
            //    val cookie = loginToAuction(client)
            // loop
            // get waiting time
            // console write current bids (), wait half of remaining time
            // end loop
            //if time is right -> place current bid + 1
            // loop
            // check -> if i am not winner, bid +1
            // if i am winner, do nothing
            // check every 1/4 second until finish
        }
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

fun placeBid(client: OkHttpClient, cookie: Cookie, auctionItem: AuctionItem) {
    val request = Request.Builder().url(auctionItem.link)
        .addHeader("Cookie", cookie)
        .post(
            FormBody.Builder()
                .add("bid", "1509")
                .add("uwa_bid_value", "0")
                .add("uwa-place-bid", "1509")
                .add("product_id", "1509")
                .add("user_id", "386")
                .build()
        )
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        println(Jsoup.parse(response.body!!.string()).getElementsByClass("woocommerce-error")[0])
    }
}

fun loginToAuction(client: OkHttpClient): Cookie {
    val request = Request.Builder().url("https://hwauction.ba.innovatrics.net/my-account/")
        .post(
            FormBody.Builder()
                .add("username", HW_AUCTION_LOGIN)
                .add("password", HW_AUCTION_PASSWORD)
                .add("woocommerce-login-nonce", "88658bfba5")
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
            AuctionItem(
                type = AuctionType.fromText(auctionTypeText),
                title = titleElement.text(),
                link = titleElement.attr("href")
            )
        }

    return auctionEntries
}

