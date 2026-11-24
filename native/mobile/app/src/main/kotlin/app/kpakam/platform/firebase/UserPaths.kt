package app.kpakam.platform.firebase

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Firestore paths under `users/{uid}`. Every read and write goes through here. */
@Singleton
class UserPaths
    @Inject
    constructor(
        private val db: FirebaseFirestore,
        private val auth: AuthGateway,
    ) {
        suspend fun user(): DocumentReference = db.collection(USERS).document(auth.awaitUid())

        suspend fun journals(): CollectionReference = user().collection(JOURNALS)

        suspend fun customers(): CollectionReference = user().collection(CUSTOMERS)

        suspend fun aiEvents(): CollectionReference = user().collection(AI_EVENTS)

        /** Runs [block] for the signed-in user and restarts it if the uid changes. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun <T> forUser(block: (DocumentReference) -> Flow<T>): Flow<T> =
            auth.state
                .filterIsInstance<AuthState.SignedIn>()
                .map { it.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid -> block(db.collection(USERS).document(uid)) }

        companion object {
            const val USERS = "users"
            const val JOURNALS = "journals"
            const val CUSTOMERS = "customers"
            const val AI_EVENTS = "aiEvents"
        }
    }
