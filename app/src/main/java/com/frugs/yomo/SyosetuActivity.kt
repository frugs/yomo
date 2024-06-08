package com.frugs.yomo

import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.frugs.yomo.syosetu.SyosetuService
import com.frugs.yomo.syosetu.SyosetuService.Companion.getNcode
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyosetuActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private var fab: FloatingActionButton? = null

    private var db: BookDb? = null
    private var syosetuService: SyosetuService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = BookyApp.getDB(this)
        syosetuService = BookyApp.getSyosetuService(this)

        this.enableEdgeToEdge()
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_syosetu)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val webView: WebView = findViewById(R.id.syosetu_webview)
        val settings = webView.getSettings()
        settings.domStorageEnabled = true

        val fab = findViewById<FloatingActionButton>(R.id.syosetu_fab)
        fab.visibility = View.INVISIBLE
        fab.setOnClickListener { v: View? ->
            if (syosetuService == null || db == null) {
                return@setOnClickListener
            }
            val url = webView.url

            if (url == null || !url.contains("ncode.syosetu.com")) {
                return@setOnClickListener
            }

            val ncode = getNcode(url)

            if (ncode.isNullOrEmpty()) {
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val details = syosetuService!!.getDetails(ncode)

                withContext(Dispatchers.Main) {
                    if (details != null) {
                        db!!.addBook("$ncode.ncode", details.title, details.author)
                        finish()
                    } else {
                        val toast = Toast.makeText(
                                this@SyosetuActivity,
                                R.string.add_syosetu_fail_toast,
                                Toast.LENGTH_SHORT);
                        toast.show()
                    }
                }
            }
        }

        webView.setWebViewClient(object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (url.contains("ncode.syosetu.com")) {
                    fab.visibility = View.VISIBLE
                } else {
                    fab.visibility = View.INVISIBLE
                }
            }
        })

        webView.loadUrl("https://syosetu.com/")

        this.webView = webView
        this.fab = fab
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.syosetu, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId

        if (itemId == R.id.menu_close_syosetu) {
            finish()

            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Check whether the key event is the Back button and if there's history.
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null) {
            if (webView!!.canGoBack()) {
                webView!!.goBack()
            } else {
                finish()
            }

            return true
        }

        // If it isn't the Back button or there's no web page history, bubble up to
        // the default system behavior. Probably exit the activity.
        return super.onKeyDown(keyCode, event)
    }
}