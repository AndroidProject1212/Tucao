package me.sweetll.tucao.business.video.viewmodel

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.listener.OnItemClickListener
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.download.model.Part
import me.sweetll.tucao.business.video.VideoActivity
import me.sweetll.tucao.business.video.adapter.DownloadPartAdapter
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.extension.*
import me.sweetll.tucao.model.json.Result
import me.sweetll.tucao.model.xml.Durl
import me.sweetll.tucao.widget.CustomBottomSheetDialog
import zlc.season.rxdownload2.entity.DownloadFlag
import java.io.File
import java.io.FileOutputStream

class VideoViewModel(val activity: VideoActivity): BaseViewModel() {
    val result = ObservableField<Result>()

    var playUrlDisposable: Disposable? = null
    var danmuDisposable: Disposable? = null

    var currentPlayerId: String? = null

    constructor(activity: VideoActivity, result: Result) : this(activity) {
        this.result.set(result)
    }

    fun queryResult(hid: String) {
        jsonApiService.view(hid)
                .bindToLifecycle(activity)
                .sanitizeJson()
                .subscribe({
                    result ->
                    this.result.set(result)
                    activity.loadResult(result)
                }, {
                    error ->
                    error.printStackTrace()
                    activity.binding.player.loadText?.let {
                        it.text = it.text.replace("获取视频信息...".toRegex(), "获取视频信息...[失败]")
                    }
                })
    }

    fun queryPlayUrls(hid: String, part: Part) {
        if (playUrlDisposable != null && !playUrlDisposable!!.isDisposed) {
            playUrlDisposable!!.dispose()
        }
        if (danmuDisposable != null && !danmuDisposable!!.isDisposed) {
            danmuDisposable!!.dispose()
        }

        if (part.flag == DownloadFlag.COMPLETED) {
            activity.loadDuals(part.durls)
        } else if (part.vid.startsWith(hid)) {
            // 这个视频是直传的
            activity.loadDuals(part.durls)
        } else {
            playUrlDisposable = xmlApiService.playUrl(part.type, part.vid, System.currentTimeMillis() / 1000)
                    .bindToLifecycle(activity)
                    .subscribeOn(Schedulers.io())
                    .flatMap {
                        response ->
                        if (response.durls.isNotEmpty()) {
                            Observable.just(response.durls)
                        } else {
                            Observable.error(Throwable("请求视频接口出错"))
                        }
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        duals ->
                        activity.loadDuals(duals)
                    }, {
                        error ->
                        error.printStackTrace()
                        activity.binding.player.loadText?.let {
                            it.text = it.text.replace("解析视频地址...".toRegex(), "解析视频地址...[失败]")
                        }
                    })
        }

        currentPlayerId = ApiConfig.generatePlayerId(hid, part.order)
        danmuDisposable = rawApiService.danmu(currentPlayerId!!, System.currentTimeMillis() / 1000)
                .bindToLifecycle(activity)
                .subscribeOn(Schedulers.io())
                .map({
                    responseBody ->
                    val outputFile = File.createTempFile("tucao", ".xml", AppApplication.get().cacheDir)
                    val outputStream = FileOutputStream(outputFile)

                    outputStream.write(responseBody.bytes())
                    outputStream.flush()
                    outputStream.close()
                    outputFile.absolutePath
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    uri ->
                    activity.loadDanmuUri(uri)
                }, {
                    error ->
                    error.printStackTrace()
                    activity.binding.player.loadText?.let {
                        it.text = it.text.replace("全舰弹幕装填...".toRegex(), "全舰弹幕装填...[失败]")
                    }
                })
    }

    fun sendDanmu(stime: Float, message: String) {
        currentPlayerId?.let {
            rawApiService.sendDanmu(it, it, stime, message)
                    .bindToLifecycle(activity)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        // 发送成功
                    }, Throwable::printStackTrace)
        }
    }
}
