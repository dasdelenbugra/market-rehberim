package com.marketrehberim.ui.view.crowdsource

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.marketrehberim.R
import com.marketrehberim.databinding.FragmentCrowdsourceBinding
import com.marketrehberim.ui.viewmodel.CrowdsourceViewModel
import com.marketrehberim.ui.viewmodel.SubmitOutcome
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Crowdsourced fiyat bildirimi. Web sitesi olmayan yerel marketler için:
 * kullanıcı raf etiketini fotoğraflar, ML Kit metin tanıma (OCR) fiyatı okur,
 * kullanıcı onaylayıp gönderir → backend'de ortak veritabanına eklenir.
 */
@AndroidEntryPoint
class CrowdsourceFragment : Fragment() {
    private var _binding: FragmentCrowdsourceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CrowdsourceViewModel by viewModels()

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) runOcr(bitmap)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCrowdsourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvCity.text = getString(R.string.report_subtitle)
        viewModel.loadMarkets()

        binding.btnCapture.setOnClickListener { takePicture.launch(null) }
        binding.btnSubmit.setOnClickListener { submit() }

        observeMarkets()
        observeSubmitting()
        observeEvents()
    }

    private fun observeMarkets() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.markets.collect { markets ->
                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        markets,
                    )
                    binding.dropdownMarket.setAdapter(adapter)
                }
            }
        }
    }

    private fun observeSubmitting() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.submitting.collect { binding.btnSubmit.isEnabled = !it }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { outcome ->
                    when (outcome) {
                        is SubmitOutcome.Success -> {
                            showMessage(getString(R.string.price_submitted))
                            binding.etName.text?.clear()
                            binding.etPrice.text?.clear()
                        }
                        // Red gerekçesi genelde düzeltilebilir bir şey söylüyor
                        // ("fiyat aralık dışı"); alanlar temizlenmez, kullanıcı
                        // yazdığını düzeltebilsin. Mesaj da uzun, o yüzden LONG.
                        is SubmitOutcome.Rejected ->
                            showMessage(outcome.reason, Snackbar.LENGTH_LONG)
                        is SubmitOutcome.Failed ->
                            showMessage(getString(R.string.price_submit_failed))
                    }
                }
            }
        }
    }

    private fun runOcr(bitmap: Bitmap) {
        binding.ocrProgress.visibility = View.VISIBLE
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                binding.ocrProgress.visibility = View.GONE
                val price = PriceParser.extractPrice(result.text)
                if (price != null) {
                    binding.etPrice.setText(price)
                } else {
                    showMessage(getString(R.string.ocr_failed))
                }
            }
            .addOnFailureListener {
                binding.ocrProgress.visibility = View.GONE
                showMessage(getString(R.string.ocr_failed))
            }
    }

    private fun submit() {
        val market = binding.dropdownMarket.text?.toString().orEmpty()
        val name = binding.etName.text?.toString().orEmpty()
        val price = binding.etPrice.text?.toString().orEmpty()
        if (market.isBlank() || name.isBlank() || price.isBlank()) {
            showMessage(getString(R.string.fill_all_fields))
            return
        }
        viewModel.submit(market, name, price)
    }

    private fun showMessage(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(binding.root, message, duration).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
