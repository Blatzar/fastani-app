package com.lagradost.shiro

import android.app.Application
import android.content.Context
import android.widget.Toast
import com.jaredrummler.cyanea.Cyanea
import org.acra.ACRA
import org.acra.annotation.AcraCore
import org.acra.annotation.AcraToast
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderFactory
import kotlin.concurrent.thread

class CustomReportSender : ReportSender {
    // Sends all your crashes to google forms
    override fun send(context: Context, report: CrashReportData) {
        try {
            println("Report sent")
            val url =
                "https://docs.google.com/forms/u/0/d/e/1FAIpQLSf8U6zVn4YPGhbCQXUBNH4k5wlYC2KmmGuUZz4O6TL2o62cAw/formResponse"
            val data = mapOf(
                "entry.1083318133" to report.toJSON()
            )
            thread {
                khttp.post(url, data = data)
            }
        } catch (e: Exception) {
            println("ERROR SENDING BUG")
        }
    }
}

class CustomSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender {
        return CustomReportSender()
    }

    override fun enabled(coreConfig: CoreConfiguration): Boolean {
        return true
    }
}

@AcraToast(
    resText = R.string.acra_report_toast,
    length = Toast.LENGTH_LONG
)

@AcraCore(reportSenderFactoryClasses = [CustomSenderFactory::class])
class AcraApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        ACRA.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        Cyanea.init(this, resources)
    }
}