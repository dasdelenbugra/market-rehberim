package com.marketrehberim.ui.view.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.marketrehberim.R
import com.marketrehberim.data.model.Item
import com.marketrehberim.data.remote.dto.MarketsDto
import com.marketrehberim.databinding.FragmentHomeBinding
import com.marketrehberim.databinding.ItemCityRowBinding
import com.marketrehberim.databinding.SheetCityPickerBinding
import com.marketrehberim.ui.adapter.FavoriteAdapter
import com.marketrehberim.ui.viewmodel.CityDetection
import com.marketrehberim.ui.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private val favoriteAdapter = FavoriteAdapter(onClick = ::openDetail)

    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) recognize(bitmap) else showMessage(getString(R.string.no_photo))
        }

    private val requestLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) detectCityFromLocation()
            else showMessage(getString(R.string.location_permission_denied))
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.favoritesList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.favoritesList.adapter = favoriteAdapter

        binding.fabOpenCamera.setOnClickListener { takePicture.launch(null) }
        binding.btnCity.setOnClickListener { showCityPicker() }

        observeCity()
        observeMarkets()
        observeSavings()
        observeRecentSearches()
        observeFavorites()
    }

    // --- Tasarruf şeridi ---
    private fun observeSavings() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.monthlySavings.collect { summary ->
                    // Henüz hiçbir seçim yapılmadıysa şerit görünmez; boş bir
                    // "0,00 ₺ kazandın" övünmesi kimseye bir şey söylemez.
                    if (summary.count == 0 || summary.total <= 0.0) {
                        binding.savingsCard.visibility = View.GONE
                        return@collect
                    }
                    binding.savingsCard.visibility = View.VISIBLE
                    binding.tvSavingsAmount.text =
                        getString(R.string.savings_amount, summary.total)
                    binding.tvSavingsHint.text =
                        getString(R.string.savings_hint, summary.count)
                }
            }
        }
    }

    // --- Şehir ---
    private fun observeCity() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cityLabel.collect { binding.btnCity.text = it }
            }
        }
    }

    // --- Şehrindeki marketler ---
    private fun observeMarkets() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.markets.collect(::buildMarketChips)
            }
        }
    }

    private fun buildMarketChips(markets: MarketsDto) {
        binding.marketChips.removeAllViews()
        val hasAny = markets.national.isNotEmpty() || markets.local.isNotEmpty()
        binding.tvMarketsTitle.visibility = if (hasAny) View.VISIBLE else View.GONE
        binding.tvMarketsHint.visibility = if (hasAny) View.VISIBLE else View.GONE
        binding.marketChips.visibility = if (hasAny) View.VISIBLE else View.GONE
        // Ulusal marketler önce, yerel (crowdsourced) marketler konum ikonuyla ayrılır.
        markets.national.forEach { addMarketChip(it, local = false) }
        markets.local.forEach { addMarketChip(it, local = true) }
    }

    private fun addMarketChip(name: String, local: Boolean) {
        val chip = Chip(requireContext()).apply {
            text = name
            isClickable = false
            isCheckable = false
            if (local) {
                setChipIconResource(R.drawable.ic_location)
                isChipIconVisible = true
            }
        }
        binding.marketChips.addView(chip)
    }

    private fun showCityPicker() {
        val cities = viewModel.cities.value
        val sheet = BottomSheetDialog(requireContext())
        val sheetBinding = SheetCityPickerBinding.inflate(layoutInflater)
        val current = viewModel.cityLabel.value

        // Şehir listesi backend'den gelir; boşsa sheet sessizce boş görünmesin.
        sheetBinding.tvCitiesEmpty.visibility = if (cities.isEmpty()) View.VISIBLE else View.GONE

        cities.forEach { city ->
            val row = ItemCityRowBinding.inflate(layoutInflater, sheetBinding.cityContainer, false)
            row.tvCityLabel.text = city.label
            row.ivSelected.visibility = if (city.label == current) View.VISIBLE else View.GONE
            row.root.setOnClickListener {
                viewModel.selectCity(city)
                sheet.dismiss()
            }
            sheetBinding.cityContainer.addView(row.root)
        }

        sheetBinding.rowUseLocation.setOnClickListener {
            sheet.dismiss()
            onUseMyLocation()
        }

        sheet.setContentView(sheetBinding.root)
        sheet.show()
    }

    // --- Konumdan şehir tespiti ---
    private fun onUseMyLocation() {
        val perm = Manifest.permission.ACCESS_COARSE_LOCATION
        if (ContextCompat.checkSelfPermission(requireContext(), perm) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            detectCityFromLocation()
        } else {
            requestLocation.launch(perm)
        }
    }

    private fun detectCityFromLocation() {
        showMessage(getString(R.string.detecting_location))
        lifecycleScope.launch {
            when (val result = viewModel.detectCity()) {
                is CityDetection.Selected ->
                    showMessage(getString(R.string.city_detected, result.label))
                is CityDetection.Unsupported ->
                    showMessage(getString(R.string.city_unsupported, result.province))
                CityDetection.Unavailable ->
                    showMessage(getString(R.string.location_unavailable))
                CityDetection.NoCityList ->
                    showMessage(getString(R.string.city_list_unavailable))
            }
        }
    }

    // --- Son aramalar ---
    private fun observeRecentSearches() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentSearches.collect(::buildRecentChips)
            }
        }
    }

    private fun buildRecentChips(queries: List<String>) {
        binding.recentChips.removeAllViews()
        binding.tvRecentTitle.visibility = if (queries.isEmpty()) View.GONE else View.VISIBLE
        queries.forEach { query ->
            val chip = Chip(requireContext()).apply {
                text = query
                setOnClickListener { searchFor(query) }
            }
            binding.recentChips.addView(chip)
        }
    }

    // --- Favoriler ---
    private fun observeFavorites() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.favorites.collect { items ->
                    favoriteAdapter.submitList(items)
                    val empty = items.isEmpty()
                    binding.favoritesEmptyCard.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.favoritesList.visibility = if (empty) View.GONE else View.VISIBLE
                }
            }
        }
    }

    // --- Kamera ile tanıma ---
    private fun recognize(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val label = labels.firstOrNull()?.text
                if (label.isNullOrBlank()) showMessage(getString(R.string.no_object_detected))
                else searchFor(LabelTranslator.toTurkishQuery(label))
            }
            .addOnFailureListener { showMessage(getString(R.string.recognition_failed)) }
    }

    /**
     * Arama sekmesine geçip sorguyu çalıştırır.
     *
     * `NavOptions` şart: düz `navigate()` her çağrıda yığına yeni bir
     * SearchFragment ekliyordu. Kullanıcı kamerayla üst üste üç ürün tarayınca
     * geri tuşu anasayfaya değil, önceki arama ekranlarına dönüyordu —
     * alt gezinme çubuğuyla varılan bir hedef için yanlış davranış.
     *
     * `restoreState` bilerek kapalı: sekmenin eski durumu geri yüklenirse yeni
     * `query` argümanı yok sayılır ve tarama sonucu hiç aranmaz.
     */
    private fun searchFor(query: String) {
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.homeFragment, /* inclusive = */ false, /* saveState = */ true)
            .build()
        findNavController().navigate(R.id.searchFragment, bundleOf("query" to query), options)
    }

    private fun openDetail(item: Item) {
        findNavController().navigate(R.id.productDetailFragment, bundleOf("item" to item))
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
