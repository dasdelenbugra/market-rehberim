package com.marketrehberim.ui.view.detail

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.marketrehberim.R
import com.marketrehberim.data.model.Item
import com.marketrehberim.databinding.FragmentProductDetailBinding
import com.marketrehberim.ui.adapter.ProductCompareAdapter
import com.marketrehberim.ui.theme.MarketPalette
import com.marketrehberim.ui.viewmodel.ProductDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProductDetailFragment : Fragment() {
    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProductDetailViewModel by viewModels()
    private val compareAdapter = ProductCompareAdapter()
    private lateinit var item: Item
    private var isFavorite = false

    /**
     * Bildirim izni açılışta değil, ilk favori eklendiğinde istenir — o an izin
     * ne işe yarayacağı ("bu ürün ucuzlayınca haber ver") kullanıcı için belli.
     * Reddedilse de favori eklenir; takip sessizce çalışmaz, o kadar.
     */
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && _binding != null) {
                Snackbar.make(binding.root, R.string.price_watch_on, Snackbar.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        val incoming = arguments?.getParcelable<Item>("item")
        if (incoming == null) {
            findNavController().navigateUp()
            return
        }
        item = incoming

        binding.compareList.adapter = compareAdapter

        bindItem()
        observeFavorite()
        observeHistory()
        observeComparison()

        viewModel.loadHistory(item)
        viewModel.loadComparison(item)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnFavorite.setOnClickListener {
            val makeFavorite = !isFavorite
            viewModel.setFavorite(item, makeFavorite = makeFavorite)
            if (makeFavorite) askNotificationPermissionIfNeeded()
        }
        binding.btnAddToBasket.setOnClickListener { addToBasket() }
    }

    private fun bindItem() {
        binding.name.text = item.name
        binding.from.text = item.from
        binding.price.text = item.formattedPrice
        binding.marketDot.backgroundTintList =
            ColorStateList.valueOf(MarketPalette.colorFor(requireContext(), item.from))
        Glide.with(this).load(item.image).into(binding.image)
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun addToBasket() {
        val added = viewModel.addToBasket(item)
        val message = if (added) R.string.added_to_basket else R.string.already_in_basket
        Snackbar.make(binding.root, getString(message), Snackbar.LENGTH_SHORT).show()
    }

    private fun observeFavorite() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isFavorite(item).collect { fav ->
                    isFavorite = fav
                    // Tam genişlik buton yerine görselin üzerindeki ikon geçişi
                    binding.btnFavorite.setIconResource(
                        if (fav) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                    )
                    binding.btnFavorite.contentDescription = getString(
                        if (fav) R.string.favorite else R.string.add_to_favorites
                    )
                }
            }
        }
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.history.collect { points ->
                    val prices = points.mapNotNull { it.price.toFloatOrNull() }
                    binding.chart.setValues(prices)
                    if (prices.isNotEmpty()) {
                        binding.tvMin.text = getString(R.string.min_price, prices.min())
                        binding.tvMax.text = getString(R.string.max_price, prices.max())
                    }
                }
            }
        }
    }

    /**
     * Karşılaştırma bölümü yalnızca gerçekten karşılaştırılacak bir şey varsa
     * görünür: tek sonuç dönen bir listede çubuk göstermenin anlamı yok.
     */
    private fun observeComparison() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.comparison.collect { items ->
                    val showable = items.size >= 2
                    binding.tvCompareTitle.visibility = if (showable) View.VISIBLE else View.GONE
                    binding.compareCard.visibility = if (showable) View.VISIBLE else View.GONE
                    if (showable) compareAdapter.submit(items)

                    bindSaving(items)
                }
            }
        }
    }

    /** "En pahalı alternatife göre ne kadar kârdayım" — fiyatın hemen yanında. */
    private fun bindSaving(items: List<Item>) {
        val highest = items
            .map { it.priceValue }
            .filter { it != Double.MAX_VALUE }
            .maxOrNull()

        val saving = if (highest != null) highest - item.priceValue else 0.0
        if (highest == null || item.priceValue == Double.MAX_VALUE || saving <= 0.0) {
            binding.tvSaving.visibility = View.GONE
            return
        }
        binding.tvSaving.text = getString(R.string.cheaper_by, saving)
        binding.tvSaving.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.compareList.adapter = null
        _binding = null
    }
}
