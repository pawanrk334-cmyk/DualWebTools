package com.dual.tools

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var webView: WebView
    private lateinit var reloadBtn: Button
    private lateinit var qrResultText: TextView
    private lateinit var statusListContainer: LinearLayout
    private lateinit var repliesContainer: LinearLayout
    private val tabViews = mutableListOf<View>()
    private val navButtons = mutableListOf<Button>()

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            qrResultText.text = "Scanned: ${result.contents}"
            copyToClipboard(result.contents)
            Toast.makeText(this, "Scan result copy ho gaya!", Toast.LENGTH_SHORT).show()
        }
    }

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            prefs.edit().putString("status_uri", uri.toString()).apply()
            loadStatusFiles(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("dual_web_prefs", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F6F8"))
        }

        // Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#075E54"))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "Dual Web Tools"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        reloadBtn = Button(this).apply {
            text = "Reload"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#128C7E"))
            textSize = 12f
            setOnClickListener { webView.reload() }
        }
        topBar.addView(title)
        topBar.addView(reloadBtn)
        root.addView(topBar)

        // Main Content Frame
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val webTab = buildWebViewTab()
        val directTab = buildDirectChatTab()
        val repliesTab = buildQuickRepliesTab()
        val qrTab = buildQrTab()
        val toolsTab = buildToolsTab()

        tabViews.addAll(listOf(webTab, directTab, repliesTab, qrTab, toolsTab))
        tabViews.forEachIndexed { idx, view ->
            view.visibility = if (idx == 0) View.VISIBLE else View.GONE
            contentFrame.addView(view)
        }
        root.addView(contentFrame)

        // Bottom Navigation Bar
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            elevation = 8f
        }
        val labels = listOf("Web", "Direct", "Replies", "QR", "Tools")
        labels.forEachIndexed { idx, label ->
            val btn = Button(this).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { switchTab(idx) }
            }
            navButtons.add(btn)
            bottomBar.addView(btn)
        }
        root.addView(bottomBar)
        setContentView(root)
        switchTab(0)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (tabViews[0].visibility == View.VISIBLE && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        checkAppLock()
    }

    private fun switchTab(index: Int) {
        tabViews.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        navButtons.forEachIndexed { i, b ->
            if (i == index) {
                b.setBackgroundColor(Color.parseColor("#075E54"))
                b.setTextColor(Color.WHITE)
            } else {
                b.setBackgroundColor(Color.WHITE)
                b.setTextColor(Color.DKGRAY)
            }
        }
        reloadBtn.visibility = if (index == 0) View.VISIBLE else View.GONE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebViewTab(): View {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    CookieManager.getInstance().flush()
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl("https://web.whatsapp.com/")
        }
        return webView
    }

    private fun buildDirectChatTab(): View {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val heading = TextView(this).apply {
            text = "Direct Chat (Bina Number Save Kiye)"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }
        val codeInput = EditText(this).apply {
            hint = "Country Code (e.g. 91)"
            setText("91")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val phoneInput = EditText(this).apply {
            hint = "10-digit Mobile Number"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val msgInput = EditText(this).apply {
            hint = "Message (Optional)"
            minLines = 3
        }
        val sendBtn = Button(this).apply {
            text = "Open Chat"
            setBackgroundColor(Color.parseColor("#25D366"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val code = codeInput.text.toString().trim().replace("+", "")
                val phone = phoneInput.text.toString().trim().replace(" ", "")
                val msg = msgInput.text.toString().trim()
                if (phone.length < 8) {
                    Toast.makeText(this@MainActivity, "Sahi mobile number dalein", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val encoded = URLEncoder.encode(msg, "UTF-8")
                val url = "https://wa.me/$code$phone?text=$encoded"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        box.addView(heading)
        box.addView(codeInput)
        box.addView(phoneInput)
        box.addView(msgInput)
        box.addView(sendBtn)
        scroll.addView(box)
        return scroll
    }

    private fun buildQuickRepliesTab(): View {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val heading = TextView(this).apply {
            text = "Quick Replies (1-Tap Copy)"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }
        val newReplyInput = EditText(this).apply {
            hint = "Naya message template likhein..."
        }
        val addBtn = Button(this).apply {
            text = "Add Template"
            setBackgroundColor(Color.parseColor("#075E54"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val txt = newReplyInput.text.toString().trim()
                if (txt.isNotEmpty()) {
                    val list = getReplies().toMutableList()
                    list.add(0, txt)
                    saveReplies(list)
                    newReplyInput.setText("")
                    refreshRepliesUI()
                }
            }
        }
        repliesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        box.addView(heading)
        box.addView(newReplyInput)
        box.addView(addBtn)
        box.addView(repliesContainer)
        scroll.addView(box)
        refreshRepliesUI()
        return scroll
    }

    private fun getReplies(): List<String> {
        val saved = prefs.getString("quick_replies", null)
        return if (saved == null) {
            listOf(
                "Abhi busy hoon, thodi der mein call karta hoon.",
                "Thanks! Payment receive ho gayi hai.",
                "Please apna address aur pincode bhej dijiye."
            )
        } else {
            saved.split("|||").filter { it.isNotEmpty() }
        }
    }

    private fun saveReplies(list: List<String>) {
        prefs.edit().putString("quick_replies", list.joinToString("|||")).apply()
    }

    private fun refreshRepliesUI() {
        repliesContainer.removeAllViews()
        val list = getReplies()
        list.forEachIndexed { idx, item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.WHITE)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 0, dp(8))
                layoutParams = lp
            }
            val tv = TextView(this).apply {
                text = item
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    copyToClipboard(item)
                    Toast.makeText(this@MainActivity, "Copied!", Toast.LENGTH_SHORT).show()
                }
            }
            val delBtn = Button(this).apply {
                text = "X"
                setTextColor(Color.RED)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    val updated = getReplies().toMutableList()
                    updated.removeAt(idx)
                    saveReplies(updated)
                    refreshRepliesUI()
                }
            }
            card.addView(tv)
            card.addView(delBtn)
            repliesContainer.addView(card)
        }
    }

    private fun buildQrTab(): View {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val heading = TextView(this).apply {
            text = "QR Scanner & Generator"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }
        val scanBtn = Button(this).apply {
            text = "Scan QR / Barcode"
            setBackgroundColor(Color.parseColor("#075E54"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val options = ScanOptions().apply {
                    setPrompt("QR Code ke saamne camera rakhein")
                    setBeepEnabled(true)
                    setOrientationLocked(false)
                }
                qrScannerLauncher.launch(options)
            }
        }
        qrResultText = TextView(this).apply {
            text = "Scan result yahan dikhega"
            setPadding(0, dp(10), 0, dp(16))
            textSize = 14f
        }
        val qrInput = EditText(this).apply {
            hint = "Text, UPI ID ya Link dalein QR banane ke liye"
        }
        val qrImage = ImageView(this).apply {
            setPadding(0, dp(12), 0, dp(12))
        }
        val genBtn = Button(this).apply {
            text = "Generate QR Code"
            setBackgroundColor(Color.parseColor("#128C7E"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val txt = qrInput.text.toString().trim()
                if (txt.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Pehle kuch text likhein", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                qrImage.setImageBitmap(generateQrBitmap(txt))
            }
        }
        box.addView(heading)
        box.addView(scanBtn)
        box.addView(qrResultText)
        box.addView(qrInput)
        box.addView(genBtn)
        box.addView(qrImage)
        scroll.addView(box)
        return scroll
    }

    private fun generateQrBitmap(text: String): Bitmap {
        val size = 500
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    private fun buildToolsTab(): View {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // App Lock Section
        val lockTitle = TextView(this).apply {
            text = "App Lock (4-Digit PIN)"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }
        val pinInput = EditText(this).apply {
            hint = "Naya 4-digit PIN (khali chhod kar save = Lock OFF)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val savePinBtn = Button(this).apply {
            text = "Save / Remove PIN"
            setBackgroundColor(Color.parseColor("#075E54"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val pin = pinInput.text.toString().trim()
                if (pin.isEmpty()) {
                    prefs.edit().remove("app_pin").apply()
                    Toast.makeText(this@MainActivity, "App Lock hata diya gaya", Toast.LENGTH_SHORT).show()
                } else if (pin.length == 4) {
                    prefs.edit().putString("app_pin", pin).apply()
                    pinInput.setText("")
                    Toast.makeText(this@MainActivity, "App Lock set ho gaya!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "4 digit ka PIN dalein", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Status Saver Section
        val statusTitle = TextView(this).apply {
            text = "Status Saver"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(22), 0, dp(6))
        }
        val statusHint = TextView(this).apply {
            text = "Folder chunein: Android > media > com.whatsapp > WhatsApp > Media > .Statuses"
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        val pickFolderBtn = Button(this).apply {
            text = "Select WhatsApp Status Folder"
            setBackgroundColor(Color.parseColor("#128C7E"))
            setTextColor(Color.WHITE)
            setOnClickListener { folderPickerLauncher.launch(null) }
        }
        statusListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        box.addView(lockTitle)
        box.addView(pinInput)
        box.addView(savePinBtn)
        box.addView(statusTitle)
        box.addView(statusHint)
        box.addView(pickFolderBtn)
        box.addView(statusListContainer)
        scroll.addView(box)

        prefs.getString("status_uri", null)?.let { savedUri ->
            try {
                loadStatusFiles(Uri.parse(savedUri))
            } catch (_: Exception) {}
        }
        return scroll
    }

    private fun loadStatusFiles(treeUri: Uri) {
        statusListContainer.removeAllViews()
        val folder = DocumentFile.fromTreeUri(this, treeUri) ?: return
        val files = folder.listFiles().filter {
            val name = it.name?.lowercase() ?: ""
            name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".mp4")
        }
        if (files.isEmpty()) {
            val emptyTv = TextView(this).apply { text = "Is folder mein koi status file nahi mili." }
            statusListContainer.addView(emptyTv)
            return
        }
        files.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.WHITE)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 0, dp(6))
                layoutParams = lp
            }
            val nameTv = TextView(this).apply {
                text = file.name ?: "Status"
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val saveBtn = Button(this).apply {
                text = "Save"
                setBackgroundColor(Color.parseColor("#25D366"))
                setTextColor(Color.WHITE)
                setOnC
