package com.gowtham.videotrimmer

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cocosw.bottomsheet.BottomSheet
import com.gowtham.library.utils.CompressOption
import com.gowtham.library.utils.LogMessage
import com.gowtham.library.utils.TrimType
import com.gowtham.library.utils.TrimVideo
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var videoView: VideoView? = null
    private var mediaController: MediaController? = null
    private var edtFixedGap: EditText? = null
    private var edtMinGap: EditText? = null
    private var edtMinFrom: EditText? = null
    private var edtMAxTo: EditText? = null
    private var trimType = 0
    private var videoTrimResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK &&
            result.data != null
        ) {
            val uri = Uri.parse(TrimVideo.getTrimmedVideoPath(result.data))
            Log.d(TAG, "Trimmed path:: $uri")
            videoView?.setMediaController(mediaController)
            videoView?.setVideoURI(uri)
            videoView?.requestFocus()
            videoView?.start()
            videoView?.setOnPreparedListener { _: MediaPlayer? ->
                mediaController?.setAnchorView(videoView)
            }
            val filepath = uri.toString()
            val file = File(filepath)
            val length = file.length()
            Log.d(TAG, "Video size:: " + length / 1024)
        } else LogMessage.v("videoTrimResultLauncher data is null")
    }
    private var takeOrSelectVideoResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            if( result.data != null) {
                val data = result.data
                //check video duration if needed
                /*        if (TrimmerUtils.getDuration(this,data.getData())<=30){
                    Toast.makeText(this,"Video should be larger than 30 sec",Toast.LENGTH_SHORT).show();
                    return;
                }*/if (data!!.data != null) {
                    LogMessage.v("Video path:: " + data.data)
                    openTrimActivity(data.data.toString())
                } else {
                    Toast.makeText(this, "video uri is null", Toast.LENGTH_SHORT).show()
                }
            } else {
                 LogMessage.v("takeVideoResultLauncher data is null")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        videoView = findViewById(R.id.video_view)
        edtFixedGap = findViewById(R.id.edt_fixed_gap)
        edtMinGap = findViewById(R.id.edt_min_gap)
        edtMinFrom = findViewById(R.id.edt_min_from)
        edtMAxTo = findViewById(R.id.edt_max_to)
        mediaController = MediaController(this)
        findViewById<View>(R.id.btn_default_trim).setOnClickListener(this)
        findViewById<View>(R.id.btn_fixed_gap).setOnClickListener(this)
        findViewById<View>(R.id.btn_min_gap).setOnClickListener(this)
        findViewById<View>(R.id.btn_min_max_gap).setOnClickListener(this)
    }

    private fun openTrimActivity(data: String) {
        when (trimType) {
            0 -> {
                TrimVideo.activity(data)
                    .setCompressOption(CompressOption()) //pass empty constructor for default compress option
                    .setTrimType(TrimType.MIN_MAX_DURATION)
                    .setMinToMax(3, 60)
                    .setTitle("Test")
                    .start(this, videoTrimResultLauncher)
            }
            1 -> {
                TrimVideo.activity(data)
                    .setTrimType(TrimType.FIXED_DURATION)
                    .setFixedDuration(getEdtValueLong(edtFixedGap))
                    .setLocal("en")
                    .start(this, videoTrimResultLauncher)
            }
            2 -> {
                TrimVideo.activity(data)
                    .setTrimType(TrimType.MIN_DURATION)
                    .setLocal("en")
                    .setMinDuration(getEdtValueLong(edtMinGap))
                    .start(this, videoTrimResultLauncher)
            }
            else -> {
                TrimVideo.activity(data)
                    .setTrimType(TrimType.MIN_MAX_DURATION)
                    .setLocal("en")
                    .setMinToMax(getEdtValueLong(edtMinFrom), getEdtValueLong(edtMAxTo))
                    .start(this, videoTrimResultLauncher)
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_default_trim -> onDefaultTrimClicked()
            R.id.btn_fixed_gap -> onFixedTrimClicked()
            R.id.btn_min_gap -> onMinGapTrimClicked()
            R.id.btn_min_max_gap -> onMinToMaxTrimClicked()
        }
    }

    private fun onDefaultTrimClicked() {
        trimType = 0
        checkPermission()
    }

    private fun onFixedTrimClicked() {
        trimType = 1
        if (isEdtTxtEmpty(edtFixedGap)) Toast.makeText(
            this,
            "Enter fixed gap duration",
            Toast.LENGTH_SHORT
        ).show() else {
            checkPermission()
        }
    }

    private fun onMinGapTrimClicked() {
        trimType = 2
        if (isEdtTxtEmpty(edtMinGap)) Toast.makeText(
            this,
            "Enter min gap duration",
            Toast.LENGTH_SHORT
        ).show() else {
            checkPermission()
        }
    }

    private fun onMinToMaxTrimClicked() {
        trimType = 3
        if (isEdtTxtEmpty(edtMinFrom)) Toast.makeText(
            this,
            "Enter min gap duration",
            Toast.LENGTH_SHORT
        ).show() else if (isEdtTxtEmpty(edtMAxTo)) Toast.makeText(
            this,
            "Enter max gap duration",
            Toast.LENGTH_SHORT
        ).show() else {
            checkPermission()
        }
    }

    private fun showVideoOptions() {
        try {
            val builder = bottomSheet
            builder.sheet(R.menu.menu_video)
            builder.listener { item: MenuItem ->
                if (R.id.action_take == item.itemId) captureVideo() else openVideo()
                false
            }
            builder.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val bottomSheet: BottomSheet.Builder
        get() = BottomSheet.Builder(this).title(R.string.txt_option)

    private fun captureVideo() {
        try {
            val intent = Intent("android.media.action.VIDEO_CAPTURE")
            intent.putExtra("android.intent.extra.durationLimit", 30)
            takeOrSelectVideoResultLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openVideo() {
        try {
            val intent = Intent()
            intent.type = "video/*"
            intent.action = Intent.ACTION_GET_CONTENT
            takeOrSelectVideoResultLauncher.launch(Intent.createChooser(intent, "Select Video"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isEdtTxtEmpty(editText: EditText?): Boolean {
        return editText?.text.toString().trim { it <= ' ' }.isEmpty()
    }

    private fun getEdtValueLong(editText: EditText?): Long {
        return editText?.text.toString().trim { it <= ' ' }.toLong()
    }


    private val videoPermissions by lazy {
        arrayOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Manifest.permission.ACCESS_MEDIA_LOCATION
            } else {
                ""
            }
        )
    }

    private fun checkPermission() {
        RxPermissions(this)
            .request(*(videoPermissions.filter { it.isNotEmpty() }).toTypedArray())
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({
                if (it) {
                    showVideoOptions()
                }
            }, {

            })?.let {

            }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}