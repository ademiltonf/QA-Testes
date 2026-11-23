package com.oxe.iptvplayer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Primeira tela do PRO: confere a licença, mostra o código do aparelho e o pagamento. */
class LicenseActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_MANAGE = "manage"

        /** Abre a tela de ativação pelas Configurações. */
        fun manage(ctx: Context) {
            ctx.startActivity(Intent(ctx, LicenseActivity::class.java).putExtra(EXTRA_MANAGE, true))
        }

        /** Volta para a tela de ativação, fechando o resto (teste acabou). */
        fun lock(ctx: Context) {
            ctx.startActivity(
                Intent(ctx, LicenseActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }

    private var manage = false
    private var pollJob: Job? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    private lateinit var loading: View
    private lateinit var activation: View
    private lateinit var payment: View
    private lateinit var statusText: TextView
    private lateinit var errorText: TextView
    private lateinit var continueButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manage = intent.getBooleanExtra(EXTRA_MANAGE, false)

        // Já liberado (verificado há pouco)? Entra direto e confere em segundo plano.
        if (!manage && License.allowedOffline(this)) {
            lifecycleScope.launch(Dispatchers.IO) { runCatching { License.check(applicationContext) } }
            proceed()
            return
        }

        setContentView(R.layout.activity_license)
        loading = findViewById(R.id.loading)
        activation = findViewById(R.id.activation)
        payment = findViewById(R.id.payment)
        statusText = findViewById(R.id.license_status)
        errorText = findViewById(R.id.license_error)
        continueButton = findViewById(R.id.continue_button)

        findViewById<TextView>(R.id.device_code).text = License.deviceId(this)

        findViewById<View>(R.id.plan_anual).apply {
            focusZoom(1.05f)
            setOnClickListener { startCheckout("anual") }
        }
        findViewById<View>(R.id.plan_vitalicio).apply {
            focusZoom(1.05f)
            setOnClickListener { startCheckout("vitalicio") }
        }
        findViewById<View>(R.id.check_button).setOnClickListener { verify(showToast = true) }
        findViewById<View>(R.id.payment_back).setOnClickListener { showActivation() }
        continueButton.setOnClickListener { if (manage) finish() else proceed() }

        // Na TV (deitado) o QR Code fica do lado do texto
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            findViewById<LinearLayout>(R.id.payment_row).orientation = LinearLayout.HORIZONTAL
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (payment.isVisible) showActivation() else finish()
            }
        })

        verify(showToast = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        pollJob?.cancel()
    }

    /** Consulta o servidor e decide o que mostrar. */
    private fun verify(showToast: Boolean) {
        if (!payment.isVisible) {
            loading.isVisible = true
            activation.isVisible = false
        }
        errorText.isVisible = false
        lifecycleScope.launch {
            try {
                val s = withContext(Dispatchers.IO) { License.check(this@LicenseActivity) }
                if (s.allowed && !manage && !payment.isVisible) {
                    proceed()
                    return@launch
                }
                if (payment.isVisible) {
                    if (s.isPaid) onActivated()
                    else if (showToast) {
                        Toast.makeText(this@LicenseActivity, R.string.lic_not_paid_yet, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                showActivation(s)
                if (showToast && !s.isPaid) {
                    Toast.makeText(this@LicenseActivity, R.string.lic_not_paid_yet, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                showActivation(null)
                errorText.text = getString(R.string.lic_offline, e.message ?: "")
                errorText.isVisible = true
            }
        }
    }

    private fun showActivation(s: License.State? = License.cached(this)) {
        pollJob?.cancel()
        loading.isVisible = false
        payment.isVisible = false
        activation.isVisible = true

        statusText.text = when {
            s == null -> getString(R.string.lic_choose)
            s.status == "blocked" -> getString(R.string.lic_blocked)
            s.isPaid && s.plan == "vitalicio" -> getString(R.string.lic_active_lifetime)
            s.isPaid -> getString(R.string.lic_active_until, dateFormat.format(Date(s.activeUntil ?: 0)))
            s.allowed -> resources.getQuantityString(R.plurals.lic_trial_left, s.trialDaysLeft, s.trialDaysLeft)
            else -> getString(R.string.lic_trial_over)
        }
        continueButton.isVisible = s?.allowed == true
        findViewById<View>(if (s?.allowed == true) R.id.continue_button else R.id.plan_anual).requestFocus()
    }

    private fun startCheckout(plan: String) {
        loading.isVisible = true
        activation.isVisible = false
        errorText.isVisible = false
        lifecycleScope.launch {
            try {
                val url = withContext(Dispatchers.IO) { License.checkout(this@LicenseActivity, plan) }
                val qr = withContext(Dispatchers.Default) { License.qrCode(url, 600) }
                showPayment(plan, url, qr)
            } catch (e: Exception) {
                showActivation()
                errorText.text = getString(R.string.lic_payment_error, e.message ?: "")
                errorText.isVisible = true
            }
        }
    }

    private fun showPayment(plan: String, url: String, qr: android.graphics.Bitmap) {
        loading.isVisible = false
        activation.isVisible = false
        payment.isVisible = true
        findViewById<ImageView>(R.id.qr_image).setImageBitmap(qr)
        findViewById<TextView>(R.id.payment_plan).setText(
            if (plan == "vitalicio") R.string.lic_plan_lifetime_full else R.string.lic_plan_year_full
        )
        findViewById<TextView>(R.id.payment_url).text = url
        findViewById<View>(R.id.check_button).requestFocus()

        // Fica conferindo sozinho até o pagamento cair
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                delay(5_000)
                val s = withContext(Dispatchers.IO) { runCatching { License.check(this@LicenseActivity) }.getOrNull() }
                if (s?.isPaid == true) {
                    onActivated()
                    break
                }
            }
        }
    }

    private fun onActivated() {
        pollJob?.cancel()
        Toast.makeText(this, R.string.lic_activated, Toast.LENGTH_LONG).show()
        if (manage) finish() else proceed()
    }

    private fun proceed() {
        startActivity(Intent(this, WelcomeActivity::class.java))
        finish()
    }
}
