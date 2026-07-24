package com.marketrehberim.ui.view.main

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.marketrehberim.R
import com.marketrehberim.data.local.BasketStore
import com.marketrehberim.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var navHostFragment: NavHostFragment
    private lateinit var bottomNavigationView: BottomNavigationView

    /** Sepet rozeti için: listeye ürün eklendiğinde alt gezinme geri bildirim verir. */
    @Inject
    lateinit var basketStore: BasketStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setupSplashExit(splashScreen)
        bindingCodes()
        defaultActivityCodes()
        bindViews()
    }

    /** Splash'tan uygulamaya yumuşak geçiş: ikon yukarı süzülüp büyürken ekran solar. */
    private fun setupSplashExit(splashScreen: androidx.core.splashscreen.SplashScreen) {
        splashScreen.setOnExitAnimationListener { provider ->
            val iconView = provider.iconView
            val slideUp = ObjectAnimator.ofFloat(iconView, View.TRANSLATION_Y, 0f, -80f)
            val scaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.4f)
            val scaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.4f)
            val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
            AnimatorSet().apply {
                duration = 450L
                interpolator = AnticipateInterpolator()
                playTogether(slideUp, scaleX, scaleY, fade)
                doOnEnd { provider.remove() }
                start()
            }
        }
    }

    private fun bindingCodes() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun defaultActivityCodes() {
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun bindViews() {
        navHostFragment = supportFragmentManager.findFragmentById(
            binding.fragmentContainerView.id
        ) as NavHostFragment

        bottomNavigationView = binding.bottomNavigationView
        bottomNavigationView.setupWithNavController(navHostFragment.navController)

        observeBasketBadge()
    }

    /** Sepet sekmesindeki sayaç. Ürün eklendiğinde tek geri bildirim buydu, yoktu. */
    private fun observeBasketBadge() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                basketStore.items.collect { items ->
                    val badge = bottomNavigationView.getOrCreateBadge(R.id.basketFragment)
                    badge.backgroundColor = MaterialColors.getColor(
                        bottomNavigationView,
                        com.google.android.material.R.attr.colorSecondary,
                    )
                    badge.badgeTextColor = MaterialColors.getColor(
                        bottomNavigationView,
                        com.google.android.material.R.attr.colorOnSecondary,
                    )
                    badge.isVisible = items.isNotEmpty()
                    badge.number = items.size
                }
            }
        }
    }
}