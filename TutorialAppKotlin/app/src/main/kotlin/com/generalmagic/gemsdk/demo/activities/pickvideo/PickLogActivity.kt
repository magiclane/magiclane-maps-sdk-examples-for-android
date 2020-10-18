package com.generalmagic.gemsdk.demo.activities.pickvideo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.gemsdk.LogUploader
import com.generalmagic.gemsdk.LogUploaderListener
import com.generalmagic.gemsdk.TLogUploaderState
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.app.BaseActivity
import com.generalmagic.gemsdk.util.GEMError
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class PickLogActivity : BaseActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    companion object {
        const val RESULT_VIDEO_PATH = "RESULT_VIDEO_PATH"
        const val INPUT_DIR = "INPUT_DIR"
        private const val REQUEST_PERMISSIONS = 113
    }

    data class VideoFileModel(
        var name: String = "",
        var filepath: String = ""
    )

    private var hasStoragePermission = false
    private var inputDirectoryPath: String = ""

    private val uploadUsername = ""
    private val uploadEmail = "mstoica@generalmagic.com"
    private val uploadListener = object : LogUploaderListener() {
        override fun onLogStatusChanged(sLogPath: String, nProgress: Int, nStatus: Int) {
            if (nStatus < 0) {
                val error = GEMError.fromInt(nStatus)

                return
            }

            when (EnumHelp.fromInt<TLogUploaderState>(nStatus)) {
                TLogUploaderState.ELU_Progress -> {
                    //
                }
                TLogUploaderState.ELU_Ready -> {
                    Toast.makeText(
                        this@PickLogActivity,
                        "$nStatus - $sLogPath",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    private val logUploader = LogUploader.produce(uploadListener)

    // //////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_video)

        inputDirectoryPath = intent.getStringExtra(INPUT_DIR) ?: ""

        recyclerView = findViewById(R.id.list_files)

        viewManager = LinearLayoutManager(this)
        recyclerView.layoutManager = viewManager

        val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
        recyclerView.setPadding(lateralPadding, 0, lateralPadding, 0)
    }

    override fun onResume() {
        super.onResume()

        requestPermissions(this)

        if (hasStoragePermission) {
            updateListAdapter(inputDirectoryPath)
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

    override fun onBackPressed() {
        onVideoPickCanceled()
    }

    //
// Business code
//
    private fun upload(filepath: String) {
        val logUploader = logUploader ?: return
        val username = ""
        val email = "mstoica@generalmagic.com"
        val details = ""

        val error = GEMError.fromInt(logUploader.upload(filepath, username, email, details))

        if (error != GEMError.KNoError) {
            Toast.makeText(
                this@PickLogActivity,
                "uploadAtIndex error=$error",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateListAdapter(dirPath: String) {
        val dataModel = getVideoFileModels(dirPath)

        viewAdapter = CustomAdapter(dataModel)
        recyclerView.adapter = viewAdapter
    }

    private fun isValidModelPicked(model: VideoFileModel): Boolean {
        if (model.filepath.isEmpty() || !File(model.filepath).exists()) {
            return false
        }

        return true
    }

    private fun getVideoFileModels(dirPath: String): ArrayList<VideoFileModel> {
        val result = ArrayList<VideoFileModel>()
        val allVideoFiles = getAvailableFileLogs(dirPath)

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

    private fun getAvailableFileLogs(dirPath: String): ArrayList<File> {
        if (dirPath.isEmpty()) return ArrayList()
        if (!hasStoragePermission) return ArrayList()

        val moviesDir = File(dirPath)
        if (!moviesDir.exists() || !moviesDir.isDirectory) return ArrayList()

        // OBTAIN ALL FILES
        val logs = ArrayList<File>()

        moviesDir.listFiles { _, name ->
            name.toLowerCase(Locale.getDefault()).endsWith(".mp4")
        }?.let {
            logs.addAll(it)
        }
        moviesDir.listFiles { _, name ->
            name.toLowerCase(Locale.getDefault()).endsWith(".gm")
        }?.let {
            logs.addAll(it)
        }

        logs.sortByDescending { it.lastModified() }

        val result = ArrayList<File>()
        result.addAll(logs)

        return result
    }

    private fun getFileLastModifiedDate(pathStr: String): Date {
        val file = File(pathStr)
        return Date(file.lastModified())
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

        // ----------------------------------------------------------------
        private fun itemClicked(index: Int) {
            onVideoPicked(mDataset[index])
        }

        private fun uploadAtIndex(index: Int) {
            val it = mDataset[index]

            upload(it.filepath)
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

        // ----------------------------------------------------------------
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
                    menu.menu.add("Upload")
                    menu.menu.add("Delete")
                    menu.show()

                    menu.setOnMenuItemClickListener {
                        val title = it.title.toString()

                        when (title.toLowerCase(Locale.getDefault())) {
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

    //
//    permissions
//
    private fun requestPermissions(context: Activity) {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val hasPermissions = permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            ActivityCompat.requestPermissions(context, permissions, REQUEST_PERMISSIONS)
        } else {
            hasStoragePermission = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (grantResults.isEmpty()) {
            return
        }

        val result = grantResults[0]
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                // If request is cancelled, the result arrays are empty.
                if (result == PackageManager.PERMISSION_GRANTED) {
                    hasStoragePermission = true
                } else if (result == PackageManager.PERMISSION_DENIED) {
                    onVideoPickCanceled()

//                    val showRationale =
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
//                        } else {
//                            //show toast??
//                        }
//
//                    if (!showRationale) {
//                        showDialog("Storage permission is mandatory for log playback!")
//                    }
                }
            }
        }

        updateListAdapter(inputDirectoryPath)
    }
}
