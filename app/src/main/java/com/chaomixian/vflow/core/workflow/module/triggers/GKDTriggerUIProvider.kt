package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.textfield.TextInputEditText
import java.io.File

class GKDTriggerViewHolder(
    view: View,
    val urlInput: TextInputEditText,
    val fileInput: TextInputEditText,
    val rulesDirPath: TextView,
    val downloadButton: Button
) : CustomEditorViewHolder(view)

class GKDTriggerUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("subscriptionUrl", "subscriptionFile")

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_gkd_trigger_editor, parent, false)
        val holder = GKDTriggerViewHolder(
            view,
            view.findViewById(R.id.input_subscription_url),
            view.findViewById(R.id.input_subscription_file),
            view.findViewById(R.id.text_rules_dir),
            view.findViewById(R.id.button_download)
        )

        // 显示规则目录
        val rulesDir = File(StorageManager.tempDir, "gkd_rules")
        holder.rulesDirPath.text = context.getString(R.string.gkd_rules_dir_hint, rulesDir.absolutePath)

        // 恢复状态
        val url = currentParameters["subscriptionUrl"] as? String
        val file = currentParameters["subscriptionFile"] as? String
        if (url != null) {
            holder.urlInput.setText(url)
        }
        if (file != null) {
            holder.fileInput.setText(file)
        }

        // 下载按钮点击事件
        holder.downloadButton.setOnClickListener {
            val url = holder.urlInput.text?.toString()?.trim()
            if (url.isNullOrBlank()) {
                return@setOnClickListener
            }

            holder.downloadButton.isEnabled = false
            holder.downloadButton.text = context.getString(R.string.gkd_downloading)

            // 异步下载
            Thread {
                try {
                    val client = okhttp3.OkHttpClient.Builder().build()
                    val request = okhttp3.Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null && body.isNotEmpty()) {
                            val fileName = url.substringAfterLast("/").ifEmpty { "subscription_${System.currentTimeMillis()}.json" }
                            val destFile = File(rulesDir, fileName)
                            destFile.writeText(body)

                            // 更新文件输入框
                            val finalFileName = fileName
                            view.post {
                                holder.fileInput.setText(finalFileName)
                                onParametersChanged()
                                holder.downloadButton.text = context.getString(R.string.gkd_download_success)
                            }
                        } else {
                            view.post {
                                holder.downloadButton.text = context.getString(R.string.gkd_download_empty)
                            }
                        }
                    } else {
                        view.post {
                            holder.downloadButton.text = context.getString(R.string.gkd_download_failed, response.code)
                        }
                    }
                } catch (e: Exception) {
                    view.post {
                        holder.downloadButton.text = context.getString(R.string.gkd_download_error, e.message)
                    }
                } finally {
                    view.post {
                        holder.downloadButton.isEnabled = true
                    }
                }
            }.start()
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as GKDTriggerViewHolder
        return mapOf(
            "subscriptionUrl" to h.urlInput.text?.toString(),
            "subscriptionFile" to h.fileInput.text?.toString()
        )
    }

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}
