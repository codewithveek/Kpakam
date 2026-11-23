package app.kpakam.di

import android.content.Context
import androidx.room.Room
import app.kpakam.core.draft.DraftValidator
import app.kpakam.core.ids.IdGenerator
import app.kpakam.core.ids.SystemIdGenerator
import app.kpakam.core.language.LanguageScorer
import app.kpakam.core.prompts.PromptAssembler
import app.kpakam.inference.queue.KpakamDatabase
import app.kpakam.platform.assets.ContractAssets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** The process-wide scope for work that outlives a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/** Pure core objects built from the contract files in assets, plus app-wide infrastructure. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    fun idGenerator(): IdGenerator = SystemIdGenerator

    @Provides
    @Singleton
    fun promptAssembler(assets: ContractAssets): PromptAssembler = PromptAssembler(assets.prompts)

    @Provides
    @Singleton
    fun draftValidator(assets: ContractAssets): DraftValidator = DraftValidator(assets.draftSchema)

    @Provides
    @Singleton
    fun languageScorer(assets: ContractAssets): LanguageScorer = LanguageScorer(assets.lexicon)

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): KpakamDatabase =
        Room.databaseBuilder(context, KpakamDatabase::class.java, "kpakam.db").build()
}
