package io.legado.app.ui.welcome

import android.os.Bundle
import android.view.View
import com.drducbook.app.R
import io.legado.app.base.BaseFragment
import com.drducbook.app.databinding.FragmentPrivacyBinding
import io.legado.app.utils.viewbindingdelegate.viewBinding

class PrivacyFragment : BaseFragment(R.layout.fragment_privacy) {

    private val binding by viewBinding(FragmentPrivacyBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {

        binding.tvPrivacy.text =
            String(requireContext().assets.open(welcomeAssetName("privacyPolicy.md")).readBytes())
        binding.tvDisclaimer.text =
            String(requireContext().assets.open(welcomeAssetName("disclaimer.md")).readBytes())

    }

    private fun welcomeAssetName(defaultName: String): String {
        val language = requireContext().resources.configuration.locales[0].language
        return if (language == "vi") {
            defaultName.removeSuffix(".md") + "_vi.md"
        } else {
            defaultName
        }
    }

}
