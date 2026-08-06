package com.marketrehberim.ui.view.main

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
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

    /** Rozet animasyonu yalnız artışta oynasın diye önceki sayı tutuluyor. */
    private var lastBasketCount = 0

    /**
     * Splash, ikon animasyonu (~860 ms) bitene kadar ekranda tutulur. Uygulama
     * ilk kareyi çok hızlı çizdiği için splash animasyonun ortasında kapanıyor,
     * "dükkân açılıyor" anlatısı yarıda kesiliyordu.
     */
    private var holdSplash = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { holdSplash }
        lifecycleScope.launch {
            kotlinx.coroutines.delay(900L)
            holdSplash = false
        }
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
        animateTabSwitches()

        observeBasketBadge()
    }

    /**
     * Sekme geçişlerine "fade through" animasyonu.
     *
     * `setupWithNavController` geçişleri animasyonsuz yapar ve kendi tıklama
     * dinleyicisini kurar; burada o dinleyici, **aynı** gezinme davranışını
     * (yığını başlangıç hedefine kadar durum saklayarak boşalt + durumu geri
     * yükle) koruyan ama animasyon da veren bir kopyayla değiştirilir.
     * `setupWithNavController` çağrısı yine de gerekli: hedef değişince seçili
     * sekmeyi güncelleyen dinleyicisi ayrı yaşar ve burada bozulmaz.
     */
    private fun animateTabSwitches() {
        val navController = navHostFragment.navController
        bottomNavigationView.setOnItemSelectedListener { item ->
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(
                    navController.graph.findStartDestination().id,
                    /* inclusive = */ false,
                    /* saveState = */ true,
                )
                .setEnterAnim(R.anim.nav_tab_enter)
                .setExitAnim(R.anim.nav_tab_exit)
                .setPopEnterAnim(R.anim.nav_tab_enter)
                .setPopExitAnim(R.anim.nav_tab_exit)
                .build()
            try {
                navController.navigate(item.itemId, null, options)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }
        // Zaten açık sekmeye dokununca yeniden gezinme (ve animasyon) olmasın.
        bottomNavigationView.setOnItemReselectedListener { }
    }

    /** Sepet sekmesindeki sayaç. Ürün eklendiğinde tek geri bildirim buydu, yoktu. */
    private fun observeBasketBadge() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                basketStore.items.collect { items ->
                    val badge = bottomNavigationView.getOrCreateBadge(R.id.basketFragment)
                    // Rozet için standart Material kırmızısı: secondary artık
                    // sakin bir yüzey tonu, sayaç onunla görünmez olurdu.
                    badge.backgroundColor = MaterialColors.getColor(
                        bottomNavigationView,
                        com.google.android.material.R.attr.colorError,
                    )
                    badge.badgeTextColor = MaterialColors.getColor(
                        bottomNavigationView,
                        com.google.android.material.R.attr.colorOnError,
                    )
                    badge.isVisible = items.isNotEmpty()
                    badge.number = items.size

                    // Kullanıcı ürünü detay ekranından ekliyor; rozet ekranın öbür
                    // ucunda sessizce artıyordu. Sayı **arttığında** zıplasın:
                    // silmede ya da ekran ilk kurulduğunda oynatmak gürültü olur.
                    if (items.size > lastBasketCount) bounceBasketBadge()
                    lastBasketCount = items.size
                }
            }
        }
    }

    private fun bounceBasketBadge() {
        val icon = bottomNavigationView.findViewById<View>(R.id.basketFragment) ?: return
        AnimatorSet().apply {
            duration = 220L
            interpolator = OvershootInterpolator(3f)
            playTogether(
                ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.25f, 1f),
                ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.25f, 1f),
            )
            start()
        }
    }
}