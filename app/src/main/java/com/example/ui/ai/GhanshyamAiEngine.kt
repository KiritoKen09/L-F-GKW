package com.example.ui.ai

import android.content.Context
import com.example.data.AppRepository
import com.example.model.*
import com.example.ui.util.PhoneUtil
import com.example.util.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

sealed class AiResponse {
    data class Success(
        val replyText: String,
        val actionTitle: String? = null,
        val reportContent: String? = null,
        val executeAction: (() -> Unit)? = null,
        val actionType: String? = null,
        val actionData: String? = null
    ) : AiResponse()

    data class OutOfScope(val replyText: String) : AiResponse()
}

class GhanshyamAiEngine(
    private val repository: AppRepository,
    private val context: Context? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    suspend fun processQuery(userInput: String): AiResponse = withContext(Dispatchers.IO) {
        val query = userInput.trim()
        if (query.isBlank()) {
            return@withContext AiResponse.Success("Aap kuch boliye ya type kijiye, main dukaan ke hisaab-kitaab me madad karunga.")
        }

        // Save USER message to database
        try {
            repository.saveChatMessage(sender = "USER", text = query)
        } catch (e: Exception) {
            android.util.Log.w("GhanshyamAiEngine", "Failed to persist user message: ${e.message}")
        }

        val effectiveApiKey = getEffectiveApiKey()

        // If Gemini API key is available, run Gemini Function Calling
        if (effectiveApiKey.isNotBlank() && effectiveApiKey != "YOUR_GEMINI_API_KEY") {
            try {
                val geminiResponse = runGeminiFunctionCalling(query, effectiveApiKey)
                if (geminiResponse != null) {
                    persistAiResponse(geminiResponse)
                    return@withContext geminiResponse
                }
            } catch (e: Exception) {
                android.util.Log.e("GhanshyamAiEngine", "Gemini function calling failed, falling back to keyword engine: ${e.message}", e)
            }
        }

        // Offline / Keyword fallback engine
        val fallbackResponse = processQueryOffline(query)
        persistAiResponse(fallbackResponse)
        fallbackResponse
    }

    private fun getEffectiveApiKey(): String {
        val keyFromPrefs = if (context != null) SecurityManager.getGeminiApiKey(context) else ""
        if (keyFromPrefs.isNotBlank()) return keyFromPrefs
        return try {
            val buildConfigClass = Class.forName("com.example.BuildConfig")
            val field = buildConfigClass.getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun persistAiResponse(response: AiResponse) {
        try {
            when (response) {
                is AiResponse.Success -> {
                    repository.saveChatMessage(
                        sender = "AI",
                        text = response.replyText,
                        actionType = response.actionType,
                        actionData = response.actionData,
                        actionTitle = response.actionTitle,
                        reportContent = response.reportContent
                    )
                }
                is AiResponse.OutOfScope -> {
                    repository.saveChatMessage(
                        sender = "AI",
                        text = response.replyText
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("GhanshyamAiEngine", "Failed to persist AI response: ${e.message}")
        }
    }

    // ==========================================
    // GEMINI FUNCTION CALLING (MAX 5 LOOPS)
    // ==========================================

    private suspend fun runGeminiFunctionCalling(query: String, apiKey: String): AiResponse? {
        val toolsDeclarations = buildToolsDeclaration()
        val contentsArray = JSONArray()

        // Initial user turn
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", query) })
            })
        })

        val systemInstruction = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().apply {
                    put("text", """
                        You are Ghanshyam AI, the financial & billing intelligence assistant for 'Ghanshyam Kids Wear', a wholesale kids wear shop in Surat, Gujarat.
                        CRITICAL FINANCIAL ACCURACY RULES:
                        1. Answers about money, balances, cash in hand, inventory stock, debtors, or sales MUST come ONLY from calling the read-only functions (get_summary, find_party, get_party_ledger, get_overdue, get_top_debtors, get_cashbook, get_stock, get_sales). NEVER guess, invent, or estimate any financial number or customer data.
                        2. Write actions (adding a party, adding an entry, marking payment received, creating a bill) MUST call proposal functions (propose_add_party, propose_add_entry, propose_mark_received, propose_create_bill). These functions return proposals so the user can review and tap 'Apply'. NEVER pretend an entry was saved without user confirmation.
                        3. Reply in friendly, professional Hinglish (default) or Hindi/Gujarati/English matching user input.
                    """.trimIndent())
                })
            })
        }

        var loopCount = 0
        val maxLoops = 5

        while (loopCount < maxLoops) {
            loopCount++

            val requestBodyJson = JSONObject().apply {
                put("contents", contentsArray)
                put("tools", toolsDeclarations)
                put("system_instruction", systemInstruction)
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                android.util.Log.w("GhanshyamAiEngine", "Gemini API HTTP ${response.code}: $errorBody")
                return null
            }

            val respStr = response.body?.string() ?: return null
            val respJson = JSONObject(respStr)
            val candidates = respJson.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null

            var functionCallPart: JSONObject? = null
            var textPart: String? = null

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("functionCall")) {
                    functionCallPart = part.getJSONObject("functionCall")
                    break
                }
                if (part.has("text")) {
                    textPart = part.getString("text")
                }
            }

            // If model called a function
            if (functionCallPart != null) {
                val functionName = functionCallPart.getString("name")
                val args = functionCallPart.optJSONObject("args") ?: JSONObject()

                // Check for write action proposals
                if (functionName.startsWith("propose_")) {
                    return handleProposalFunction(functionName, args)
                }

                // Execute read-only function
                val functionResult = executeReadOnlyFunction(functionName, args)

                // Append model's functionCall turn
                contentsArray.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("functionCall", functionCallPart) })
                    })
                })

                // Append functionResponse turn
                contentsArray.put(JSONObject().apply {
                    put("role", "function")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionResponse", JSONObject().apply {
                                put("name", functionName)
                                put("response", functionResult)
                            })
                        })
                    })
                })

                // Continue loop with function response
                continue
            }

            // If model returned text response
            if (!textPart.isNullOrBlank()) {
                return AiResponse.Success(replyText = textPart)
            }
        }

        return null
    }

    private fun handleProposalFunction(name: String, args: JSONObject): AiResponse {
        when (name) {
            "propose_add_party" -> {
                val partyName = args.optString("name", "").trim()
                val phone = args.optString("phone", "").trim()
                val type = args.optString("type", "CUSTOMER").uppercase()
                val openingBalance = args.optDouble("openingBalance", 0.0)

                val actionTitle = "Add $type: $partyName"
                val reply = "Main $partyName ($type) ko khata me add karne ka proposal taiyyar kar chuka hoon. Phone: ${if (phone.isNotBlank()) phone else "None"}, Opening Balance: ₹${"%.2f".format(openingBalance)}. Kripya 'Apply' dabakar confirm karein."

                return AiResponse.Success(
                    replyText = reply,
                    actionTitle = actionTitle,
                    actionType = "ADD_PARTY",
                    actionData = args.toString(),
                    executeAction = {
                        val party = Customer(
                            name = partyName,
                            phone = PhoneUtil.normalize(phone),
                            type = type
                        )
                        repository.addCustomer(party)
                        if (Math.abs(openingBalance) > 0.009) {
                            repository.createTransaction(
                                customerId = party.id,
                                amount = Math.abs(openingBalance),
                                type = if (openingBalance > 0) "GAVE" else "GOT",
                                note = "Opening Balance"
                            )
                        }
                    }
                )
            }

            "propose_add_entry" -> {
                val partyName = args.optString("partyName", "").trim()
                val type = args.optString("type", "GAVE").uppercase()
                val amount = args.optDouble("amount", 0.0)
                val notes = args.optString("notes", "Via Ghanshyam AI")

                val customer = repository.customers.value.find { it.name.contains(partyName, ignoreCase = true) }
                val targetName = customer?.name ?: partyName
                val typeDesc = if (type == "GAVE") "Udhar (Gave)" else "Jama (Got)"
                val actionTitle = "Record ₹${"%.2f".format(amount)} $typeDesc for $targetName"
                val reply = "$targetName ke khata me ₹${"%.2f".format(amount)} $typeDesc likhne ka proposal taiyyar hai. Kripya 'Apply' dabayein."

                return AiResponse.Success(
                    replyText = reply,
                    actionTitle = actionTitle,
                    actionType = "ADD_TRANSACTION",
                    actionData = args.toString(),
                    executeAction = {
                        val custId = customer?.id ?: run {
                            val newC = Customer(name = targetName, phone = "", type = "CUSTOMER")
                            repository.addCustomer(newC)
                            newC.id
                        }
                        repository.createTransaction(
                            customerId = custId,
                            amount = amount,
                            type = type,
                            note = notes
                        )
                    }
                )
            }

            "propose_mark_received" -> {
                val partyName = args.optString("partyName", "").trim()
                val amount = args.optDouble("amount", 0.0)
                val mode = args.optString("paymentMode", "CASH").uppercase()

                val customer = repository.customers.value.find { it.name.contains(partyName, ignoreCase = true) }
                val targetName = customer?.name ?: partyName
                val actionTitle = "Record Payment: ₹${"%.2f".format(amount)} from $targetName ($mode)"
                val reply = "$targetName se ₹${"%.2f".format(amount)} payment ($mode) prapt hone ki entry confirm karne ke liye 'Apply' dabayein."

                return AiResponse.Success(
                    replyText = reply,
                    actionTitle = actionTitle,
                    actionType = "MARK_RECEIVED",
                    actionData = args.toString(),
                    executeAction = {
                        val custId = customer?.id ?: run {
                            val newC = Customer(name = targetName, phone = "", type = "CUSTOMER")
                            repository.addCustomer(newC)
                            newC.id
                        }
                        repository.createTransaction(
                            customerId = custId,
                            amount = amount,
                            type = "GOT",
                            note = "Payment Received via $mode",
                            paymentMode = mode
                        )
                    }
                )
            }

            "propose_create_bill" -> {
                val custName = args.optString("customerName", "Walk-in Customer").trim()
                val custPhone = args.optString("customerPhone", "")
                val itemName = args.optString("itemName", "Kids Wear Item")
                val size = args.optString("size", "Free")
                val qty = args.optInt("quantity", 1)
                val price = args.optDouble("price", 0.0)
                val gst = args.optDouble("gstPercent", 5.0)
                val payMode = args.optString("paymentMode", "CASH").uppercase()

                val lineTotal = qty * price * (1.0 + gst / 100.0)
                val actionTitle = "Create Bill: #Draft for $custName (₹${"%.2f".format(lineTotal)})"
                val reply = "$custName ke liye bill ($qty pcs $itemName [Size: $size] @ ₹$price, GST $gst%, Total: ₹${"%.2f".format(lineTotal)}) banane ka proposal ready hai. 'Apply' dabayein."

                return AiResponse.Success(
                    replyText = reply,
                    actionTitle = actionTitle,
                    actionType = "CREATE_BILL",
                    actionData = args.toString(),
                    executeAction = {
                        val matchedItem = repository.inventory.value.find { it.name.contains(itemName, ignoreCase = true) }
                        val invItem = InvoiceItem(
                            itemId = matchedItem?.id,
                            name = itemName,
                            size = size,
                            hsnCode = matchedItem?.hsnCode ?: "",
                            quantity = qty,
                            unitPrice = price,
                            gstPercent = gst
                        )
                        val inv = Invoice(
                            customerName = custName,
                            customerPhone = custPhone,
                            items = listOf(invItem),
                            paymentMode = payMode
                        )
                        repository.createInvoice(inv)
                    }
                )
            }
        }

        return AiResponse.Success("Action proposal processed.")
    }

    // ==========================================
    // READ-ONLY FUNCTIONS EXECUTED LOCALLY
    // ==========================================

    private fun executeReadOnlyFunction(name: String, args: JSONObject): JSONObject {
        val result = JSONObject()
        val customers = repository.customers.value
        val transactions = repository.transactions.value
        val cashbook = repository.cashbook.value
        val inventory = repository.inventory.value
        val invoices = repository.invoices.value

        when (name) {
            "get_summary" -> {
                val totalReceivable = repository.getTotalReceivable()
                val totalPayable = repository.getTotalPayable()
                val cashInHand = repository.getCashInHand()
                val activeInvoices = invoices.filter { !it.isCancelled }
                val totalSales = activeInvoices.sumOf { it.totalAmount }

                result.put("totalReceivable_Udhar", totalReceivable)
                result.put("totalPayable_Advance", totalPayable)
                result.put("cashInHand", cashInHand)
                result.put("totalCustomers", customers.size)
                result.put("totalInvoicesCount", activeInvoices.size)
                result.put("totalSalesRevenue", totalSales)
                result.put("lowStockCount", inventory.count { it.stock < it.lowStockThreshold })
            }

            "find_party" -> {
                val q = args.optString("query", "").trim().lowercase()
                val matched = customers.filter {
                    it.name.lowercase().contains(q) || it.phone.contains(q)
                }
                val arr = JSONArray()
                matched.forEach { c ->
                    val o = JSONObject()
                    o.put("id", c.id)
                    o.put("name", c.name)
                    o.put("phone", c.phone)
                    o.put("type", c.type)
                    val bal = repository.getCustomerBalance(c.id)
                    o.put("currentBalance", bal)
                    o.put("status", if (bal > 0) "OWES_SHOP (Udhar)" else if (bal < 0) "SHOP_OWES (Advance)" else "SETTLED")
                    arr.put(o)
                }
                result.put("parties", arr)
                result.put("count", matched.size)
            }

            "get_party_ledger" -> {
                val partyId = args.optString("partyId", "")
                val party = customers.find { it.id == partyId }
                if (party != null) {
                    val txs = transactions.filter { it.customerId == partyId }
                    val arr = JSONArray()
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    txs.forEach { t ->
                        val o = JSONObject()
                        o.put("date", sdf.format(Date(t.date)))
                        o.put("type", t.type)
                        o.put("amount", t.amount)
                        o.put("note", t.note)
                        o.put("paymentMode", t.paymentMode)
                        o.put("isCompleted", t.isCompleted)
                        o.put("billNumber", t.billNumber)
                        arr.put(o)
                    }
                    result.put("partyName", party.name)
                    result.put("partyPhone", party.phone)
                    result.put("currentBalance", repository.getCustomerBalance(partyId))
                    result.put("transactions", arr)
                } else {
                    result.put("error", "Party not found with id: $partyId")
                }
            }

            "get_overdue" -> {
                val days = args.optInt("days", 30)
                val cutoff = System.currentTimeMillis() - (days * 86400000L)
                val overdueParties = JSONArray()

                customers.forEach { c ->
                    val bal = repository.getCustomerBalance(c.id)
                    if (bal > 0) {
                        val custTxs = transactions.filter { it.customerId == c.id }
                        val lastTxDate = custTxs.maxOfOrNull { it.date } ?: 0L
                        if (lastTxDate < cutoff) {
                            val daysInactive = if (lastTxDate > 0) ((System.currentTimeMillis() - lastTxDate) / 86400000L).toInt() else 999
                            val o = JSONObject()
                            o.put("name", c.name)
                            o.put("phone", c.phone)
                            o.put("balance", bal)
                            o.put("daysInactive", daysInactive)
                            overdueParties.put(o)
                        }
                    }
                }
                result.put("overdueParties", overdueParties)
                result.put("count", overdueParties.length())
            }

            "get_top_debtors" -> {
                val n = args.optInt("n", 5)
                val debtors = customers.map { c ->
                    c to repository.getCustomerBalance(c.id)
                }.filter { it.second > 0 }
                 .sortedByDescending { it.second }
                 .take(n)

                val arr = JSONArray()
                debtors.forEach { (c, bal) ->
                    val o = JSONObject()
                    o.put("name", c.name)
                    o.put("phone", c.phone)
                    o.put("balance", bal)
                    o.put("type", c.type)
                    arr.put(o)
                }
                result.put("topDebtors", arr)
                result.put("count", debtors.size)
            }

            "get_cashbook" -> {
                val totalIn = cashbook.filter { it.type.equals("IN", ignoreCase = true) }.sumOf { it.amount }
                val totalOut = cashbook.filter { it.type.equals("OUT", ignoreCase = true) }.sumOf { it.amount }
                result.put("totalCashIn", totalIn)
                result.put("totalCashOut", totalOut)
                result.put("netBalance", totalIn - totalOut)

                val entriesArr = JSONArray()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                cashbook.take(15).forEach { c ->
                    val o = JSONObject()
                    o.put("date", sdf.format(Date(c.date)))
                    o.put("type", c.type)
                    o.put("amount", c.amount)
                    o.put("category", c.category)
                    o.put("remark", c.remark)
                    entriesArr.put(o)
                }
                result.put("recentEntries", entriesArr)
            }

            "get_stock" -> {
                val q = args.optString("query", "").trim().lowercase()
                val matched = if (q.isBlank()) inventory else inventory.filter {
                    it.name.lowercase().contains(q) || it.itemCode.lowercase().contains(q) || it.hsnCode.contains(q)
                }
                val arr = JSONArray()
                matched.forEach { item ->
                    val o = JSONObject()
                    o.put("id", item.id)
                    o.put("name", item.name)
                    o.put("itemCode", item.itemCode)
                    o.put("hsnCode", item.hsnCode)
                    o.put("salePrice", item.salePrice)
                    o.put("totalStock", item.stock)
                    o.put("lowStockThreshold", item.lowStockThreshold)
                    o.put("isLowStock", item.stock < item.lowStockThreshold)

                    val sizeStockObj = JSONObject()
                    item.sizeStock.forEach { (s, qty) ->
                        sizeStockObj.put(s, qty)
                    }
                    o.put("sizeStock", sizeStockObj)
                    arr.put(o)
                }
                result.put("items", arr)
                result.put("count", matched.size)
            }

            "get_sales" -> {
                val active = invoices.filter { !it.isCancelled }
                val totalRev = active.sumOf { it.totalAmount }
                result.put("totalInvoices", active.size)
                result.put("totalRevenue", totalRev)

                val arr = JSONArray()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                active.take(10).forEach { inv ->
                    val o = JSONObject()
                    o.put("billNumber", inv.billNumber)
                    o.put("date", sdf.format(Date(inv.date)))
                    o.put("customerName", inv.customerName)
                    o.put("totalAmount", inv.totalAmount)
                    o.put("paymentMode", inv.paymentMode)
                    o.put("itemCount", inv.items.size)
                    arr.put(o)
                }
                result.put("recentInvoices", arr)
            }

            else -> {
                result.put("error", "Unknown function: $name")
            }
        }

        return result
    }

    private fun buildToolsDeclaration(): JSONArray {
        val funcs = JSONArray()

        // 1. get_summary
        funcs.put(JSONObject().apply {
            put("name", "get_summary")
            put("description", "Get high-level store financials (total Udhar receivable, total payables, cash in hand, active customer count, total sales revenue)")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("from", JSONObject().apply { put("type", "STRING"); put("description", "Start date yyyy-MM-dd") })
                    put("to", JSONObject().apply { put("type", "STRING"); put("description", "End date yyyy-MM-dd") })
                })
            })
        })

        // 2. find_party
        funcs.put(JSONObject().apply {
            put("name", "find_party")
            put("description", "Find a customer or supplier party by name or phone query to check their current balance and status")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply { put("type", "STRING"); put("description", "Party name or phone number") })
                })
                put("required", JSONArray().apply { put("query") })
            })
        })

        // 3. get_party_ledger
        funcs.put(JSONObject().apply {
            put("name", "get_party_ledger")
            put("description", "Get full transaction ledger history for a specific party by partyId")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("partyId", JSONObject().apply { put("type", "STRING"); put("description", "Party UUID") })
                })
                put("required", JSONArray().apply { put("partyId") })
            })
        })

        // 4. get_overdue
        funcs.put(JSONObject().apply {
            put("name", "get_overdue")
            put("description", "Get list of parties with outstanding udhar balance who have had no transaction for specified days")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("days", JSONObject().apply { put("type", "INTEGER"); put("description", "Days inactive, default 30") })
                })
            })
        })

        // 5. get_top_debtors
        funcs.put(JSONObject().apply {
            put("name", "get_top_debtors")
            put("description", "Get top N parties who owe the shop money (largest positive udhar balance)")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("n", JSONObject().apply { put("type", "INTEGER"); put("description", "Number of parties to return, default 5") })
                })
            })
        })

        // 6. get_cashbook
        funcs.put(JSONObject().apply {
            put("name", "get_cashbook")
            put("description", "Get cashbook income/expense transactions and net cash balance")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("from", JSONObject().apply { put("type", "STRING"); put("description", "Start date yyyy-MM-dd") })
                    put("to", JSONObject().apply { put("type", "STRING"); put("description", "End date yyyy-MM-dd") })
                })
            })
        })

        // 7. get_stock
        funcs.put(JSONObject().apply {
            put("name", "get_stock")
            put("description", "Get inventory items, size stocks, and low stock warnings")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply { put("type", "STRING"); put("description", "Optional item name, code, or HSN query") })
                })
            })
        })

        // 8. get_sales
        funcs.put(JSONObject().apply {
            put("name", "get_sales")
            put("description", "Get sales invoices and total revenue")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("from", JSONObject().apply { put("type", "STRING"); put("description", "Start date yyyy-MM-dd") })
                    put("to", JSONObject().apply { put("type", "STRING"); put("description", "End date yyyy-MM-dd") })
                })
            })
        })

        // Write action proposals
        funcs.put(JSONObject().apply {
            put("name", "propose_add_party")
            put("description", "Propose adding a new party (customer/supplier). Returns proposal card for user confirmation.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply { put("type", "STRING"); put("description", "Party name") })
                    put("phone", JSONObject().apply { put("type", "STRING"); put("description", "Phone number") })
                    put("type", JSONObject().apply { put("type", "STRING"); put("enum", JSONArray(listOf("CUSTOMER", "SUPPLIER"))) })
                    put("openingBalance", JSONObject().apply { put("type", "NUMBER"); put("description", "Opening balance") })
                })
                put("required", JSONArray().apply { put("name") })
            })
        })

        funcs.put(JSONObject().apply {
            put("name", "propose_add_entry")
            put("description", "Propose recording an Udhar (GAVE) or Jama (GOT) entry for a customer. Shows confirmation card.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("partyName", JSONObject().apply { put("type", "STRING") })
                    put("type", JSONObject().apply { put("type", "STRING"); put("enum", JSONArray(listOf("GAVE", "GOT"))) })
                    put("amount", JSONObject().apply { put("type", "NUMBER") })
                    put("notes", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("partyName"); put("type"); put("amount") })
            })
        })

        funcs.put(JSONObject().apply {
            put("name", "propose_mark_received")
            put("description", "Propose recording payment received from a party. Shows confirmation card.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("partyName", JSONObject().apply { put("type", "STRING") })
                    put("amount", JSONObject().apply { put("type", "NUMBER") })
                    put("paymentMode", JSONObject().apply { put("type", "STRING"); put("enum", JSONArray(listOf("CASH", "UPI", "BANK"))) })
                })
                put("required", JSONArray().apply { put("partyName"); put("amount") })
            })
        })

        funcs.put(JSONObject().apply {
            put("name", "propose_create_bill")
            put("description", "Propose generating a wholesale invoice. Shows confirmation card.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("customerName", JSONObject().apply { put("type", "STRING") })
                    put("customerPhone", JSONObject().apply { put("type", "STRING") })
                    put("itemName", JSONObject().apply { put("type", "STRING") })
                    put("size", JSONObject().apply { put("type", "STRING") })
                    put("quantity", JSONObject().apply { put("type", "INTEGER") })
                    put("price", JSONObject().apply { put("type", "NUMBER") })
                    put("gstPercent", JSONObject().apply { put("type", "NUMBER") })
                    put("paymentMode", JSONObject().apply { put("type", "STRING"); put("enum", JSONArray(listOf("CASH", "UPI", "CREDIT"))) })
                })
                put("required", JSONArray().apply { put("customerName"); put("itemName"); put("quantity"); put("price") })
            })
        })

        val tools = JSONArray()
        tools.put(JSONObject().apply {
            put("function_declarations", funcs)
        })
        return tools
    }

    // ==========================================
    // OFFLINE DETERMINISTIC KEYWORD ENGINE
    // ==========================================

    fun processQueryOffline(query: String): AiResponse {
        val lower = query.lowercase()

        // 1. Out of Scope Check
        val outOfScopeKeywords = listOf(
            "weather", "mausam", "song", "gaana", "movie", "film", "cricket", "score",
            "joke", "chutkula", "poem", "kavita", "president", "pm", "modi", "politics",
            "recipe", "khana kaise banaye", "translate english to french"
        )
        if (outOfScopeKeywords.any { lower.contains(it) }) {
            return AiResponse.OutOfScope(
                "Maaf kijiye, ye Ghanshyam Billing ke scope ke bahar hai. Main sirf aapki dukaan ka khata, billing, inventory, cashbook aur financial reports manage kar sakta hoon."
            )
        }

        // 2. Generate Reports
        if (lower.contains("report") || lower.contains("statement") || lower.contains("hisaab") || lower.contains("hisab")) {
            return generateBusinessReport()
        }

        // 3. Top Debtors ("who owes me more than", "who owes", "sabse zyada udhar")
        if (lower.contains("owe") || lower.contains("debtor") || lower.contains("kispe baaki") || lower.contains("kin par baaki")) {
            val numbers = extractNumbers(query)
            val threshold = numbers.firstOrNull() ?: 0.0
            val customers = repository.customers.value
            val debtors = customers.map { it to repository.getCustomerBalance(it.id) }
                .filter { it.second > threshold }
                .sortedByDescending { it.second }

            if (debtors.isEmpty()) {
                return AiResponse.Success(
                    replyText = if (threshold > 0) "Aise koi customer nahi hain jinpar ₹${"%.0f".format(threshold)} se zyada udhar baaki ho." else "Aapka kisi bhi party par udhar baaki nahi hai!"
                )
            }

            val sb = StringBuilder("📋 Udhar List (> ₹${"%.0f".format(threshold)}):\n")
            debtors.take(10).forEachIndexed { i, (c, b) ->
                sb.append("${i + 1}. ${c.name}: ₹${"%.2f".format(b)} (${c.phone})\n")
            }
            return AiResponse.Success(replyText = sb.toString())
        }

        // 4. Sales Summary ("total cash sales", "sales this week", "aaj ki sales")
        if (lower.contains("sale") || lower.contains("bikri") || lower.contains("biki")) {
            val invoices = repository.invoices.value.filter { !it.isCancelled }
            val isCash = lower.contains("cash")
            val isCredit = lower.contains("credit") || lower.contains("udhar")
            val filtered = when {
                isCash -> invoices.filter { it.paymentMode.equals("CASH", ignoreCase = true) }
                isCredit -> invoices.filter { it.paymentMode.equals("CREDIT", ignoreCase = true) }
                else -> invoices
            }
            val total = filtered.sumOf { it.totalAmount }
            val modeStr = if (isCash) "Cash " else if (isCredit) "Credit " else ""
            return AiResponse.Success(
                replyText = "Total ${modeStr}Sales: ₹${"%.2f".format(total)} (${filtered.size} bills)."
            )
        }

        // 5. Cashbook / Cash in hand
        if (lower.contains("cash in hand") || lower.contains("galle me") || lower.contains("counter cash")) {
            val cash = repository.getCashInHand()
            return AiResponse.Success(
                replyText = "Aapka current Cash in Hand (Galla Balance): ₹${"%.2f".format(cash)}."
            )
        }

        // 6. Record Udhar (Gave) or Jama (Got)
        if (lower.contains("udhar") || lower.contains("diya") || lower.contains("gave") || lower.contains("mila") || lower.contains("jama") || lower.contains("got")) {
            return handleTransactionCommand(query, lower)
        }

        // 7. Add Customer
        if (lower.contains("customer") && (lower.contains("add") || lower.contains("nava") || lower.contains("jodo"))) {
            return handleAddCustomerCommand(query)
        }

        // 8. Cashbook Entry
        if (lower.contains("kharcha") || lower.contains("expense") || lower.contains("cash out") || lower.contains("cash in") || lower.contains("chai")) {
            return handleCashEntryCommand(query, lower)
        }

        // 9. Stock query
        if (lower.contains("stock") || lower.contains("inventory") || lower.contains("item")) {
            return handleInventoryCommand(query, lower)
        }

        // 10. Default friendly help
        return AiResponse.Success(
            replyText = "Main aapka Voice AI Assistant hoon. Aap pooch sakte hain: 'Who owes me more than 5000?', 'Total cash sales', 'Daily report', ya 'Ramesh ko 500 udhar likho'."
        )
    }

    private fun generateBusinessReport(): AiResponse {
        val customers = repository.customers.value
        val totalReceivable = repository.getTotalReceivable()
        val totalPayable = repository.getTotalPayable()
        val cashInHand = repository.getCashInHand()
        val invoices = repository.invoices.value.filter { !it.isCancelled }

        val sb = StringBuilder()
        sb.append("📊 GHANSHYAM BILLING BUSINESS REPORT - GHANSHYAM KIDS WEAR\n")
        sb.append("──────────────────────────────\n")
        sb.append("Total Parties: ${customers.size}\n")
        sb.append("Total Udhar (Receivable): ₹${"%.2f".format(totalReceivable)}\n")
        sb.append("Total Advance (Payable): ₹${"%.2f".format(totalPayable)}\n")
        sb.append("Cash in Hand: ₹${"%.2f".format(cashInHand)}\n")
        sb.append("Total Sales: ₹${"%.2f".format(invoices.sumOf { it.totalAmount })} (${invoices.size} bills)\n")
        sb.append("──────────────────────────────\n")

        return AiResponse.Success(
            replyText = "Aapki dukaan ka business hisaab report taiyyar hai.",
            reportContent = sb.toString()
        )
    }

    private fun handleTransactionCommand(query: String, lower: String): AiResponse {
        val numbers = extractNumbers(query)
        val amount = numbers.firstOrNull() ?: 0.0
        val isGave = lower.contains("udhar") || lower.contains("diya") || lower.contains("gave")
        val type = if (isGave) "GAVE" else "GOT"
        val typeDesc = if (isGave) "Udhar (Gave)" else "Jama (Got)"

        val customers = repository.customers.value
        val matchedCustomer = customers.find { lower.contains(it.name.lowercase()) }
        val partyName = matchedCustomer?.name ?: "Customer"

        val actionTitle = "Record ₹${"%.2f".format(amount)} $typeDesc for $partyName"
        val replyText = "$partyName ke khata me ₹${"%.2f".format(amount)} $typeDesc likhne ka proposal taiyyar hai. Kripya 'Apply' dabayein."

        return AiResponse.Success(
            replyText = replyText,
            actionTitle = actionTitle,
            actionType = "ADD_TRANSACTION",
            executeAction = {
                val custId = matchedCustomer?.id ?: run {
                    val newC = Customer(name = partyName, phone = "", type = "CUSTOMER")
                    repository.addCustomer(newC)
                    newC.id
                }
                repository.createTransaction(
                    customerId = custId,
                    amount = amount,
                    type = type,
                    note = "Via Ghanshyam AI"
                )
            }
        )
    }

    private fun handleAddCustomerCommand(query: String): AiResponse {
        val words = query.split(" ").filter { it.isNotBlank() }
        val nameWords = words.filter {
            !it.equals("add", true) && !it.equals("customer", true) &&
            !it.equals("nava", true) && !it.equals("jodo", true) &&
            !it.equals("karo", true) && !it.equals("ko", true) &&
            !it.all { ch -> ch.isDigit() }
        }
        val name = if (nameWords.isNotEmpty()) nameWords.joinToString(" ") else "Nava Customer"
        val phoneDigits = query.filter { it.isDigit() }
        val phone = if (phoneDigits.length >= 10) phoneDigits.takeLast(10) else ""

        val actionTitle = "Add Customer: $name"
        val replyText = "$name ko khata me jodne ka proposal ready hai. 'Apply' dabakar save karein."

        return AiResponse.Success(
            replyText = replyText,
            actionTitle = actionTitle,
            actionType = "ADD_PARTY",
            executeAction = {
                repository.addCustomer(
                    Customer(name = name, phone = PhoneUtil.normalize(phone), type = "CUSTOMER")
                )
            }
        )
    }

    private fun handleCashEntryCommand(query: String, lower: String): AiResponse {
        val numbers = extractNumbers(query)
        val amount = numbers.firstOrNull() ?: 0.0
        val isOut = lower.contains("kharcha") || lower.contains("cash out") || lower.contains("chai")
        val type = if (isOut) "OUT" else "IN"
        val category = if (lower.contains("chai")) "Tea/Refreshment" else if (isOut) "Shop Expense" else "Cash In"

        val actionTitle = "Cashbook: ₹${"%.2f".format(amount)} ($type)"
        val replyText = "Cashbook me ₹${"%.2f".format(amount)} ($type - $category) entry ka proposal ready hai. 'Apply' dabayein."

        return AiResponse.Success(
            replyText = replyText,
            actionTitle = actionTitle,
            actionType = "CASH_ENTRY",
            executeAction = {
                repository.addCashEntry(
                    type = type,
                    amount = amount,
                    category = category,
                    remark = "Via Ghanshyam AI"
                )
            }
        )
    }

    private fun handleInventoryCommand(query: String, lower: String): AiResponse {
        val inventory = repository.inventory.value
        val lowStock = inventory.filter { it.stock < it.lowStockThreshold }
        return if (lowStock.isNotEmpty()) {
            AiResponse.Success(
                replyText = "Dukaan me ${lowStock.size} items low stock par hain:\n" +
                        lowStock.take(5).joinToString("\n") { "• ${it.name}: ${it.stock} pcs left" }
            )
        } else {
            AiResponse.Success(
                replyText = "Total Inventory Items: ${inventory.size}. Sabhi items ka stock threshold se upar hai."
            )
        }
    }

    private fun extractNumbers(text: String): List<Double> {
        val regex = Regex("(\\d+(\\.\\d+)?)")
        return regex.findAll(text).mapNotNull { it.value.toDoubleOrNull() }.toList()
    }
}
