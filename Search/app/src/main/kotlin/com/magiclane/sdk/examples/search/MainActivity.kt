/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.search

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.search.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.search.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// Thin UI layer: binds views, reacts to SDK lifecycle events, and delegates
// search logic to SearchViewModel.
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private var isProgrammaticQuery = false

    private companion object {
        private const val REQUEST_PERMISSIONS = 110

        val searchDiffCallback = object : DiffUtil.ItemCallback<SearchViewModel.SearchItem>() {
            override fun areItemsTheSame(
                oldItem: SearchViewModel.SearchItem,
                newItem: SearchViewModel.SearchItem,
            ): Boolean = false

            override fun areContentsTheSame(
                oldItem: SearchViewModel.SearchItem,
                newItem: SearchViewModel.SearchItem,
            ): Boolean = false
        }

        val categoryDiffCallback = object : DiffUtil.ItemCallback<SearchViewModel.CategoryItem>() {
            override fun areItemsTheSame(
                oldItem: SearchViewModel.CategoryItem,
                newItem: SearchViewModel.CategoryItem,
            ): Boolean = oldItem.categoryId == newItem.categoryId

            override fun areContentsTheSame(
                oldItem: SearchViewModel.CategoryItem,
                newItem: SearchViewModel.CategoryItem,
            ): Boolean = oldItem == newItem
        }
    }

    // Handles the async result of SDK token verification.
    private val checkAuthorizationListener = ProgressListener.create(
        onCompleted = { errorCode, _ ->
            if (errorCode != GemError.NoError) showInvalidTokenDialog()
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val imageSize = resources.getDimensionPixelSize(R.dimen.list_image_size)
        val iconSize = resources.getDimensionPixelSize(R.dimen.category_icon_size)
        viewModel.initialize(imageSize, iconSize)

        // Keep the idling resource busy until the SDK map data is ready.
        EspressoIdlingResource.increment()

        binding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(DividerItemDecoration(applicationContext, LinearLayoutManager.VERTICAL))
            searchAdapter = SearchAdapter()
            adapter = searchAdapter
            itemAnimator = null
        }

        binding.categoriesView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            categoryAdapter = CategoryAdapter()
            adapter = categoryAdapter
            itemAnimator = null
        }

        setSupportActionBar(binding.toolbar)

        binding.searchInput.apply {
            // Remove the default SearchView background so the field's own rounded drawable shows.
            findViewById<View>(androidx.appcompat.R.id.search_plate)?.background = null
            // Apply dark text/hint colours to match the light grey field design.
            findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.apply {
                setTextColor(ContextCompat.getColor(context, R.color.search_dark_gray))
                setHintTextColor(ContextCompat.getColor(context, R.color.search_dark_gray))
            }

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    if (!isProgrammaticQuery) viewModel.search((newText ?: "").trim())
                    return true
                }
            })
        }

        viewModel.results.observe(this) { items ->
            searchAdapter.submitList(items)
            binding.listView.smoothScrollToPosition(0)
            val query = binding.searchInput.query.toString().trim()
            binding.noResultText.isVisible = items.isEmpty() && (query.isNotBlank() || viewModel.selectedCategory.value != SearchViewModel.NO_CATEGORY)
        }

        viewModel.isSearching.observe(this) { searching ->
            binding.searchProgressBar.isInvisible = !searching
        }

        viewModel.categories.observe(this) { items ->
            categoryAdapter.submitList(items)
        }

        viewModel.selectedCategory.observe(this) { selectedIndex ->
            categoryAdapter.setSelectedIndex(selectedIndex)
            if (selectedIndex != SearchViewModel.NO_CATEGORY) {
                isProgrammaticQuery = true
                val name = viewModel.categories.value?.getOrNull(selectedIndex)?.name ?: ""
                binding.searchInput.setQuery(name, false)
                isProgrammaticQuery = false
            }
        }

        val initResult = GemSdk.initSdkWithDefaults(this)
        if (initResult != GemError.NoError) {
            showDialog(
                message = getString(
                    R.string.sdk_initialization_failed,
                    SdkCall.runSynced {
                        GemError.getMessage(initResult, this)
                    },
                ),
            ) { finish() }
            return
        }

        requestPermissions()

        if (!Util.isInternetConnected(this)) {
            runOnAliveUi { showDialog(message = getString(R.string.internet_required)) }
        }

        registerSdkListeners()
    }

    override fun onDestroy() {
        clearSdkListeners()
        GemSdk.release()
        super.onDestroy()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        // Self-clearing listener: fires once when the SDK map data is ready, then removes itself.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                runOnAliveUi {
                    binding.progressBar.visibility = View.GONE
                    binding.searchInput.isEnabled = true
                    binding.searchInput.requestFocus()
                    viewModel.loadCategories()
                    EspressoIdlingResource.decrement()
                }
            }
        }

        SdkSettings.onApiTokenRejected = { showInvalidTokenDialog() }

        // Verify the app token on the first successful internet connection.
        // Self-clearing so it fires only once per session.
        SdkSettings.onConnectionStatusUpdated = { isConnected ->
            if (isConnected) {
                SdkSettings.appAuthorization?.let {
                    SdkCall.execute { SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener) }
                } ?: showInvalidTokenDialog()
                SdkSettings.onConnectionStatusUpdated = {}
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        SdkSettings.onConnectionStatusUpdated = {}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            PermissionsHelper.instance?.notifyOnPermissionsStatusChanged()
        }
    }

    private fun requestPermissions(): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        return PermissionsHelper.requestPermissions(REQUEST_PERMISSIONS, this, permissions.toTypedArray())
    }

    private fun showDialog(
        title: String = getString(R.string.error),
        message: String,
        onDismiss: (() -> Unit)? = null,
    ) {
        if (!isActivityAlive()) return

        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            this.title.text = title
            this.message.text = message
            button.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun showInvalidTokenDialog() {
        runOnAliveUi { showDialog(message = getString(R.string.invalid_token)) { finish() } }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    inner class SearchAdapter :
        ListAdapter<SearchViewModel.SearchItem, SearchAdapter.SearchViewHolder>(searchDiffCallback) {

        inner class SearchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.text)
            val descriptionView: TextView = view.findViewById(R.id.description)
            val imageView: ImageView = view.findViewById(R.id.image)
            val distanceTextView: TextView = view.findViewById(R.id.status_text)
            val unitTextView: TextView = view.findViewById(R.id.status_description)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): SearchViewHolder {
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item, viewGroup, false)
            return SearchViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: SearchViewHolder, position: Int) {
            val item = getItem(position)
            with(viewHolder) {
                SdkCall.execute {
                    textView.text = item.name
                    descriptionView.text = item.description
                    imageView.setImageBitmap(item.image)
                    distanceTextView.text = item.distance
                    unitTextView.text = item.unit
                }
            }
        }
    }

    inner class CategoryAdapter :
        ListAdapter<SearchViewModel.CategoryItem, CategoryAdapter.CategoryViewHolder>(categoryDiffCallback) {

        private var selectedIndex: Int = SearchViewModel.NO_CATEGORY

        fun setSelectedIndex(index: Int) {
            val previous = selectedIndex
            selectedIndex = index
            if (previous != SearchViewModel.NO_CATEGORY) notifyItemChanged(previous)
            if (index != SearchViewModel.NO_CATEGORY) notifyItemChanged(index)
        }

        inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val container: View = view.findViewById(R.id.category_container)
            val icon: ImageView = view.findViewById(R.id.category_icon)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): CategoryViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item_category, viewGroup, false)
            return CategoryViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: CategoryViewHolder, position: Int) {
            val item = getItem(position)
            val isSelected = position == selectedIndex

            viewHolder.icon.setImageBitmap(item.icon)

            if (isSelected) {
                viewHolder.container.setBackgroundResource(R.drawable.rounded_background_primary)
            } else {
                viewHolder.container.background = null
            }

            viewHolder.container.setOnClickListener {
                viewModel.selectCategory(viewHolder.bindingAdapterPosition)
            }
        }
    }
}
