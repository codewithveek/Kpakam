package app.kpakam.platform.files

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Writes text to a document the trader picked (Storage Access Framework). */
fun interface DocumentWriter {
    /** Writes [text] to the document at [uri]. Returns `false` when it could not be written. */
    suspend fun write(uri: String, text: String): Boolean
}

/** ContentResolver adapter for [DocumentWriter]. */
@Singleton
class AndroidDocumentWriter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DocumentWriter {
        override suspend fun write(uri: String, text: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
                    stream?.use { it.write(text.toByteArray()) }
                    stream != null
                } catch (e: IOException) {
                    false
                } catch (e: SecurityException) {
                    false
                }
            }
    }
