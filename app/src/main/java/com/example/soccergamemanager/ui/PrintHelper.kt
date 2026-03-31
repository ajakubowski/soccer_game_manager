package com.example.soccergamemanager.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.soccergamemanager.domain.PrintableReport

fun printReport(context: Context, report: PrintableReport) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            printManager.print(
                report.title,
                webView.createPrintDocumentAdapter(report.title),
                PrintAttributes.Builder().build(),
            )
        }
    }
    webView.loadDataWithBaseURL(null, report.html, "text/html", "UTF-8", null)
}
