package com.example.ui.ai

import com.example.data.AppRepository
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

sealed class AiResponse {
    data class Success(val replyText: String, val actionTitle: String? = null, val reportContent: String? = null, val executeAction: (() -> Unit)? = null) : AiResponse()
    data class OutOfScope(val replyText: String) : AiResponse()
}

class GhanshyamAiEngine(
    private val repository: AppRepository,
    private val apiKey: String = ""
) {
    private val client = OkHttpClient()

    suspend fun processQuery(userInput: String): AiResponse = withContext(Dispatchers.IO) {
        val query = userInput.trim()
        if (query.isBlank()) {
            return@withContext AiResponse.Success("Aap kuch boliye ya type kijiye, main dukaan ke kaam me madad karunga.")
        }

        val lower = query.lowercase()

        // 1. Check for Out-of-Scope queries
        val outOfScopeKeywords = listOf(
            "weather", "mausam", "song", "gaana", "movie", "film", "cricket", "score",
            "joke", "chutkula", "poem", "kavita", "president", "pm", "modi", "politics",
            "recipe", "khana kaise banaye", "translate english to french"
        )
        if (outOfScopeKeywords.any { lower.contains(it) }) {
            return@withContext AiResponse.OutOfScope(
                "Maaf kijiye, ye Ghanshyam Billing ke scope ke bahar hai. Main sirf aapki dukaan ka khata, billing, inventory, cashbook aur financial reports manage kar sakta hoon."
            )
        }

        // 2. Generate Reports
        if (lower.contains("report") || lower.contains("statement") || lower.contains("hisaab") || lower.contains("hisab")) {
            return@withContext generateBusinessReport(query, lower)
        }

        // 3. Cancel out Udhar (Specific user request!)
        // e.g. "Rajesh Sharma ka 1850 udhar cancel karo" or "cancel out udhar"
        if (lower.contains("cancel") && (lower.contains("udhar") || lower.contains("entry") || lower.contains("transaction") || lower.contains("gave"))) {
            return@withContext handleCancelTransaction(query, lower)
        }

        // 4. Record Udhar (Gave) or Jama (Got)
        // e.g. "Ramesh ko 500 udhar diya", "Rajesh se 400 mila"
        if (lower.contains("udhar") || lower.contains("diya") || lower.contains("gave") || lower.contains("mila") || lower.contains("jama") || lower.contains("got")) {
            return@withContext handleTransactionCommand(query, lower)
        }

        // 5. Add Customer
        if (lower.contains("customer") && (lower.contains("add") || lower.contains("nava") || lower.contains("jodo"))) {
            return@withContext handleAddCustomerCommand(query)
        }

        // 6. Cashbook Entry (e.g. "Chai samosa kharcha 60 rupay", "Counter cash in 2000")
        if (lower.contains("kharcha") || lower.contains("expense") || lower.contains("cash out") || lower.contains("cash in") || lower.contains("chai") || lower.contains("petty")) {
            return@withContext handleCashEntryCommand(query, lower)
        }

        // 7. Inventory stock update
        if (lower.contains("stock") || lower.contains("inventory") || lower.contains("item")) {
            return@withContext handleInventoryCommand(query, lower)
        }

        // 8. Generate Invoice
        if (lower.contains("bill") || lower.contains("invoice")) {
            return@withContext handleInvoiceCommand(query, lower)
        }

        // 9. Update Store Profile (UPI, address)
        if (lower.contains("upi") || lower.contains("address") || lower.contains("pata") || lower.contains("phone")) {
            return@withContext handleProfileCommand(query, lower)
        }

        // 10. Switch Language
        if (lower.contains("language") || lower.contains("bhasha") || lower.contains("hindi") || lower.contains("gujarati") || lower.contains("hinglish") || lower.contains("english")) {
            return@withContext handleLanguageCommand(lower)
        }

        // If not matched by deterministic patterns, try LLM if API key is provided
        if (apiKey.isNotBlank() && apiKey != "YOUR_GEMINI_API_KEY") {
            try {
                return@withContext callGeminiFallback(query)
            } catch (_: Exception) {}
        }

        // Friendly default fallback
        AiResponse.Success(
            replyText = "Samajh gaya. Main aapki dukaan me Khata (Udhar/Jama), Bill generation, Stock update, ya Daily Sales Report generate kar sakta hoon. Boliye kya karna hai?",
            actionTitle = "Available Commands: 'Report dikhao', 'Ramesh ko 500 udhar likho', 'Chai samosa kharcha 60', 'Rajesh ka udhar cancel karo'"
        )
    }

    private fun generateBusinessReport(query: String, lower: String): AiResponse {
        val customers = repository.customers.value
        val transactions = repository.transactions.value.filter { !it.isCancelled }
        val invoices = repository.invoices.value
        val cashbook = repository.cashbook.value
        val totalReceivable = repository.getTotalReceivable()
        val totalPayable = repository.getTotalPayable()

        val sb = StringBuilder()
        sb.append("📊 GHANSHYAM BILLING BUSINESS REPORT\n")
        sb.append("===============================\n")
        sb.append("Total Customers: ${customers.size}\n")
        sb.append("Total Udhar (Receivable): ₹${"%.2f".format(totalReceivable)}\n")
        sb.append("Total Advance (Payable): ₹${"%.2f".format(totalPayable)}\n\n")

        val totalBilled = invoices.sumOf { it.totalAmount }
        sb.append("🧾 Invoices Issued: ${invoices.size} (Total ₹${"%.2f".format(totalBilled)})\n")

        val cashIn = cashbook.filter { it.type == "IN" }.sumOf { it.amount }
        val cashOut = cashbook.filter { it.type == "OUT" }.sumOf { it.amount }
        sb.append("💰 Cashbook - In: ₹${"%.2f".format(cashIn)} | Out: ₹${"%.2f".format(cashOut)}\n")
        sb.append("💰 Net Cash Balance: ₹${"%.2f".format(cashIn - cashOut)}\n\n")

        sb.append("Top Customers With Outstanding Udhar:\n")
        val withUdhar = customers.map { it to repository.getCustomerBalance(it.id) }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)

        if (withUdhar.isEmpty()) {
            sb.append("• Koi pending udhar nahi hai. Sab hisaab clear hai!\n")
        } else {
            withUdhar.forEach { (c, bal) ->
                sb.append("• ${c.name} (${c.phone}): ₹${"%.2f".format(bal)}\n")
            }
        }

        return AiResponse.Success(
            replyText = "Aapki dukaan ka business statement aur report ready hai. Total Lena Hai: ₹${"%.2f".format(totalReceivable)}. Neeche pura statement dekhein ya share karein.",
            actionTitle = "Complete Business Statement",
            reportContent = sb.toString()
        )
    }

    private fun handleCancelTransaction(query: String, lower: String): AiResponse {
        val transactions = repository.transactions.value
        val customers = repository.customers.value

        // Try to match customer name or amount
        val numbers = extractNumbers(query)
        val targetAmount = numbers.firstOrNull()

        var matchedTx: Transaction? = null
        var matchedCustomerName = ""

        for (c in customers) {
            if (lower.contains(c.name.lowercase())) {
                matchedCustomerName = c.name
                val cTx = transactions.filter { it.customerId == c.id && !it.isCancelled }
                matchedTx = if (targetAmount != null) {
                    cTx.find { Math.abs(it.amount - targetAmount) < 0.01 } ?: cTx.firstOrNull()
                } else {
                    cTx.firstOrNull()
                }
                break
            }
        }

        if (matchedTx == null && targetAmount != null) {
            matchedTx = transactions.find { !it.isCancelled && Math.abs(it.amount - targetAmount) < 0.01 }
            if (matchedTx != null) {
                matchedCustomerName = customers.find { it.id == matchedTx.customerId }?.name ?: "Customer"
            }
        }

        if (matchedTx != null) {
            val txId = matchedTx.id
            val amt = matchedTx.amount
            val name = matchedCustomerName
            return AiResponse.Success(
                replyText = "$name ka ₹$amt ka udhar entry cancel out kar diya gaya hai. Ab balance automatically update ho gaya hai.",
                actionTitle = "Cancel Out: $name - ₹$amt",
                executeAction = {
                    repository.toggleTransactionCancel(txId)
                }
            )
        }

        return AiResponse.Success(
            replyText = "Kripya customer ka naam aur amount batayein jise cancel out karna hai, jaise 'Rajesh Sharma ka 1850 udhar cancel karo'."
        )
    }

    private fun handleTransactionCommand(query: String, lower: String): AiResponse {
        val numbers = extractNumbers(query)
        val amount = numbers.firstOrNull() ?: 500.0

        val isUdhar = lower.contains("udhar") || lower.contains("diya") || lower.contains("gave")
        val type = if (isUdhar) "GAVE" else "GOT"

        // Find customer name from existing or extract
        var customerName = "Rajesh Sharma"
        val existing = repository.customers.value
        for (c in existing) {
            if (lower.contains(c.name.lowercase())) {
                customerName = c.name
                break
            }
        }

        val typeText = if (isUdhar) "Udhar (Aapne Diye)" else "Jama (Aapko Mile)"

        return AiResponse.Success(
            replyText = "$customerName ke khate me ₹$amount $typeText record karne ke liye ready hai.",
            actionTitle = "Record $typeText: ₹$amount for $customerName",
            executeAction = {
                var cust = repository.customers.value.find { it.name.equals(customerName, ignoreCase = true) }
                if (cust == null) {
                    cust = repository.addCustomer(customerName, "+91 98000 00000")
                }
                repository.addTransaction(
                    customerId = cust.id,
                    amount = amount,
                    type = type,
                    note = "Recorded via Voice AI"
                )
            }
        )
    }

    private fun handleAddCustomerCommand(query: String): AiResponse {
        val numbers = query.filter { it.isDigit() }
        val phone = if (numbers.length >= 10) numbers.takeLast(10) else "+91 98765 43210"

        // Clean customer name
        var name = query
            .replace(Regex("(?i)add|customer|jodo|nava|ko|khata|me"), "")
            .replace(numbers, "")
            .trim()

        if (name.isBlank()) name = "New Customer"

        return AiResponse.Success(
            replyText = "Naye customer '$name' (Phone: $phone) ko Khata me jod diya gaya hai.",
            actionTitle = "Add Customer: $name",
            executeAction = {
                repository.addCustomer(name, phone)
            }
        )
    }

    private fun handleCashEntryCommand(query: String, lower: String): AiResponse {
        val numbers = extractNumbers(query)
        val amount = numbers.firstOrNull() ?: 60.0

        val isOut = lower.contains("kharcha") || lower.contains("expense") || lower.contains("out") || lower.contains("chai")
        val type = if (isOut) "OUT" else "IN"
        val category = if (lower.contains("chai")) "Refreshment (Chai/Samosa)" else "General Shop Expense"

        return AiResponse.Success(
            replyText = "Cashbook me ₹$amount ka ${if (isOut) "Kharcha (Cash Out)" else "Cash In"} enter kar diya gaya.",
            actionTitle = "Cash $type: ₹$amount ($category)",
            executeAction = {
                repository.addCashEntry(type, amount, category, "Entered via AI")
            }
        )
    }

    private fun handleInventoryCommand(query: String, lower: String): AiResponse {
        val numbers = extractNumbers(query)
        val qty = numbers.firstOrNull()?.toInt() ?: 10

        return AiResponse.Success(
            replyText = "Stock update: Inventory item add/update karne ke liye tayyar hai (Qty: $qty).",
            actionTitle = "Update Stock ($qty items)",
            executeAction = {
                repository.addInventoryItem(
                    name = "Cotton Fabric Item",
                    itemCode = "AI-${System.currentTimeMillis() % 1000}",
                    salePrice = 450.0,
                    purchasePrice = 300.0,
                    stock = qty,
                    category = "Clothing",
                    gstPercent = 5.0
                )
            }
        )
    }

    private fun handleInvoiceCommand(query: String, lower: String): AiResponse {
        val numbers = extractNumbers(query)
        val total = numbers.firstOrNull() ?: 950.0

        return AiResponse.Success(
            replyText = "Invoice generate ho gaya hai. Bill Amount: ₹$total. Is bill ko aap customer ke saath share kar sakte hain.",
            actionTitle = "Generate Invoice: ₹$total",
            executeAction = {
                val billNum = "INV-${System.currentTimeMillis() % 10000}"
                val invoice = Invoice(
                    billNumber = billNum,
                    customerName = "Walk-in Customer",
                    customerPhone = "+91 98000 11111",
                    items = listOf(InvoiceItem(name = "General Goods", quantity = 1, unitPrice = total, total = total)),
                    subtotal = total,
                    totalAmount = total,
                    paymentMode = "CASH"
                )
                repository.createInvoice(invoice)
            }
        )
    }

    private fun handleProfileCommand(query: String, lower: String): AiResponse {
        val current = repository.storeProfile.value

        var newUpi = current.upiId
        var newAddress = current.address

        if (lower.contains("@")) {
            val words = query.split(" ")
            val upiWord = words.find { it.contains("@") }
            if (upiWord != null) newUpi = upiWord
        }

        if (lower.contains("address") || lower.contains("pata")) {
            newAddress = query.replace(Regex("(?i).*address|pata"), "").trim()
            if (newAddress.isBlank()) newAddress = "Shop #4, Commercial Hub"
        }

        return AiResponse.Success(
            replyText = "Dukaan profile details update ho gayi: UPI ID = $newUpi, Address = $newAddress.",
            actionTitle = "Update Store Profile",
            executeAction = {
                repository.updateStoreProfile(
                    current.storeName,
                    newAddress,
                    current.phone,
                    newUpi,
                    current.gstin,
                    current.ownerName
                )
            }
        )
    }

    private fun handleLanguageCommand(lower: String): AiResponse {
        val newLang = when {
            lower.contains("hindi") -> AppLanguage.HINDI
            lower.contains("gujarati") -> AppLanguage.GUJARATI
            lower.contains("english") -> AppLanguage.ENGLISH
            else -> AppLanguage.HINGLISH
        }

        return AiResponse.Success(
            replyText = "Bhasha badal di gayi hai: ${newLang.displayName}.",
            actionTitle = "Switch Language to ${newLang.displayName}",
            executeAction = {
                repository.setLanguage(newLang)
            }
        )
    }

    private fun extractNumbers(text: String): List<Double> {
        val regex = Regex("(\\d+(\\.\\d+)?)")
        return regex.findAll(text).mapNotNull { it.value.toDoubleOrNull() }.toList()
    }

    private fun callGeminiFallback(query: String): AiResponse {
        // Fallback calling Gemini API via okhttp with server-side key
        val jsonPayload = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are the AI assistant for 'Ghanshyam Billing', a shopkeeper app. Answer in Hinglish briefly and helpfully for Indian merchants. User request: $query")
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val respStr = response.body?.string() ?: ""
            val obj = JSONObject(respStr)
            val text = obj.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            return AiResponse.Success(replyText = text)
        }

        return AiResponse.Success("Dukaan management ke liye main taiyyar hoon. Boliye!")
    }
}
