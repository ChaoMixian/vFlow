// 文件：LocalizationExtensions.kt
// 描述：本地化相关的扩展函数，提供便捷的Toast和对话框方法

package com.chaomixian.vflow.core.locale

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 显示Toast消息（使用字符串资源）
 *
 * @param resId 字符串资源ID
 * @param duration 显示时长，默认为Toast.LENGTH_SHORT
 */
fun Context.toast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, this.getString(resId), duration).show()
}

/**
 * 显示Toast消息（使用字符串）
 *
 * @param message 要显示的消息
 * @param duration 显示时长，默认为Toast.LENGTH_SHORT
 */
fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * 显示长时Toast消息（使用字符串资源）
 *
 * @param resId 字符串资源ID
 */
fun Context.longToast(@StringRes resId: Int) {
    Toast.makeText(this, this.getString(resId), Toast.LENGTH_LONG).show()
}

/**
 * 显示长时Toast消息（使用字符串）
 *
 * @param message 要显示的消息
 */
fun Context.longToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

/**
 * 显示确认对话框
 *
 * @param titleRes 对话框标题的资源ID
 * @param messageRes 对话框消息的资源ID
 * @param onConfirm 用户点击确认按钮时的回调
 */
fun AppCompatActivity.showConfirmDialog(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int,
    onConfirm: () -> Unit
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(titleRes)
        .setMessage(messageRes)
        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/**
 * 显示确认对话框（带字符串消息）
 *
 * @param titleRes 对话框标题的资源ID
 * @param message 对话框消息的字符串
 * @param onConfirm 用户点击确认按钮时的回调
 */
fun AppCompatActivity.showConfirmDialog(
    @StringRes titleRes: Int,
    message: String,
    onConfirm: () -> Unit
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(titleRes)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/**
 * 显示确认对话框（完全自定义）
 *
 * @param title 对话框标题的字符串
 * @param message 对话框消息的字符串
 * @param onConfirm 用户点击确认按钮时的回调
 */
fun AppCompatActivity.showConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/**
 * 显示警告对话框
 *
 * @param titleRes 对话框标题的资源ID
 * @param messageRes 对话框消息的资源ID
 */
fun AppCompatActivity.showAlertDialog(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(titleRes)
        .setMessage(messageRes)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

/**
 * 显示警告对话框（带字符串消息）
 *
 * @param titleRes 对话框标题的资源ID
 * @param message 对话框消息的字符串
 */
fun AppCompatActivity.showAlertDialog(
    @StringRes titleRes: Int,
    message: String
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(titleRes)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

/**
 * 显示警告对话框（完全自定义）
 *
 * @param title 对话框标题的字符串
 * @param message 对话框消息的字符串
 */
fun AppCompatActivity.showAlertDialog(
    title: String,
    message: String
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

/**
 * 显示带自定义按钮文本的对话框
 *
 * @param titleRes 对话框标题的资源ID
 * @param messageRes 对话框消息的资源ID
 * @param positiveButtonTextRes 确认按钮文本的资源ID
 * @param onConfirm 用户点击确认按钮时的回调
 */
fun AppCompatActivity.showDialogWithCustomButtons(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int,
    @StringRes positiveButtonTextRes: Int,
    onConfirm: () -> Unit
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(titleRes)
        .setMessage(messageRes)
        .setPositiveButton(positiveButtonTextRes) { _, _ -> onConfirm() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
