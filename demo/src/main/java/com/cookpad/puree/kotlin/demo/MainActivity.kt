package com.cookpad.puree.kotlin.demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.cookpad.puree.kotlin.PureeLogger
import com.cookpad.puree.kotlin.demo.databinding.ActivityMainBinding
import com.cookpad.puree.kotlin.demo.log.ClickLog
import com.cookpad.puree.kotlin.demo.log.MenuLog
import com.cookpad.puree.kotlin.demo.log.PeriodicLog

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var logger: PureeLogger
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var periodicLogSequence: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLogs()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add -> logger.postLog(MenuLog("add"))
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    private fun setupLogs() {
        logger = (application as DemoApp).logger

        binding.button1.setOnClickListener {
            logger.postLog(ClickLog("Button 1"))
        }
        binding.logPerSecond.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                periodicLogSequence = 1
                sendPeriodicLog()
            }
        }
    }

    private fun sendPeriodicLog() {
        if (binding.logPerSecond.isChecked) {
            logger.postLog(PeriodicLog(periodicLogSequence++))
            handler.postDelayed(this::sendPeriodicLog, 1000)
        }
    }
}
