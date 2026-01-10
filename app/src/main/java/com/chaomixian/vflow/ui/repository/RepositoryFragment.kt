// 文件: main/java/com/chaomixian/vflow/ui/repository/RepositoryFragment.kt
package com.chaomixian.vflow.ui.repository

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.chaomixian.vflow.databinding.FragmentRepositoryBinding
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 仓库Fragment
 * 包含两个Tab：工作流和模块
 */
class RepositoryFragment : Fragment() {

    private var _binding: FragmentRepositoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var repositoryPagerAdapter: RepositoryPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepositoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置ViewPager和TabLayout
        repositoryPagerAdapter = RepositoryPagerAdapter(this)
        binding.viewPagerRepository.adapter = repositoryPagerAdapter

        TabLayoutMediator(binding.tabsRepository, binding.viewPagerRepository) { tab, position ->
            tab.text = when (position) {
                0 -> "工作流"
                1 -> "模块"
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = RepositoryFragment()
    }
}
