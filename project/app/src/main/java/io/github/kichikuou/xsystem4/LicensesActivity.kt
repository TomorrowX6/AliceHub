package io.github.kichikuou.xsystem4

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import java.io.BufferedReader

class LicensesActivity : ThemedActivity() {
    companion object {
        const val EXTRA_DISPLAY_NAME = "DISPLAY_NAME"
        const val EXTRA_FILE_NAME = "FILE_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_licenses)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.title = intent.getStringExtra(EXTRA_DISPLAY_NAME)

        val path = "licenses/" + intent.getStringExtra(EXTRA_FILE_NAME)
        val text = assets.open(path).bufferedReader().use(BufferedReader::readText)
        findViewById<TextView>(R.id.license_text).text = text
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
