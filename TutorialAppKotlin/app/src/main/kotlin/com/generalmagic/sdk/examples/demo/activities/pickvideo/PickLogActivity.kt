package com.generalmagic.sdk.examples.demo.activities.pickvideo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.sdk.core.LogUploaderListener
import com.generalmagic.sdk.core.SettingsService
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.app.BaseActivity
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.util.Util
import com.generalmagic.sdk.examples.demo.util.Util.getFileLastModifiedDate
import com.generalmagic.sdk.examples.demo.util.Util.getFileSize
import com.generalmagic.sdk.examples.demo.util.UtilUITexts.formatSizeAsText
import com.generalmagic.sdk.sensordatasource.RecorderBookmarks
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util.exportVideo
import kotlinx.android.synthetic.main.upload_view.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class UploadLogActivity : BaseActivity() {
    private lateinit var mData: UploadViewData
    private var mName: EditText? = null
    private var mEmail: EditText? = null
    private var mIssueDescription: EditText? = null

    companion object {
        const val VIDEO_PATH = "RESULT_VIDEO_PATH"
        const val UPLOAD_SAVED_EMAIL = "UPLOAD_SAVED_EMAIL"
    }

    class UploadViewData(val context: Context, inputFilepath: String) {
        private var mService: SettingsService? = null

        private var mUploadString: String? = null
        private var mTitle: String? = null
        private var mInfo: String? = null
        private var mNameString: String? = null
        private var mName: String? = null
        private var mNameHint: String? = null
        private var mEmailString: String? = null
        private var mEmail: String? = null
        private var mEmailHint: String? = null
        private var mEmailExplanationString: String? = null
        private var mIssueDescriptionString: String? = null
        private var mIssueDescriptionHint: String? = null
        private var mIssueDescription: String? = null

        init {
            SdkCall.execute { mService = SettingsService.produce("Settings.ini") }

            mEmail = mService?.getStringValue(UPLOAD_SAVED_EMAIL, "")

            val theDate = getFileLastModifiedDate(inputFilepath)

            val datePattern = "yyyy-MM-dd"
            val dateFormat = SimpleDateFormat(datePattern, Locale.getDefault())
            val dateText = dateFormat.format(theDate)

            val timePattern = "HH:mm:ss"
            val timeFormat = SimpleDateFormat(timePattern, Locale.getDefault())
            val timeText = timeFormat.format(theDate)

            val sizeText = formatSizeAsText(getFileSize(inputFilepath))

            mInfo = String.format("VideoLog : $dateText ($timeText) (Size: $sizeText)")
        }

        fun getUploadString(): String = mUploadString ?: "Upload"
        fun getTitle(): String = mTitle ?: "Upload Video Log"
        fun getUploadInfo(): String = mInfo ?: "Video Log: 1970-01-01 (00:00:00) (Size: 0 B)"
        fun getNameString(): String = mNameString ?: "Name"
        fun getName(): String = mName ?: ""
        fun getNameHint(): String = mNameHint ?: "Enter your name (optional)"
        fun getEmailString(): String = mEmailString ?: "Email"
        fun getEmail(): String = mEmail ?: ""
        fun getEmailHint(): String = mEmailHint ?: "Enter your email (mandatory)"
        fun getEmailExplanationString(): String = mEmailExplanationString
            ?: "Your email is needed to contact you about the reported problem."

        fun getIssueDescriptionString(): String = mIssueDescriptionString ?: ""
        fun getIssueDescription(): String = mIssueDescription ?: ""
        fun getIssueDescriptionHint(): String =
            mIssueDescriptionHint ?: "Enter a brief description of the problem(optional)"

        fun didPushUploadButton(
            filepath: String, username: String, email: String, details: String
        ) {
            if (email != mEmail) {
                mEmail = email
                mService?.setStringValue(UPLOAD_SAVED_EMAIL, email)
            }

            val resultCode = GEMApplication.uploadLog(filepath, username, email, details)
            val error = SdkError.fromInt(resultCode)

            if (error != SdkError.NoError) {
                Toast.makeText(context, "uploadAtIndex error=$error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.upload_view)

        val inputFilepath = intent.getStringExtra(VIDEO_PATH) ?: ""

        this.mData = UploadViewData(this, inputFilepath)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = mData.getTitle()

        upload_button?.let {
            it.text = mData.getUploadString()
            it.setOnClickListener {
                if (mName != null && mEmail != null && mIssueDescription != null) {
                    mData.didPushUploadButton(
                        inputFilepath,
                        mName?.text.toString(),
                        mEmail?.text.toString(),
                        mIssueDescription?.text.toString()
                    )

                    finish()
                }
            }
        }

        upload_info?.text = mData.getUploadInfo()
        upload_name_string?.text = mData.getNameString()

        mName = upload_name
        mName?.let {
            it.setText(mData.getName())
            it.hint = mData.getNameHint()
        }

        upload_email_string?.text = mData.getEmailString()

        mEmail = upload_email
        mEmail?.let {
            it.setText(mData.getEmail())
            it.hint = mData.getEmailHint()
        }

        upload_email_explanation?.text = mData.getEmailExplanationString()

        upload_issue_description_string?.text = mData.getIssueDescriptionString()

        mIssueDescription = upload_issue_description
        mIssueDescription?.let {
            it.hint = mData.getIssueDescriptionHint()
            it.setText(mData.getIssueDescription())
        }
    }
}

class PickLogActivity : BaseActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    companion object {
        const val CODE_PERMISSIONS = 100
        const val CODE_RESULT_SELECT_VIDEO = 100
        const val RESULT_VIDEO_PATH = "RESULT_VIDEO_PATH"
        const val INPUT_DIR = "INPUT_DIR"
    }

    data class VideoFileModel(
        var name: String = "",
        var filepath: String = ""
    )

    private var hasPermissions = false
    private var inputDirectories: ArrayList<String>? = null

    private val uploadListener = object : LogUploaderListener() {
        override fun onLogStatusChanged(sLogPath: String, nProgress: Int, nStatus: Int) {

        }
    }

    // //////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_video)

        inputDirectories = intent.getStringArrayListExtra(INPUT_DIR)

        recyclerView = findViewById(R.id.list_files)

        viewManager = LinearLayoutManager(this)
        recyclerView.layoutManager = viewManager

        val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
        recyclerView.setPadding(lateralPadding, 0, lateralPadding, 0)
        GEMApplication.addLogUploadListener(uploadListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        GEMApplication.removeLogUploadListener(uploadListener)
    }

    override fun onResume() {
        super.onResume()

        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        hasPermissions = GEMApplication.hasPermissions(this, permissions)

        if (!hasPermissions) {
            PermissionsHelper.requestPermissions(CODE_PERMISSIONS, this, permissions)
        }

        if (hasPermissions) {
            updateListAdapter(inputDirectories)
        }
    }

    private fun onVideoPicked(model: VideoFileModel) {
        if (!isValidModelPicked(model)) {
            return
        } // onVideoPickCanceled()

        val intent = Intent()
        intent.putExtra(RESULT_VIDEO_PATH, model.filepath)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun onVideoPickCanceled() {
        setResult(Activity.RESULT_CANCELED, Intent())
        finish()
    }

    override fun doBackPressed(): Boolean {
        onVideoPickCanceled()
        return true
    }

    override fun onRequestPermissionsFinish(granted: Boolean) {
        if (granted) {
            hasPermissions = true
            updateListAdapter(inputDirectories)
        } else {
            onVideoPickCanceled()
        }
    }

    //
    // Business code
    //
    private fun isProtectedLog(filepath: String): Boolean {
        val recordsPath = GEMApplication.getInternalRecordsPath()

        return SdkCall.execute {
            val bookmarks = RecorderBookmarks.produce(recordsPath) ?: return@execute false

            val fileMeta = bookmarks.getMetadata(filepath) ?: return@execute false
            return@execute fileMeta.isProtected()
        } ?: false
    }

    private fun protect(filepath: String, protect: Boolean) {
        val recordsPath = GEMApplication.getInternalRecordsPath()

        SdkCall.execute {
            val bookmarks = RecorderBookmarks.produce(recordsPath) ?: return@execute

            if (protect) {
                bookmarks.markLogProtected(filepath)
            }
        }
    }

    private fun upload(filepath: String) {
        val intent = Intent(this, UploadLogActivity::class.java)
        intent.putExtra(UploadLogActivity.VIDEO_PATH, filepath)

        startActivity(intent)
    }

    private fun export(filepath: String): File? {
        val videoFile = File(filepath)
        if (!videoFile.exists())
            return null

        val context = GEMApplication.applicationContext()

        return exportVideo(context, videoFile, GEMApplication.getPublicRecordsDir())
    }

    private fun updateListAdapter(inputPaths: ArrayList<String>?) {
        val dataModel = getVideoFileModels(inputPaths)

        viewAdapter = CustomAdapter(dataModel)
        recyclerView.adapter = viewAdapter
    }

    private fun isValidModelPicked(model: VideoFileModel): Boolean {
        if (model.filepath.isEmpty() || !File(model.filepath).exists()) {
            return false
        }

        return true
    }

    private fun getVideoFileModels(inputPaths: ArrayList<String>?): ArrayList<VideoFileModel> {
        val result = ArrayList<VideoFileModel>()
        val allVideoFiles = getAvailableFileLogs(inputPaths)

        // PROCESS FILES
        if (allVideoFiles.size > 0) {
            for (i in allVideoFiles.indices) {
                val path = allVideoFiles[i].path
                val changeDate = getFileLastModifiedDate(path)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

                val videoFileModel = VideoFileModel()
                videoFileModel.filepath = path
                videoFileModel.name = dateFormat.format(changeDate)

                result.add(videoFileModel)
            }
        }

        return result
    }

    private fun getAvailableFileLogs(inputPaths: ArrayList<String>?): ArrayList<File> {
        if (inputPaths == null) return ArrayList()
        if (inputPaths.isEmpty()) return ArrayList()
        if (!hasPermissions) return ArrayList()

        val result = ArrayList<File>()
        for (dirPath in inputPaths) {
            val moviesDir = File(dirPath)
            if (!moviesDir.exists() || !moviesDir.isDirectory)
                continue

            // OBTAIN ALL FILES
            val logs = ArrayList<File>()

            moviesDir.listFiles { _, name ->
                name.toLowerCase(Locale.getDefault()).endsWith(".mp4")
            }?.let {
                logs.addAll(it)
            }
            moviesDir.listFiles { _, name ->
                name.toLowerCase(Locale.getDefault()).endsWith(".mov")
            }?.let {
                logs.addAll(it)
            }
            moviesDir.listFiles { _, name ->
                name.toLowerCase(Locale.getDefault()).endsWith(".gm")
            }?.let {
                logs.addAll(it)
            }

            result.addAll(logs)
        }

        result.sortByDescending { it.lastModified() }

        return result
    }

    //
    // LIST ADAPTER
    //
    inner class CustomAdapter(private val mDataset: ArrayList<VideoFileModel>) :
        RecyclerView.Adapter<CustomAdapter.ListItemViewHolder>() {
        private val imageLoader = ThumbnailLoader()

        init {
            imageLoader.setStubId(android.R.drawable.ic_media_play)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListItemViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val inflated = inflater.inflate(R.layout.item_videofile, parent, false)

            return ListItemViewHolder(inflated)
        }

        override fun onBindViewHolder(holder: ListItemViewHolder, position: Int) {
            holder.nameTextView.text = mDataset[position].name
            holder.filepathTextView.text = mDataset[position].filepath

            imageLoader.displayImage(mDataset[position].filepath, holder.thumbnailView)
        }

        override fun getItemCount(): Int {
            return mDataset.size
        }

        private fun itemClicked(index: Int) {
            onVideoPicked(mDataset[index])
        }

        private fun uploadAtIndex(index: Int) {
            val it = mDataset[index]

            upload(it.filepath)
        }

        private fun isProtectedLog(index: Int): Boolean {
            val it = mDataset[index]

            return isProtectedLog(it.filepath)
        }

        @Suppress("SameParameterValue")
        private fun setProtectAtIndex(index: Int, protect: Boolean) {
            val it = mDataset[index]

            protect(it.filepath, protect)
            notifyDataSetChanged()
        }

        private fun exportAtIndex(index: Int) {
            val it = mDataset[index]

            val context = GEMApplication.applicationContext()

            val newFilepath = export(it.filepath)?.absolutePath

            val text = if (newFilepath != null) {
                mDataset[index].filepath = newFilepath
                "Exported!"
            } else "Not Exported!"

            notifyDataSetChanged()

            GEMApplication.postOnMain { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
        }

        private fun isInternalLog(index: Int): Boolean {
            val it = mDataset[index]

            return Util.isInternalLog(it.filepath)
        }

        private fun deleteAtIndex(index: Int) {
            val it = mDataset[index]
            mDataset.removeAt(index)

            val file = File(it.filepath)
            if (file.exists()) {
                file.delete()
            }

            notifyDataSetChanged()
        }

        @Suppress("DEPRECATION")
        inner class ListItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameTextView: TextView = view.findViewById(R.id.name)
            val filepathTextView: TextView = view.findViewById(R.id.filepath)
            val thumbnailView: ImageView = view.findViewById(R.id.thumbnail)

            init {
                view.setOnClickListener {
                    itemClicked(adapterPosition)
                }
                view.setOnLongClickListener { _ ->
                    val menu = PopupMenu(view.context, view)

                    if (isInternalLog(adapterPosition)) {
                        menu.menu.add("Export")
                        if (!isProtectedLog(adapterPosition))
                            menu.menu.add("Protect")
                    }

                    menu.menu.add("Upload")
                    menu.menu.add("Delete")
                    menu.show()

                    menu.setOnMenuItemClickListener {
                        val title = it.title.toString()

                        when (title.toLowerCase(Locale.getDefault())) {
                            "export" -> {
                                exportAtIndex(adapterPosition)
                            }
                            "protect" -> {
                                setProtectAtIndex(adapterPosition, true)
                            }
                            "upload" -> {
                                uploadAtIndex(adapterPosition)
                            }
                            "delete" -> {
                                deleteAtIndex(adapterPosition)
                            }
                            else -> {
                            }
                        }
                        true
                    }

                    true
                }
            }
        }
    }
}
