package com.example.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.data.model.HyperlinkUtils
import com.example.data.model.MomentHyperlink

object LinkAnnotationUtils {

    private var lastLaunchTimestamp = 0L
    private const val CLICK_DEBOUNCE_MS = 600L

    /**
     * Builds an AnnotatedString for a Moment note text with interactive, styled hyperlinks.
     * Combines explicit hyperlinks and raw non-overlapping HTTP/HTTPS URLs.
     */
    fun buildAnnotatedNote(
        note: String,
        explicitLinks: List<MomentHyperlink>,
        linkColor: Color
    ): AnnotatedString {
        if (note.isEmpty()) return AnnotatedString("")

        val displayLinks = HyperlinkUtils.getAllDisplayLinks(note, explicitLinks)

        return buildAnnotatedString {
            append(note)

            for (link in displayLinks) {
                val start = link.startIndex.coerceIn(0, note.length)
                val end = link.endIndex.coerceIn(0, note.length)
                if (start < end) {
                    addStyle(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold
                        ),
                        start = start,
                        end = end
                    )
                    addStringAnnotation(
                        tag = "URL",
                        annotation = link.url,
                        start = start,
                        end = end
                    )
                }
            }
        }
    }

    /**
     * Safely opens an external web link using an Android ACTION_VIEW intent.
     * Prevents duplicate rapid-clicks, verifies safe HTTP/HTTPS scheme, and catches all Activity/Security exceptions.
     */
    fun openSafeWebUrl(
        context: Context,
        rawUrl: String,
        onError: ((String) -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        if (now - lastLaunchTimestamp < CLICK_DEBOUNCE_MS) {
            return
        }
        lastLaunchTimestamp = now

        val normalized = HyperlinkUtils.normalizeUrl(rawUrl)
        if (normalized == null) {
            val msg = "Unable to open link: Invalid or unsupported web address."
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = Uri.parse(normalized)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val msg = "No application available to open this link."
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            val msg = "Security restriction prevented opening this link."
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Failed to open link."
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
