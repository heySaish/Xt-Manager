package com.xtmanager.runtime

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

open class NativeTerminalClient(
    private val context: Context,
    private val onSessionFinishedCallback: (() -> Unit)? = null
) : TerminalSessionClient, TerminalViewClient {

    var terminalView: TerminalView? = null

    companion object {
        private const val TAG = "NativeTerminalClient"
    }

    // TerminalSessionClient implementations
    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView?.post {
            terminalView?.onScreenUpdated(false)
        }
    }
    override fun onTitleChanged(changedSession: TerminalSession) {
        terminalView?.postInvalidate()
    }
    
    override fun onSessionFinished(finishedSession: TerminalSession) {
        Log.d(TAG, "Terminal session finished with exit status: ${finishedSession.exitStatus}")
        onSessionFinishedCallback?.invoke()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = android.content.ClipData.newPlainText("Terminal", text)
            clipboard?.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy text to clipboard", e)
        }
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasteText = clipData.getItemAt(0).coerceToText(context).toString()
                session?.write(pasteText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to paste text from clipboard", e)
        }
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int? = null

    var currentTextSize: Int = 36

    override fun onScale(scale: Float): Float {
        if (scale > 1.04f) {
            if (currentTextSize < 96) {
                currentTextSize += 2
                terminalView?.post {
                    terminalView?.setTextSize(currentTextSize)
                }
            }
            return 1.0f
        } else if (scale < 0.96f) {
            if (currentTextSize > 18) {
                currentTextSize -= 2
                terminalView?.post {
                    terminalView?.setTextSize(currentTextSize)
                }
            }
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        terminalView?.let { view ->
            view.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    var isCtrlActive: Boolean = false
    var isAltActive: Boolean = false

    override fun readControlKey(): Boolean {
        val ctrl = isCtrlActive
        if (ctrl) isCtrlActive = false
        return ctrl
    }

    override fun readAltKey(): Boolean {
        val alt = isAltActive
        if (alt) isAltActive = false
        return alt
    }

    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}

    // Logging methods
    override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: TAG, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "Exception", e) }
}
