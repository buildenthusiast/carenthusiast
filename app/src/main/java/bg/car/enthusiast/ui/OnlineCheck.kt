package bg.car.enthusiast.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/** Официалните страници за проверка по регистрационен номер. */
enum class OnlineCheck(
    val label: String,
    val url: String,
    /** CSS селектор на полето за номер. */
    val field: String,
    /** Текст на бутона, който да се натисне сам (само където няма код за сигурност). */
    val autoClick: String?,
    /** Сайтът иска номера на латиница. */
    val latin: Boolean,
    val hint: String,
) {
    VIGNETTE(
        "Винетка (BG TOLL)",
        "https://check.bgtoll.bg/#/",
        "input[type=text]",
        "Проверка",
        true,
        "Проверката е на официалния сайт на BG TOLL.",
    ),
    CIVIL(
        "Гражданска (Гаранционен фонд)",
        "https://www.guaranteefund.org/bg/%D0%B8%D0%BD%D1%84%D0%BE%D1%80%D0%BC%D0%B0%D1%86%D0%B8%D0%BE%D0%BD%D0%B5%D0%BD-%D1%86%D0%B5%D0%BD%D1%82%D1%8A%D1%80-%D0%B8-%D1%81%D0%BF%D1%80%D0%B0%D0%B2%D0%BA%D0%B8/%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8/%D0%BF%D1%80%D0%BE%D0%B2%D0%B5%D1%80%D0%BA%D0%B0-%D0%B7%D0%B0-%D0%B2%D0%B0%D0%BB%D0%B8%D0%B4%D0%BD%D0%B0-%D0%B7%D0%B0%D1%81%D1%82%D1%80%D0%B0%D1%85%D0%BE%D0%B2%D0%BA%D0%B0-%D0%B3%D1%80a%D0%B6%D0%B4a%D0%BD%D1%81%D0%BAa-%D0%BE%D1%82%D0%B3%D0%BE%D0%B2%D0%BE%D1%80%D0%BD%D0%BE%D1%81%D1%82-%D0%BD%D0%B0-%D0%B0%D0%B2%D1%82%D0%BE%D0%BC%D0%BE%D0%B1%D0%B8%D0%BB%D0%B8%D1%81%D1%82%D0%B8%D1%82%D0%B5",
        "#dkn",
        null,
        false,
        "Номерът е попълнен. Отбележи кода за сигурност и натисни бутона за проверка.",
    ),
    INSPECTION(
        "Технически преглед (ИААА)",
        "https://rta.government.bg/services/check-inspection/index.html",
        "input[placeholder='Рег. номер']",
        null,
        false,
        "Номерът е попълнен. Въведи кода за сигурност и натисни „Провери“.",
    ),
}

private const val CYR = "АВСЕНКМОРТХУ"
private const val LAT = "ABCEHKMOPTXY"

fun plateLatin(p: String) = p.uppercase().filter { !it.isWhitespace() && it != '-' }.map { c ->
    val i = CYR.indexOf(c)
    if (i >= 0) LAT[i] else c
}.joinToString("")

fun plateCyrillic(p: String) = p.uppercase().filter { !it.isWhitespace() && it != '-' }.map { c ->
    val i = LAT.indexOf(c)
    if (i >= 0) CYR[i] else c
}.joinToString("")

/** Отваря официалната страница вътре в приложението и попълва номера. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OnlineCheckScreen(check: OnlineCheck, plate: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val value = if (check.latin) plateLatin(plate) else plateCyrillic(plate)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Column {
                Text(check.label, style = MaterialTheme.typography.titleMedium)
                Text("$value · ${check.hint}", style = MaterialTheme.typography.bodySmall)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Номер", value))
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript(fillScript(check, value), null)
                        }
                    }
                    loadUrl(check.url)
                }
            },
        )
    }
}

/** Изчаква полето да се появи, попълва номера и, ако може, натиска бутона за проверка. */
private fun fillScript(check: OnlineCheck, plate: String): String {
    val p = JSONObject.quote(plate)
    val sel = JSONObject.quote(check.field)
    val click = check.autoClick?.let { JSONObject.quote(it) } ?: "null"
    return """
        (function() {
          var n = 0;
          var t = setInterval(function() {
            n++;
            var el = document.querySelector($sel);
            if (el) {
              clearInterval(t);
              var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
              setter.call(el, $p);
              el.dispatchEvent(new Event('input', { bubbles: true }));
              el.dispatchEvent(new Event('change', { bubbles: true }));
              var label = $click;
              if (label) {
                setTimeout(function() {
                  var b = Array.prototype.find.call(document.querySelectorAll('button'), function(x) { return x.textContent.trim() === label; });
                  if (b) b.click();
                }, 400);
              }
            }
            if (n > 60) clearInterval(t);
          }, 250);
        })();
    """.trimIndent()
}
