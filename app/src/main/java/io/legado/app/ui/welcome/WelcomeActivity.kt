package io.legado.app.ui.welcome


import android.os.Bundle
import androidx.activity.addCallback
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.drducbook.app.R
import io.legado.app.base.BaseActivity
import com.drducbook.app.databinding.ActivityWelcomeBinding
import io.legado.app.help.config.LocalConfig
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding

class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    override val binding by viewBinding(ActivityWelcomeBinding::inflate)

    private val pages = listOf(
        PrivacyFragment(),
        WebDavFragment(),
        BookFolderFragment(),
        ThemeFragment()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            val current = binding.viewPager.currentItem
            if (current > 0) {
                binding.viewPager.currentItem = current - 1
            } else {
                finish()
            }
        }

        updateProgress(0)

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = pages.size
            override fun createFragment(position: Int) = pages[position]
        }

        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.btnNext.text = when (position) {
                    0 -> getString(R.string.welcome_agree) // PrivacyFragment
                    pages.lastIndex -> getString(R.string.welcome_finish)
                    else -> getString(R.string.next)
                }
                binding.tvTitle.text = when (position) {
                    0 -> getString(R.string.welcome_title) // PrivacyFragment
                    1 -> getString(R.string.backup_restore)
                    2 -> getString(R.string.book_tree_uri_t)
                    else -> getString(R.string.theme_setting)
                }
                binding.tvSummary.text = when (position) {
                    0 -> getString(R.string.first_read)
                    1 -> getString(R.string.welcome_backup_summary)
                    2 -> getString(R.string.welcome_book_folder_tip)
                    else -> getString(R.string.welcome_theme_summary)
                }
                updateProgress(position)
            }
        })

        // 初始化按钮文字
        binding.btnNext.text = getString(R.string.welcome_agree)

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            when (current) {
                0 -> {
                    LocalConfig.privacyPolicyOk = true
                    binding.viewPager.currentItem = 1
                }

                pages.lastIndex -> {
                    updateProgress(2)
                    finishSetup()
                }

                else -> {
                    binding.viewPager.currentItem = current + 1
                }
            }
        }
    }

    private fun updateProgress(position: Int) {
        val progressMax = pages.size
        val progress = (position * 100 / progressMax)
        binding.progressBar.setProgress(progress, true)
    }

    private fun finishSetup() {
        startActivity(MainActivity.createHomeIntent(this))
        finish()
    }
}
