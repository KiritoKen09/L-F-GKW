package com.example.ui.util

import com.example.model.AppLanguage

object LanguageStrings {

    fun get(key: String, language: AppLanguage): String {
        val entry = map[key] ?: return key
        return when (language) {
            AppLanguage.HINGLISH -> entry.hinglish
            AppLanguage.HINDI -> entry.hindi
            AppLanguage.GUJARATI -> entry.gujarati
            AppLanguage.ENGLISH -> entry.english
        }
    }

    private data class Trans(
        val hinglish: String,
        val hindi: String,
        val gujarati: String,
        val english: String
    )

    private val map = mapOf(
        "app_title" to Trans("Ghanshyam Billing", "घनश्याम बिलिंग", "ઘનશ્યામ બિલિંગ", "Ghanshyam Billing"),
        "tab_khata" to Trans("Khata", "खाता", "ખાતાવહી", "Khata"),
        "tab_billing" to Trans("Billing", "बिलिंग", "બિલિંગ", "Billing"),
        "tab_stock" to Trans("Stock", "स्टॉक", "સ્ટોક", "Stock"),
        "tab_cashbook" to Trans("Cashbook", "कैशबुक", "રોકડમેળ", "Cashbook"),
        "tab_profile" to Trans("Shop", "दुकान", "દુકાન", "Shop"),
        "you_gave" to Trans("Aapne Diye (Udhar)", "आपने दिए (उधार)", "તમે આપ્યા (ઉધાર)", "You Gave (Credit)"),
        "you_got" to Trans("Aapko Mile (Jama)", "आपको मिले (जमा)", "તમને મળ્યા (જમા)", "You Got (Debit)"),
        "total_receivable" to Trans("Total Lena Hai", "कुल लेना है", "કુલ લેવાના છે", "Total Receivable"),
        "total_payable" to Trans("Total Dena Hai", "कुल देना है", "કુલ આપવાના છે", "Total Payable"),
        "add_customer" to Trans("Customer Add Karo", "ग्राहक जोड़ें", "ગ્રાહક ઉમેરો", "Add Customer"),
        "search_hint" to Trans("Search customer ya phone...", "ग्राहक या फोन खोजें...", "ગ્રાહક કે ફોન શોધો...", "Search customer or phone..."),
        "filter_all" to Trans("Sabhi", "सभी", "બધા", "All"),
        "filter_receive" to Trans("Lena Hai", "लेना है", "લેવાના છે", "You'll Get"),
        "filter_give" to Trans("Dena Hai", "देना है", "આપવાના છે", "You'll Give"),
        "net_balance" to Trans("Net Balance", "शुद्ध शेष", "ચોખ્ખી બાકી", "Net Balance"),
        "cancelled_out" to Trans("Cancelled Out (Kharij)", "रद्द किया गया", "રદ કરેલ", "Cancelled Out"),
        "active_entry" to Trans("Valid Entry", "सक्रिय प्रविष्टि", "માન્ય એન્ટ્રી", "Active Entry"),
        "ai_assistant" to Trans("AI Assistant", "एआई सहायक", "AI સહાયક", "AI Assistant"),
        "listening" to Trans("Sun raha hoon...", "सुन रहा हूँ...", "સાંભળી રહ્યો છું...", "Listening..."),
        "tap_to_speak" to Trans("Bolne ke liye Mic dabayein", "बोलने के लिए माइक दबाएं", "બોલવા માટે માઇક દબાવો", "Tap mic to speak"),
        "generate_bill" to Trans("Bill Banayein", "बिल बनाएं", "બિલ બનાવો", "Generate Bill"),
        "import_khatabook" to Trans("Khatabook Se Import Karein", "खाताबुक से इम्पोर्ट करें", "ખાતાબુકમાંથી ઇમ્પોર્ટ કરો", "Import from Khatabook"),
        "store_address" to Trans("Store Ka Pata", "दुकान का पता", "દુકાનનું સરનામું", "Store Address"),
        "store_upi" to Trans("Store UPI ID", "दुकान की UPI आईडी", "દુકાન UPI ID", "Store UPI ID"),
        "store_phone" to Trans("Store Phone Number", "दुकान का फोन नंबर", "દુકાનનો ફોન નંબર", "Store Phone Number")
    )
}
