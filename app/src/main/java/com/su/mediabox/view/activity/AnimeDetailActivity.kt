package com.su.mediabox.view.activity

import android.content.res.Configuration
import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.su.mediabox.R
import com.su.mediabox.bean.FavoriteAnimeBean
import com.su.mediabox.config.Api
import com.su.mediabox.database.getAppDataBase
import com.su.mediabox.databinding.ActivityAnimeDetailBinding
import com.su.mediabox.util.Util.getSkinResourceId
import com.su.mediabox.util.Util.setTransparentStatusBar
import com.su.mediabox.util.showToast
import com.su.mediabox.util.coil.DarkBlurTransformation
import com.su.mediabox.util.smartNotifyDataSetChanged
import com.su.mediabox.view.adapter.AnimeDetailAdapter
import com.su.mediabox.view.adapter.decoration.AnimeShowItemDecoration
import com.su.mediabox.view.adapter.spansize.AnimeDetailSpanSize
import com.su.mediabox.view.fragment.ShareDialogFragment
import com.su.mediabox.viewmodel.AnimeDetailViewModel
import com.su.mediabox.plugin.Constant
import com.su.mediabox.util.coil.CoilUtil.loadImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL


class AnimeDetailActivity : BasePluginActivity<ActivityAnimeDetailBinding>() {
    private var partUrl: String = ""
    private var isFavorite: Boolean = false
    private lateinit var viewModel: AnimeDetailViewModel
    private lateinit var adapter: AnimeDetailAdapter
    override var statusBarSkin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTransparentStatusBar(window, isDark = false)

        viewModel = ViewModelProvider(this).get(AnimeDetailViewModel::class.java)
        adapter = AnimeDetailAdapter(this, viewModel.animeDetailList)

        partUrl = intent.getStringExtra("partUrl") ?: ""

        mBinding.atbAnimeDetailActivityToolbar.run {
            setBackButtonClickListener { finish() }
            // 分享
            setButtonClickListener(0) {
                ShareDialogFragment().setShareContent(Api.MAIN_URL + partUrl)
                    .show(supportFragmentManager, "share_dialog")
            }
            addButton(null)
            // 收藏
            lifecycleScope.launch(Dispatchers.IO) {
                val favoriteAnime = getAppDataBase().favoriteAnimeDao().getFavoriteAnime(partUrl)
                isFavorite = favoriteAnime != null
                withContext(Dispatchers.Main) {
                    setButtonDrawable(
                        1, if (isFavorite) R.drawable.ic_star_white_24_skin else
                            R.drawable.ic_star_border_white_24
                    )
                }
            }
            setButtonEnable(1, false)
            setButtonClickListener(1) {
                lifecycleScope.launch(Dispatchers.IO) {
                    if (isFavorite) {
                        getAppDataBase().favoriteAnimeDao().deleteFavoriteAnime(partUrl)
                        withContext(Dispatchers.Main) {
                            isFavorite = false
                            setButtonDrawable(1, R.drawable.ic_star_border_white_24)
                            getString(R.string.remove_favorite_succeed).showToast()
                        }
                    } else {
                        getAppDataBase().favoriteAnimeDao().insertFavoriteAnime(
                            FavoriteAnimeBean(
                                Constant.ViewHolderTypeString.ANIME_COVER_8, "",
                                partUrl,
                                viewModel.title,
                                System.currentTimeMillis(),
                                viewModel.cover
                            )
                        )
                        withContext(Dispatchers.Main) {
                            isFavorite = true
                            setButtonDrawable(1, R.drawable.ic_star_white_24_skin)
                            getString(R.string.favorite_succeed).showToast()
                        }
                    }
                }
            }
        }

        mBinding.run {
            rvAnimeDetailActivityInfo.layoutManager = GridLayoutManager(this@AnimeDetailActivity, 4)
                .apply { spanSizeLookup = AnimeDetailSpanSize(adapter) }
            // 复用AnimeShow的ItemDecoration
            rvAnimeDetailActivityInfo.addItemDecoration(AnimeShowItemDecoration())
            rvAnimeDetailActivityInfo.adapter = adapter

            srlAnimeDetailActivity.setOnRefreshListener { viewModel.getAnimeDetailData(partUrl) }
            srlAnimeDetailActivity.setColorSchemeResources(getSkinResourceId(R.color.main_color_skin))
        }

        viewModel.mldAnimeDetailList.observe(this, Observer {
            mBinding.srlAnimeDetailActivity.isRefreshing = false
            adapter.smartNotifyDataSetChanged(it.first, it.second, viewModel.animeDetailList)
            mBinding.atbAnimeDetailActivityToolbar.setButtonEnable(1, true)

            if (viewModel.cover.url.isBlank()) return@Observer
            mBinding.ivAnimeDetailActivityBackground.loadImage(viewModel.cover.url) {
                transformations(DarkBlurTransformation(this@AnimeDetailActivity))
                addHeader("Referer", viewModel.cover.referer)
                addHeader("Host", URL(viewModel.cover.url).host)
                addHeader("Accept", "*/*")
                addHeader("Accept-Encoding", "gzip, deflate")
                addHeader("Connection", "keep-alive")
                addHeader("User-Agent", Constant.Request.getRandomUserAgent())
            }
            mBinding.atbAnimeDetailActivityToolbar.titleText = viewModel.title
        })

        mBinding.srlAnimeDetailActivity.isRefreshing = true
        viewModel.getAnimeDetailData(partUrl)
    }

    override fun getBinding(): ActivityAnimeDetailBinding =
        ActivityAnimeDetailBinding.inflate(layoutInflater)

    fun getPartUrl(): String = partUrl

    override fun onChangeSkin() {
        super.onChangeSkin()
        adapter.notifyDataSetChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adapter.notifyDataSetChanged()
    }
}